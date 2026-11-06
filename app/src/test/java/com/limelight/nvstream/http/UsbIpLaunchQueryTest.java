package com.limelight.nvstream.http;

import org.junit.Test;
import static org.junit.Assert.*;

/** Verify opt-in wire compatibility without loading the Android-only streaming native library. */
public class UsbIpLaunchQueryTest {
    @Test public void disabledModeDoesNotAdvertiseAnImporterRequest() {
        assertEquals("", NvHTTP.usbIpLaunchQuery(false));
    }
    @Test public void enabledModeAdvertisesOnlyTheFixedOptInFlag() {
        assertEquals("&usbipMode=1&usbipTls=1", NvHTTP.usbIpLaunchQuery(true));
    }
}
