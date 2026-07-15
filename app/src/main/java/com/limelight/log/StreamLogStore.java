package com.limelight.log;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class StreamLogStore {
    private static final String STATE_PREFS = "stream_session_log_state";
    private static final String ACTIVE_FILE_KEY = "active_file";
    private static final int MAX_FILES = 50;
    private static final long MAX_BYTES = 40L * 1024L * 1024L;

    private StreamLogStore() {
    }

    public static File getDirectory(Context context) {
        return new File(context.getFilesDir(), "logs/streams");
    }

    static synchronized File createSessionFile(Context context, String pcName, String appName) throws IOException {
        recoverInterruptedSession(context);
        File directory = getDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create stream log directory");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String suffix = sanitize(pcName) + "-" + sanitize(appName);
        File file = new File(directory, "stream-" + timestamp + "-" + suffix + ".log");
        int duplicate = 1;
        while (file.exists()) {
            file = new File(directory, "stream-" + timestamp + "-" + suffix + "-" + duplicate++ + ".log");
        }
        if (!file.createNewFile()) {
            throw new IOException("Unable to create stream log file");
        }
        markActive(context, file);
        prune(context);
        return file;
    }

    static synchronized void clearActive(Context context, File file) {
        SharedPreferences prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        String activePath = prefs.getString(ACTIVE_FILE_KEY, null);
        if (file != null && file.getAbsolutePath().equals(activePath)) {
            prefs.edit().remove(ACTIVE_FILE_KEY).commit();
        }
    }

    public static synchronized void recoverInterruptedSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        String activePath = prefs.getString(ACTIVE_FILE_KEY, null);
        if (activePath == null) {
            return;
        }
        if (StreamSessionLogger.isFileActive(activePath)) {
            return;
        }

        File file = new File(activePath);
        if (isManagedFile(context, file) && file.isFile()) {
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                writer.write(now + " [WARN] 会话未正常结束：应用进程可能闪退、被系统终止或被强制停止");
                writer.newLine();
                writer.write(now + " [END] 异常中断（下次启动时补记）");
                writer.newLine();
            }
            catch (IOException ignored) {
            }
        }
        prefs.edit().remove(ACTIVE_FILE_KEY).commit();
    }

    public static synchronized List<File> list(Context context) {
        recoverInterruptedSession(context);
        File[] files = getDirectory(context).listFiles((dir, name) -> name.startsWith("stream-") && name.endsWith(".log"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        Arrays.sort(files, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        return new ArrayList<>(Arrays.asList(files));
    }

    public static synchronized boolean delete(Context context, File file) {
        return isManagedFile(context, file) && !StreamSessionLogger.isFileActive(file.getAbsolutePath()) && file.delete();
    }

    public static boolean export(Context context, File file, Uri destination) {
        if (!isManagedFile(context, file) || !file.isFile()) {
            return false;
        }
        try (FileInputStream input = new FileInputStream(file);
             OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            output.flush();
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    public static String readPreview(Context context, File file, int maxChars) {
        if (!isManagedFile(context, file) || !file.isFile()) {
            return "";
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, input.length() - (long) maxChars * 4L);
            input.seek(start);
            byte[] bytes = new byte[(int) (input.length() - start)];
            input.readFully(bytes);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (content.length() > maxChars) {
                content = content.substring(content.length() - maxChars);
            }
            return start > 0 ? "……（显示日志末尾）\n" + content : content;
        }
        catch (IOException ignored) {
            return "";
        }
    }

    private static void markActive(Context context, File file) {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(ACTIVE_FILE_KEY, file.getAbsolutePath()).commit();
    }

    private static void prune(Context context) {
        List<File> files = listWithoutRecovery(context);
        long totalBytes = 0;
        for (File file : files) {
            totalBytes += file.length();
        }
        for (int i = files.size() - 1; i >= 0 && (files.size() > MAX_FILES || totalBytes > MAX_BYTES); i--) {
            File file = files.get(i);
            if (StreamSessionLogger.isFileActive(file.getAbsolutePath())) {
                continue;
            }
            long length = file.length();
            if (file.delete()) {
                totalBytes -= length;
                files.remove(i);
            }
        }
    }

    private static List<File> listWithoutRecovery(Context context) {
        File[] files = getDirectory(context).listFiles((dir, name) -> name.startsWith("stream-") && name.endsWith(".log"));
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> result = new ArrayList<>(Arrays.asList(files));
        result.sort((left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        return result;
    }

    private static boolean isManagedFile(Context context, File file) {
        if (file == null) {
            return false;
        }
        try {
            String directoryPath = getDirectory(context).getCanonicalPath() + File.separator;
            return file.getCanonicalPath().startsWith(directoryPath);
        }
        catch (IOException e) {
            return false;
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        return sanitized.substring(0, Math.min(32, sanitized.length()));
    }
}
