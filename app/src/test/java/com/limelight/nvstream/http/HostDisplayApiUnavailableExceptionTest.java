package com.limelight.nvstream.http;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

/** Distinguish absent display APIs from pairing, permission and temporary server failures. */
public class HostDisplayApiUnavailableExceptionTest {
    @Test public void absentOrUnimplementedEndpointsSelectLegacyControls() {
        for (int status : new int[] {404, 405, 501}) {
            assertTrue(HostDisplayApiUnavailableException.forStatus(status)
                    instanceof HostDisplayApiUnavailableException);
        }
    }

    @Test public void authenticationAndPermissionFailuresRemainVisible() {
        for (int status : new int[] {401, 403}) {
            IOException error = HostDisplayApiUnavailableException.forStatus(status);
            assertFalse(error instanceof HostDisplayApiUnavailableException);
            assertTrue(error instanceof HostHttpResponseException);
            assertEquals(status, ((HostHttpResponseException) error).getErrorCode());
        }
    }

    @Test public void otherFailuresDoNotSilentlyDowngradeTheMenu() {
        for (int status : new int[] {400, 408, 429, 500, 502, 503, 504}) {
            assertFalse(HostDisplayApiUnavailableException.forStatus(status)
                    instanceof HostDisplayApiUnavailableException);
        }
    }

}
