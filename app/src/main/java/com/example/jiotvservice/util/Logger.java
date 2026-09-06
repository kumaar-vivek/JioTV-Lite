package com.example.jiotvservice.util;

import android.content.Context;
import android.util.Log;

import com.example.jiotvservice.BuildConfig;
import com.example.jiotvservice.JioTvApplication;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight application logger.
 *
 * Design goals:
 *  - Debug/info/warn/verbose messages are visible only in DEBUG builds.
 *  - Only ERROR messages are persisted to the on-device log file.
 *  - File writes are asynchronous so logging never blocks the Android main/UI thread.
 *  - Expensive caller stack inspection is performed only for errors.
 */
public final class Logger {
    private static final String FOLDER_NAME = "JioTvLogs";
    private static final String TAG_INTERNAL = "JioTvLogger";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB per file
    private static final int MAX_FILES = 3;

    private static final SimpleDateFormat FILE_NAME_FORMAT =
            new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
    private static final SimpleDateFormat LOG_ENTRY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    // A single background writer preserves log ordering without blocking UI/network callbacks.
    private static final ExecutorService FILE_WRITER =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "JioTv-ErrorLogWriter");
                t.setDaemon(true);
                return t;
            });

    private static File currentLogFile;
    private static final Object FILE_LOCK = new Object();

    private Logger() {}

    /**
     * Debug logging is enabled only for the DEBUG application variant.
     * A release APK installed directly on a TV therefore emits no application debug/info/warn logs.
     */
    public static boolean isDebugLoggingEnabled() {
        return BuildConfig.DEBUG;
    }

    private static String getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : stackTrace) {
            String className = ste.getClassName();
            if (!className.equals(Logger.class.getName())
                    && !className.equals(Thread.class.getName())
                    && !className.contains("dalvik.system.VMStack")) {
                String simpleClass = className.contains(".")
                        ? className.substring(className.lastIndexOf('.') + 1)
                        : className;
                return String.format(Locale.US, "%s:%s::%s(%d) > ",
                        ste.getFileName(), simpleClass, ste.getMethodName(), ste.getLineNumber());
            }
        }
        return "";
    }

    public static void v(String tag, String msg) {
        if (!BuildConfig.DEBUG) return;
        Log.v(tag, msg);
    }

    public static void d(String tag, String msg) {
        if (!BuildConfig.DEBUG) return;
        Log.d(tag, msg);
    }

    public static void i(String tag, String msg) {
        if (!BuildConfig.DEBUG) return;
        Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        if (!BuildConfig.DEBUG) return;
        Log.w(tag, msg);
    }

    public static void p(String tag, String msg)
    {
        // Caller lookup is intentionally limited to errors; normal logging must remain cheap.
        String info = getCallerInfo();
        String fullMsg = info + msg;

        if (BuildConfig.DEBUG) {
                Log.d(tag, fullMsg);

        }

        // Only errors/exceptions enter the persistent log. Never write D/I/V/W here.
        enqueueFileWrite(tag, fullMsg, null);
    }

    /**
     * Errors are always persisted, including in a release APK, but Logcat output is restricted
     * to DEBUG builds. This gives a field-installable APK an error file without flooding Logcat.
     */
    public static void e(String tag, String msg) {
        e(tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        // Caller lookup is intentionally limited to errors; normal logging must remain cheap.
        String info = getCallerInfo();
        String fullMsg = info + msg;

        if (BuildConfig.DEBUG) {
            if (tr != null) {
                Log.e(tag, fullMsg, tr);
            } else {
                Log.e(tag, fullMsg);
            }
        }

        // Only errors/exceptions enter the persistent log. Never write D/I/V/W here.
        enqueueFileWrite(tag, fullMsg, tr);
    }

    private static void enqueueFileWrite(String tag, String msg, Throwable tr) {
        final String safeTag = tag == null ? "APP" : tag;
        final String safeMsg = msg == null ? "" : msg;
        final Throwable error = tr;

        FILE_WRITER.execute(() -> writeToFile(safeTag, safeMsg, error));
    }

    private static void writeToFile(String tag, String msg, Throwable tr) {
        Context context = JioTvApplication.getContext();
        if (context == null) return;

        synchronized (FILE_LOCK) {
            File folder = getLogFolder(context);
            if (folder == null) return;

            if (currentLogFile == null || currentLogFile.length() >= MAX_FILE_SIZE) {
                rotateAndCreateNewFile(folder);
            }
            if (currentLogFile == null) return;

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentLogFile, true))) {
                String timestamp = LOG_ENTRY_FORMAT.format(new Date());
                writer.write(String.format(Locale.US, "%s E/%s: %s%n", timestamp, tag, msg));
                if (tr != null) {
                    writer.write(Log.getStackTraceString(tr));
                    writer.write("\n");
                }
                writer.flush();
            } catch (IOException ignored) {
                // Do not recursively log Logger errors. Logging must never destabilize the app.
            }
        }
    }

    private static File getLogFolder(Context context) {
        File[] mediaDirs = context.getExternalMediaDirs();
        if (mediaDirs != null && mediaDirs.length > 0 && mediaDirs[0] != null) {
            File folder = new File(mediaDirs[0], "files/" + FOLDER_NAME);
            if (folder.exists() || folder.mkdirs()) return folder;
        }

        File root = context.getExternalFilesDir(null);
        if (root != null) {
            File folder = new File(root, FOLDER_NAME);
            if (folder.exists() || folder.mkdirs()) return folder;
        }

        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        if (folder.exists() || folder.mkdirs()) return folder;
        return null;
    }

    private static void rotateAndCreateNewFile(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".log"));
        if (files != null && files.length >= MAX_FILES) {
            Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            int toDelete = files.length - MAX_FILES + 1;
            for (int i = 0; i < toDelete; i++) {
                // Best effort. Never write another Logger entry from here.
                files[i].delete();
            }
        }

        String fileName = FILE_NAME_FORMAT.format(new Date()) + ".log";
        currentLogFile = new File(folder, fileName);
        try {
            if (!currentLogFile.exists()) currentLogFile.createNewFile();
        } catch (IOException ignored) {
            currentLogFile = null;
        }
    }
}
