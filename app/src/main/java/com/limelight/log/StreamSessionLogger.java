package com.limelight.log;

import android.content.Context;
import android.os.Build;

import com.limelight.BuildConfig;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StreamSessionLogger {
    private static final Set<String> ACTIVE_FILES = ConcurrentHashMap.newKeySet();

    private final Context appContext;
    private final File file;
    private final BufferedWriter writer;
    private final Thread.UncaughtExceptionHandler previousExceptionHandler;
    private final Thread.UncaughtExceptionHandler exceptionHandler;
    private boolean closed;

    public static StreamSessionLogger create(Context context, PreferenceConfiguration config,
                                             String pcName, String appName) {
        if (config == null || !config.enableStreamSessionLogging) {
            return null;
        }
        try {
            return new StreamSessionLogger(context, config, pcName, appName);
        }
        catch (IOException ignored) {
            return null;
        }
    }

    private StreamSessionLogger(Context context, PreferenceConfiguration config,
                                String pcName, String appName) throws IOException {
        appContext = context.getApplicationContext();
        file = StreamLogStore.createSessionFile(appContext, pcName, appName);
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
        ACTIVE_FILES.add(file.getAbsolutePath());
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        exceptionHandler = (thread, throwable) -> {
            error("CRASH", "Java 未捕获异常，线程=" + thread.getName(), throwable);
            close("Java 异常退出");
            if (previousExceptionHandler != null) {
                previousExceptionHandler.uncaughtException(thread, throwable);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);

        writeRaw("Moonlight 串流会话日志");
        writeRaw("应用版本: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        writeRaw("设备: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + "), ABI " + firstAbi());
        writeRaw("目标: " + safe(pcName) + " / " + safe(appName));
        writeRaw(String.format(Locale.US,
                "配置: %dx%d@%d, 码率=%d Kbps, HDR=%s, USB驱动=%s, 音频震动=%s",
                config.width, config.height, config.fps, config.bitrate,
                onOff(config.enableHdr), onOff(config.usbDriver),
                onOff(config.enableAudioHaptics)));
        writeRaw("触觉: 音频输出=" + safe(config.audioHapticsOutputTarget)
                + ", 强度=" + config.audioHapticsStrength + "%"
                + ", 保留普通震动=" + onOff(config.audioHapticsKeepControllerRumble));
        writeRaw("------------------------------------------------------------");
        info("SESSION", "会话日志开始");
    }

    public synchronized void info(String category, String message) {
        write("INFO", category, message, null);
    }

    public synchronized void warn(String category, String message) {
        write("WARN", category, message, null);
    }

    public synchronized void error(String category, String message, Throwable throwable) {
        write("ERROR", category, message, throwable);
    }

    public synchronized void close(String reason) {
        if (closed) {
            return;
        }
        write("END", "SESSION", reason, null);
        closed = true;
        try {
            writer.close();
        }
        catch (IOException ignored) {
        }
        ACTIVE_FILES.remove(file.getAbsolutePath());
        StreamLogStore.clearActive(appContext, file);
        if (Thread.getDefaultUncaughtExceptionHandler() == exceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler);
        }
    }

    public File getFile() {
        return file;
    }

    static boolean isFileActive(String path) {
        return path != null && ACTIVE_FILES.contains(path);
    }

    private void write(String level, String category, String message, Throwable throwable) {
        if (closed) {
            return;
        }
        String prefix = timestamp() + " [" + level + "] [" + safe(category) + "] ";
        try {
            writer.write(prefix + safe(message));
            writer.newLine();
            if (throwable != null) {
                writer.write(prefix + throwable.getClass().getName() + ": " + safe(throwable.getMessage()));
                writer.newLine();
                StackTraceElement[] stack = throwable.getStackTrace();
                for (int i = 0; i < Math.min(stack.length, 16); i++) {
                    writer.write(prefix + "  at " + stack[i]);
                    writer.newLine();
                }
            }
            writer.flush();
        }
        catch (IOException ignored) {
        }
    }

    private void writeRaw(String value) {
        try {
            writer.write(value);
            writer.newLine();
            writer.flush();
        }
        catch (IOException ignored) {
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String onOff(boolean enabled) {
        return enabled ? "开" : "关";
    }

    private static String firstAbi() {
        return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
    }
}
