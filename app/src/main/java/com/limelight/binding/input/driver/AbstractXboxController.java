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

public abstract class AbstractXboxController extends AbstractController {
    protected final UsbDevice device;
    protected final UsbDeviceConnection connection;

    private Thread inputThread;
    private boolean stopped;

    protected UsbEndpoint inEndpt, outEndpt;

    public AbstractXboxController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_XBOX;
        this.capabilities = MoonBridge.LI_CCAP_ANALOG_TRIGGERS | MoonBridge.LI_CCAP_RUMBLE;
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

                    // Use a short transfer timeout with a small sleep between
                    // attempts instead of a single long (3000ms) blocking read.
                    // On low-end TV SoCs (e.g. Amlogic T972) a long pending bulk
                    // transfer on an idle wireless dongle input endpoint keeps
                    // the Android USB framework's device lock held for seconds,
                    // which periodically stalls the whole process (audio feed
                    // thread included) and causes audio dropouts.
                    int ioErrorCount = 0;
                    while (true) {
                        // Read the next input state packet
                        long lastMillis = SystemClock.uptimeMillis();
                        res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 64);

                        // If we get a zero length response, treat it as an error
                        if (res == 0) {
                            res = -1;
                        }

                        if (res == -1) {
                            // A fast failure (< timeout) is a real device I/O error
                            // (e.g. the wireless dongle hiccuped or was unplugged).
                            // Only tear the controller down after many consecutive
                            // fast failures so transient 2.4G link glitches don't
                            // kill the gamepad and trigger USB re-open storms.
                            long elapsed = SystemClock.uptimeMillis() - lastMillis;
                            if (elapsed < 64) {
                                if (++ioErrorCount > 50) {
                                    LimeLog.warning("Detected device I/O error");
                                    AbstractXboxController.this.stop();
                                    return;
                                }
                            }
                            else {
                                // Timeout with no data: normal idle state
                                ioErrorCount = 0;
                            }

                            // Give the USB stack a breather between attempts
                            try {
                                Thread.sleep(8);
                            } catch (InterruptedException e) {
                                return;
                            }
                            continue;
                        }
                        break;
                    }

                    if (stopped) {
                        break;
                    }

                    if (handleRead(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN))) {
                        // Report input if handleRead() returns true
                        reportInput();
                    }
                }
            }
        };
    }
    private List<UsbInterface> ifaces=new ArrayList<>();

    public boolean start() {
        ifaces.clear();
        // Force claim all interfaces except audio so the system can handle the headset jack
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);

            // Let the OS own the USB audio interface so headset audio keeps working
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
                continue;
            }

            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("Failed to claim interfaces");
                return false;
            }else{
                ifaces.add(iface);
            }
        }

        // Find the endpoints
        UsbInterface iface = device.getInterface(0);
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpt = iface.getEndpoint(i);
            if (endpt.getDirection() == UsbConstants.USB_DIR_IN) {
                if (inEndpt != null) {
                    LimeLog.warning("Found duplicate IN endpoint");
                    return false;
                }
                inEndpt = endpt;
            }
            else if (endpt.getDirection() == UsbConstants.USB_DIR_OUT) {
                if (outEndpt != null) {
                    LimeLog.warning("Found duplicate OUT endpoint");
                    return false;
                }
                outEndpt = endpt;
            }
        }

        // Make sure the required endpoints were present
        if (inEndpt == null || outEndpt == null) {
            LimeLog.warning("Missing required endpoint");
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

    protected abstract boolean handleRead(ByteBuffer buffer);
    protected abstract boolean doInit();
}
