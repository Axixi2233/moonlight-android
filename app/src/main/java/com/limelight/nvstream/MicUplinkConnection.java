package com.limelight.nvstream;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;

import com.limelight.LimeLog;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;

public class MicUplinkConnection {
    private static final int AXI_MIC_PAYLOAD_OPUS = 1;
    private static final int OPUS_BITRATE = 32000;

    private final String hostAddress;
    private final int hostPort;
    private final int sessionId;
    private final byte[] token;
    private final String codec;
    private final int sampleRate;
    private final int channels;
    private final int frameMs;
    private final int frameBytes;

    private volatile boolean running;
    private volatile String lastErrorMessage;
    private Thread workerThread;
    private AudioRecord audioRecord;
    private MediaCodec encoder;
    private DatagramSocket socket;
    private long nextPresentationTimeUs;
    private int sequenceNumber;
    private int timestamp;
    private long packetCount;

    private static String tokenPrefixHex(byte[] token, int byteCount) {
        if (token == null) {
            return "null";
        }

        int count = Math.min(byteCount, token.length);
        final char[] lut = "0123456789ABCDEF".toCharArray();
        char[] chars = new char[count * 2];
        for (int i = 0; i < count; i++) {
            int value = token[i] & 0xFF;
            chars[i * 2] = lut[value >>> 4];
            chars[i * 2 + 1] = lut[value & 0x0F];
        }
        return new String(chars);
    }

    public MicUplinkConnection(String hostAddress, int hostPort, int sessionId, byte[] token,
                               String codec, int sampleRate, int channels, int frameMs) {
        this.hostAddress = hostAddress;
        this.hostPort = hostPort;
        this.sessionId = sessionId;
        this.token = token;
        this.codec = codec;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.frameMs = frameMs;
        this.frameBytes = (sampleRate * frameMs / 1000) * channels * 2;
    }

    public static boolean isSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }

        try {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, OPUS_BITRATE);
            return new MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null;
        }
        catch (Exception e) {
            LimeLog.warning("Unable to query Opus encoder support: " + e.getMessage());
            return false;
        }
    }

    public boolean start() {
        if (!isConfigurationSupported()) {
            LimeLog.warning("mic-uplink configuration rejected: " + getLastErrorMessage());
            return false;
        }

        CountDownLatch startupLatch = new CountDownLatch(1);
        running = true;
        workerThread = new Thread(() -> runWorker(startupLatch), "MicUplinkConnection");
        workerThread.start();

        try {
            startupLatch.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastErrorMessage = "Interrupted while starting microphone uplink";
            running = false;
            return false;
        }

        return running;
    }

    public void stop() {
        running = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            }
            catch (IllegalStateException ignored) {
            }
        }

        if (socket != null) {
            socket.close();
        }

        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(2000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            workerThread = null;
        }
    }

    public String getLastErrorMessage() {
        return lastErrorMessage != null ? lastErrorMessage : "mic-uplink failed";
    }

    private boolean isConfigurationSupported() {
        if (!"opus".equalsIgnoreCase(codec)) {
            lastErrorMessage = "mic-uplink codec is unsupported";
            return false;
        }
        if (sampleRate != 48000 || channels != 1 || frameMs != 20) {
            lastErrorMessage = "mic-uplink parameters are unsupported";
            return false;
        }
        if (token == null || token.length != 16) {
            lastErrorMessage = "mic-uplink token is invalid";
            return false;
        }
        if (!isSupported()) {
            lastErrorMessage = "mic-uplink requires Android 10 or a device with an Opus encoder";
            return false;
        }
        return true;
    }

    private void runWorker(CountDownLatch startupLatch) {
        byte[] pcmFrame = new byte[frameBytes];
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        try {
            LimeLog.info("Starting mic-uplink to " + hostAddress + ":" + hostPort +
                    " session=" + Integer.toUnsignedString(sessionId) +
                    " codec=" + codec +
                    " rate=" + sampleRate +
                    " channels=" + channels +
                    " frameMs=" + frameMs +
                    " tokenPrefix=" + tokenPrefixHex(token, 4));
            InetAddress serverAddress = InetAddress.getByName(hostAddress);
            socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(serverAddress, hostPort));

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, OPUS_BITRATE);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, frameBytes);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            int minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBufferSize <= 0) {
                throw new IllegalStateException("Unable to determine microphone buffer size");
            }

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBufferSize, frameBytes * 4));
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Unable to initialize microphone capture");
            }

            audioRecord.startRecording();
            LimeLog.info("mic-uplink capture and encoder initialized");
            startupLatch.countDown();

            while (running) {
                if (!readFully(pcmFrame)) {
                    break;
                }

                long presentationTimeUs = nextPresentationTimeUs;
                queueInputFrame(pcmFrame, presentationTimeUs);
                nextPresentationTimeUs += frameMs * 1000L;
                drainEncoder(bufferInfo);
            }
        }
        catch (Exception e) {
            lastErrorMessage = "mic-uplink unavailable: " + e.getMessage();
            LimeLog.warning(lastErrorMessage);
            running = false;
            startupLatch.countDown();
        }
        finally {
            startupLatch.countDown();
            cleanup();
        }
    }

    private boolean readFully(byte[] buffer) {
        int offset = 0;
        while (running && offset < buffer.length) {
            int bytesRead = audioRecord.read(buffer, offset, buffer.length - offset);
            if (bytesRead <= 0) {
                if (running) {
                    lastErrorMessage = "Failed to capture microphone audio";
                    LimeLog.warning(lastErrorMessage + ": " + bytesRead);
                }
                return false;
            }
            offset += bytesRead;
        }
        return running;
    }

    private void queueInputFrame(byte[] pcmFrame, long presentationTimeUs) {
        int inputBufferIndex = encoder.dequeueInputBuffer(10000);
        if (inputBufferIndex < 0) {
            return;
        }

        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
        if (inputBuffer == null) {
            encoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, 0);
            return;
        }

        inputBuffer.clear();
        inputBuffer.put(pcmFrame);
        encoder.queueInputBuffer(inputBufferIndex, 0, pcmFrame.length, presentationTimeUs, 0);
    }

    private void drainEncoder(MediaCodec.BufferInfo bufferInfo) throws Exception {
        while (running) {
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return;
            }
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue;
            }
            if (outputBufferIndex < 0) {
                continue;
            }

            ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
            if (outputBuffer != null && bufferInfo.size > 0 &&
                    (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                ByteBuffer payload = outputBuffer.duplicate();
                payload.position(bufferInfo.offset);
                payload.limit(bufferInfo.offset + bufferInfo.size);
                sendPacket(payload, bufferInfo.presentationTimeUs);
            }

            encoder.releaseOutputBuffer(outputBufferIndex, false);
        }
    }

    private void sendPacket(ByteBuffer payload, long presentationTimeUs) throws Exception {
        int payloadSize = payload.remaining();
        ByteBuffer packet = ByteBuffer.allocate(32 + payloadSize).order(ByteOrder.BIG_ENDIAN);
        packet.putInt(sessionId);
        packet.putInt(sequenceNumber++);
        packet.putInt(timestamp);
        packet.put(token);
        packet.put((byte) AXI_MIC_PAYLOAD_OPUS);
        packet.put((byte) 0);
        packet.putShort((short) payloadSize);
        packet.put(payload);

        timestamp += (sampleRate * frameMs) / 1000;

        DatagramPacket datagramPacket = new DatagramPacket(packet.array(), packet.position());
        socket.send(datagramPacket);
        if (packetCount == 0) {
            LimeLog.info("Sent first mic-uplink packet: bytes=" + payloadSize +
                    " ptsUs=" + presentationTimeUs +
                    " seq=" + sequenceNumber +
                    " ts=" + timestamp +
                    " tokenPrefix=" + tokenPrefixHex(token, 4));
        }
        packetCount++;
    }

    private void cleanup() {
        running = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            }
            catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }

        if (encoder != null) {
            try {
                encoder.stop();
            }
            catch (IllegalStateException ignored) {
            }
            encoder.release();
            encoder = null;
        }

        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}
