package me.xpyex.android.realrandommusic;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import me.xpyex.android.realrandommusic.impl.MusicPlaybackInfo;
import me.xpyex.android.realrandommusic.util.ConfigManager;
import me.xpyex.android.realrandommusic.util.MusicNotificationService;
import me.xpyex.android.realrandommusic.util.NotificationUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 应用程序入口
 * <p>
 * 初始化全局组件，并串联核心逻辑：
 * 通知事件 → 重复检测 → 自动跳过
 */
public class RrmApp extends Application {
    private static final String TAG = "RealRandomMusic";

    public static RrmApp instance;
    public ConfigManager configManager;
    public SongHistoryManager historyManager;

    private boolean paused;
    private String lastProcessedIdentifier;

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 初始化
        configManager = new ConfigManager(this);
        historyManager = new SongHistoryManager(this, configManager);

        // 串联：收到切歌事件 → 查重 → 如重复则跳过
        MusicNotificationService.addListener(new MusicNotificationService.MusicListener() {
            @Override
            public void onSongChanged(@NotNull MusicPlaybackInfo info) {
                handleSongChanged(info);
            }

            @Override
            public void onPlaybackStateChanged(MusicPlaybackInfo info) {
                // 仅播放/暂停切换，不处理
            }

            @Override
            public void onPlaybackStopped() {
                Log.d(TAG, "播放停止");
            }
        });

        Log.i(TAG, "应用初始化完成（事件驱动模式）");
    }

    /**
     * 切歌时判断是否重复，重复则跳过
     */
    private void handleSongChanged(MusicPlaybackInfo info) {
        // 暂停模式：只更新前端通知，不查重不切歌
        if (paused) {
            sendUpdateBroadcast(info);
            return;
        }

        // 构建歌曲标识（和之前逻辑一致）
        String identifier = buildSongIdentifier(info);
        if (identifier == null || identifier.isEmpty()) return;

        // 同一首歌不重复处理（车载蓝牙场景：歌词更新触发重复回调）
        if (identifier.equals(lastProcessedIdentifier)) {
            Log.d(TAG, "跳过重复回调: " + identifier);
            return;
        }
        lastProcessedIdentifier = identifier;

        Log.i(TAG, "检测到歌曲: " + identifier + " | 来源: " + info.getPackageName());

        // 查重
        boolean isRepeat = historyManager.checkAndRecord(identifier);

        if (isRepeat) {
            Log.w(TAG, "重复歌曲，自动跳过: " + identifier);
            MusicNotificationService.skipToNext(info.getPackageName());

            // 调试模式下发通知
            if (configManager.isDebugMode()) {
                sendSkipNotification(identifier);
            }
        }

        // 更新前台服务通知
        sendUpdateBroadcast(info);
    }

    /**
     * 构建歌曲标识（兼容车载模式）
     */
    private String buildSongIdentifier(MusicPlaybackInfo info) {
        return info.getSongIdentifier(configManager.isCarMode());
    }

    /**
     * 发送广播更新 NoticeService 的前台通知
     */
    private void sendUpdateBroadcast(MusicPlaybackInfo info) {
        Intent intent = new Intent("me.xpyex.android.realrandommusic.UPDATE_NOTIFICATION");
        intent.putExtra("title", info.getTitle());
        intent.putExtra("artist", info.getArtist() != null ? info.getArtist() : "");
        sendBroadcast(intent);
    }

    /**
     * 调试模式下发送跳过通知
     */
    private void sendSkipNotification(String identifier) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        String channelId = "skip_notification";
        NotificationUtils.createNotificationChannel(this, channelId,
            "跳过通知", null, NotificationManager.IMPORTANCE_DEFAULT);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                                        .setContentTitle("已跳过重复歌曲")
                                        .setContentText(identifier)
                                        .setSmallIcon(android.R.drawable.ic_media_next)
                                        .setAutoCancel(true)
                                        .build();

        nm.notify(2001, notification);
    }
}
