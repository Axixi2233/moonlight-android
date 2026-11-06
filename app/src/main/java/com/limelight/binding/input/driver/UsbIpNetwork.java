package com.limelight.binding.input.driver;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;

/** Numeric stream-peer validation and address-family selection for the session-owned USB/IP export. */
final class UsbIpNetwork {
    private UsbIpNetwork() {}

    /** Parse only literal unicast addresses; never resolve hostnames supplied to USB/IP. */
    static InetAddress parsePeer(String host) {
        if (host == null) return null;
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        try {
            InetAddress peer;
            if (host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
                String[] parts = host.split("\\.");
                byte[] bytes = new byte[4];
                for (int i = 0; i < bytes.length; ++i) {
                    int part = Integer.parseInt(parts[i]);
                    if (part > 255) return null;
                    bytes[i] = (byte) part;
                }
                peer = InetAddress.getByAddress(bytes);
            } else if (host.contains(":") && host.matches("[0-9a-fA-F:.]+(%[a-zA-Z0-9_.-]+)?")) {
                peer = InetAddress.getByName(host);
            } else {
                return null;
            }
            return isAllowedPeer(peer, true) ? peer : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Select a paired host's advertised source without changing family or reusing remote scope IDs. */
    static InetAddress resolvePeer(String streamHost, String importHost) {
        InetAddress original = parsePeer(streamHost);
        InetAddress advertised = parsePeer(importHost);
        // A zone ID is local to each machine. Never reuse the PC's link-local scope on Android.
        if (original instanceof Inet6Address && !original.isLinkLocalAddress()
                && advertised instanceof Inet6Address && !advertised.isLinkLocalAddress()) return advertised;
        return original;
    }

    /** Global IPv6 addresses are local only when the selected host has a direct on-link route. */
    static boolean isAllowedPeer(InetAddress peer, boolean onLink) {
        return isAllowedPeer(peer, onLink, false);
    }

    /** Public IPv6 requires paired TLS; IPv4 remains limited to private LAN addresses. */
    static boolean isAllowedPeer(InetAddress peer, boolean onLink, boolean encrypted) {
        if (peer == null) return false;
        if (peer instanceof Inet4Address) return peer.isSiteLocalAddress();
        int first = peer.getAddress()[0] & 255;
        return (first & 0xfe) == 0xfc
                || (peer.isLinkLocalAddress() && ((Inet6Address) peer).getScopeId() > 0)
                || ((first & 0xe0) == 0x20 && (onLink || encrypted));
    }

    /** Query the OS-selected source on a worker thread without transmitting a UDP packet. */
    static InetAddress localAddress(InetAddress peer) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.connect(new InetSocketAddress(peer, UsbIpControllerExport.PORT));
            InetAddress local = socket.getLocalAddress();
            if (local.isAnyLocalAddress() || local.getAddress().length != peer.getAddress().length) {
                throw new IOException("No USB/IP route for the streaming address family");
            }
            return local;
        }
    }

    /** Bind the streaming family, allowing all local privacy addresses while authorizing one remote peer. */
    static InetAddress wildcard(InetAddress peer) throws IOException {
        return InetAddress.getByAddress(new byte[peer.getAddress().length]);
    }

    /** Include brackets around IPv6 so the port remains unambiguous in the performance overlay. */
    static String endpoint(InetAddress address) {
        String host = address.getHostAddress();
        return (address instanceof Inet6Address ? "[" + host + "]" : host) + ":" + UsbIpControllerExport.PORT;
    }

    /** Authorize only the selected peer, including its local interface for a link-local IPv6 connection. */
    static boolean samePeer(InetAddress expected, InetAddress actual) {
        return Arrays.equals(expected.getAddress(), actual.getAddress())
                && (!expected.isLinkLocalAddress() || (actual instanceof Inet6Address
                && ((Inet6Address) expected).getScopeId() == ((Inet6Address) actual).getScopeId()));
    }
}
