package com.limelight.binding.input.driver;

import com.limelight.nvstream.http.LimelightCryptoProvider;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;

/** TLS server identity reuses Moonlight's existing paired certificate; no new trust prompt or persistent secret. */
final class UsbIpTls {
    private UsbIpTls() {}

    /** Require exactly the host-generated 256-bit hexadecimal session credential. */
    static boolean validToken(String token) {
        return token != null && token.matches("[0-9a-fA-F]{64}");
    }

    /** Create a modern TLS listener. Session-token authentication happens before any USB/IP operation. */
    static ServerSocket createServer(LimelightCryptoProvider crypto) throws IOException {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            char[] password = new char[0];
            store.setKeyEntry("usbip", crypto.getClientPrivateKey(), password,
                    new Certificate[]{crypto.getClientCertificate()});
            KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(store, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, new SecureRandom());
            SSLServerSocket socket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
            ArrayList<String> protocols = new ArrayList<>();
            for (String supported : socket.getSupportedProtocols()) {
                if (supported.equals("TLSv1.2") || supported.equals("TLSv1.3")) protocols.add(supported);
            }
            socket.setEnabledProtocols(protocols.toArray(new String[0]));
            return socket;
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to initialize paired USB/IP TLS identity", e);
        }
    }
}
