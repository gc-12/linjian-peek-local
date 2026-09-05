package dev.linjian.peek.mcp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import dev.linjian.peek.AppPrefs;
import dev.linjian.peek.DebugState;
import dev.linjian.peek.MainActivity;

/**
 * Foreground service hosting the local MCP server. Keeps a partial wake lock,
 * shows a notification with the endpoint address and restarts itself a few
 * seconds after being killed (unless stopped explicitly).
 */
public class McpLocalService extends Service {
    public static final String ACTION_STOP = "dev.linjian.peek.mcp.STOP";
    public static final int DEFAULT_PORT = 5000;

    private static final String CHANNEL_ID = "linjian_mcp_service";
    private static final int NOTIFICATION_ID = 20260905;

    private static volatile McpLocalService instance;
    private static volatile boolean running = false;
    private static volatile boolean stopping = false;
    private static volatile String currentUrl = "";
    private static volatile int currentPort = -1;

    private McpHttpServer server;
    private PowerManager.WakeLock wakeLock;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isRunning() {
        return running;
    }

    public static String currentUrl() {
        return currentUrl;
    }

    public static int currentPort() {
        return currentPort;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopping = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        stopping = false;
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("本地 MCP 服务启动中…"));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Linjian::LocalMcp");
            try {
                wakeLock.acquire();
            } catch (Exception ignored) {
            }
        }
        if (!running) startServer();
        return START_STICKY;
    }

    private void startServer() {
        final boolean allowLan = AppPrefs.get(this).getBoolean(AppPrefs.KEY_MCP_LAN, false);
        final String token = McpLocalSecurity.getOrCreateToken(this);
        new Thread(() -> {
            try {
                McpToolRegistry registry = new McpToolRegistry(getApplicationContext());
                server = new McpHttpServer(token, allowLan, DEFAULT_PORT, registry,
                        "linjian-peek-local-mcp", AppPrefs.APP_VERSION_NAME,
                        new McpHttpServer.Listener() {
                            @Override
                            public void onStarted(int port) {
                                currentPort = port;
                                currentUrl = McpLocalSecurity.endpointUrl(McpLocalService.this, port, allowLan);
                                running = true;
                                DebugState.append(McpLocalService.this, "本地 MCP 服务已启动：" + currentUrl);
                                updateNotification();
                            }

                            @Override
                            public void onStopped() {
                                running = false;
                                currentUrl = "";
                            }

                            @Override
                            public void onError(String message) {
                                DebugState.append(McpLocalService.this, "本地 MCP 服务异常：" + message);
                                updateNotification("启动失败：" + message);
                            }
                        });
                boolean ok = server.start();
                if (!ok) running = false;
            } catch (Exception e) {
                running = false;
                DebugState.append(this, "本地 MCP 服务启动异常：" + e.getMessage());
                updateNotification("异常：" + e.getMessage());
            }
        }, "mcp-start").start();
    }

    private void updateNotification() {
        updateNotification(null);
    }

    private void updateNotification(String statusText) {
        mainHandler.post(() -> {
            try {
                String text = statusText != null ? statusText : (currentUrl.isEmpty() ? "未启动" : currentUrl);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
            } catch (Exception ignored) {
            }
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "本地 MCP 服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("本地 MCP 服务运行状态与地址");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent tap = new Intent(this, MainActivity.class);
        tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(this, 0, tap,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, McpLocalService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification n = b
                .setContentTitle("本地 MCP 服务运行中")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPi)
                .build();
        return n;
    }

    private void scheduleRestart() {
        try {
            Intent restart = new Intent(this, McpLocalService.class);
            PendingIntent pi = PendingIntent.getService(this, 2, restart,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_ONE_SHOT);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pi);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        currentUrl = "";
        try {
            if (server != null) server.stop();
        } catch (Exception ignored) {
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        if (!stopping) scheduleRestart();
        instance = null;
        DebugState.append(this, "本地 MCP 服务已停止");
        super.onDestroy();
    }
}
