package com.limelight.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** The three pairing files, with fixed entry names and bounded decompression. */
public final class PairingBackupArchive {
    public static final String DATABASE = "computers4.db";
    public static final String CERTIFICATE = "client.crt";
    public static final String PRIVATE_KEY = "client.key";
    private static final List<String> NAMES = Arrays.asList(DATABASE, CERTIFICATE, PRIVATE_KEY);
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    private PairingBackupArchive() {}

    public static void write(OutputStream output, Map<String, byte[]> files) throws IOException {
        validate(files);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String name : NAMES) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(files.get(name));
                zip.closeEntry();
            }
        }
    }

    public static Map<String, byte[]> read(InputStream input) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !NAMES.contains(name) || files.containsKey(name)) {
                    throw new IOException("Unexpected or duplicate backup entry: " + name);
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) {
                        throw new IOException("Backup is too large");
                    }
                    bytes.write(buffer, 0, count);
                }
                zip.closeEntry();
                files.put(name, bytes.toByteArray());
            }
        }
        validate(files);
        return files;
    }

    private static void validate(Map<String, byte[]> files) throws IOException {
        long total = 0;
        if (files.size() != NAMES.size()) {
            throw new IOException("Backup must contain the database, certificate and private key");
        }
        for (String name : NAMES) {
            byte[] bytes = files.get(name);
            if (bytes == null || bytes.length == 0) {
                throw new IOException("Missing or empty backup entry: " + name);
            }
            total += bytes.length;
        }
        if (total > MAX_BYTES) {
            throw new IOException("Backup is too large");
        }
    }
}
