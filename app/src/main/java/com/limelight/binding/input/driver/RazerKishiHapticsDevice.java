package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import com.limelight.LimeLog;

import java.util.ArrayList;
import java.util.List;

public final class RazerKishiHapticsDevice {
    // Disabled until the Kishi haptics protocol is validated on real hardware.
    private static final boolean ENABLE_KISHI_AUDIO_HAPTICS = false;
    private static final String TAG = "RazerKishiDebug";
    private static final int RAZER_VID = 0x1532;
    private static final int UNIVERSAL_XINPUT_PID = 0x0037;
    private static final int KISHI_XINPUT_PLUS_PID = 0x0719;
    private static final int KISHI_ULTRA_HID_PID = 0x071a;
    private static final int KISHI_V3_HID_PID = 0x0721;
    private static final int KISHI_V3_PRO_HID_PID = 0x0724;

    private static final int HAPTIC_INTERFACE_INDEX = 3;
    private static final int FRAME_SIZE = 64;
    private static final int HEADER_SIZE = 10;
    private static final int PAYLOAD_SIZE = 48;
    private static final int CHECKSUM_INDEX = HEADER_SIZE + PAYLOAD_SIZE;
    private static final int SAMPLES_PER_FRAME = 12;
    private static final int INPUT_SAMPLE_RATE = 3000;
    private static final int OUTPUT_SAMPLE_RATE = 4000;
    private static final int USB_TYPE_CLASS = 0x20;
    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int HID_SET_REPORT = 0x09;
    private static final int HID_REPORT_TYPE_FEATURE = 0x03;
    private static final int CONTROL_TIMEOUT_MS = 1000;
    private static final byte HIGH_INTENSITY = 0x64;

    private static final byte[] FRAME_HEADER = new byte[] {
            (byte) 0x55, (byte) 0xAA, 0x00, 0x00, 0x00,
            0x00, 0x00, (byte) PAYLOAD_SIZE, (byte) 0xFE, (byte) 0x79
    };

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final List<Short> resampleBuffer = new ArrayList<>();
    private UsbInterface hapticInterface;
    private UsbEndpoint hapticEndpoint;
    private RazerKishiHapticSender hapticSender;
    private boolean started;
    private int submittedFrameCount;

    public static boolean canUseDevice(UsbDevice device) {
        if (device == null) {
            return false;
        }

        return canUseDevice(device.getVendorId(), device.getProductId(), device.getProductName());
    }

    public static boolean isFeatureEnabled() {
        return ENABLE_KISHI_AUDIO_HAPTICS;
    }

    public static boolean canUseDevice(int vendorId, int productId, String productName) {
        if (!ENABLE_KISHI_AUDIO_HAPTICS) {
            return false;
        }

        if (vendorId != RAZER_VID) {
            return false;
        }

        if (productId == KISHI_XINPUT_PLUS_PID ||
                productId == KISHI_ULTRA_HID_PID ||
                productId == KISHI_V3_HID_PID ||
                productId == KISHI_V3_PRO_HID_PID) {
            return true;
        }

        if (productId == UNIVERSAL_XINPUT_PID) {
            if (productName == null) {
                return false;
            }

            String lowerName = productName.toLowerCase();
            return lowerName.contains("kishi") || lowerName.contains("ultra");
        }

        return false;
    }

    public RazerKishiHapticsDevice(UsbDevice device, UsbDeviceConnection connection) {
        this.device = device;
        this.connection = connection;
    }

    public int getDeviceId() {
        return device.getDeviceId();
    }

    public int getVendorId() {
        return device.getVendorId();
    }

    public int getProductId() {
        return device.getProductId();
    }

    public boolean start() {
        if (started) {
            Log.d(TAG, "start() skipped because device already started: pid=0x" +
                    Integer.toHexString(device.getProductId()));
            return true;
        }

        Log.i(TAG, "Starting Kishi haptics device: vid=0x" +
                Integer.toHexString(device.getVendorId()) + " pid=0x" +
                Integer.toHexString(device.getProductId()) + " name=" + device.getProductName() +
                " interfaces=" + device.getInterfaceCount());
        detectHapticEndpoint();
        if (hapticInterface == null || hapticEndpoint == null) {
            LimeLog.warning("Razer Kishi haptic interface not found");
            Log.e(TAG, "No Kishi haptic endpoint found");
            return false;
        }

        Log.i(TAG, "Using haptic interface id=" + hapticInterface.getId() +
                " endpoint=0x" + Integer.toHexString(hapticEndpoint.getAddress()) +
                " maxPacket=" + hapticEndpoint.getMaxPacketSize());
        if (!connection.claimInterface(hapticInterface, true)) {
            LimeLog.warning("Failed to claim Razer Kishi haptic interface");
            Log.e(TAG, "Failed to claim haptic interface id=" + hapticInterface.getId());
            return false;
        }

        if (!sendHapticStateControl(true)) {
            Log.e(TAG, "Failed to enable Kishi haptic state");
            connection.releaseInterface(hapticInterface);
            return false;
        }

        sendHapticIntensity(HIGH_INTENSITY);
        hapticSender = new RazerKishiHapticSender(connection);
        hapticSender.start(hapticEndpoint);
        submittedFrameCount = 0;
        started = true;
        Log.i(TAG, "Kishi haptics started successfully");
        return true;
    }

    public void stop() {
        if (!started) {
            Log.d(TAG, "stop() ignored because device was not started");
            return;
        }

        Log.i(TAG, "Stopping Kishi haptics device pid=0x" +
                Integer.toHexString(device.getProductId()));
        if (hapticSender != null) {
            hapticSender.stop();
            hapticSender = null;
        }

        sendHapticStateControl(false);
        resampleBuffer.clear();

        if (hapticInterface != null) {
            try {
                connection.releaseInterface(hapticInterface);
            }
            catch (Exception ignored) {
            }
        }

        try {
            connection.close();
        }
        catch (Exception ignored) {
        }

        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean submitFrame(byte[] frame, float intensityGain) {
        if (!started || hapticSender == null || frame == null || frame.length == 0) {
            Log.w(TAG, "submitFrame rejected: started=" + started +
                    " sender=" + (hapticSender != null) +
                    " frameLength=" + (frame == null ? -1 : frame.length));
            return false;
        }

        submittedFrameCount++;
        if (submittedFrameCount <= 5 || submittedFrameCount % 50 == 0) {
            Log.d(TAG, "submitFrame #" + submittedFrameCount +
                    " bytes=" + frame.length + " gain=" + intensityGain);
        }
        enqueueHapticData(frame, intensityGain);
        return true;
    }

    private void detectHapticEndpoint() {
        hapticInterface = null;
        hapticEndpoint = null;

        if (device.getInterfaceCount() > HAPTIC_INTERFACE_INDEX) {
            UsbInterface candidate = device.getInterface(HAPTIC_INTERFACE_INDEX);
            UsbEndpoint endpoint = findInterruptOutEndpoint(candidate);
            if (endpoint != null) {
                Log.d(TAG, "Found preferred interface 3 for Kishi haptics");
                hapticInterface = candidate;
                hapticEndpoint = endpoint;
                return;
            }
        }

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface candidate = device.getInterface(i);
            UsbEndpoint endpoint = findInterruptOutEndpoint(candidate);
            if (endpoint != null) {
                Log.d(TAG, "Found fallback haptic interface id=" + candidate.getId() +
                        " endpoint=0x" + Integer.toHexString(endpoint.getAddress()));
                hapticInterface = candidate;
                hapticEndpoint = endpoint;
                return;
            }
        }
    }

    private UsbEndpoint findInterruptOutEndpoint(UsbInterface iface) {
        if (iface == null) {
            return null;
        }

        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = iface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT &&
                    endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                Log.d(TAG, "Interrupt OUT endpoint match: iface=" + iface.getId() +
                        " endpoint=0x" + Integer.toHexString(endpoint.getAddress()) +
                        " maxPacket=" + endpoint.getMaxPacketSize());
                return endpoint;
            }
        }

        return null;
    }

    private boolean sendHapticStateControl(boolean enable) {
        byte state = enable ? (byte) 0x01 : (byte) 0x00;
        byte[] leftCmd = new byte[] {0x00, 0x01, state};
        byte[] rightCmd = new byte[] {0x00, 0x02, state};
        boolean leftOk = sendControlCommand("SetHapticStateControl-L", leftCmd);
        boolean rightOk = sendControlCommand("SetHapticStateControl-R", rightCmd);
        Log.i(TAG, "sendHapticStateControl(" + enable + ") left=" + leftOk + " right=" + rightOk);
        return leftOk && rightOk;
    }

    private void sendHapticIntensity(byte intensity) {
        byte[] leftCmd = new byte[] {0x00, 0x01, intensity};
        byte[] rightCmd = new byte[] {0x00, 0x02, intensity};
        boolean leftOk = sendControlCommand("SetHapticIntensity-L", leftCmd);
        boolean rightOk = sendControlCommand("SetHapticIntensity-R", rightCmd);
        Log.i(TAG, "sendHapticIntensity(0x" + Integer.toHexString(intensity & 0xFF) +
                ") left=" + leftOk + " right=" + rightOk);
    }

    private boolean sendControlCommand(String name, byte[] data) {
        if (hapticInterface == null || data == null) {
            Log.w(TAG, "sendControlCommand(" + name + ") skipped because interface/data missing");
            return false;
        }

        int requestType = UsbConstants.USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE;
        int request = HID_SET_REPORT;
        int value = (HID_REPORT_TYPE_FEATURE << 8);
        int index = hapticInterface.getId();
        int result = connection.controlTransfer(requestType, request, value, index, data, data.length, CONTROL_TIMEOUT_MS);
        Log.d(TAG, "controlTransfer " + name + " result=" + result +
                " expected=" + data.length + " interface=" + index);
        return result == data.length;
    }

    private void enqueueHapticData(byte[] pcmData, float gain) {
        short[] inputSamples = new short[pcmData.length / 2];
        for (int i = 0; i < inputSamples.length; i++) {
            int low = pcmData[i * 2] & 0xFF;
            int high = pcmData[i * 2 + 1] & 0xFF;
            inputSamples[i] = (short) ((high << 8) | low);
        }

        short[] resampled = resample3kTo4k(inputSamples);
        float clampedGain = clampGain(gain);
        if (submittedFrameCount <= 5 || submittedFrameCount % 50 == 0) {
            Log.d(TAG, "enqueueHapticData inputSamples=" + inputSamples.length +
                    " resampled=" + resampled.length + " gain=" + clampedGain);
        }
        if (clampedGain != 1.0f) {
            for (int i = 0; i < resampled.length; i++) {
                int value = Math.round(resampled[i] * clampedGain);
                resampled[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
            }
        }

        for (short sample : resampled) {
            resampleBuffer.add(sample);
        }

        while (resampleBuffer.size() >= SAMPLES_PER_FRAME * 2) {
            byte[] packet = new byte[FRAME_SIZE];
            System.arraycopy(FRAME_HEADER, 0, packet, 0, HEADER_SIZE);

            for (int i = 0; i < SAMPLES_PER_FRAME * 2; i++) {
                short sample = resampleBuffer.remove(0);
                int offset = HEADER_SIZE + (i * 2);
                packet[offset] = (byte) (sample & 0xFF);
                packet[offset + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            byte checksum = 0;
            for (int i = 2; i < CHECKSUM_INDEX; i++) {
                checksum ^= packet[i];
            }
            packet[CHECKSUM_INDEX] = checksum;
            hapticSender.enqueue(packet);
        }
    }

    private short[] resample3kTo4k(short[] input) {
        int outputLength = (int) Math.ceil(input.length * (OUTPUT_SAMPLE_RATE / (double) INPUT_SAMPLE_RATE));
        short[] output = new short[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double sourcePosition = i * (INPUT_SAMPLE_RATE / (double) OUTPUT_SAMPLE_RATE);
            int leftIndex = (int) Math.floor(sourcePosition);
            int rightIndex = Math.min(leftIndex + 1, input.length - 1);
            double fraction = sourcePosition - leftIndex;

            if (leftIndex >= input.length) {
                output[i] = 0;
                continue;
            }

            double value = input[leftIndex] * (1.0 - fraction) + input[rightIndex] * fraction;
            output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
        }

        return output;
    }

    private float clampGain(float gain) {
        if (Float.isNaN(gain) || Float.isInfinite(gain)) {
            return 1.0f;
        }

        if (gain < 0.0f) {
            return 0.0f;
        }

        if (gain > 3.0f) {
            return 3.0f;
        }

        return gain;
    }
}
