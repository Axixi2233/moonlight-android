package com.limelight.binding.input.driver;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.InputDevice;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

public class UsbDriverService extends Service implements UsbDriverListener {

    private static final String ACTION_USB_PERMISSION =
            "com.limelight.USB_PERMISSION";

    private UsbManager usbManager;
    private PreferenceConfiguration prefConfig;
    private boolean started;

    private final UsbEventReceiver receiver = new UsbEventReceiver();
    private final UsbDriverBinder binder = new UsbDriverBinder();

    private final ArrayList<AbstractController> controllers = new ArrayList<>();

    private UsbDriverListener listener;
    private UsbDriverStateListener stateListener;
    private int nextDeviceId;
    private final Handler usbIpHandler = new Handler(Looper.getMainLooper());
    private InetAddress usbIpPeer;
    private byte[] usbIpTlsToken;
    private volatile InetAddress usbIpLocalAddress;
    private boolean usbIpSessionActive;
    private UsbIpControllerExport usbIpExport;
    private String usbIpDeviceName;
    private String usbIpFallbackDeviceName;
    private int usbIpStatusRes = R.string.usbip_disabled;
    private boolean usbIpGamepad;
    private static final long USBIP_IMPORT_TIMEOUT_MS = 30_000;

    @Override
    public void reportControllerState(int controllerId, int buttonFlags, float leftStickX, float leftStickY,
                                      float rightStickX, float rightStickY, float leftTrigger, float rightTrigger) {
        // Call through to the client's listener
        if (listener != null) {
            listener.reportControllerState(controllerId, buttonFlags, leftStickX, leftStickY, rightStickX, rightStickY, leftTrigger, rightTrigger);
        }
    }

    @Override
    public void reportControllerMotion(int controllerId, byte motionType, float motionX, float motionY, float motionZ) {
        // Call through to the client's listener
        if (listener != null) {
            listener.reportControllerMotion(controllerId, motionType, motionX, motionY, motionZ);
        }
    }

    @Override
    public void reportControllerTouchpadEvent(int controllerId, byte eventType, int pointerId,
                                              float x, float y, float pressure) {
        if (listener != null) {
            listener.reportControllerTouchpadEvent(controllerId, eventType, pointerId, x, y, pressure);
        }
    }

    @Override
    public void deviceRemoved(AbstractController controller) {
        // Remove the the controller from our list (if not removed already)
        controllers.remove(controller);

        // Call through to the client's listener
        if (listener != null) {
            listener.deviceRemoved(controller);
        }
    }

    @Override
    public void deviceAdded(AbstractController controller) {
        // Call through to the client's listener
        if (listener != null) {
            listener.deviceAdded(controller);
        }
    }

    public class UsbEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // Initial attachment broadcast
            if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                // USB/IP claims the already enumerated USB device directly, without consulting system input.
                if (device != null && usbIpPeer != null && usbIpSessionActive && usbIpExport == null
                        && UsbIpControllerExport.supports(device)) {
                    handleUsbDeviceState(device);
                    return;
                }

                // shouldClaimDevice() looks at the kernel's enumerated input
                // devices to make its decision about whether to prompt to take
                // control of the device. The kernel bringing up the input stack
                // may race with this callback and cause us to prompt when the
                // kernel is capable of running the device. Let's post a delayed
                // message to process this state change to allow the kernel
                // some time to bring up the stack.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Continue the state machine
                        handleUsbDeviceState(device);
                    }
                }, 1000);
            }
            else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.getDeviceName().equals(usbIpDeviceName)) {
                    stopUsbIpExport();
                }
                if (device != null && device.getDeviceName().equals(usbIpFallbackDeviceName)) {
                    usbIpFallbackDeviceName = null;
                    usbIpStatusRes = R.string.usbip_waiting_device;
                }
            }
            // Subsequent permission dialog completion intent
            else if (action.equals(ACTION_USB_PERMISSION)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                // Permission dialog is now closed
                if (stateListener != null) {
                    stateListener.onUsbPermissionPromptCompleted();
                }

                // If we got this far, we've already found we're able to handle this device
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    handleUsbDeviceState(device);
                } else if (device != null && usbIpPeer != null && usbIpSessionActive
                        && UsbIpControllerExport.supports(device)) {
                    usbIpStatusRes = R.string.usbip_permission_denied;
                }
            }
        }
    }

    public class UsbDriverBinder extends Binder {
        public void setListener(UsbDriverListener listener) {
            UsbDriverService.this.listener = listener;

            // Report all controllerMap that already exist
            if (listener != null) {
                for (AbstractController controller : controllers) {
                    listener.deviceAdded(controller);
                }
            }
        }

        public void setStateListener(UsbDriverStateListener stateListener) {
            UsbDriverService.this.stateListener = stateListener;
        }

        public void start() {
            UsbDriverService.this.start();
        }

        public void stop() {
            UsbDriverService.this.stop();
        }

        /** Configure the importer address from the selected stream host. */
        public void configureUsbIp(String host, String importHost, String tlsToken) {
            usbIpTlsToken = UsbIpTls.validToken(tlsToken) ? tlsToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII) : null;
            boolean invalidTls = tlsToken != null && usbIpTlsToken == null;
            usbIpPeer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? UsbIpNetwork.resolvePeer(host, importHost) : null;
            if (invalidTls || !UsbIpNetwork.isAllowedPeer(usbIpPeer, hasDirectUsbIpRoute(usbIpPeer), usbIpTlsToken != null)) {
                usbIpPeer = null;
            }
            usbIpLocalAddress = null;
            final InetAddress peer = usbIpPeer;
            if (peer != null) {
                LimeLog.info("USB/IP authorized importer: " + peer.getHostAddress());
                new Thread(() -> {
                    try {
                        InetAddress local = UsbIpNetwork.localAddress(peer);
                        usbIpHandler.post(() -> {
                            if (usbIpPeer == peer) usbIpLocalAddress = local;
                        });
                    } catch (IOException e) {
                        LimeLog.warning("USB/IP source address unavailable: " + e);
                    }
                }, "USB/IP address").start();
            }
            usbIpStatusRes = host == null ? R.string.usbip_disabled
                    : Build.VERSION.SDK_INT < Build.VERSION_CODES.O ? R.string.usbip_android_required
                    : invalidTls ? R.string.usbip_secure_error
                    : usbIpPeer == null ? R.string.usbip_host_required : R.string.usbip_waiting_session;
        }

        /** Return the session's USB/IP state for the full performance overlay. */
        public String getUsbIpStatus() {
            return getString(usbIpStatusRes) + (usbIpTlsToken != null ? " · TLS" : "");
        }

        /** Return the OS-selected streaming-family address and the USB/IP listening port. */
        public String getUsbIpAddress() {
            if (usbIpPeer == null) {
                return "--";
            }
            InetAddress address = usbIpLocalAddress;
            return address == null ? "--" : UsbIpNetwork.endpoint(address);
        }

        /** Keep the export listener open only during a connected stream. */
        public void setUsbIpSessionActive(boolean active) {
            if (usbIpPeer == null || usbIpSessionActive == active) {
                return;
            }
            usbIpSessionActive = active;
            if (active) {
                usbIpStatusRes = R.string.usbip_waiting_device;
                for (UsbDevice device : usbManager.getDeviceList().values()) {
                    if (UsbIpControllerExport.supports(device)) {
                        handleUsbDeviceState(device);
                        break;
                    }
                }
            } else {
                usbIpStatusRes = R.string.usbip_waiting_session;
                usbIpFallbackDeviceName = null;
                stopUsbIpExport();
            }
        }

        public void changeUSBFlag(boolean flag){
            prefConfig.bindAllUsb=flag;
        }

    }

    /** Require a non-default on-link route before exposing USB/IP to a global IPv6 stream host. */
    private boolean hasDirectUsbIpRoute(InetAddress peer) {
        if (peer == null) return false;
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        for (Network network : manager.getAllNetworks()) {
            LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) continue;
            for (RouteInfo route : properties.getRoutes()) {
                if (!route.hasGateway() && route.getDestination().getPrefixLength() > 0 && route.matches(peer)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleUsbDeviceState(UsbDevice device) {
        if (!started || device == null || !usbManager.getDeviceList().containsKey(device.getDeviceName())) {
            return;
        }
        boolean export = usbIpPeer != null && UsbIpControllerExport.supports(device)
                && !device.getDeviceName().equals(usbIpFallbackDeviceName)
                && (usbIpExport == null || device.getDeviceName().equals(usbIpDeviceName));
        if (export && (!usbIpSessionActive || usbIpExport != null)) {
            return;
        }
        if (export || (prefConfig.usbDriver && shouldClaimDevice(device, prefConfig.bindAllUsb))) {
            // Do we have permission yet?
            if (!usbManager.hasPermission(device)) {
                if (export) {
                    usbIpStatusRes = R.string.usbip_waiting_permission;
                }
                // Let's ask for permission
                try {
                    // Tell the state listener that we're about to display a permission dialog
                    if (stateListener != null) {
                        stateListener.onUsbPermissionPromptStarting();
                    }

                    int intentFlags = 0;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // This PendingIntent must be mutable to allow the framework to populate EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED.
                        intentFlags |= PendingIntent.FLAG_MUTABLE;
                    }

                    // This function is not documented as throwing any exceptions (denying access
                    // is indicated by calling the PendingIntent with a false result). However,
                    // Samsung Knox has some policies which block this request, but rather than
                    // just returning a false result or returning 0 enumerated devices,
                    // they throw an undocumented SecurityException from this call, crashing
                    // the whole app. :(

                    // Use an explicit intent to activate our unexported broadcast receiver, as required on Android 14+
                    Intent i = new Intent(ACTION_USB_PERMISSION);
                    i.setPackage(getPackageName());

                    usbManager.requestPermission(device, PendingIntent.getBroadcast(UsbDriverService.this, 0, i, intentFlags));
                } catch (SecurityException e) {
                    if (export) {
                        usbIpStatusRes = R.string.error_usb_prohibited;
                    } else {
                        Toast.makeText(this, this.getText(R.string.error_usb_prohibited), Toast.LENGTH_LONG).show();
                    }
                    if (stateListener != null) {
                        stateListener.onUsbPermissionPromptCompleted();
                    }
                }
                return;
            }

            // Open the device
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) {
                if (export) {
                    usbIpStatusRes = R.string.usbip_open_failed;
                }
                LimeLog.warning("Unable to open USB device: "+device.getDeviceName());
                return;
            }


            if (export) {
                startUsbIpExport(device, connection);
                return;
            }

            AbstractController controller;

            if (XboxOneController.canClaimDevice(device)) {
                controller = new XboxOneController(device, connection, nextDeviceId++, this);
            }
            else if (Xbox360Controller.canClaimDevice(device)) {
                controller = new Xbox360Controller(device, connection, nextDeviceId++, this);
            }
            else if (Xbox360WirelessDongle.canClaimDevice(device)) {
                controller = new Xbox360WirelessDongle(device, connection, nextDeviceId++, this);
            }
            else if (ProCon2Controller.canClaimDevice(device)) {
                controller = new ProCon2Controller(device, connection, nextDeviceId++, this);
            }
            else if (ProConController.canClaimDevice(device)) {
                controller = new ProConController(device, connection, nextDeviceId++, this);
            }
            else if (DualSenseController.canClaimDevice(device)) {
                controller = new DualSenseController(device, connection, nextDeviceId++, this);
            }else if (Dualshock4Controller.canClaimDevice(device)) {
                controller = new Dualshock4Controller(device, connection, nextDeviceId++, this);
            }
            else {
                // Unreachable
                return;
            }

            // Start the controller
            if (!controller.start()) {
                connection.close();
                return;
            }

            // Add this controller to the list
            controllers.add(controller);
        }else{
            if (stateListener != null) {
                stateListener.onUSBInfo(device);
            }
        }
    }

    private void startUsbIpExport(UsbDevice device, UsbDeviceConnection connection) {
        usbIpGamepad = UsbIpControllerExport.isGamepad(device, connection);
        if (usbIpGamepad && stateListener != null) {
            stateListener.onUsbIpHandoffStarted(device);
        }
        try {
            UsbIpControllerExport exporter = new UsbIpControllerExport(device, connection, usbIpPeer, usbIpTlsToken, com.limelight.binding.PlatformBinding.getCryptoProvider(UsbDriverService.this));
            usbIpExport = exporter;
            usbIpDeviceName = device.getDeviceName();
            exporter.start(new UsbIpControllerExport.Listener() {
                private boolean imported;

                private final Runnable timeout = () -> {
                    if (usbIpExport == exporter && usbIpSessionActive && !imported) {
                        LimeLog.info("USB/IP import timed out; restoring controller input");
                        exporter.close();
                    }
                };

                {
                    usbIpHandler.postDelayed(timeout, USBIP_IMPORT_TIMEOUT_MS);
                }
                @Override
                public void onImportStarted() {
                    usbIpHandler.post(() -> {
                        if (usbIpExport == exporter && usbIpSessionActive && exporter.isOpen()) {
                            usbIpStatusRes = R.string.usbip_initializing;
                        }
                    });
                    // Idle keyboards and mice may have no interrupt IN data until the user interacts.
                    if (!usbIpGamepad) {
                        onImported();
                    }
                }

                @Override
                public void onImported() {
                    usbIpHandler.post(() -> {
                        if (usbIpExport == exporter && usbIpSessionActive && exporter.isOpen() && !imported) {
                            imported = true;
                            usbIpHandler.removeCallbacks(timeout);
                            if (usbIpGamepad && stateListener != null) {
                                stateListener.onUsbIpHandoffConfirmed();
                            }
                            usbIpStatusRes = R.string.usbip_imported;
                        }
                    });
                }

                @Override
                public void onStopped(UsbIpControllerExport stopped, IOException error) {
                    usbIpHandler.post(() -> {
                        usbIpHandler.removeCallbacks(timeout);
                        if (usbIpExport != stopped) {
                            return;
                        }
                        usbIpExport = null;
                        usbIpDeviceName = null;
                        if (error != null) {
                            LimeLog.warning("USB/IP export ended: " + error);
                        }
                        finishUsbIpHandoff(device);
                    });
                }
            });
            LimeLog.info("USB/IP waiting on TCP " + UsbIpControllerExport.PORT + " for " + usbIpPeer.getHostAddress());
            usbIpStatusRes = R.string.usbip_waiting;
        } catch (IOException | RuntimeException e) {
            connection.close();
            LimeLog.warning("USB/IP export failed: " + e);
            finishUsbIpHandoff(device);
        }
    }

    private void finishUsbIpHandoff(UsbDevice device) {
        if (usbIpGamepad && stateListener != null) {
            stateListener.onUsbIpHandoffFinished();
        }
        usbIpGamepad = false;
        if (!started || !usbIpSessionActive || !usbManager.getDeviceList().containsKey(device.getDeviceName())) {
            usbIpStatusRes = usbIpSessionActive ? R.string.usbip_waiting_device : R.string.usbip_waiting_session;
            return;
        }
        usbIpFallbackDeviceName = device.getDeviceName();
        usbIpStatusRes = R.string.usbip_fallback;
        // Let Android's system input driver rebind before deciding whether to claim in-app.
        usbIpHandler.postDelayed(() -> {
            if (started && usbIpSessionActive && device.getDeviceName().equals(usbIpFallbackDeviceName)) {
                handleUsbDeviceState(device);
            }
        }, 500);
    }

    private void stopUsbIpExport() {
        if (usbIpExport != null) {
            usbIpExport.close();
        }
    }

    public static boolean isRecognizedInputDevice(UsbDevice device) {
        // Determine if this VID and PID combo matches an existing input device
        // and defer to the built-in controller support in that case.
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice inputDev = InputDevice.getDevice(id);
            if (inputDev == null) {
                // Device was removed while looping
                continue;
            }

            if (inputDev.getVendorId() == device.getVendorId() &&
                    inputDev.getProductId() == device.getProductId()) {
                return true;
            }
        }

        return false;
    }

    public static boolean kernelSupportsXboxOne() {
        String kernelVersion = System.getProperty("os.version");
        LimeLog.info("Kernel Version: "+kernelVersion);

        if (kernelVersion == null) {
            // We'll assume this is some newer version of Android
            // that doesn't let you read the kernel version this way.
            return true;
        }
        else if (kernelVersion.startsWith("2.") || kernelVersion.startsWith("3.")) {
            // These are old kernels that definitely don't support Xbox One controllers properly
            return false;
        }
        else if (kernelVersion.startsWith("4.4.") || kernelVersion.startsWith("4.9.")) {
            // These aren't guaranteed to have backported kernel patches for proper Xbox One
            // support (though some devices will).
            return false;
        }
        else {
            // The next AOSP common kernel is 4.14 which has working Xbox One controller support
            return true;
        }
    }

    public static boolean kernelSupportsXbox360W() {
        // Check if this kernel is 4.2+ to see if the xpad driver sets Xbox 360 wireless LEDs
        // https://github.com/torvalds/linux/commit/75b7f05d2798ee3a1cc5bbdd54acd0e318a80396
        String kernelVersion = System.getProperty("os.version");
        if (kernelVersion != null) {
            if (kernelVersion.startsWith("2.") || kernelVersion.startsWith("3.") ||
                    kernelVersion.startsWith("4.0.") || kernelVersion.startsWith("4.1.")) {
                // Even if LED devices are present, the driver won't set the initial LED state.
                return false;
            }
        }

        // We know we have a kernel that should set Xbox 360 wireless LEDs, but we still don't
        // know if CONFIG_JOYSTICK_XPAD_LEDS was enabled during the kernel build. Unfortunately
        // it's not possible to detect this reliably due to Android's app sandboxing. Reading
        // /proc/config.gz and enumerating /sys/class/leds are both blocked by SELinux on any
        // relatively modern device. We will assume that CONFIG_JOYSTICK_XPAD_LEDS=y on these
        // kernels and users can override by using the settings option to claim all devices.
        return true;
    }

    public static boolean shouldClaimDevice(UsbDevice device, boolean claimAllAvailable) {
        LimeLog.info("UsbDevice info: "+device.toString());
        return ((!kernelSupportsXboxOne() || !isRecognizedInputDevice(device) || claimAllAvailable) && XboxOneController.canClaimDevice(device)) ||
                ((!isRecognizedInputDevice(device) || claimAllAvailable) && Xbox360Controller.canClaimDevice(device)) ||
                // We must not call isRecognizedInputDevice() because wireless controllers don't share the same product ID as the dongle
                ((!kernelSupportsXbox360W() || claimAllAvailable) && Xbox360WirelessDongle.canClaimDevice(device)) ||
                ((!isRecognizedInputDevice(device) || claimAllAvailable) && ProCon2Controller.canClaimDevice(device)) ||
                ((!isRecognizedInputDevice(device) || claimAllAvailable) && ProConController.canClaimDevice(device)) ||
                ((!isRecognizedInputDevice(device) || claimAllAvailable) && DualSenseController.canClaimDevice(device))||
                ((!isRecognizedInputDevice(device) || claimAllAvailable) && Dualshock4Controller.canClaimDevice(device));
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void start() {
        if (started || usbManager == null) {
            return;
        }

        started = true;

        // Register for USB attach broadcasts and permission completions
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        }
        else {
            registerReceiver(receiver, filter);
        }

        // Enumerate existing devices
        for (UsbDevice dev : usbManager.getDeviceList().values()) {
            handleUsbDeviceState(dev);
        }
    }

    private void stop() {
        if (!started) {
            return;
        }

        started = false;
        usbIpSessionActive = false;
        stopUsbIpExport();

        // Stop the attachment receiver
        unregisterReceiver(receiver);

        // Stop all controllers
        while (controllers.size() > 0) {
            // Stop and remove the controller
            controllers.remove(0).stop();
        }
    }

    @Override
    public void onCreate() {
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        this.prefConfig = PreferenceConfiguration.readPreferences(this);
    }

    @Override
    public void onDestroy() {
        stop();

        // Remove listeners
        listener = null;
        stateListener = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public interface UsbDriverStateListener {
        void onUsbPermissionPromptStarting();
        void onUsbPermissionPromptCompleted();
        default void onUSBInfo(UsbDevice device){}
        default void onUsbIpHandoffStarted(UsbDevice device) {}
        default void onUsbIpHandoffConfirmed() {}
        default void onUsbIpHandoffFinished() {}
    }
}
