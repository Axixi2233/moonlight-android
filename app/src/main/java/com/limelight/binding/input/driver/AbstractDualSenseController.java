package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;
import android.util.Log;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDualSenseController extends AbstractController {
    private static final int HAPTIC_AUDIO_ENDPOINT_PACKET_SIZE = 0x188;
    private static final int HAPTIC_INIT_REPORT_SIZE = 48;
    private static final int HAPTIC_INIT_TIMEOUT_MS = 1000;
    private static final int REPORT_MIN_TRIGGER_LEN = 31;
    private static final int REPORT_RIGHT_TRIGGER_TYPE_IDX = 11;
    private static final int REPORT_RIGHT_TRIGGER_DATA_IDX = 12;
    private static final int REPORT_LEFT_TRIGGER_TYPE_IDX = 22;
    private static final int REPORT_LEFT_TRIGGER_DATA_IDX = 23;
    private static final int TRIGGER_DATA_LEN = 10;

    protected final UsbDevice device;
    protected final UsbDeviceConnection connection;

    private Thread inputThread;
    private boolean stopped;
    private boolean advancedAudioHapticsRequested;
    private boolean advancedAudioHapticsPrimed;
    private byte cachedLeftTriggerType;
    private final byte[] cachedLeftTriggerData = new byte[TRIGGER_DATA_LEN];
    private byte cachedRightTriggerType;
    private final byte[] cachedRightTriggerData = new byte[TRIGGER_DATA_LEN];
    private DualSenseHapticSender advancedAudioHapticsSender;

    protected UsbEndpoint inEndpt, outEndpt;
    protected UsbInterface hapticIface;
    protected UsbEndpoint hapticEndpt;

    public AbstractDualSenseController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_PS;
        this.capabilities = MoonBridge.LI_CCAP_GYRO | MoonBridge.LI_CCAP_ACCEL | MoonBridge.LI_CCAP_RUMBLE;
        this.buttonFlags =
                ControllerPacket.A_FLAG | ControllerPacket.B_FLAG | ControllerPacket.X_FLAG | ControllerPacket.Y_FLAG |
                        ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG |
                        ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG |
                        ControllerPacket.LS_CLK_FLAG | ControllerPacket.RS_CLK_FLAG |
                        ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG | ControllerPacket.SPECIAL_BUTTON_FLAG;
    }

    private Thread createInputThread() {
        return new Thread() {
            public void run() {
                try {
                    // Delay for a moment before reporting the new gamepad and
                    // accepting new input. This allows time for the old InputDevice
                    // to go away before we reclaim its spot. If the old device is still
                    // around when we call notifyDeviceAdded(), we won't be able to claim
                    // the controller number used by the original InputDevice.
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }

                // Report that we're added _before_ reporting input
                notifyDeviceAdded();

                while (!isInterrupted() && !stopped) {
                    byte[] buffer = new byte[64];

                    int res;

                    //
                    // There's no way that I can tell to determine if a device has failed
                    // or if the timeout has simply expired. We'll check how long the transfer
                    // took to fail and assume the device failed if it happened before the timeout
                    // expired.
                    //

                    do {
                        // Read the next input state packet
                        long lastMillis = SystemClock.uptimeMillis();
                        res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 3000);

                        // If we get a zero length response, treat it as an error
                        if (res == 0) {
                            res = -1;
                        }

                        if (res == -1 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                            Log.d("DualSenseController", "Detected device I/O error");
                            AbstractDualSenseController.this.stop();
                            break;
                        }
                    } while (res == -1 && !isInterrupted() && !stopped);

                    if (res == -1 || stopped) {
                        break;
                    }

                    if (handleRead(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN))) {
                        // Report input if handleRead() returns true
                        reportInput();
                        reportMotion();
                    }
                }
            }
        };
    }

    private static UsbInterface findInterface(UsbDevice device) {
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_HID && intf.getEndpointCount()>=2) {
                Log.d("DualSenseController", "Found HID interface: " + i);
                return intf;
            }
        }
        return null;
    }

    private void detectHapticEndpoint() {
        hapticIface = null;
        hapticEndpt = null;

        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() != UsbConstants.USB_CLASS_AUDIO) {
                continue;
            }

            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint endpt = intf.getEndpoint(j);
                if (endpt.getDirection() == UsbConstants.USB_DIR_OUT &&
                        endpt.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                        endpt.getMaxPacketSize() == HAPTIC_AUDIO_ENDPOINT_PACKET_SIZE) {
                    hapticIface = intf;
                    hapticEndpt = endpt;
                    Log.d("DualSenseController", "Found advanced haptics endpoint iface=" +
                            intf.getId() + " alt=" + intf.getAlternateSetting() +
                            " addr=" + endpt.getAddress());
                    return;
                }
            }
        }
    }

    private List<UsbInterface> ifaces=new ArrayList<>();

    public boolean start() {
        ifaces.clear();
        advancedAudioHapticsRequested = false;
        advancedAudioHapticsPrimed = false;
        inEndpt = null;
        outEndpt = null;
        hapticIface = null;
        hapticEndpt = null;
        Log.d("DualSenseController", "start");
        // Force claim all interfaces
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);

            if (!connection.claimInterface(iface, true)) {
                Log.d("DualSenseController", "Failed to claim interfaces");
                return false;
            }else{
                ifaces.add(iface);
            }
        }
        Log.d("DualSenseController", "getInterfaceCount:" + device.getInterfaceCount());
        detectHapticEndpoint();

        // Find the endpoints
        UsbInterface iface = findInterface(device);

        if (iface == null) {
            Log.e("DualSenseController", "Failed to find interface");
            return false;
        }

        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpt = iface.getEndpoint(i);
            if (endpt.getDirection() == UsbConstants.USB_DIR_OUT) {
                if (outEndpt != null) {
                    Log.d("DualSenseController", "Found duplicate OUT endpoint");
                    return false;
                }
                outEndpt = endpt;
            } else if (endpt.getDirection() == UsbConstants.USB_DIR_IN) {
                if (inEndpt != null) {
                    Log.d("DualSenseController", "Found duplicate IN endpoint");
                    return false;
                }
                inEndpt = endpt;
            }
        }
        Log.d("DualSenseController", "inEndpt: " + inEndpt);
        Log.d("DualSenseController", "outEndpt: " + outEndpt);
        // Make sure the required endpoints were present
        if (inEndpt == null || outEndpt == null) {
            Log.d("DualSenseController", "Missing required endpoin");
            return false;
        }
        // Run the init function
        if (!doInit()) {
            return false;
        }
        // Start listening for controller input
        inputThread = createInputThread();
        inputThread.start();
        return true;
    }

    public void stop() {
        if (stopped) {
            return;
        }

        stopped = true;
        stopAdvancedAudioHaptics();

        // Cancel any rumble effects
        rumble((short)0, (short)0);

        // Stop the input thread
        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
        }

        if(!ifaces.isEmpty()&&connection!=null){
            for (int i = 0; i < ifaces.size(); i++) {
                UsbInterface iface = ifaces.get(i);
                connection.releaseInterface(iface);
            }
            ifaces.clear();
        }

        // Close the USB connection
        connection.close();

        // Report the device removed
        notifyDeviceRemoved();
    }

    @Override
    public boolean hasAdvancedAudioHapticsSupport() {
        return hapticIface != null && hapticEndpt != null;
    }

    @Override
    public boolean startAdvancedAudioHaptics() {
        if (advancedAudioHapticsRequested && advancedAudioHapticsSender != null) {
            return true;
        }

        if (!hasAdvancedAudioHapticsSupport()) {
            Log.d("DualSenseController", "Advanced audio haptics endpoint not available");
            return false;
        }

        final int fd = connection.getFileDescriptor();
        if (fd < 0) {
            Log.d("DualSenseController", "Invalid USB file descriptor for advanced haptics");
            return false;
        }

        if (!HapticNative.nativeConnectHaptics(fd,
                getAdvancedAudioHapticsInterfaceId(),
                getAdvancedAudioHapticsAlternateSetting(),
                (byte) getAdvancedAudioHapticsEndpointAddress())) {
            return false;
        }

        if (!HapticNative.nativeEnableHaptics()) {
            HapticNative.nativeCleanupHaptics();
            return false;
        }

        advancedAudioHapticsSender = new DualSenseHapticSender();
        advancedAudioHapticsSender.start();
        advancedAudioHapticsRequested = true;
        advancedAudioHapticsPrimed = false;
        Log.d("DualSenseController", "Advanced audio haptics requested");
        return true;
    }

    @Override
    public void stopAdvancedAudioHaptics() {
        if (advancedAudioHapticsSender != null) {
            advancedAudioHapticsSender.stop();
            advancedAudioHapticsSender = null;
        }
        HapticNative.nativeCleanupHaptics();
        advancedAudioHapticsRequested = false;
        advancedAudioHapticsPrimed = false;
    }

    @Override
    public boolean isAdvancedAudioHapticsActive() {
        return advancedAudioHapticsRequested && hasAdvancedAudioHapticsSupport();
    }

    @Override
    public boolean submitAdvancedAudioHapticsFrame(byte[] frame, float intensityGain) {
        if (!isAdvancedAudioHapticsActive() || frame == null || frame.length == 0) {
            return false;
        }

        if (!advancedAudioHapticsPrimed && !tryPrimeAdvancedAudioHaptics()) {
            return false;
        }

        return advancedAudioHapticsSender != null &&
                advancedAudioHapticsSender.enqueue(frame, clampAdvancedAudioHapticsGain(intensityGain));
    }

    public int getAdvancedAudioHapticsInterfaceId() {
        return hapticIface != null ? hapticIface.getId() : -1;
    }

    public int getAdvancedAudioHapticsAlternateSetting() {
        return hapticIface != null ? hapticIface.getAlternateSetting() : -1;
    }

    public int getAdvancedAudioHapticsEndpointAddress() {
        return hapticEndpt != null ? hapticEndpt.getAddress() : -1;
    }

    protected synchronized void invalidateAdvancedAudioHapticsPrime() {
        if (advancedAudioHapticsRequested) {
            advancedAudioHapticsPrimed = false;
        }
    }

    protected synchronized void updateAdvancedAudioHapticsTriggerCache(byte[] report) {
        if (report == null || report.length < REPORT_MIN_TRIGGER_LEN || report[0] != 0x02) {
            return;
        }

        cachedRightTriggerType = report[REPORT_RIGHT_TRIGGER_TYPE_IDX];
        System.arraycopy(report, REPORT_RIGHT_TRIGGER_DATA_IDX,
                cachedRightTriggerData, 0, TRIGGER_DATA_LEN);
        cachedLeftTriggerType = report[REPORT_LEFT_TRIGGER_TYPE_IDX];
        System.arraycopy(report, REPORT_LEFT_TRIGGER_DATA_IDX,
                cachedLeftTriggerData, 0, TRIGGER_DATA_LEN);
    }

    private boolean tryPrimeAdvancedAudioHaptics() {
        if (outEndpt == null) {
            return false;
        }

        byte[] initReport = new byte[HAPTIC_INIT_REPORT_SIZE];
        initReport[0] = 0x02;
        initReport[1] = 0x0C;
        initReport[2] = 0x40;

        synchronized (this) {
            initReport[REPORT_RIGHT_TRIGGER_TYPE_IDX] = cachedRightTriggerType;
            System.arraycopy(cachedRightTriggerData, 0,
                    initReport, REPORT_RIGHT_TRIGGER_DATA_IDX, TRIGGER_DATA_LEN);
            initReport[REPORT_LEFT_TRIGGER_TYPE_IDX] = cachedLeftTriggerType;
            System.arraycopy(cachedLeftTriggerData, 0,
                    initReport, REPORT_LEFT_TRIGGER_DATA_IDX, TRIGGER_DATA_LEN);
        }

        int res = connection.bulkTransfer(outEndpt, initReport, initReport.length, HAPTIC_INIT_TIMEOUT_MS);
        if (res == initReport.length) {
            advancedAudioHapticsPrimed = true;
            return true;
        }

        Log.w("DualSenseController", "Advanced haptics prime failed: " + res);
        return false;
    }

    private float clampAdvancedAudioHapticsGain(float gain) {
        if (Float.isNaN(gain) || Float.isInfinite(gain)) {
            return 0.5f;
        }
        if (gain < 0f) {
            return 0f;
        }
        if (gain > 2.75f) {
            return 2.75f;
        }
        return gain;
    }

    protected abstract boolean handleRead(ByteBuffer buffer);
    protected abstract boolean doInit();
}
