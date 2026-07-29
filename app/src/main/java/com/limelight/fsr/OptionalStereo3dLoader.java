package com.limelight.fsr;

import android.content.Context;
import android.os.Build;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.stereo3d.NoOpStereo3dBackend;
import com.limelight.stereo3d.Stereo3dBackend;

import java.lang.reflect.Constructor;

final class OptionalStereo3dLoader {
    private static final String PROVIDER_CLASS = "com.axi.stereo3d.Stereo3dProvider";

    private OptionalStereo3dLoader() {
    }

    static Stereo3dBackend load(Context context) {
        if (!BuildConfig.HAS_STEREO3D_AI) {
            LimeLog.info("Optional AI stereo module not included; using built-in 3D renderer");
            return NoOpStereo3dBackend.INSTANCE;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            LimeLog.info("Optional AI stereo requires Android 7.0; using built-in 3D renderer");
            return NoOpStereo3dBackend.INSTANCE;
        }

        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS);
            if (!Stereo3dBackend.class.isAssignableFrom(providerClass)) {
                LimeLog.warning("Optional AI stereo provider has an incompatible API");
                return NoOpStereo3dBackend.INSTANCE;
            }

            Constructor<?> constructor = providerClass.getConstructor(Context.class);
            return (Stereo3dBackend) constructor.newInstance(context.getApplicationContext());
        }
        catch (ReflectiveOperationException | LinkageError error) {
            LimeLog.warning("Optional AI stereo provider unavailable: " + error.getMessage());
            return NoOpStereo3dBackend.INSTANCE;
        }
    }
}
