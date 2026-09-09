package com.limelight.utils;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class PairingBackupArchiveTest {
    private Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(PairingBackupArchive.DATABASE, new byte[] {0, 1, 2, 3, -1});
        files.put(PairingBackupArchive.CERTIFICATE, new byte[] {4, 5, 6});
        files.put(PairingBackupArchive.PRIVATE_KEY, new byte[] {7, 8, 9});
        return files;
    }

    private byte[] rawZip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private void assertRejected(byte[] bytes) {
        assertThrows(IOException.class, () -> PairingBackupArchive.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void roundTripPreservesAllThreeBinaryFiles() throws Exception {
        Map<String, byte[]> source = files();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PairingBackupArchive.write(output, source);
        Map<String, byte[]> restored = PairingBackupArchive.read(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(source.keySet(), restored.keySet());
        for (String name : source.keySet()) {
            assertArrayEquals(source.get(name), restored.get(name));
        }
    }

    @Test
    public void missingKeyIsRejectedBeforeRestore() throws Exception {
        Map<String, byte[]> files = files();
        files.remove(PairingBackupArchive.PRIVATE_KEY);
        assertRejected(rawZip(files));
    }

    @Test
    public void emptyCertificateIsRejected() throws Exception {
        Map<String, byte[]> files = files();
        files.put(PairingBackupArchive.CERTIFICATE, new byte[0]);
        assertRejected(rawZip(files));
    }

    @Test
    public void pathsOutsideBackupRootAreRejected() throws Exception {
        Map<String, byte[]> files = files();
        files.put("../client.key", new byte[] {1});
        assertRejected(rawZip(files));
    }

    @Test
    public void duplicateEntryIsRejected() throws Exception {
        Map<String, byte[]> files = files();
        files.put("clienx.key", new byte[] {1});
        byte[] zip = rawZip(files);
        byte[] name = "clienx.key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i <= zip.length - name.length; i++) {
            if (Arrays.equals(name, Arrays.copyOfRange(zip, i, i + name.length))) {
                zip[i + 5] = 't';
            }
        }
        assertRejected(zip);
    }

    @Test
    public void nonZipAndTruncatedPayloadAreRejected() throws Exception {
        assertRejected(new byte[] {1, 2, 3});
        byte[] zip = rawZip(files());
        assertRejected(Arrays.copyOf(zip, 48));
    }

    @Test
    public void decompressedSizeLimitIsEnforced() throws Exception {
        Map<String, byte[]> files = files();
        files.put(PairingBackupArchive.DATABASE, new byte[32 * 1024 * 1024]);
        assertRejected(rawZip(files));
    }

    @Test
    public void incompleteExportDoesNotWritePartialArchive() {
        Map<String, byte[]> files = files();
        files.remove(PairingBackupArchive.CERTIFICATE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> PairingBackupArchive.write(output, files));
        assertEquals(0, output.size());
    }
}
