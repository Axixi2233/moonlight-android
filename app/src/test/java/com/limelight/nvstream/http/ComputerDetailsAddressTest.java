package com.limelight.nvstream.http;

import org.junit.Test;

import static org.junit.Assert.*;

public class ComputerDetailsAddressTest {
    private static ComputerDetails.AddressTuple address(String host, int port) {
        return new ComputerDetails.AddressTuple(host, port);
    }

    @Test
    public void discoveredLanAddressIsSelectableWithoutManualHistory() {
        ComputerDetails computer = new ComputerDetails();
        computer.localAddress = address("192.168.1.10", 47989);
        computer.remoteAddress = address("203.0.113.10", 48000);
        computer.ipv6Address = address("2001:db8::1", 47989);
        assertEquals(computer.localAddress, computer.getSelectableAddresses().get(0));
        assertTrue(computer.getSelectableAddresses().contains(computer.remoteAddress));
        assertTrue(computer.getSelectableAddresses().contains(computer.ipv6Address));
        assertTrue(computer.getManualAddressHistory().isEmpty());
    }

    @Test
    public void routeSourcesDeduplicateWithoutDroppingDifferentPorts() {
        ComputerDetails computer = new ComputerDetails();
        computer.localAddress = address("192.168.1.10", 47989);
        computer.manualAddress = address("192.168.1.10", 47989);
        computer.activeAddress = address("192.168.1.10", 47989);
        computer.rememberManualAddress(address("192.168.1.10", 47989));
        computer.rememberManualAddress(address("PC.example.com", 48000));
        computer.remoteAddress = address("pc.example.com", 48000);
        computer.preferredAddress = address("192.168.1.10", 48000);
        assertEquals(3, computer.getSelectableAddresses().size());
        assertTrue(computer.getSelectableAddresses().contains(computer.preferredAddress));
    }

    @Test
    public void pinnedLanRouteRemainsSelectableAfterDiscoveryChanges() {
        ComputerDetails computer = new ComputerDetails();
        computer.preferredAddress = address("192.168.1.10", 47989);
        computer.localAddress = address("192.168.1.11", 47989);
        computer.activeAddress = address("pc.example.com", 48000);
        assertTrue(computer.getSelectableAddresses().contains(computer.preferredAddress));
        assertTrue(computer.getSelectableAddresses().contains(computer.activeAddress));
        computer.getSelectableAddresses().get(0).port = 1234;
        assertEquals(47989, computer.localAddress.port);
    }

    @Test
    public void addingSameHostAtNewAddressRetainsPreviousAddress() {
        ComputerDetails saved = new ComputerDetails();
        saved.manualAddress = address("192.168.1.10", 47989);
        ComputerDetails added = new ComputerDetails();
        added.manualAddress = address("pc.example.com", 48000);
        saved.update(added);
        assertEquals(2, saved.getManualAddressHistory().size());
        assertEquals("192.168.1.10", saved.getManualAddressHistory().get(0).address);
        assertEquals("pc.example.com", saved.getManualAddressHistory().get(1).address);
    }

    @Test
    public void hostnamesDeduplicateButDifferentPortsRemainSelectable() {
        ComputerDetails saved = new ComputerDetails();
        saved.rememberManualAddress(address("PC.example.com", 47989));
        saved.rememberManualAddress(address("pc.example.com", 47989));
        saved.rememberManualAddress(address("pc.example.com", 48000));
        assertEquals(2, saved.getManualAddressHistory().size());
    }

    @Test
    public void serverinfoCannotClearOrReplaceUserSelection() {
        ComputerDetails saved = new ComputerDetails();
        saved.preferredAddress = address("pc.example.com", 48000);
        ComputerDetails poll = new ComputerDetails();
        poll.localAddress = address("192.168.1.10", 47989);
        saved.update(poll);
        assertEquals(address("pc.example.com", 48000), saved.preferredAddress);
        // A stale snapshot must not reinstate manual mode after choosing automatic mode.
        ComputerDetails stale = new ComputerDetails(saved);
        saved.preferredAddress = null;
        saved.update(stale);
        assertNull(saved.preferredAddress);
    }

    @Test
    public void failedCandidateDoesNotMutateLiveAddressesOrHistory() {
        ComputerDetails live = new ComputerDetails();
        live.manualAddress = address("pc.example.com", 47989);
        live.remoteAddress = address("203.0.113.10", 47989);
        live.preferredAddress = address("pc.example.com", 47989);
        ComputerDetails candidate = new ComputerDetails(live);
        candidate.remoteAddress.port = 48000;
        candidate.preferredAddress.port = 48000;
        candidate.rememberManualAddress(address("other.example.com", 47989));
        assertEquals(47989, live.remoteAddress.port);
        assertEquals(47989, live.preferredAddress.port);
        assertTrue(live.getManualAddressHistory().isEmpty());
        candidate.getManualAddressHistory().get(0).port = 1234;
        assertEquals(47989, candidate.getManualAddressHistory().get(0).port);
    }

    @Test
    public void mergingDiscoveryRetainsEntireHistoryAndIpv6Port() {
        ComputerDetails saved = new ComputerDetails();
        saved.rememberManualAddress(address("[2001:db8::1]", 48000));
        saved.rememberManualAddress(address("pc.example.com", 47989));
        ComputerDetails discovery = new ComputerDetails();
        discovery.localAddress = address("192.168.1.10", 47989);
        saved.update(discovery);
        saved.update(saved);
        assertEquals(2, saved.getManualAddressHistory().size());
        assertEquals("[2001:db8::1]:48000", saved.getManualAddressHistory().get(0).toString());
    }
}
