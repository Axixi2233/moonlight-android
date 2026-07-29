package com.limelight.utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class UdpNetworkProbe {
    public static final int DEFAULT_PORT = 47998;

    private static final byte[] MAGIC = "moonlight-ctest".getBytes(StandardCharsets.US_ASCII);
    private static final int PACKET_SIZE = 1040;
    private static final int VERSION_OFFSET = 16;
    private static final int NONCE_OFFSET = 20;
    private static final int SEQUENCE_OFFSET = 24;
    private static final int TIMESTAMP_OFFSET = 32;
    private static final int PROTOCOL_VERSION = 1;

    private static final int DISCOVERY_PACKET_COUNT = 3;
    private static final int DISCOVERY_TIMEOUT_MS = 800;
    private static final int PROBE_PACKET_COUNT = 100;
    private static final int PROBE_INTERVAL_MS = 20;
    private static final int RECEIVE_TIMEOUT_MS = 100;
    private static final int REPLY_GRACE_MS = 1000;

    public enum Status {
        SUCCESS,
        UNREACHABLE,
        INCONCLUSIVE
    }

    public static final class Result {
        public final Status status;
        public final int sentPackets;
        public final int receivedPackets;
        public final int duplicatePackets;
        public final float packetLossPercent;
        public final float averageRttMs;
        public final float jitterMs;

        private Result(Status status, int sentPackets, int receivedPackets,
                       int duplicatePackets, float packetLossPercent,
                       float averageRttMs, float jitterMs) {
            this.status = status;
            this.sentPackets = sentPackets;
            this.receivedPackets = receivedPackets;
            this.duplicatePackets = duplicatePackets;
            this.packetLossPercent = packetLossPercent;
            this.averageRttMs = averageRttMs;
            this.jitterMs = jitterMs;
        }

        private static Result failure(Status status) {
            return new Result(status, 0, 0, 0, 0, 0, 0);
        }
    }

    private static final class AddressSelection {
        final InetAddress address;
        final boolean receivedInvalidResponse;

        AddressSelection(InetAddress address, boolean receivedInvalidResponse) {
            this.address = address;
            this.receivedInvalidResponse = receivedInvalidResponse;
        }
    }

    private UdpNetworkProbe() {
    }

    public static Result run(String hostName) {
        return run(hostName, DEFAULT_PORT);
    }

    public static Result run(String hostName, int port) {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(hostName);
        }
        catch (IOException e) {
            return Result.failure(Status.INCONCLUSIVE);
        }

        AddressSelection selection = selectResponsiveAddress(addresses, port);
        if (selection.address == null) {
            return Result.failure(selection.receivedInvalidResponse
                    ? Status.INCONCLUSIVE
                    : Status.UNREACHABLE);
        }

        return runProbe(selection.address, port);
    }

    private static AddressSelection selectResponsiveAddress(InetAddress[] addresses, int port) {
        boolean receivedInvalidResponse = false;

        for (InetAddress address : addresses) {
            int nonce = ThreadLocalRandom.current().nextInt();
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(address, port);
                socket.setSoTimeout(RECEIVE_TIMEOUT_MS);

                for (int sequence = -DISCOVERY_PACKET_COUNT; sequence < 0; sequence++) {
                    byte[] payload = createPayload(nonce, sequence, System.nanoTime());
                    socket.send(new DatagramPacket(payload, payload.length));
                }

                long deadlineNanos = System.nanoTime() + DISCOVERY_TIMEOUT_MS * 1_000_000L;
                byte[] responseBuffer = new byte[PACKET_SIZE];
                DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
                while (System.nanoTime() < deadlineNanos) {
                    try {
                        response.setLength(responseBuffer.length);
                        socket.receive(response);
                        if (isValidResponse(response, nonce, -DISCOVERY_PACKET_COUNT, -1)) {
                            return new AddressSelection(address, receivedInvalidResponse);
                        }
                        receivedInvalidResponse = true;
                    }
                    catch (SocketTimeoutException e) {
                        // Continue until the per-address discovery deadline expires.
                    }
                }
            }
            catch (IOException e) {
                // Try the next resolved test server address.
            }
        }

        return new AddressSelection(null, receivedInvalidResponse);
    }

    private static Result runProbe(InetAddress address, int port) {
        final int nonce = ThreadLocalRandom.current().nextInt();
        final long[] sendTimesNanos = new long[PROBE_PACKET_COUNT];
        final long[] receiveTimesNanos = new long[PROBE_PACKET_COUNT];
        final int[] duplicatePackets = new int[1];
        final AtomicBoolean senderFinished = new AtomicBoolean(false);
        final AtomicLong receiverDeadlineNanos = new AtomicLong(Long.MAX_VALUE);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(address, port);
            socket.setSoTimeout(RECEIVE_TIMEOUT_MS);

            Thread receiverThread = new Thread(() -> receiveResponses(
                    socket,
                    nonce,
                    sendTimesNanos,
                    receiveTimesNanos,
                    duplicatePackets,
                    senderFinished,
                    receiverDeadlineNanos),
                    "UdpNetworkProbeReceiver");
            receiverThread.start();

            int sentPackets = 0;
            try {
                long nextSendNanos = System.nanoTime();
                for (int sequence = 0; sequence < PROBE_PACKET_COUNT; sequence++) {
                    waitUntil(nextSendNanos);

                    long sendTimeNanos = System.nanoTime();
                    byte[] payload = createPayload(nonce, sequence, sendTimeNanos);
                    sendTimesNanos[sequence] = sendTimeNanos;
                    socket.send(new DatagramPacket(payload, payload.length));
                    sentPackets++;
                    nextSendNanos += PROBE_INTERVAL_MS * 1_000_000L;
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failure(Status.INCONCLUSIVE);
            }
            finally {
                senderFinished.set(true);
                receiverDeadlineNanos.set(System.nanoTime() + REPLY_GRACE_MS * 1_000_000L);
            }

            try {
                receiverThread.join(REPLY_GRACE_MS + RECEIVE_TIMEOUT_MS * 2L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failure(Status.INCONCLUSIVE);
            }
            finally {
                if (receiverThread.isAlive()) {
                    socket.close();
                    receiverThread.interrupt();
                    try {
                        receiverThread.join(RECEIVE_TIMEOUT_MS * 2L);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Result.failure(Status.INCONCLUSIVE);
                    }
                }
            }

            return summarize(sendTimesNanos, receiveTimesNanos, sentPackets, duplicatePackets[0]);
        }
        catch (IOException e) {
            return Result.failure(Status.INCONCLUSIVE);
        }
    }

    private static void receiveResponses(DatagramSocket socket,
                                         int nonce,
                                         long[] sendTimesNanos,
                                         long[] receiveTimesNanos,
                                         int[] duplicatePackets,
                                         AtomicBoolean senderFinished,
                                         AtomicLong receiverDeadlineNanos) {
        byte[] responseBuffer = new byte[PACKET_SIZE];
        DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);

        while (!Thread.currentThread().isInterrupted()) {
            if (senderFinished.get() && System.nanoTime() >= receiverDeadlineNanos.get()) {
                return;
            }

            try {
                response.setLength(responseBuffer.length);
                socket.receive(response);
                long receiveTimeNanos = System.nanoTime();
                if (!isValidResponse(response, nonce, 0, sendTimesNanos.length - 1)) {
                    continue;
                }

                int sequence = ByteBuffer.wrap(
                        response.getData(),
                        response.getOffset() + SEQUENCE_OFFSET,
                        Integer.BYTES).getInt();
                if (receiveTimesNanos[sequence] == 0) {
                    receiveTimesNanos[sequence] = receiveTimeNanos;
                }
                else {
                    duplicatePackets[0]++;
                }
            }
            catch (SocketTimeoutException e) {
                // Re-check whether the reply grace period has expired.
            }
            catch (SocketException e) {
                return;
            }
            catch (IOException e) {
                return;
            }
        }
    }

    private static byte[] createPayload(int nonce, int sequence, long timestampNanos) {
        byte[] payload = new byte[PACKET_SIZE];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31 + sequence);
        }
        System.arraycopy(MAGIC, 0, payload, 0, MAGIC.length);

        ByteBuffer header = ByteBuffer.wrap(payload);
        header.putInt(VERSION_OFFSET, PROTOCOL_VERSION);
        header.putInt(NONCE_OFFSET, nonce);
        header.putInt(SEQUENCE_OFFSET, sequence);
        header.putLong(TIMESTAMP_OFFSET, timestampNanos);
        return payload;
    }

    private static boolean isValidResponse(DatagramPacket response,
                                           int nonce,
                                           int minimumSequence,
                                           int maximumSequence) {
        if (response.getLength() < TIMESTAMP_OFFSET + Long.BYTES) {
            return false;
        }

        byte[] data = response.getData();
        int offset = response.getOffset();
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[offset + i] != MAGIC[i]) {
                return false;
            }
        }

        ByteBuffer header = ByteBuffer.wrap(data);
        int version = header.getInt(offset + VERSION_OFFSET);
        int responseNonce = header.getInt(offset + NONCE_OFFSET);
        int sequence = header.getInt(offset + SEQUENCE_OFFSET);
        return version == PROTOCOL_VERSION &&
                responseNonce == nonce &&
                sequence >= minimumSequence &&
                sequence <= maximumSequence;
    }

    private static void waitUntil(long deadlineNanos) throws InterruptedException {
        while (true) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return;
            }

            long sleepMillis = remainingNanos / 1_000_000L;
            int sleepNanos = (int) (remainingNanos % 1_000_000L);
            Thread.sleep(sleepMillis, sleepNanos);
        }
    }

    static Result summarize(long[] sendTimesNanos,
                            long[] receiveTimesNanos,
                            int sentPackets,
                            int duplicatePackets) {
        int receivedPackets = 0;
        double totalRttMs = 0;
        double totalJitterMs = 0;
        double previousRttMs = -1;

        for (int sequence = 0; sequence < sentPackets; sequence++) {
            if (sendTimesNanos[sequence] == 0 || receiveTimesNanos[sequence] == 0) {
                continue;
            }

            double rttMs = (receiveTimesNanos[sequence] - sendTimesNanos[sequence]) / 1_000_000.0;
            if (rttMs < 0) {
                continue;
            }

            receivedPackets++;
            totalRttMs += rttMs;
            if (previousRttMs >= 0) {
                totalJitterMs += Math.abs(rttMs - previousRttMs);
            }
            previousRttMs = rttMs;
        }

        if (sentPackets == 0) {
            return Result.failure(Status.INCONCLUSIVE);
        }
        if (receivedPackets == 0) {
            return Result.failure(Status.UNREACHABLE);
        }

        float packetLossPercent = (sentPackets - receivedPackets) * 100f / sentPackets;
        float averageRttMs = (float) (totalRttMs / receivedPackets);
        float jitterMs = receivedPackets > 1
                ? (float) (totalJitterMs / (receivedPackets - 1))
                : 0;
        return new Result(Status.SUCCESS,
                sentPackets,
                receivedPackets,
                duplicatePackets,
                packetLossPercent,
                averageRttMs,
                jitterMs);
    }
}
