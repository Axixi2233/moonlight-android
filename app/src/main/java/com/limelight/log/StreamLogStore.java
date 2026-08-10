package com.limelight.log;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class StreamLogStore {
    private static final String STATE_PREFS = "stream_session_log_state";
    private static final String ACTIVE_FILE_KEY = "active_file";
    private static final String ACTIVE_PID_KEY = "active_pid";
    private static final String ACTIVE_STARTED_AT_KEY = "active_started_at";
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
            clearActiveState(prefs);
        }
    }

    public static synchronized void recoverInterruptedSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        String activePath = prefs.getString(ACTIVE_FILE_KEY, null);
        int activePid = prefs.getInt(ACTIVE_PID_KEY, 0);
        long activeStartedAt = prefs.getLong(ACTIVE_STARTED_AT_KEY, 0L);
        if (activePath == null) {
            return;
        }
        if (StreamSessionLogger.isFileActive(activePath)) {
            return;
        }

        File file = new File(activePath);
        if (isManagedFile(context, file) && file.isFile()) {
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            File traceFile = traceFileFor(file);
            ExitDetails exitInfo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? Api30ExitInfo.find(context, activePid, activeStartedAt, traceFile) : null;
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                writer.write(now + " [WARN] 会话未正常结束：应用进程可能闪退、被系统终止或被强制停止");
                writer.newLine();
                if (exitInfo != null) {
                    writeExitInfo(writer, now, exitInfo);
                }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activePid > 0) {
                    writer.write(now + " [WARN] [EXIT] reason=UNAVAILABLE, previousPid=" + activePid
                            + ", sessionStartedAt=" + activeStartedAt);
                    writer.newLine();
                }
                writer.write(now + " [END] 异常中断（下次启动时补记）");
                writer.newLine();
            }
            catch (IOException ignored) {
            }
        }
        clearActiveState(prefs);
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
        if (!isManagedFile(context, file)
                || StreamSessionLogger.isFileActive(file.getAbsolutePath())
                || !file.delete()) {
            return false;
        }
        deleteTraceFiles(file);
        return true;
    }

    public static boolean hasExitTrace(Context context, File file) {
        if (!isManagedFile(context, file) || !file.isFile()) {
            return false;
        }
        File traceFile = traceFileFor(file);
        return traceFile.isFile() && traceFile.length() > 0L;
    }

    public static String getSuggestedExportName(Context context, File file) {
        if (hasExitTrace(context, file)) {
            String name = file.getName();
            int extension = name.lastIndexOf('.');
            String baseName = extension > 0 ? name.substring(0, extension) : name;
            return baseName + "-diagnostics.zip";
        }
        return file.getName();
    }

    public static boolean export(Context context, File file, Uri destination) {
        if (!isManagedFile(context, file) || !file.isFile()) {
            return false;
        }
        File traceFile = traceFileFor(file);
        boolean includeTrace = traceFile.isFile() && traceFile.length() > 0L;
        try (OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) {
                return false;
            }
            if (includeTrace) {
                try (ZipOutputStream zip = new ZipOutputStream(output)) {
                    addZipEntry(zip, file, file.getName());
                    addZipEntry(zip, traceFile, traceFile.getName());
                }
            }
            else {
                try (FileInputStream input = new FileInputStream(file)) {
                    copy(input, output);
                }
                output.flush();
            }
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
                .edit()
                .putString(ACTIVE_FILE_KEY, file.getAbsolutePath())
                .putInt(ACTIVE_PID_KEY, Process.myPid())
                .putLong(ACTIVE_STARTED_AT_KEY, System.currentTimeMillis())
                .commit();
    }

    private static void clearActiveState(SharedPreferences prefs) {
        prefs.edit()
                .remove(ACTIVE_FILE_KEY)
                .remove(ACTIVE_PID_KEY)
                .remove(ACTIVE_STARTED_AT_KEY)
                .commit();
    }

    private static void writeExitInfo(
            BufferedWriter writer, String now, ExitDetails exitInfo) throws IOException {
        writer.write(now + " [WARN] [EXIT] reason=" + exitInfo.reason
                + ", status=" + exitInfo.status
                + ", pid=" + exitInfo.pid
                + ", process=" + safeLogValue(exitInfo.processName));
        writer.newLine();
        writer.write(now + " [WARN] [EXIT] timestamp=" + exitInfo.timestamp
                + ", pssKb=" + exitInfo.pssKb
                + ", rssKb=" + exitInfo.rssKb
                + ", importance=" + exitInfo.importance
                + ", description=" + safeLogValue(exitInfo.description)
                + ", traceAvailable=" + exitInfo.traceAvailable
                + ", traceSaved=" + (exitInfo.traceBytes > 0L)
                + ", traceBytes=" + exitInfo.traceBytes
                + ", traceFile=" + (exitInfo.traceBytes > 0L ? exitInfo.traceFileName : "--"));
        writer.newLine();
    }

    private static String safeLogValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "--";
        }
        String safe = value.replace('\n', ' ').replace('\r', ' ').trim();
        return safe.substring(0, Math.min(512, safe.length()));
    }

    private static final class ExitDetails {
        final String reason;
        final String status;
        final int pid;
        final String processName;
        final long timestamp;
        final long pssKb;
        final long rssKb;
        final int importance;
        final String description;
        final boolean traceAvailable;
        final long traceBytes;
        final String traceFileName;

        ExitDetails(String reason, String status, int pid, String processName, long timestamp,
                    long pssKb, long rssKb, int importance, String description,
                    boolean traceAvailable, long traceBytes, String traceFileName) {
            this.reason = reason;
            this.status = status;
            this.pid = pid;
            this.processName = processName;
            this.timestamp = timestamp;
            this.pssKb = pssKb;
            this.rssKb = rssKb;
            this.importance = importance;
            this.description = description;
            this.traceAvailable = traceAvailable;
            this.traceBytes = traceBytes;
            this.traceFileName = traceFileName;
        }
    }

    /** Keeps API 30 classes out of StreamLogStore's verified path on older Android releases. */
    @TargetApi(Build.VERSION_CODES.R)
    private static final class Api30ExitInfo {
        private Api30ExitInfo() {
        }

        static ExitDetails find(Context context, int activePid, long activeStartedAt, File traceFile) {
            if (activePid <= 0) {
                return null;
            }
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return null;
            }
            try {
                List<ApplicationExitInfo> exits = activityManager.getHistoricalProcessExitReasons(
                        context.getPackageName(), activePid, 8);
                ApplicationExitInfo newestMatch = null;
                for (ApplicationExitInfo exit : exits) {
                    if (exit.getPid() != activePid) {
                        continue;
                    }
                    // Reject an older lifetime if Android has already reused the PID.
                    if (activeStartedAt > 0L && exit.getTimestamp() + 5_000L < activeStartedAt) {
                        continue;
                    }
                    if (newestMatch == null || exit.getTimestamp() > newestMatch.getTimestamp()) {
                        newestMatch = exit;
                    }
                }
                return newestMatch == null ? null : toDetails(newestMatch, traceFile);
            }
            catch (RuntimeException ignored) {
                return null;
            }
        }

        private static ExitDetails toDetails(ApplicationExitInfo exitInfo, File traceFile) {
            int reason = exitInfo.getReason();
            TraceAttachment trace = saveTrace(exitInfo, traceFile);
            return new ExitDetails(
                    reasonName(reason),
                    statusName(reason, exitInfo.getStatus()),
                    exitInfo.getPid(),
                    exitInfo.getProcessName(),
                    exitInfo.getTimestamp(),
                    exitInfo.getPss(),
                    exitInfo.getRss(),
                    exitInfo.getImportance(),
                    exitInfo.getDescription(),
                    trace.available,
                    trace.savedBytes,
                    trace.savedBytes > 0L ? traceFile.getName() : null);
        }

        private static TraceAttachment saveTrace(ApplicationExitInfo exitInfo, File traceFile) {
            if (traceFile.isFile() && traceFile.length() > 0L) {
                return new TraceAttachment(true, traceFile.length());
            }
            File temporaryFile = traceTempFileFor(traceFile);
            if (temporaryFile.exists()) {
                temporaryFile.delete();
            }
            try (InputStream trace = exitInfo.getTraceInputStream()) {
                if (trace == null) {
                    return new TraceAttachment(false, 0L);
                }
                long savedBytes;
                try (FileOutputStream output = new FileOutputStream(temporaryFile, false)) {
                    savedBytes = copy(trace, output);
                    output.flush();
                }
                if (savedBytes <= 0L) {
                    temporaryFile.delete();
                    return new TraceAttachment(true, 0L);
                }
                if (traceFile.exists() && !traceFile.delete()) {
                    temporaryFile.delete();
                    return new TraceAttachment(true, 0L);
                }
                if (!temporaryFile.renameTo(traceFile)) {
                    temporaryFile.delete();
                    return new TraceAttachment(true, 0L);
                }
                return new TraceAttachment(true, savedBytes);
            }
            catch (IOException | RuntimeException ignored) {
                temporaryFile.delete();
                return new TraceAttachment(false, 0L);
            }
        }

        private static final class TraceAttachment {
            final boolean available;
            final long savedBytes;

            TraceAttachment(boolean available, long savedBytes) {
                this.available = available;
                this.savedBytes = savedBytes;
            }
        }

        private static String reasonName(int reason) {
            switch (reason) {
                case ApplicationExitInfo.REASON_EXIT_SELF:
                    return "EXIT_SELF";
                case ApplicationExitInfo.REASON_SIGNALED:
                    return "SIGNALED";
                case ApplicationExitInfo.REASON_LOW_MEMORY:
                    return "LOW_MEMORY";
                case ApplicationExitInfo.REASON_CRASH:
                    return "CRASH_JAVA";
                case ApplicationExitInfo.REASON_CRASH_NATIVE:
                    return "CRASH_NATIVE";
                case ApplicationExitInfo.REASON_ANR:
                    return "ANR";
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                    return "INITIALIZATION_FAILURE";
                case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
                    return "PERMISSION_CHANGE";
                case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                    return "EXCESSIVE_RESOURCE_USAGE";
                case ApplicationExitInfo.REASON_USER_REQUESTED:
                    return "USER_REQUESTED";
                case ApplicationExitInfo.REASON_USER_STOPPED:
                    return "USER_STOPPED";
                case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                    return "DEPENDENCY_DIED";
                case ApplicationExitInfo.REASON_OTHER:
                    return "OTHER";
                case ApplicationExitInfo.REASON_FREEZER:
                    return "FREEZER";
                case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE:
                    return "PACKAGE_STATE_CHANGE";
                case ApplicationExitInfo.REASON_PACKAGE_UPDATED:
                    return "PACKAGE_UPDATED";
                case ApplicationExitInfo.REASON_UNKNOWN:
                default:
                    return "UNKNOWN(" + reason + ")";
            }
        }

        private static String statusName(int reason, int status) {
            if (reason != ApplicationExitInfo.REASON_CRASH_NATIVE
                    && reason != ApplicationExitInfo.REASON_SIGNALED) {
                return Integer.toString(status);
            }
            switch (status) {
                case 6:
                    return "6(SIGABRT)";
                case 7:
                    return "7(SIGBUS)";
                case 8:
                    return "8(SIGFPE)";
                case 9:
                    return "9(SIGKILL)";
                case 11:
                    return "11(SIGSEGV)";
                default:
                    return Integer.toString(status);
            }
        }
    }

    private static void prune(Context context) {
        List<File> files = listWithoutRecovery(context);
        long totalBytes = 0;
        for (File file : files) {
            totalBytes += storedBytes(file);
        }
        for (int i = files.size() - 1; i >= 0 && (files.size() > MAX_FILES || totalBytes > MAX_BYTES); i--) {
            File file = files.get(i);
            if (StreamSessionLogger.isFileActive(file.getAbsolutePath())) {
                continue;
            }
            long length = storedBytes(file);
            if (file.delete()) {
                deleteTraceFiles(file);
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

    private static File traceFileFor(File logFile) {
        return new File(logFile.getParentFile(), logFile.getName() + ".exit-trace");
    }

    private static File traceTempFileFor(File traceFile) {
        return new File(traceFile.getParentFile(), traceFile.getName() + ".tmp");
    }

    private static long storedBytes(File logFile) {
        File traceFile = traceFileFor(logFile);
        return logFile.length() + (traceFile.isFile() ? traceFile.length() : 0L);
    }

    private static void deleteTraceFiles(File logFile) {
        File traceFile = traceFileFor(logFile);
        if (traceFile.exists()) {
            traceFile.delete();
        }
        File temporaryFile = traceTempFileFor(traceFile);
        if (temporaryFile.exists()) {
            temporaryFile.delete();
        }
    }

    private static void addZipEntry(ZipOutputStream zip, File file, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream input = new FileInputStream(file)) {
            copy(input, zip);
        }
        zip.closeEntry();
    }

    private static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = 0L;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
            totalBytes += count;
        }
        return totalBytes;
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        return sanitized.substring(0, Math.min(32, sanitized.length()));
    }
}
