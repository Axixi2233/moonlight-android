package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Session-owned USB/IP export of one USB input device and its composite interfaces. */
final class UsbIpControllerExport implements AutoCloseable {
    static final int PORT = 3240;
    private static final String BUS_ID = "1-1";
    private static final int VERSION = 0x0111;
    private static final int MAX_PENDING = 64;
    private static final int MAX_TRANSFER = 64 * 1024;
    private static final int FORWARD_CONTROL = Integer.MIN_VALUE;
    private final UsbDeviceConnection usb;
    private final UsbConfiguration configuration;
    private final byte[] descriptor;
    private final int speed;
    private final InetAddress peer;
    private final byte[] tlsToken;
    private final ServerSocket server;
    private final ArrayList<UsbInterface> interfaces = new ArrayList<>();
    private final Map<Integer, UsbInterface> activeInterfaces = new HashMap<>();
    private final Object socketLock = new Object();
    private Socket activeSocket;
    private InetAddress lastRejectedPeer;
    private volatile boolean closed;
    private boolean usbClosed;
    private final long createdAt = SystemClock.elapsedRealtime();

    interface Listener {
        void onImportStarted();
        void onImported();
        void onStopped(UsbIpControllerExport exporter, IOException error);
    }

    static boolean supports(UsbDevice device) {
        if (device.getConfigurationCount() != 1) {
            return false;
        }
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (supportsInterface(iface.getInterfaceClass(), iface.getInterfaceSubclass(), iface.getInterfaceProtocol())) {
                return true;
            }
        }
        return false;
    }

    /** HID input and the Xbox 360 / Xbox One vendor interface signatures supported by Windows. */
    static boolean supportsInterface(int deviceClass, int subclass, int protocol) {
        return deviceClass == UsbConstants.USB_CLASS_HID
                || isXboxInterface(deviceClass, subclass, protocol);
    }

    private static boolean isXboxInterface(int deviceClass, int subclass, int protocol) {
        return deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
                && ((subclass == 0x5d && (protocol == 1 || protocol == 0x81))
                || (subclass == 0x47 && protocol == 0xd0));
    }

    /** Identify controller collections so exporting a keyboard or mouse never retires a virtual pad. */
    static boolean isGamepad(UsbDevice device, UsbDeviceConnection connection) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getAlternateSetting() != 0) {
                continue;
            }
            if (isXboxInterface(iface.getInterfaceClass(), iface.getInterfaceSubclass(), iface.getInterfaceProtocol())) {
                return true;
            }
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_HID && iface.getInterfaceSubclass() != 1) {
                byte[] report = new byte[4096];
                int length = connection.controlTransfer(0x81, 6, 0x2200, iface.getId(), report, report.length, 300);
                if (length > 0 && isGamepadReport(java.util.Arrays.copyOf(report, length))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Find a Generic Desktop joystick/gamepad application collection in a bounded HID descriptor. */
    static boolean isGamepadReport(byte[] report) {
        int page = 0;
        int usage = 0;
        for (int offset = 0; offset < report.length;) {
            int prefix = report[offset++] & 255;
            if (prefix == 0xfe) {
                if (offset + 2 > report.length) {
                    return false;
                }
                int size = report[offset] & 255;
                offset += 2 + size;
                continue;
            }
            int size = prefix & 3;
            size = size == 3 ? 4 : size;
            if (size > report.length - offset) {
                return false;
            }
            int value = 0;
            for (int i = 0; i < size; i++) {
                value |= (report[offset + i] & 255) << (8 * i);
            }
            offset += size;
            int item = prefix & 0xfc;
            if (item == 0x04) {
                page = value;
            } else if (item == 0x08) {
                usage = size == 4 ? value : (page << 16) | value;
            } else if (item == 0xa0 && value == 1
                    && (usage == 0x10004 || usage == 0x10005 || usage == 0x10008)) {
                return true;
            }
            // Local usages apply only to the next main item.
            if ((prefix & 0x0c) == 0) {
                usage = 0;
            }
        }
        return false;
    }

    /** Parse the selected streaming host; the service additionally validates its local IPv6 route. */
    static InetAddress parsePeer(String host) {
        return UsbIpNetwork.parsePeer(host);
    }

    UsbIpControllerExport(UsbDevice device, UsbDeviceConnection usb, InetAddress peer, byte[] tlsToken,
                          com.limelight.nvstream.http.LimelightCryptoProvider crypto) throws IOException {
        this.usb = usb;
        this.peer = peer;
        this.tlsToken = tlsToken == null ? null : tlsToken.clone();
        configuration = device.getConfiguration(0);
        descriptor = usb.getRawDescriptors();
        if (descriptor == null || descriptor.length < 18 || descriptor[0] != 18 || descriptor[1] != 1) {
            throw new IOException("Missing USB device descriptor");
        }
        int negotiatedSpeed = UsbIpNative.getSpeed(usb.getFileDescriptor());
        // Older Android kernels may not implement USBDEVFS_GET_SPEED.
        speed = negotiatedSpeed > 0 ? negotiatedSpeed : 2;
        LimeLog.info("USB/IP device " + device.getVendorId() + ":" + device.getProductId()
                + " speed=" + speed + " (usbfs=" + negotiatedSpeed + ")");
        server = tlsToken == null ? new ServerSocket() : UsbIpTls.createServer(crypto);
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(UsbIpNetwork.wildcard(peer), PORT), 2);
            for (int i = 0; i < configuration.getInterfaceCount(); i++) {
                UsbInterface iface = configuration.getInterface(i);
                if (iface.getAlternateSetting() == 0) {
                    if (!usb.claimInterface(iface, true)) {
                        throw new IOException("Unable to claim USB interface " + iface.getId());
                    }
                    interfaces.add(iface);
                    activeInterfaces.put(iface.getId(), iface);
                }
            }
            if (interfaces.isEmpty()) {
                throw new IOException("No USB interfaces to export");
            }
        } catch (IOException | RuntimeException e) {
            close();
            for (UsbInterface iface : interfaces) {
                usb.releaseInterface(iface);
            }
            throw e;
        }
    }

    void start(Listener listener) {
        new Thread(() -> {
            IOException error = null;
            try {
                serve(listener);
            } catch (IOException | RuntimeException e) {
                if (!closed) {
                    error = new IOException("USB/IP session ended", e);
                }
            } finally {
                close();
                closeUsb();
                listener.onStopped(this, error);
            }
        }, "USB/IP export").start();
    }

    private void serve(Listener listener) throws IOException {
        while (!closed) {
            try (Socket socket = server.accept()) {
                if (!UsbIpNetwork.samePeer(peer, socket.getInetAddress())) {
                    if (!socket.getInetAddress().equals(lastRejectedPeer)) {
                        lastRejectedPeer = socket.getInetAddress();
                        LimeLog.warning("USB/IP rejected peer " + lastRejectedPeer.getHostAddress()
                                + "; expected " + peer.getHostAddress());
                    }
                    continue;
                }
                synchronized (socketLock) {
                    if (closed) {
                        return;
                    }
                    activeSocket = socket;
                }
                try {
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                    if (tlsToken != null) {
                        ((javax.net.ssl.SSLSocket) socket).startHandshake();
                        byte[] offered = new byte[64];
                        in.readFully(offered);
                        if (!java.security.MessageDigest.isEqual(tlsToken, offered)) {
                            LimeLog.warning("USB/IP TLS session authentication rejected");
                            continue;
                        }
                    }
                    if (in.readUnsignedShort() != VERSION) {
                        continue;
                    }
                    int command = in.readUnsignedShort();
                    if (in.readInt() != 0) {
                        continue;
                    }
                    if (command == 0x8005) {
                        writeOperation(out, 5, 0);
                        out.writeInt(1);
                        writeDevice(out);
                        for (UsbInterface iface : interfaces) {
                            out.writeByte(iface.getInterfaceClass());
                            out.writeByte(iface.getInterfaceSubclass());
                            out.writeByte(iface.getInterfaceProtocol());
                            out.writeByte(0);
                        }
                        out.flush();
                    } else if (command == 0x8003) {
                        byte[] busId = new byte[32];
                        in.readFully(busId);
                        boolean matches = java.util.Arrays.equals(busId, padded(BUS_ID, 32));
                        writeOperation(out, 3, matches ? 0 : 4);
                        if (matches) {
                            long importStartedAt = SystemClock.elapsedRealtime();
                            LimeLog.info("USB/IP Windows import started after "
                                    + (importStartedAt - createdAt) + " ms waiting");
                            writeDevice(out);
                            out.flush();
                            listener.onImportStarted();
                            socket.setSoTimeout(0);
                            new Import(socket, in, out, listener, importStartedAt).run();
                            return;
                        }
                        out.flush();
                    }
                } catch (java.net.SocketTimeoutException ignored) {
                    // A probe that never completes its handshake does not own the device.
                } finally {
                    synchronized (socketLock) {
                        activeSocket = null;
                    }
                }
            }
        }
    }

    private static void writeOperation(DataOutputStream out, int command, int status) throws IOException {
        out.writeShort(VERSION);
        out.writeShort(command);
        out.writeInt(status);
    }

    private void writeDevice(DataOutputStream out) throws IOException {
        out.write(padded("/sys/devices/moonlight/usb1/1-1", 256));
        out.write(padded(BUS_ID, 32));
        out.writeInt(1);
        out.writeInt(1);
        out.writeInt(speed);
        for (int offset : new int[]{8, 10, 12}) {
            out.writeShort(le16(descriptor, offset));
        }
        out.write(descriptor, 4, 3);
        out.writeByte(configuration.getId());
        out.writeByte(1);
        out.writeByte(interfaces.size());
    }

    private static byte[] padded(String text, int length) {
        return java.util.Arrays.copyOf(text.getBytes(StandardCharsets.US_ASCII), length);
    }

    private static int le16(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
    }

    /** Android may return a tagged pointer with its sign bit set. Only Linux errno values are errors. */
    static boolean isUsbfsError(long result) {
        return result >= -4095 && result < 0;
    }

    /** usbfs control URBs include the eight setup bytes before the transfer payload. */
    static byte[] buildControlBuffer(byte[] setup, byte[] data) {
        int length = le16(setup, 26);
        byte[] buffer = new byte[8 + length];
        System.arraycopy(setup, 20, buffer, 0, 8);
        System.arraycopy(data, 0, buffer, 8, length);
        return buffer;
    }

    /** Reject the reserved standard request seen during virtual Xbox bus initialization. */
    static boolean isReservedBusProbe(int type, int request, int value, int index, int length) {
        return type == 0 && request == 0x1a && value == 0 && index == 0xffff && length == 0;
    }

    /** Encode the 48-byte USB/IP submit reply header in network byte order. */
    static void writeSubmitHeader(DataOutputStream out, int seq, int status, int length, int packets) throws IOException {
        writeSubmitHeader(out, seq, status, length, packets, 0, 0);
    }

    static void writeSubmitHeader(DataOutputStream out, int seq, int status, int length,
                                  int packets, int startFrame, int errors) throws IOException {
        writeHeader(out, 3, seq);
        out.writeInt(status);
        out.writeInt(length);
        out.writeInt(startFrame);
        out.writeInt(packets > 0 ? packets : -1);
        out.writeInt(errors);
        out.writeLong(0);
    }

    private static void writeHeader(DataOutputStream out, int command, int seq) throws IOException {
        out.writeInt(command);
        out.writeInt(seq);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
    }

    @Override
    public void close() {
        synchronized (socketLock) {
            closed = true;
            closeSocket(server);
            closeSocket(activeSocket);
        }
    }

    boolean isOpen() {
        return !closed;
    }

    private void closeUsb() {
        if (!usbClosed) {
            usbClosed = true;
            try {
                for (UsbInterface iface : interfaces) {
                    usb.releaseInterface(iface);
                }
            } finally {
                usb.close();
            }
        }
    }

    private static void closeSocket(java.io.Closeable socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final class Import {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final Listener listener;
        private final long importStartedAt;
        private boolean hidInputDelivered;
        private final Object requestsLock = new Object();
        private final Object outputLock = new Object();
        private final Map<Integer, Transfer> requests = new HashMap<>();
        private final Map<Long, Transfer> handles = new HashMap<>();
        private final HashSet<Integer> isoSubmitted = new HashSet<>();
        private final HashSet<Integer> isoCompleted = new HashSet<>();
        private final HashSet<Integer> isoRejected = new HashSet<>();
        private final int fd = usb.getFileDescriptor();
        private volatile boolean running = true;

        Import(Socket socket, DataInputStream in, DataOutputStream out, Listener listener, long importStartedAt) {
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.listener = listener;
            this.importStartedAt = importStartedAt;
        }

        void run() throws IOException {
            Thread reaper = new Thread(this::reap, "USB/IP completions");
            reaper.start();
            try {
                while (running && !closed) {
                    int command = in.readInt();
                    int seq = in.readInt();
                    int device = in.readInt();
                    int direction = in.readInt();
                    int ep = in.readInt();
                    byte[] body = new byte[28];
                    in.readFully(body);
                    if (device != 0x00010001 || direction < 0 || direction > 1 || ep < 0 || ep > 15) {
                        throw new IOException("Invalid USB/IP request header");
                    }
                    ByteBuffer fields = ByteBuffer.wrap(body);
                    if (command == 1) {
                        submit(seq, direction, ep, body, fields.getInt(4), fields.getInt(12));
                    } else if (command == 2) {
                        unlink(seq, fields.getInt());
                    } else {
                        throw new IOException("Unknown USB/IP command " + command);
                    }
                }
            } catch (java.io.EOFException ignored) {
                // Windows detached the device.
            } finally {
                running = false;
                closeSocket(socket);
                synchronized (requestsLock) {
                    for (Transfer transfer : requests.values()) {
                        UsbIpNative.cancel(fd, transfer.handle);
                    }
                }
                boolean interrupted = false;
                while (reaper.isAlive()) {
                    try {
                        reaper.join();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                // usbfs may still own cancelled buffers until the fd is closed.
                closeUsb();
                for (Transfer transfer : requests.values()) {
                    UsbIpNative.free(transfer.handle);
                }
                requests.clear();
                handles.clear();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void submit(int seq, int direction, int ep, byte[] setup, int length, int packets) throws IOException {
            if (length < 0 || length > MAX_TRANSFER || packets < -1 || packets > 128) {
                throw new IOException("Invalid USB/IP transfer size: length=" + length + ", packets=" + packets);
            }
            byte[] data = new byte[length];
            if (direction == 0) {
                in.readFully(data);
            }
            IsoPackets iso = null;
            if (packets > 0) {
                byte[] descriptors = new byte[16 * packets];
                in.readFully(descriptors);
                iso = new IsoPackets(descriptors, length);
                data = iso.makeUsbBuffer(direction == 0 ? data : null);
            } else if (ep == 0) {
                int result = control(direction, setup, data);
                if (result != FORWARD_CONTROL) {
                    reply(seq, Math.min(result, 0), Math.max(result, 0), direction == 1 ? data : null, null, 0);
                    return;
                }
                data = buildControlBuffer(setup, data);
            }

            int address = ep | (direction == 1 ? 0x80 : 0);
            if (iso != null && isoSubmitted.add(address)) {
                LimeLog.info("USB/IP audio submit endpoint=" + address + ", packets=" + packets
                        + ", bytes=" + length + ", largest packet=" + iso.maxLength);
            }
            UsbEndpoint endpoint = iso == null ? findEndpoint(address) : findIsoEndpoint(address, iso.maxLength);
            if (ep != 0 && (endpoint == null || (iso == null
                    && endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_INT))) {
                failSubmit(seq, -32, address, iso);
                return;
            }
            int error = -11;
            synchronized (requestsLock) {
                if (requests.containsKey(seq)) {
                    throw new IOException("Duplicate USB/IP request sequence");
                }
                if (requests.size() < MAX_PENDING) {
                    long handle = UsbIpNative.submit(fd, ep == 0 ? 0 : endpoint.getType(), address, data,
                            iso == null ? null : iso.lengths);
                    if (!isUsbfsError(handle)) {
                        Transfer transfer = new Transfer(seq, endpoint, handle, direction == 1 ? data : null,
                                iso, ep == 0 ? setup : null);
                        requests.put(seq, transfer);
                        handles.put(handle, transfer);
                        return;
                    }
                    error = (int) handle;
                }
            }
            failSubmit(seq, error, address, iso);
        }

        private void reap() {
            try {
                while (running) {
                    long handle = UsbIpNative.reap(fd);
                    if (handle == 0) {
                        continue;
                    }
                    if (isUsbfsError(handle)) {
                        throw new IOException("usbfs completion failed: " + handle);
                    }
                    synchronized (outputLock) {
                        Transfer transfer;
                        synchronized (requestsLock) {
                            transfer = handles.remove(handle);
                            if (transfer == null) {
                                throw new IOException("Unknown usbfs completion");
                            }
                            requests.remove(transfer.seq);
                        }
                        try {
                            if (!transfer.cancelled && running) {
                                complete(transfer);
                            }
                        } finally {
                            UsbIpNative.free(handle);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                if (running && !closed) {
                    LimeLog.warning("USB/IP completion stopped: " + e);
                }
            } finally {
                running = false;
                closeSocket(socket);
            }
        }

        private void complete(Transfer transfer) throws IOException {
            IsoPackets iso = transfer.iso;
            int[] result = new int[3 + (iso == null ? 0 : 2 * iso.lengths.length)];
            UsbIpNative.readResult(transfer.handle, result, transfer.data);
            int length = iso == null ? result[1] : iso.complete(result, transfer.data);
            if (length < 0 || (transfer.data != null && length > transfer.data.length)) {
                throw new IOException("Invalid usbfs completion length");
            }
            if (transfer.setup != null) {
                long elapsed = SystemClock.elapsedRealtime() - transfer.submittedAt;
                if (result[0] != 0 || elapsed >= 100) {
                    LimeLog.info("USB/IP control completion: type=" + (transfer.setup[20] & 255)
                            + ", request=" + (transfer.setup[21] & 255)
                            + ", value=" + le16(transfer.setup, 22) + ", index=" + le16(transfer.setup, 24)
                            + ", status=" + result[0] + ", bytes=" + length + ", ms=" + elapsed);
                }
                if (transfer.data != null) {
                    if (length > transfer.data.length - 8) {
                        throw new IOException("Invalid usbfs control completion length");
                    }
                    System.arraycopy(transfer.data, 8, transfer.data, 0, length);
                }
            }
            if (iso != null && isoCompleted.add(transfer.endpoint.getAddress())) {
                LimeLog.info("USB/IP audio completion endpoint=" + transfer.endpoint.getAddress()
                        + ", status=" + result[0] + ", bytes=" + length + ", packet errors=" + iso.errors
                        + ", first packet status=" + iso.descriptors.getInt(12));
            }
            reply(transfer.seq, result[0], length, transfer.data, iso, iso == null ? 0 : result[2]);
            // Audio completions do not prove that Windows can read the controller's HID input.
            if (!hidInputDelivered && result[0] == 0 && length > 0 && transfer.data != null
                    && transfer.endpoint != null
                    && (transfer.endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT
                    || transfer.endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                hidInputDelivered = true;
                LimeLog.info("USB/IP Windows HID ready after "
                        + (SystemClock.elapsedRealtime() - importStartedAt) + " ms initialization");
                listener.onImported();
            }
        }

        private void unlink(int seq, int target) throws IOException {
            synchronized (outputLock) {
                int status = 0;
                synchronized (requestsLock) {
                    Transfer transfer = requests.get(target);
                    if (transfer != null && UsbIpNative.cancel(fd, transfer.handle)) {
                        transfer.cancelled = true;
                        status = -104;
                    }
                }
                writeHeader(out, 4, seq);
                out.writeInt(status);
                out.write(new byte[24]);
                out.flush();
            }
        }

        private void failSubmit(int seq, int status, int address, IsoPackets iso) throws IOException {
            if (iso != null) {
                iso.fail(status);
                if (isoRejected.add(address)) {
                    LimeLog.warning("USB/IP audio submit rejected: endpoint=" + address + ", status=" + status);
                }
            }
            reply(seq, status, 0, null, iso, 0);
        }

        private void reply(int seq, int status, int length, byte[] data, IsoPackets iso, int startFrame) throws IOException {
            synchronized (outputLock) {
                writeSubmitHeader(out, seq, status, length, iso == null ? 0 : iso.lengths.length,
                        startFrame, iso == null ? 0 : iso.errors);
                if (data != null) {
                    out.write(data, 0, length);
                }
                if (iso != null) {
                    out.write(iso.descriptors.array());
                }
                out.flush();
            }
        }

    }

    private int control(int direction, byte[] setup, byte[] data) {
        int type = setup[20] & 255;
        int request = setup[21] & 255;
        int value = le16(setup, 22);
        int index = le16(setup, 24);
        int length = le16(setup, 26);
        if ((type >>> 7) != direction || length > data.length) {
            return -22;
        }
        if (isReservedBusProbe(type, request, value, index, length)) {
            LimeLog.info("USB/IP rejected reserved standard bus probe 0x1a locally");
            return -32;
        }
        if (type == 0 && request == 9) {
            return length == 0 && value == configuration.getId() ? 0 : -32;
        }
        if (type == 0 && request == 5) {
            // The virtual bus address is unrelated to the physical Android bus address.
            return length == 0 ? 0 : -22;
        }
        if (type == 1 && request == 11) {
            if (length != 0) {
                return -22;
            }
            for (int i = 0; i < configuration.getInterfaceCount(); i++) {
                UsbInterface iface = configuration.getInterface(i);
                if (iface.getId() == index && iface.getAlternateSetting() == value && usb.setInterface(iface)) {
                    activeInterfaces.put(index, iface);
                    if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
                        LimeLog.info("USB/IP audio interface=" + index + ", alternate setting=" + value);
                    }
                    return 0;
                }
            }
            return -32;
        }
        // Xbox and third-party controllers require vendor requests during Windows initialization.
        if ((type & 0x60) == 0x60) {
            return -32;
        }
        // Dispatch via the same asynchronous usbfs queue as HID and audio so a slow control
        // request cannot block input submissions or Windows UNLINK cancellation.
        return FORWARD_CONTROL;
    }

    private UsbEndpoint findEndpoint(int address) {
        for (UsbInterface iface : activeInterfaces.values()) {
            UsbEndpoint endpoint = findEndpoint(iface, address);
            if (endpoint != null) {
                return endpoint;
            }
        }
        return null;
    }

    private static UsbEndpoint findEndpoint(UsbInterface iface, int address) {
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = iface.getEndpoint(i);
            if (endpoint.getAddress() == address) {
                return endpoint;
            }
        }
        return null;
    }

    /** Some Windows clients configure their virtual pipes without forwarding SET_INTERFACE. */
    private UsbEndpoint findIsoEndpoint(int address, int maxLength) {
        UsbEndpoint active = findEndpoint(address);
        if (active != null && active.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC
                && active.getMaxPacketSize() >= maxLength) {
            return active;
        }

        UsbInterface selected = null;
        UsbEndpoint endpoint = null;
        for (int i = 0; i < configuration.getInterfaceCount(); i++) {
            UsbInterface iface = configuration.getInterface(i);
            if (iface.getInterfaceClass() != UsbConstants.USB_CLASS_AUDIO) {
                continue;
            }
            UsbEndpoint candidate = findEndpoint(iface, address);
            if (candidate != null && candidate.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC
                    && candidate.getMaxPacketSize() >= maxLength
                    && (endpoint == null || candidate.getMaxPacketSize() < endpoint.getMaxPacketSize())) {
                selected = iface;
                endpoint = candidate;
            }
        }
        if (selected == null || !usb.setInterface(selected)) {
            return null;
        }
        activeInterfaces.put(selected.getId(), selected);
        LimeLog.info("USB/IP selected audio alternate setting " + selected.getAlternateSetting()
                + " for interface " + selected.getId() + ", endpoint " + address);
        return endpoint;
    }

    /** Translates wire packet offsets to consecutive usbfs slots, and compacts microphone replies. */
    static final class IsoPackets {
        final ByteBuffer descriptors;
        final int[] lengths;
        final int maxLength;
        private final int totalLength;
        int errors;

        IsoPackets(byte[] descriptors, int transferLength) throws IOException {
            this.descriptors = ByteBuffer.wrap(descriptors);
            lengths = new int[descriptors.length / 16];
            int total = 0;
            int largest = 0;
            int end = 0;
            for (int i = 0; i < lengths.length; i++) {
                int offset = this.descriptors.getInt(i * 16);
                int length = this.descriptors.getInt(i * 16 + 4);
                if (offset < end || offset > transferLength || length < 0 || length > transferLength - offset) {
                    throw new IOException("Invalid USB/IP isochronous packet range");
                }
                lengths[i] = length;
                end = offset + length;
                total += length;
                largest = Math.max(largest, length);
            }
            totalLength = total;
            maxLength = largest;
        }

        byte[] makeUsbBuffer(byte[] data) {
            if (data != null && data.length == totalLength) {
                return data;
            }
            byte[] buffer = new byte[totalLength];
            if (data != null) {
                int target = 0;
                for (int i = 0; i < lengths.length; i++) {
                    System.arraycopy(data, descriptors.getInt(i * 16), buffer, target, lengths[i]);
                    target += lengths[i];
                }
            }
            return buffer;
        }

        int complete(int[] result, byte[] data) throws IOException {
            int source = 0;
            int total = 0;
            for (int i = 0; i < lengths.length; i++) {
                int actual = result[3 + 2 * i];
                int status = result[4 + 2 * i];
                if (actual < 0 || actual > lengths[i]) {
                    throw new IOException("Invalid usbfs isochronous packet length");
                }
                if (data != null && actual != 0 && source != total) {
                    System.arraycopy(data, source, data, total, actual);
                }
                setResult(i, actual, status);
                source += lengths[i];
                total += actual;
            }
            return total;
        }

        void fail(int status) {
            for (int i = 0; i < lengths.length; i++) {
                setResult(i, 0, status);
            }
        }

        private void setResult(int packet, int actual, int status) {
            descriptors.putInt(packet * 16 + 8, actual);
            descriptors.putInt(packet * 16 + 12, status);
            if (status != 0) {
                errors++;
            }
        }
    }

    private static final class Transfer {
        final int seq;
        final UsbEndpoint endpoint;
        final long handle;
        final byte[] data;
        final IsoPackets iso;
        final byte[] setup;
        final long submittedAt = SystemClock.elapsedRealtime();
        boolean cancelled;

        Transfer(int seq, UsbEndpoint endpoint, long handle, byte[] data, IsoPackets iso, byte[] setup) {
            this.seq = seq;
            this.endpoint = endpoint;
            this.handle = handle;
            this.data = data;
            this.iso = iso;
            this.setup = setup;
        }
    }
}
