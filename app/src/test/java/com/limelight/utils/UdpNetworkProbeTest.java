package com.limelight.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UdpNetworkProbeTest {
    @Test
    public void summarizeCalculatesLossRttAndJitter() {
        long[] sendTimes = {
                1_000_000_000L,
                2_000_000_000L,
                3_000_000_000L,
                4_000_000_000L
        };
        long[] receiveTimes = {
                1_010_000_000L,
                2_030_000_000L,
                0,
                4_020_000_000L
        };

        UdpNetworkProbe.Result result =
                UdpNetworkProbe.summarize(sendTimes, receiveTimes, 4, 1);

        assertEquals(UdpNetworkProbe.Status.SUCCESS, result.status);
        assertEquals(4, result.sentPackets);
        assertEquals(3, result.receivedPackets);
        assertEquals(1, result.duplicatePackets);
        assertEquals(25.0f, result.packetLossPercent, 0.001f);
        assertEquals(20.0f, result.averageRttMs, 0.001f);
        assertEquals(15.0f, result.jitterMs, 0.001f);
    }

    @Test
    public void summarizeReportsUnreachableWhenNoRepliesArrive() {
        long[] sendTimes = {1_000_000_000L, 2_000_000_000L};
        long[] receiveTimes = {0, 0};

        UdpNetworkProbe.Result result =
                UdpNetworkProbe.summarize(sendTimes, receiveTimes, 2, 0);

        assertEquals(UdpNetworkProbe.Status.UNREACHABLE, result.status);
    }

    @Test
    public void summarizeReportsInconclusiveWhenNothingWasSent() {
        UdpNetworkProbe.Result result =
                UdpNetworkProbe.summarize(new long[0], new long[0], 0, 0);

        assertEquals(UdpNetworkProbe.Status.INCONCLUSIVE, result.status);
    }
}
