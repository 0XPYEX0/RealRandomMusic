package me.xpyex.android.realrandommusic;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import me.xpyex.android.realrandommusic.util.ConfigManager;
import me.xpyex.android.realrandommusic.util.NotificationUtils;

/**
 * 前台服务 - 显示常驻通知
 * 保持应用运行，并显示当前状态
 */
public class NoticeService extends Service {

    private static final String TAG = "RealRandomMusic";
    private static final String CHANNEL_ID = "media_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ConfigManager configManager;
    private String currentSongName = ""; // 保存当前歌曲名
    private BroadcastReceiver updateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "前台服务已启动");

        // 初始化配置管理器
        configManager = new ConfigManager(this);

        // 创建通知渠道
        createNotificationChannel();

        // 启动前台服务，显示常驻通知
        startForeground(NOTIFICATION_ID, createNotification());

        // 注册广播接收器，用于更新通知
        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String title = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                if (title != null) {
                    currentSongName = artist != null && !artist.isEmpty()
                                          ? artist + " - " + title
                                          : title;
                    // 更新通知
                    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, createNotification());
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(getPackageName() + ".UPDATE_NOTIFICATION");
        registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        Log.i(TAG, "服务初始化完成");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 注销广播接收器
        if (updateReceiver != null) {
            unregisterReceiver(updateReceiver);
        }

        Log.i(TAG, "前台服务已停止");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        NotificationUtils.createNotificationChannel(this, CHANNEL_ID,
            "音乐防重复监听", "正在监听音乐播放，防止重复播放",
            NotificationManager.IMPORTANCE_LOW);
    }

    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        // 第一行：运行状态和车载模式
        String carModeText = configManager.isCarMode() ? "车载模式" : "普通模式";
        String titleLine = "运行中: " + carModeText;

        // 第二行：当前歌曲
        String contentText = currentSongName.isEmpty() ? "当前歌曲: 无" : "当前歌曲: " + currentSongName;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                   .setContentTitle(titleLine)
                   .setContentText(contentText)
                   .setSmallIcon(android.R.drawable.ic_media_play)
                   .setOngoing(true)
                   .setPriority(NotificationCompat.PRIORITY_LOW)
                   .build();
    }
}
