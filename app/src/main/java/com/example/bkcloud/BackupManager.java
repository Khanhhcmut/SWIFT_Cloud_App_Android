package com.example.bkcloud;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;
import java.util.concurrent.TimeUnit;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import android.util.Log;
import android.widget.Toast;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import java.io.InputStream;
import java.io.IOException;
import okhttp3.MediaType;
import androidx.documentfile.provider.DocumentFile;

public class BackupManager {

    private static final String PREF = "backup_manager";
    private static final String KEY_ENABLED = "backup_enabled";
    private static final String KEY_MODE = "backup_mode";
    private static final String KEY_TIME = "backup_time";
    private static final String KEY_WEEKDAYS = "backup_weekdays";
    private static final String KEY_SPECIFIC_DATE = "backup_specific_date";
    private static final String KEY_FOLDERS = "backup_folders";
    private static final String BACKUP_CONTAINER = "Backup";


    public static class NotifyWorker extends Worker {
        public NotifyWorker(Context c, WorkerParameters p) {
            super(c, p);
        }
        @Override
        public Result doWork() {
            int type = getInputData().getInt("type", 0);
            BackupManager.showNotification(getApplicationContext(), type);
            return Result.success();
        }
    }

    public static class TriggerWorker extends Worker {
        public TriggerWorker(Context c, WorkerParameters p) {
            super(c, p);
        }
        @Override
        public Result doWork() {
            BackupManager.showNotification(getApplicationContext(), 0);
            return Result.success();
        }
    }

    private static String buildTimeFolder() {
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("dd.MM.yyyy.HH.mm", java.util.Locale.US);
        return f.format(new java.util.Date());
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_ENABLED, v).apply();
    }

    public static String getMode(Context c) {
        return prefs(c).getString(KEY_MODE, "");
    }

    public static void setMode(Context c, String mode) {
        prefs(c).edit().putString(KEY_MODE, mode).apply();
    }

    public static String getTime(Context c) {
        return prefs(c).getString(KEY_TIME, "");
    }

    public static void setTime(Context c, String time) {
        prefs(c).edit().putString(KEY_TIME, time).apply();
    }

    public static List<Integer> getWeekdays(Context c) {
        List<Integer> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_WEEKDAYS, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getInt(i));
        } catch (Exception ignored) {}
        return out;
    }

    public static void setWeekdays(Context c, List<Integer> days) {
        JSONArray arr = new JSONArray();
        for (int d : days) arr.put(d);
        prefs(c).edit().putString(KEY_WEEKDAYS, arr.toString()).apply();
    }

    public static String getSpecificDate(Context c) {
        return prefs(c).getString(KEY_SPECIFIC_DATE, "");
    }

    public static void setSpecificDate(Context c, String date) {
        prefs(c).edit().putString(KEY_SPECIFIC_DATE, date).apply();
    }

    public static List<String> getFolders(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_FOLDERS, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public static void setFolders(Context c, List<String> uris) {
        JSONArray arr = new JSONArray();
        java.util.HashSet<String> set = new java.util.HashSet<>(uris);
        for (String u : set) arr.put(u);
        prefs(c).edit().putString(KEY_FOLDERS, arr.toString()).apply();
    }

    public static String getStatusText(Context c) {
        if (!isEnabled(c)) return "Backup is not set up yet";

        String mode = getMode(c);
        String time = getTime(c);

        StringBuilder foldersText = new StringBuilder();
        for (String uriStr : getFolders(c)) {
            android.net.Uri uri = android.net.Uri.parse(uriStr);
            androidx.documentfile.provider.DocumentFile df =
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(c, uri);
            if (df != null && df.getName() != null) {
                foldersText.append("- ").append(df.getName()).append("\n");
            }
        }

        String base;
        if (mode.equals("DAILY"))
            base = "Daily backup at " + time;
        else if (mode.equals("WEEKLY"))
            base = "Weekly backup at " + time;
        else if (mode.equals("SPECIFIC_DAY"))
            base = "Backup on " + getSpecificDate(c) + " at " + time;
        else
            base = "Backup configured";

        if (foldersText.length() == 0)
            return base + "\nNo folders selected";

        return base + "\nFolders:\n" + foldersText.toString();
    }

    public static long getNextTriggerMillis(Context c) {
        if (!isEnabled(c)) return -1;

        String time = getTime(c);
        if (time.isEmpty()) return -1;

        String[] t = time.split(":");
        int hour = Integer.parseInt(t[0]);
        int minute = Integer.parseInt(t[1]);

        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);

        String mode = getMode(c);

        if (mode.equals("DAILY")) {
            if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1);
            return target.getTimeInMillis();
        }

        if (mode.equals("WEEKLY")) {
            List<Integer> days = getWeekdays(c);
            if (days.isEmpty()) return -1;

            for (int i = 0; i < 7; i++) {
                int dow = target.get(Calendar.DAY_OF_WEEK);
                if (days.contains(dow) && target.after(now))
                    return target.getTimeInMillis();
                target.add(Calendar.DAY_OF_MONTH, 1);
            }
            return -1;
        }

        if (mode.equals("SPECIFIC_DAY")) {
            String d = getSpecificDate(c);
            if (d.isEmpty()) return -1;

            String[] p = d.split("-");
            target.set(Calendar.YEAR, Integer.parseInt(p[0]));
            target.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
            target.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));

            if (!target.after(now)) return -1;
            return target.getTimeInMillis();
        }

        return -1;
    }

    public static long getNotify30Millis(Context c) {
        long t = getNextTriggerMillis(c);
        if (t <= 0) return -1;
        long n = t - 30 * 60 * 1000L;
        return n > System.currentTimeMillis() ? n : -1;
    }

    public static long getNotify10Millis(Context c) {
        long t = getNextTriggerMillis(c);
        if (t <= 0) return -1;
        long n = t - 10 * 60 * 1000L;
        return n > System.currentTimeMillis() ? n : -1;
    }

    public static void schedule(Context c) {

        WorkManager wm = WorkManager.getInstance(c);
        wm.cancelAllWorkByTag("backup");

        long t30 = getNotify30Millis(c);
        long t10 = getNotify10Millis(c);
        long t0  = getNextTriggerMillis(c);

        long now = System.currentTimeMillis();

        if (t30 > 0) {
            wm.enqueue(
                    new OneTimeWorkRequest.Builder(NotifyWorker.class)
                            .setInitialDelay(t30 - now, TimeUnit.MILLISECONDS)
                            .addTag("backup")
                            .setInputData(new Data.Builder().putInt("type", 30).build())
                            .build()
            );
        }

        if (t10 > 0) {
            wm.enqueue(
                    new OneTimeWorkRequest.Builder(NotifyWorker.class)
                            .setInitialDelay(t10 - now, TimeUnit.MILLISECONDS)
                            .addTag("backup")
                            .setInputData(new Data.Builder().putInt("type", 10).build())
                            .build()
            );
        }

        if (t0 > 0) {
            wm.enqueue(
                    new OneTimeWorkRequest.Builder(TriggerWorker.class)
                            .setInitialDelay(t0 - now, TimeUnit.MILLISECONDS)
                            .addTag("backup")
                            .build()
            );
        }
    }

    public static void showNotification(Context c, int type) {

        String channelId = "backup_channel";
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    channelId,
                    "Backup",
                    NotificationManager.IMPORTANCE_HIGH
            );
            nm.createNotificationChannel(ch);
        }

        String title;
        String text;

        if (type == 30) {
            title = "Backup scheduled";
            text = "Backup will start in 30 minutes";
        } else if (type == 10) {
            title = "Backup soon";
            text = "Backup will start in 10 minutes";
        } else {
            title = "Backup time";
            text = "Tap to start backup now";
        }

        Intent i = new Intent(c, HomeActivity.class);
        i.putExtra("backup_trigger", true);

        PendingIntent pi = PendingIntent.getActivity(
                c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)
        );

        NotificationCompat.Builder b =
                new NotificationCompat.Builder(c, channelId)
                        .setSmallIcon(android.R.drawable.stat_sys_upload)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setContentIntent(pi);

        nm.notify(type == 0 ? 100 : type, b.build());
    }

    public static void startBackup(Context c) {

        new Thread(() -> {

            if (!isEnabled(c)) return;

            if (!hasValidFolder(c)) {
                android.os.Handler h =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() ->
                        Toast.makeText(
                                c,
                                "No valid folders to backup",
                                Toast.LENGTH_SHORT
                        ).show()
                );
                return;
            }

            ensureBackupContainer();

            int total = 0;
            for (String uriStr : getFolders(c)) {
                total += countFiles(c, android.net.Uri.parse(uriStr));
            }

            String timeFolder = buildTimeFolder();

            for (String uriStr : getFolders(c)) {
                android.net.Uri uri = android.net.Uri.parse(uriStr);
                uploadTree(c, uri, timeFolder);
            }

            if (getMode(c).equals("SPECIFIC_DAY")) {
                clear(c);
            }

            android.os.Handler h =
                    new android.os.Handler(android.os.Looper.getMainLooper());

            final int finalTotal = total;

            h.post(() -> {
                if (finalTotal > 0) {
                    Toast.makeText(c, "Backup completed", Toast.LENGTH_SHORT).show();
                    if (c instanceof HomeActivity) {
                        HomeActivity act = (HomeActivity) c;
                        act.doRefresh(false);
                    }
                } else {
                    Toast.makeText(c, "No files to backup", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private static void uploadTree(Context c, android.net.Uri treeUri, String timeFolder) {

        androidx.documentfile.provider.DocumentFile root =
                androidx.documentfile.provider.DocumentFile.fromTreeUri(c, treeUri);

        if (root == null || !root.isDirectory()) return;

        String rootName = root.getName();

        for (androidx.documentfile.provider.DocumentFile f : root.listFiles()) {
            uploadRecursive(c, f, timeFolder, rootName, "");
        }
    }

    private static void uploadRecursive(
            Context c,
            androidx.documentfile.provider.DocumentFile f,
            String timeFolder,
            String rootName,
            String relativePath
    ) {

        if (f.isDirectory()) {
            String rp = relativePath.isEmpty()
                    ? f.getName()
                    : relativePath + "/" + f.getName();

            for (androidx.documentfile.provider.DocumentFile ch : f.listFiles()) {
                uploadRecursive(c, ch, timeFolder, rootName, rp);
            }
            return;
        }

        String objectName =
                timeFolder + "/" +
                        rootName + "/" +
                        (relativePath.isEmpty() ? f.getName() : relativePath + "/" + f.getName());

        uploadUsingHomeUploader(c, f, objectName);
    }

    private static int countFiles(Context c, android.net.Uri treeUri) {

        androidx.documentfile.provider.DocumentFile root =
                androidx.documentfile.provider.DocumentFile.fromTreeUri(c, treeUri);

        if (root == null) return 0;

        int count = 0;

        for (androidx.documentfile.provider.DocumentFile f : root.listFiles()) {
            count += countRecursive(f);
        }

        return count;
    }

    private static int countRecursive(androidx.documentfile.provider.DocumentFile f) {
        if (f.isDirectory()) {
            int n = 0;
            for (androidx.documentfile.provider.DocumentFile ch : f.listFiles()) {
                n += countRecursive(ch);
            }
            return n;
        }
        return 1;
    }

    private static boolean hasValidFolder(Context c) {

        for (String uriStr : getFolders(c)) {
            android.net.Uri uri = android.net.Uri.parse(uriStr);
            androidx.documentfile.provider.DocumentFile df =
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(c, uri);

            if (df != null && df.exists() && df.isDirectory()) {
                return true;
            }
        }
        return false;
    }

    private static void ensureBackupContainer() {
        try {
            OkHttpClient client = new OkHttpClient();
            String url = HomeActivity.storageUrl + "/" + BACKUP_CONTAINER;

            Request req = new Request.Builder()
                    .url(url)
                    .put(RequestBody.create(new byte[0], null))
                    .addHeader("X-Auth-Token", HomeActivity.token)
                    .build();

            client.newCall(req).execute();
        } catch (Exception e) {
            Log.e("BACKUP", "Create backup container failed", e);
        }
    }

    private static void uploadUsingHomeUploader(
            Context c,
            DocumentFile f,
            String objectName
    ) {
        try {
            InputStream in = c.getContentResolver().openInputStream(f.getUri());

            RequestBody body = new RequestBody() {
                @Override
                public MediaType contentType() {
                    return MediaType.parse("application/octet-stream");
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        sink.write(buf, 0, len);
                    }
                }
            };

            String url =
                    HomeActivity.storageUrl +
                            "/" + BACKUP_CONTAINER + "/" +
                            objectName;

            Request req = new Request.Builder()
                    .url(url)
                    .put(body)
                    .addHeader("X-Auth-Token", HomeActivity.token)
                    .build();

            Response resp = new OkHttpClient().newCall(req).execute();

            Log.d("BACKUP", "PUT " + url + " → " + resp.code());

        } catch (Exception e) {
            Log.e("BACKUP", "Upload error", e);
        }
    }



}
