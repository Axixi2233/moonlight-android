package com.limelight.utils;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;

import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.nvstream.http.ComputerDetails;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PairingBackupManager {
    private PairingBackupManager() {}

    public static synchronized void exportBackup(Context context, Uri destination) throws Exception {
        AndroidCryptoProvider crypto = new AndroidCryptoProvider(context);
        crypto.getClientCertificate();
        File snapshot = File.createTempFile("pairing-export-", ".db", context.getCacheDir());
        try {
            ComputerDatabaseManager database = new ComputerDatabaseManager(context);
            try {
                database.writeBackup(snapshot);
            } finally {
                database.close();
            }
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put(PairingBackupArchive.DATABASE, readFile(snapshot));
            files.put(PairingBackupArchive.CERTIFICATE, readFile(identityFile(context, PairingBackupArchive.CERTIFICATE)));
            files.put(PairingBackupArchive.PRIVATE_KEY, readFile(identityFile(context, PairingBackupArchive.PRIVATE_KEY)));
            validateIdentity(files);
            try (OutputStream output = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) {
                    throw new IOException("Cannot open backup destination");
                }
                PairingBackupArchive.write(output, files);
            }
        } finally {
            context.deleteDatabase(snapshot.getAbsolutePath());
        }
    }

    public static synchronized void importBackup(Context context, Uri source) throws Exception {
        Map<String, byte[]> files;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) {
                throw new IOException("Cannot open backup");
            }
            files = PairingBackupArchive.read(input);
        }
        validateIdentity(files);
        File staging = File.createTempFile("pairing-import-", ".db", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(staging)) {
                output.write(files.get(PairingBackupArchive.DATABASE));
            }
            List<ComputerDetails> computers;
            ComputerDatabaseManager imported = new ComputerDatabaseManager(context, staging);
            try {
                computers = imported.getAllComputers();
                for (ComputerDetails computer : computers) {
                    if (computer.uuid == null || computer.uuid.isEmpty() || computer.name == null) {
                        throw new IOException("Invalid host database");
                    }
                }
            } finally {
                imported.close();
            }

            File certificate = identityFile(context, PairingBackupArchive.CERTIFICATE);
            File key = identityFile(context, PairingBackupArchive.PRIVATE_KEY);
            byte[] oldCertificate = certificate.exists() ? readFile(certificate) : null;
            byte[] oldKey = key.exists() ? readFile(key) : null;
            ComputerDatabaseManager database = new ComputerDatabaseManager(context);
            try {
                writeAtomic(certificate, files.get(PairingBackupArchive.CERTIFICATE));
                writeAtomic(key, files.get(PairingBackupArchive.PRIVATE_KEY));
                database.restoreComputers(computers);
            } catch (Exception failure) {
                // Restore the previous identity if either file or the database transaction fails.
                try {
                    writeAtomic(certificate, oldCertificate);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                try {
                    writeAtomic(key, oldKey);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                database.close();
            }
        } finally {
            context.deleteDatabase(staging.getAbsolutePath());
        }
    }

    private static File identityFile(Context context, String name) {
        return new File(context.getFilesDir(), name);
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 32 * 1024 * 1024) {
                    throw new IOException("Backup file is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void writeAtomic(File file, byte[] bytes) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        if (bytes == null) {
            atomic.delete();
            return;
        }
        FileOutputStream output = atomic.startWrite();
        try {
            output.write(bytes);
            atomic.finishWrite(output);
        } catch (IOException e) {
            atomic.failWrite(output);
            throw e;
        }
    }

    static void validateIdentity(Map<String, byte[]> files) throws GeneralSecurityException {
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(files.get(PairingBackupArchive.CERTIFICATE)));
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(files.get(PairingBackupArchive.PRIVATE_KEY)));
        Signature signature = Signature.getInstance("SHA256withRSA");
        byte[] challenge = {0x4d, 0x4c, 0x42, 0x4b};
        signature.initSign(key);
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(certificate.getPublicKey());
        signature.update(challenge);
        if (!signature.verify(signed)) {
            throw new GeneralSecurityException("The backup certificate and private key do not match");
        }
    }
}
