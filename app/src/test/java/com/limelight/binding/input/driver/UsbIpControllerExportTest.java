package com.limelight.binding.input.driver;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UsbIpControllerExportTest {
    @Test
    public void stallsOnlyTheExactReservedBusProbe() {
        assertTrue(UsbIpControllerExport.isReservedBusProbe(0, 0x1a, 0, 0xffff, 0));
        assertFalse(UsbIpControllerExport.isReservedBusProbe(0x40, 0x1a, 0, 0xffff, 0));
        assertFalse(UsbIpControllerExport.isReservedBusProbe(0xc0, 0x90, 0, 4, 32));
        assertFalse(UsbIpControllerExport.isReservedBusProbe(0, 9, 0, 0xffff, 0));
        assertFalse(UsbIpControllerExport.isReservedBusProbe(0, 0x1a, 1, 0xffff, 0));
        assertFalse(UsbIpControllerExport.isReservedBusProbe(0, 0x1a, 0, 0, 0));
        assertFalse(UsbIpControllerExport.isReservedBusProbe(0, 0x1a, 0, 0xffff, 1));
    }

    @Test
    public void controlUrbsPreserveRawSetupAndPayloadWithoutNetworkByteSwapping() {
        byte[] setup = new byte[28];
        byte[] raw = new byte[]{0x40, (byte) 0x90, 0x34, 0x12, 4, 0, 3, 0};
        System.arraycopy(raw, 0, setup, 20, 8);
        byte[] buffer = UsbIpControllerExport.buildControlBuffer(setup, new byte[]{1, 2, 3, 4});
        assertArrayEquals(raw, Arrays.copyOf(buffer, 8));
        assertArrayEquals(new byte[]{1, 2, 3}, Arrays.copyOfRange(buffer, 8, buffer.length));
        setup[20] = (byte) 0xc0;
        buffer = UsbIpControllerExport.buildControlBuffer(setup, new byte[3]);
        assertEquals(11, buffer.length);
        assertEquals((byte) 0xc0, buffer[0]);
        assertArrayEquals(new byte[3], Arrays.copyOfRange(buffer, 8, 11));
        setup[26] = setup[27] = 0;
        assertEquals(8, UsbIpControllerExport.buildControlBuffer(setup, new byte[0]).length);
    }

    @Test
    public void exportsHidAndXboxInterfacesWithoutAVendorWhitelist() {
        assertTrue(UsbIpControllerExport.supportsInterface(3, 1, 1));
        assertTrue(UsbIpControllerExport.supportsInterface(3, 1, 2));
        assertTrue(UsbIpControllerExport.supportsInterface(3, 0, 0));
        assertTrue(UsbIpControllerExport.supportsInterface(255, 0x5d, 1));
        assertTrue(UsbIpControllerExport.supportsInterface(255, 0x5d, 0x81));
        assertTrue(UsbIpControllerExport.supportsInterface(255, 0x47, 0xd0));
        assertFalse(UsbIpControllerExport.supportsInterface(8, 6, 0x50));
        assertFalse(UsbIpControllerExport.supportsInterface(9, 0, 0));
        assertFalse(UsbIpControllerExport.supportsInterface(255, 0, 0));
    }

    @Test
    public void retiresVirtualPadsOnlyForControllerApplicationCollections() {
        assertTrue(UsbIpControllerExport.isGamepadReport(new byte[]{5, 1, 9, 5, (byte) 0xa1, 1}));
        assertTrue(UsbIpControllerExport.isGamepadReport(new byte[]{5, 1, 9, 4, (byte) 0xa1, 1}));
        assertTrue(UsbIpControllerExport.isGamepadReport(new byte[]{0x0b, 5, 0, 1, 0, (byte) 0xa1, 1}));
        assertFalse(UsbIpControllerExport.isGamepadReport(new byte[]{5, 1, 9, 2, (byte) 0xa1, 1}));
        assertFalse(UsbIpControllerExport.isGamepadReport(new byte[]{5, 1, 9, 6, (byte) 0xa1, 1}));
        assertFalse(UsbIpControllerExport.isGamepadReport(new byte[]{6, 0, (byte) 0xff, 9, 5, (byte) 0xa1, 1}));
        assertFalse(UsbIpControllerExport.isGamepadReport(new byte[]{5, 1, 9}));
        assertFalse(UsbIpControllerExport.isGamepadReport(new byte[]{(byte) 0xfe}));
        assertFalse(UsbIpControllerExport.isGamepadReport(new byte[]{5, 1, 9, 5, (byte) 0x81, 0, (byte) 0xa1, 1}));
        assertTrue(UsbIpControllerExport.isGamepadReport(new byte[]{
                (byte) 0xfe, 1, 2, 3, 5, 1, 9, 5, (byte) 0xa1, 1}));
    }

    @Test
    public void taggedUsbfsHandlesAreNotErrnos() {
        assertFalse(UsbIpControllerExport.isUsbfsError(-5476376657256469248L));
        assertFalse(UsbIpControllerExport.isUsbfsError(0xf8002000L));
        assertFalse(UsbIpControllerExport.isUsbfsError(0));
        assertTrue(UsbIpControllerExport.isUsbfsError(-19));
        assertTrue(UsbIpControllerExport.isUsbfsError(-4095));
    }

    @Test
    public void acceptsOnlyPrivateIpv4StreamHosts() {
        for (String host : new String[]{"192.168.1.10", "10.0.0.2", "172.31.1.3"}) {
            assertEquals(host, UsbIpControllerExport.parsePeer(host).getHostAddress());
        }
        for (String host : new String[]{null, "", "localhost", "127.0.0.1", "192.168.1.256",
                "192.168.1", "::1", "8.8.8.8", "172.32.0.1", "192.168.1.2:47989"}) {
            assertNull(host, UsbIpControllerExport.parsePeer(host));
        }
    }

    @Test
    public void interruptReplyUsesNetworkByteOrderAndNonIsoPacketMarker() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        UsbIpControllerExport.writeSubmitHeader(new DataOutputStream(bytes), 0x12345678, 0, 64, 0);
        assertEquals(48, bytes.size());
        ByteBuffer header = ByteBuffer.wrap(bytes.toByteArray());
        assertEquals(3, header.getInt(0));
        assertEquals(0x12345678, header.getInt(4));
        assertEquals(0, header.getInt(8));
        assertEquals(64, header.getInt(24));
        assertEquals(-1, header.getInt(32));
        assertEquals(0, header.getInt(36));
    }

    @Test
    public void speakerPacketsRemoveTransferBufferGaps() throws IOException {
        UsbIpControllerExport.IsoPackets packets = packets(9, 0, 3, 5, 2);
        assertArrayEquals(new int[]{3, 2}, packets.lengths);
        assertEquals(3, packets.maxLength);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5},
                packets.makeUsbBuffer(new byte[]{1, 2, 3, 0, 0, 4, 5, 0, 0}));
        assertEquals(5, packets.complete(new int[]{0, 5, 10, 3, 0, 2, 0}, null));
        assertEquals(0, packets.errors);
    }

    @Test
    public void microphonePacketsCompactShortReadsAndPreservePacketOffsets() throws IOException {
        UsbIpControllerExport.IsoPackets packets = packets(12, 0, 4, 4, 4, 8, 4);
        byte[] data = packets.makeUsbBuffer(null);
        data[0] = 1;
        data[1] = 2;
        data[8] = 3;
        data[9] = 4;
        data[10] = 5;
        int length = packets.complete(new int[]{0, 5, 10, 2, 0, 0, -18, 3, 0}, data);
        assertEquals(5, length);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, Arrays.copyOf(data, length));
        assertEquals(8, packets.descriptors.getInt(32));
        assertEquals(3, packets.descriptors.getInt(40));
        assertEquals(-18, packets.descriptors.getInt(28));
        assertEquals(1, packets.errors);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        UsbIpControllerExport.writeSubmitHeader(new DataOutputStream(bytes), 7, 0, length, 3, 10, packets.errors);
        ByteBuffer header = ByteBuffer.wrap(bytes.toByteArray());
        assertEquals(5, header.getInt(24));
        assertEquals(10, header.getInt(28));
        assertEquals(3, header.getInt(32));
        assertEquals(1, header.getInt(36));
    }

    @Test
    public void rejectsInvalidPacketRangesAndCompletionLengths() throws IOException {
        assertThrows(IOException.class, () -> packets(8, 2, 4, 4, 2));
        assertThrows(IOException.class, () -> packets(8, 7, 2));
        assertThrows(IOException.class, () -> packets(8, 0, -1));
        UsbIpControllerExport.IsoPackets packets = packets(8, 0, 4, 4, 4);
        assertThrows(IOException.class, () -> packets.complete(new int[]{0, 6, 0, 6, 0, 0, 0}, null));
        packets.fail(-32);
        assertEquals(2, packets.errors);
        assertEquals(0, packets.descriptors.getInt(8));
        assertEquals(-32, packets.descriptors.getInt(12));
    }

    private static UsbIpControllerExport.IsoPackets packets(int length, int... ranges) throws IOException {
        ByteBuffer descriptors = ByteBuffer.allocate(ranges.length * 8);
        for (int i = 0; i < ranges.length; i += 2) {
            descriptors.putInt(ranges[i]).putInt(ranges[i + 1]).putLong(0);
        }
        return new UsbIpControllerExport.IsoPackets(descriptors.array(), length);
    }
}
