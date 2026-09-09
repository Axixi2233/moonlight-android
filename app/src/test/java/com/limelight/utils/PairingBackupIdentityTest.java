package com.limelight.utils;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertThrows;

public class PairingBackupIdentityTest {
    private Map<String, byte[]> identity(boolean matching) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair pair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=Backup Test");
        byte[] certificate = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                new Date(0), new Date(4102444800000L), name, pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())).getEncoded();
        Map<String, byte[]> files = new HashMap<>();
        files.put(PairingBackupArchive.CERTIFICATE, certificate);
        files.put(PairingBackupArchive.PRIVATE_KEY,
                (matching ? pair : generator.generateKeyPair()).getPrivate().getEncoded());
        return files;
    }

    @Test
    public void matchingIdentityIsAccepted() throws Exception {
        PairingBackupManager.validateIdentity(identity(true));
    }

    @Test
    public void exportedPemCertificateIsAccepted() throws Exception {
        Map<String, byte[]> files = identity(true);
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(files.get(PairingBackupArchive.CERTIFICATE))
                + "\n-----END CERTIFICATE-----\n";
        files.put(PairingBackupArchive.CERTIFICATE, pem.getBytes(StandardCharsets.US_ASCII));
        PairingBackupManager.validateIdentity(files);
    }

    @Test
    public void mismatchedIdentityIsRejected() throws Exception {
        Map<String, byte[]> files = identity(false);
        assertThrows(GeneralSecurityException.class, () -> PairingBackupManager.validateIdentity(files));
    }

    @Test
    public void malformedCertificateIsRejected() throws Exception {
        Map<String, byte[]> files = identity(true);
        files.put(PairingBackupArchive.CERTIFICATE, new byte[] {1, 2, 3});
        assertThrows(GeneralSecurityException.class, () -> PairingBackupManager.validateIdentity(files));
    }
}
