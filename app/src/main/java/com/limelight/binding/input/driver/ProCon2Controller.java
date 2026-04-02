package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class ProCon2Controller extends AbstractController {

    private static final int NINTENDO_VENDOR_ID = 0x057e;
    private static final int SWITCH2_PRO_PRODUCT_ID = 0x2069;
    private static final int USB_PACKET_SIZE = 64;
    private static final int FLASH_BLOCK_READ_SIZE = 0x40;
    private static final int FLASH_READ_RESPONSE_SIZE = 0x50;
    private static final int USB_INIT_INTERFACE_ID = 1;
    private static final int INIT_RECV_TIMEOUT_MS = 200;
    private static final int INPUT_RECV_TIMEOUT_MS = 3000;
    private static final int RUMBLE_INTERVAL_MS = 12;
    private static final int RUMBLE_MAX = 29000;
    private static final int FACTORY_LEFT_STICK_CALIBRATION_ADDRESS = 0x13080;
    private static final int FACTORY_RIGHT_STICK_CALIBRATION_ADDRESS = 0x130C0;
    private static final int FACTORY_GYRO_BIAS_ADDRESS = 0x13040;
    private static final int FACTORY_ACCEL_BIAS_ADDRESS = 0x13100;
    private static final int USER_LEFT_STICK_CALIBRATION_ADDRESS = 0x1FC040;
    private static final int USER_RIGHT_STICK_CALIBRATION_ADDRESS = 0x1FC080;
    private static final float DEFAULT_GYRO_COEFFICIENT = 34.8f;
    private static final float DEFAULT_ACCEL_SCALE = 9.80665f * 8.0f / 32767.0f;

    private static final byte[][] USB_INIT_SEQUENCE = new byte[][] {
            new byte[] {0x07, (byte) 0x91, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00},
            new byte[] {0x0c, (byte) 0x91, 0x00, 0x02, 0x00, 0x04, 0x00, 0x00, 0x27, 0x00, 0x00, 0x00},
            new byte[] {0x11, (byte) 0x91, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00},
            new byte[] {
                    0x0a, (byte) 0x91, 0x00, 0x08, 0x00, 0x14, 0x00, 0x00,
                    0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, 0x35, 0x00, 0x46, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00
            },
            new byte[] {0x0c, (byte) 0x91, 0x00, 0x04, 0x00, 0x04, 0x00, 0x00, 0x27, 0x00, 0x00, 0x00},
            new byte[] {0x01, (byte) 0x91, 0x00, 0x0c, 0x00, 0x00, 0x00, 0x00},
            new byte[] {0x01, (byte) 0x91, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00},
            new byte[] {0x08, (byte) 0x91, 0x00, 0x02, 0x00, 0x04, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00},
            new byte[] {0x03, (byte) 0x91, 0x00, 0x0a, 0x00, 0x04, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00},
            new byte[] {
                    0x03, (byte) 0x91, 0x00, 0x0d, 0x00, 0x08, 0x00, 0x00,
                    0x01, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
            }
    };

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final List<UsbInterface> claimedInterfaces = new ArrayList<>();
    private final StickCalibration leftStickCalibration = new StickCalibration();
    private final StickCalibration rightStickCalibration = new StickCalibration();

    private UsbInterface bulkInterface;
    private UsbEndpoint bulkInEndpoint;
    private UsbEndpoint bulkOutEndpoint;
    private UsbInterface hidInterface;
    private UsbEndpoint hidInEndpoint;
    private UsbEndpoint hidOutEndpoint;

    private Thread inputThread;
    private boolean stopped;
    private long rumbleTimestamp;
    private int rumbleSeq;
    private int rumbleLowAmp;
    private int rumbleHighAmp;
    private boolean rumbleUpdated;
    private boolean sensorsEnabled;
    private float gyroBiasX;
    private float gyroBiasY;
    private float gyroBiasZ;
    private float accelBiasX;
    private float accelBiasY;
    private float accelBiasZ;

    private static final class AxisCalibration {
        int neutral;
        int max;
        int min;
    }

    private static final class StickCalibration {
        final AxisCalibration x = new AxisCalibration();
        final AxisCalibration y = new AxisCalibration();
    }

    public static boolean canClaimDevice(UsbDevice device) {
        return device.getVendorId() == NINTENDO_VENDOR_ID &&
                device.getProductId() == SWITCH2_PRO_PRODUCT_ID;
    }

    public ProCon2Controller(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_NINTENDO;
        this.capabilities = MoonBridge.LI_CCAP_RUMBLE | MoonBridge.LI_CCAP_GYRO | MoonBridge.LI_CCAP_ACCEL;
        applyDefaultStickCalibration(leftStickCalibration);
        applyDefaultStickCalibration(rightStickCalibration);
    }

    @Override
    public boolean start() {
        stopped = false;
        claimedInterfaces.clear();
        bulkInterface = null;
        bulkInEndpoint = null;
        bulkOutEndpoint = null;
        hidInterface = null;
        hidInEndpoint = null;
        hidOutEndpoint = null;
        sensorsEnabled = false;
        gyroBiasX = 0.0f;
        gyroBiasY = 0.0f;
        gyroBiasZ = 0.0f;
        accelBiasX = 0.0f;
        accelBiasY = 0.0f;
        accelBiasZ = 0.0f;

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("ProCon2: Failed to claim interface " + iface.getId());
                releaseClaimedInterfaces();
                return false;
            }
            claimedInterfaces.add(iface);

            if (iface.getId() == USB_INIT_INTERFACE_ID) {
                findBulkEndpoints(iface);
            }

            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_HID && hidInEndpoint == null) {
                findHidEndpoints(iface);
            }
        }

        if (bulkInterface == null || bulkInEndpoint == null || bulkOutEndpoint == null) {
            LimeLog.warning("ProCon2: Missing bulk init interface or endpoints");
            releaseClaimedInterfaces();
            return false;
        }

        if (hidInterface == null || hidInEndpoint == null) {
            LimeLog.warning("ProCon2: Missing HID input interface");
            releaseClaimedInterfaces();
            return false;
        }

        if (!initializeUsbMode()) {
            releaseClaimedInterfaces();
            return false;
        }

        inputThread = createInputThread();
        inputThread.start();
        return true;
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }

        stopped = true;

        rumble((short) 0, (short) 0);
        updateRumble();

        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
        }

        releaseClaimedInterfaces();
        connection.close();
        notifyDeviceRemoved();
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        synchronized (this) {
            int newLow = lowFreqMotor & 0xFFFF;
            int newHigh = highFreqMotor & 0xFFFF;
            if (newLow != rumbleLowAmp || newHigh != rumbleHighAmp) {
                rumbleLowAmp = newLow;
                rumbleHighAmp = newHigh;
                rumbleUpdated = true;
            }
        }
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // Switch 2 Pro exposes digital trigger buttons only.
    }

    private void findBulkEndpoints(UsbInterface iface) {
        UsbEndpoint foundIn = null;
        UsbEndpoint foundOut = null;

        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = iface.getEndpoint(i);
            if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) {
                continue;
            }

            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                foundIn = endpoint;
            } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                foundOut = endpoint;
            }
        }

        if (foundIn != null && foundOut != null) {
            bulkInterface = iface;
            bulkInEndpoint = foundIn;
            bulkOutEndpoint = foundOut;
        }
    }

    private void findHidEndpoints(UsbInterface iface) {
        UsbEndpoint foundIn = null;
        UsbEndpoint foundOut = null;

        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = iface.getEndpoint(i);
            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN && foundIn == null) {
                foundIn = endpoint;
            } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT && foundOut == null) {
                foundOut = endpoint;
            }
        }

        if (foundIn != null) {
            hidInterface = iface;
            hidInEndpoint = foundIn;
            hidOutEndpoint = foundOut;
        }
    }

    private boolean initializeUsbMode() {
        byte[] responseBuffer = new byte[USB_PACKET_SIZE];

        loadStickCalibration();
        loadSensorCalibration();

        for (byte[] packet : USB_INIT_SEQUENCE) {
            int sent = connection.bulkTransfer(bulkOutEndpoint, packet, packet.length, 1000);
            if (sent != packet.length) {
                LimeLog.warning("ProCon2: Failed to send init packet, sent=" + sent + " expected=" + packet.length);
                return false;
            }

            int received = connection.bulkTransfer(bulkInEndpoint, responseBuffer, responseBuffer.length, INIT_RECV_TIMEOUT_MS);
            if (received < 0) {
                LimeLog.warning("ProCon2: Failed to read init reply");
                return false;
            }
        }

        sensorsEnabled = enableSensors();
        LimeLog.info("ProCon2: USB init sequence completed");
        return true;
    }

    private Thread createInputThread() {
        return new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }

            notifyDeviceAdded();

            while (!Thread.currentThread().isInterrupted() && !stopped) {
                byte[] buffer = new byte[USB_PACKET_SIZE];
                int res;

                do {
                    long lastMillis = SystemClock.uptimeMillis();
                    res = connection.bulkTransfer(hidInEndpoint, buffer, buffer.length, INPUT_RECV_TIMEOUT_MS);

                    if (res == 0) {
                        res = -1;
                    }

                    if (res == -1 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                        LimeLog.warning("ProCon2: Detected device I/O error");
                        ProCon2Controller.this.stop();
                        break;
                    }
                } while (res == -1 && !Thread.currentThread().isInterrupted() && !stopped);

                if (res == -1 || stopped) {
                    break;
                }

                if (handleRead(buffer, res)) {
                    reportInput();
                    reportMotion();
                }

                updateRumble();
            }
        }, "ProCon2 Input");
    }

    private boolean handleRead(byte[] data, int size) {
        if (size < 17) {
            return false;
        }

        buttonFlags = 0;

        // Nintendo layout is swapped to match Moonlight's logical ABXY positions.
        setButtonFlag(ControllerPacket.X_FLAG, data[5] & 0x01);
        setButtonFlag(ControllerPacket.Y_FLAG, data[5] & 0x02);
        setButtonFlag(ControllerPacket.A_FLAG, data[5] & 0x04);
        setButtonFlag(ControllerPacket.B_FLAG, data[5] & 0x08);
        setButtonFlag(ControllerPacket.RB_FLAG, data[5] & 0x40);
        rightTrigger = (data[5] & 0x80) != 0 ? 1.0f : 0.0f;

        setButtonFlag(ControllerPacket.BACK_FLAG, data[6] & 0x01);
        setButtonFlag(ControllerPacket.PLAY_FLAG, data[6] & 0x02);
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, data[6] & 0x04);
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, data[6] & 0x08);
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, data[6] & 0x10);
        setButtonFlag(ControllerPacket.MISC_FLAG, data[6] & 0x20);
        setButtonFlag(ControllerPacket.PADDLE1_FLAG, data[6] & 0x40);

        setButtonFlag(ControllerPacket.DOWN_FLAG, data[7] & 0x01);
        setButtonFlag(ControllerPacket.UP_FLAG, data[7] & 0x02);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, data[7] & 0x04);
        setButtonFlag(ControllerPacket.LEFT_FLAG, data[7] & 0x08);
        setButtonFlag(ControllerPacket.LB_FLAG, data[7] & 0x40);
        leftTrigger = (data[7] & 0x80) != 0 ? 1.0f : 0.0f;

        setButtonFlag(ControllerPacket.PADDLE2_FLAG, data[8] & 0x01);
        setButtonFlag(ControllerPacket.PADDLE3_FLAG, data[8] & 0x02);

        leftStickX = normalizeStick((data[11] & 0xFF) | ((data[12] & 0x0F) << 8), leftStickCalibration, false, false);
        leftStickY = normalizeStick(((data[12] & 0xF0) >> 4) | ((data[13] & 0xFF) << 4), leftStickCalibration, true, true);
        rightStickX = normalizeStick((data[14] & 0xFF) | ((data[15] & 0x0F) << 8), rightStickCalibration, false, false);
        rightStickY = normalizeStick(((data[15] & 0xF0) >> 4) | ((data[16] & 0xFF) << 4), rightStickCalibration, true, true);

        if (sensorsEnabled && size >= 0x3d) {
            parseSensors(data);
        } else {
            gyroX = 0.0f;
            gyroY = 0.0f;
            gyroZ = 0.0f;
            accelX = 0.0f;
            accelY = 0.0f;
            accelZ = 0.0f;
        }

        return true;
    }

    private float normalizeStick(int rawValue, StickCalibration stickCalibration, boolean yAxis, boolean invert) {
        AxisCalibration calibration = yAxis ? stickCalibration.y : stickCalibration.x;
        float delta = rawValue - calibration.neutral;
        float divisor = delta < 0 ? calibration.min : calibration.max;
        if (divisor <= 0) {
            divisor = 2047.0f;
        }

        float normalized = delta / divisor;
        if (normalized < -1.0f) {
            normalized = -1.0f;
        } else if (normalized > 1.0f) {
            normalized = 1.0f;
        }
        return invert ? -normalized : normalized;
    }

    private boolean loadStickCalibration() {
        byte[] flashData = new byte[FLASH_BLOCK_READ_SIZE];

        if (readFlashBlock(FACTORY_LEFT_STICK_CALIBRATION_ADDRESS, flashData)) {
            parseStickCalibration(leftStickCalibration, flashData, 0x28);
        }

        if (readFlashBlock(FACTORY_RIGHT_STICK_CALIBRATION_ADDRESS, flashData)) {
            parseStickCalibration(rightStickCalibration, flashData, 0x28);
        }

        if (readFlashBlock(USER_LEFT_STICK_CALIBRATION_ADDRESS, flashData) &&
                (flashData[0] & 0xFF) == 0xB2 && (flashData[1] & 0xFF) == 0xA1) {
            parseStickCalibration(leftStickCalibration, flashData, 2);
        }

        if (readFlashBlock(USER_RIGHT_STICK_CALIBRATION_ADDRESS, flashData) &&
                (flashData[0] & 0xFF) == 0xB2 && (flashData[1] & 0xFF) == 0xA1) {
            parseStickCalibration(rightStickCalibration, flashData, 2);
        }

        return true;
    }

    private void loadSensorCalibration() {
        byte[] flashData = new byte[FLASH_BLOCK_READ_SIZE];

        if (readFlashBlock(FACTORY_GYRO_BIAS_ADDRESS, flashData)) {
            gyroBiasX = readLittleEndianFloat(flashData, 4);
            gyroBiasY = readLittleEndianFloat(flashData, 8);
            gyroBiasZ = readLittleEndianFloat(flashData, 12);
        }

        if (readFlashBlock(FACTORY_ACCEL_BIAS_ADDRESS, flashData)) {
            accelBiasX = readLittleEndianFloat(flashData, 12);
            accelBiasY = readLittleEndianFloat(flashData, 16);
            accelBiasZ = readLittleEndianFloat(flashData, 20);
        }
    }

    private boolean readFlashBlock(int address, byte[] out) {
        byte[] flashReadCommand = new byte[] {
                0x02, (byte) 0x91, 0x00, 0x01, 0x00, 0x08, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        byte[] response = new byte[FLASH_READ_RESPONSE_SIZE];

        flashReadCommand[12] = (byte) address;
        flashReadCommand[13] = (byte) (address >> 8);
        flashReadCommand[14] = (byte) (address >> 16);
        flashReadCommand[15] = (byte) (address >> 24);

        int sent = connection.bulkTransfer(bulkOutEndpoint, flashReadCommand, flashReadCommand.length, 1000);
        if (sent != flashReadCommand.length) {
            LimeLog.warning("ProCon2: Failed to send flash read command for 0x" + Integer.toHexString(address));
            return false;
        }

        int received = connection.bulkTransfer(bulkInEndpoint, response, response.length, INIT_RECV_TIMEOUT_MS);
        if (received < 0x10 + FLASH_BLOCK_READ_SIZE) {
            LimeLog.warning("ProCon2: Failed to read flash response for 0x" + Integer.toHexString(address));
            return false;
        }

        System.arraycopy(response, 0x10, out, 0, FLASH_BLOCK_READ_SIZE);
        return true;
    }

    private boolean enableSensors() {
        byte[] command = new byte[] {
                0x0c, (byte) 0x91, 0x00, 0x04, 0x00, 0x04, 0x00, 0x00,
                0x23, 0x00, 0x00, 0x00
        };
        byte[] reply = new byte[12];

        int sent = connection.bulkTransfer(bulkOutEndpoint, command, command.length, 1000);
        if (sent != command.length) {
            LimeLog.warning("ProCon2: Failed to send sensor enable packet");
            return false;
        }

        int received = connection.bulkTransfer(bulkInEndpoint, reply, reply.length, INIT_RECV_TIMEOUT_MS);
        if (received < 0) {
            LimeLog.warning("ProCon2: Failed to read sensor enable reply");
            return false;
        }

        return true;
    }

    private void parseStickCalibration(StickCalibration stickCalibration, byte[] data, int offset) {
        stickCalibration.x.neutral = (data[offset] & 0xFF) | ((data[offset + 1] & 0x0F) << 8);
        stickCalibration.y.neutral = ((data[offset + 1] & 0xF0) >> 4) | ((data[offset + 2] & 0xFF) << 4);
        stickCalibration.x.max = (data[offset + 3] & 0xFF) | ((data[offset + 4] & 0x0F) << 8);
        stickCalibration.y.max = ((data[offset + 4] & 0xF0) >> 4) | ((data[offset + 5] & 0xFF) << 4);
        stickCalibration.x.min = (data[offset + 6] & 0xFF) | ((data[offset + 7] & 0x0F) << 8);
        stickCalibration.y.min = ((data[offset + 7] & 0xF0) >> 4) | ((data[offset + 8] & 0xFF) << 4);
    }

    private void applyDefaultStickCalibration(StickCalibration stickCalibration) {
        stickCalibration.x.neutral = 2048;
        stickCalibration.y.neutral = 2048;
        stickCalibration.x.max = 2047;
        stickCalibration.y.max = 2047;
        stickCalibration.x.min = 2048;
        stickCalibration.y.min = 2048;
    }

    private void parseSensors(byte[] data) {
        // Switch 2 uses a different final axis definition than the original Switch path.
        // Keep this aligned with SDL's switch2 driver and Moonlight's direct XYZ semantics used
        // by the PlayStation controllers.
        accelX = readLittleEndianShort(data, 0x31) / 4096.0f;
        accelY = readLittleEndianShort(data, 0x35) / 4096.0f;
        accelZ = -readLittleEndianShort(data, 0x33) / 4096.0f;

        gyroX = readLittleEndianShort(data, 0x37) / 16.0f;
        gyroY = readLittleEndianShort(data, 0x3b) / 16.0f;
        gyroZ = -readLittleEndianShort(data, 0x39) / 16.0f;
    }

    private short readLittleEndianShort(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private float readLittleEndianFloat(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private void updateRumble() {
        if (hidOutEndpoint == null) {
            return;
        }

        byte[] rumblePacket = null;

        synchronized (this) {
            if (!rumbleUpdated && rumbleLowAmp == 0 && rumbleHighAmp == 0) {
                return;
            }

            long now = SystemClock.uptimeMillis();
            if (rumbleTimestamp != 0 && now < rumbleTimestamp) {
                return;
            }

            rumblePacket = buildRumblePacket();
            rumbleSeq = (rumbleSeq + 1) & 0x0F;
            rumbleUpdated = false;

            if (rumbleLowAmp == 0 && rumbleHighAmp == 0) {
                rumbleTimestamp = 0;
            } else {
                if (rumbleTimestamp == 0) {
                    rumbleTimestamp = now;
                }
                rumbleTimestamp += RUMBLE_INTERVAL_MS;
            }
        }

        int sent = connection.bulkTransfer(hidOutEndpoint, rumblePacket, rumblePacket.length, 100);
        if (sent != rumblePacket.length) {
            LimeLog.warning("ProCon2: Failed to send rumble packet, sent=" + sent);
        }
    }

    private byte[] buildRumblePacket() {
        byte[] rumblePacket = new byte[USB_PACKET_SIZE];
        int lowAmp = (rumbleLowAmp * RUMBLE_MAX) / 0xFFFF;
        int highAmp = (rumbleHighAmp * RUMBLE_MAX) / 0xFFFF;

        rumblePacket[0] = 0x02;
        rumblePacket[1] = (byte) (0x50 | (rumbleSeq & 0x0F));
        encodeHdRumble(0x0187, highAmp, 0x0112, lowAmp, rumblePacket, 2);
        System.arraycopy(rumblePacket, 1, rumblePacket, 0x11, 6);

        return rumblePacket;
    }

    private void encodeHdRumble(int highFreq, int highAmp, int lowFreq, int lowAmp, byte[] out, int offset) {
        out[offset] = (byte) (highFreq & 0xFF);
        out[offset + 1] = (byte) ((((highAmp >> 4) & 0xFC) | ((highFreq >> 8) & 0x03)) & 0xFF);
        out[offset + 2] = (byte) ((((highAmp >> 12) & 0x0F) | (lowFreq << 4)) & 0xFF);
        out[offset + 3] = (byte) ((((lowAmp & 0xC0) | ((lowFreq >> 4) & 0x3F))) & 0xFF);
        out[offset + 4] = (byte) ((lowAmp >> 8) & 0xFF);
    }

    private void releaseClaimedInterfaces() {
        for (UsbInterface iface : claimedInterfaces) {
            connection.releaseInterface(iface);
        }
        claimedInterfaces.clear();
    }
}
