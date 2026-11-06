package com.limelight.binding.input.driver;

import org.junit.Test;
import java.net.Inet6Address;
import java.net.InetAddress;
import static org.junit.Assert.*;

/** Cover literal parsing, LAN policy, peer authorization and diagnostic endpoint formatting. */
public class UsbIpNetworkTest {
    @Test
    public void requiresPairedTlsForPublicIpv6AndRejectsMalformedCredentials() throws Exception {
        InetAddress peer = UsbIpNetwork.parsePeer("2408:1234::2");
        assertFalse(UsbIpNetwork.isAllowedPeer(peer, false, false));
        assertTrue(UsbIpNetwork.isAllowedPeer(peer, false, true));
        assertTrue(UsbIpTls.validToken("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"));
        for (String invalid : new String[]{null, "", "bad", "0123456789", "Z".repeat(64)}) assertFalse(UsbIpTls.validToken(invalid));
        assertFalse(UsbIpNetwork.isAllowedPeer(InetAddress.getByName("8.8.8.8"), false, true));
        assertFalse(UsbIpNetwork.isAllowedPeer(InetAddress.getByName("::1"), false, true));
        assertFalse(UsbIpNetwork.isAllowedPeer(InetAddress.getByName("ff02::1"), false, true));
    }
    @Test
    public void usesOnlyPairedNumericSameFamilyImportSources() throws Exception {
        String original = "2001:db8::228";
        String temporary = "2001:db8::1234";
        assertEquals(UsbIpNetwork.parsePeer(temporary), UsbIpNetwork.resolvePeer(original, temporary));
        for (String advertised : new String[]{null, "", "host.invalid", "::", "::1", "ff02::1", "fe80::2%11", "192.168.1.2"}) {
            assertEquals(UsbIpNetwork.parsePeer(original), UsbIpNetwork.resolvePeer(original, advertised));
        }
        assertEquals(UsbIpNetwork.parsePeer("192.168.1.2"), UsbIpNetwork.resolvePeer("192.168.1.2", temporary));
        assertNull(UsbIpNetwork.resolvePeer(null, temporary));
        assertEquals(20, ((Inet6Address) UsbIpNetwork.resolvePeer("fe80::2%20", "fe80::2%11")).getScopeId());
    }
    @Test
    public void acceptsLocalIpv6AndMappedIpv4() throws Exception {
        for (String host : new String[]{"fd12:3456::2", "fc00::2", "fe80::1234%7", "[fd00::2]", "2001:db8::2", "::ffff:192.168.1.2"}) {
            assertNotNull(host, UsbIpNetwork.parsePeer(host));
        }
        for (String host : new String[]{null, "", "example.com", "::", "::1", "ff02::1", "fe80::1", "fec0::1",
                "::ffff:8.8.8.8", "[fd00::2]:3240", "fd00:::2", "fd00::2%", "192.168.1.999"}) {
            assertNull(host, UsbIpNetwork.parsePeer(host));
        }
        assertFalse(UsbIpNetwork.isAllowedPeer(InetAddress.getByName("2001:db8::2"), false));
        assertTrue(UsbIpNetwork.isAllowedPeer(InetAddress.getByName("2001:db8::2"), true));
        assertTrue(UsbIpNetwork.isAllowedPeer(InetAddress.getByName("fd00::2"), false));
    }

    @Test
    public void authorizesExactAddressAndLinkLocalScope() throws Exception {
        InetAddress peer = UsbIpNetwork.parsePeer("fe80::1234%7");
        assertTrue(UsbIpNetwork.samePeer(peer, UsbIpNetwork.parsePeer("fe80::1234%7")));
        assertFalse(UsbIpNetwork.samePeer(peer, UsbIpNetwork.parsePeer("fe80::1234%8")));
        assertFalse(UsbIpNetwork.samePeer(peer, UsbIpNetwork.parsePeer("fe80::5678%7")));
        assertTrue(UsbIpNetwork.samePeer(UsbIpNetwork.parsePeer("192.168.1.2"), UsbIpNetwork.parsePeer("::ffff:192.168.1.2")));
        assertFalse(UsbIpNetwork.samePeer(UsbIpNetwork.parsePeer("192.168.1.2"), UsbIpNetwork.parsePeer("fd00::2")));
    }

    @Test
    public void bracketsIpv6EndpointsAndPreservesScope() throws Exception {
        assertEquals("192.168.1.2:3240", UsbIpNetwork.endpoint(UsbIpNetwork.parsePeer("192.168.1.2")));
        InetAddress peer = UsbIpNetwork.parsePeer("fe80::1234%7");
        assertEquals("[" + peer.getHostAddress() + "]:3240", UsbIpNetwork.endpoint(peer));
        assertTrue(UsbIpNetwork.localAddress(InetAddress.getByName("::1")) instanceof Inet6Address);
        assertTrue(UsbIpNetwork.localAddress(InetAddress.getByName("127.0.0.1")).isLoopbackAddress());
        assertEquals(16, UsbIpNetwork.wildcard(peer).getAddress().length);
        assertEquals(4, UsbIpNetwork.wildcard(UsbIpNetwork.parsePeer("192.168.1.2")).getAddress().length);
        assertTrue(UsbIpNetwork.wildcard(peer).isAnyLocalAddress());
    }
}
