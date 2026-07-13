package com.limelight.binding.input.haptics;

import android.app.Activity;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.haptics.NoOpPcmHapticsBackend;
import com.limelight.haptics.PcmHapticsBackend;

import java.lang.reflect.Constructor;

public final class OptionalPcmHapticsLoader {
    private static final String PROVIDER_CLASS = "com.axi.kishihaptics.KishiHapticsProvider";

    private OptionalPcmHapticsLoader() {
    }

    public static PcmHapticsBackend load(Activity activity, PcmHapticsBackend.Callback callback) {
        if (!BuildConfig.HAS_KISHI_HAPTICS) {
            return NoOpPcmHapticsBackend.INSTANCE;
        }

        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS);
            if (!PcmHapticsBackend.class.isAssignableFrom(providerClass)) {
                LimeLog.warning("Optional PCM haptics provider has an incompatible API");
                return NoOpPcmHapticsBackend.INSTANCE;
            }

            Constructor<?> constructor = providerClass.getConstructor(
                    Activity.class, PcmHapticsBackend.Callback.class);
            return (PcmHapticsBackend) constructor.newInstance(activity, callback);
        }
        catch (ReflectiveOperationException | LinkageError error) {
            LimeLog.warning("Optional PCM haptics provider unavailable: " + error.getMessage());
            return NoOpPcmHapticsBackend.INSTANCE;
        }
    }
}
