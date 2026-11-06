package com.limelight.binding.input.driver;

/** Owns usbfs requests for the USB/IP session, including audio and HID. */
final class UsbIpNative {
    static {
        System.loadLibrary("moonlight-core");
    }

    private UsbIpNative() {
    }

    /** Reads the current USB speed using the Linux values carried on the USB/IP wire. */
    static native int getSpeed(int fd);

    /** Returns a request handle, or a negative Linux errno. */
    static native long submit(int fd, int type, int endpoint, byte[] data, int[] packetLengths);

    /** Waits for a completion, returning zero on timeout or a negative errno on failure. */
    static native long reap(int fd);

    /** Reads URB status, actual length, start frame and pairs of packet length and status. */
    static native void readResult(long handle, int[] result, byte[] data);

    static native boolean cancel(int fd, long handle);

    /** Releases a completed request, or a pending request after closing the USB fd. */
    static native void free(long handle);
}
