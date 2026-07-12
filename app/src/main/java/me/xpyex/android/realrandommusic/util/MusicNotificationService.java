package me.xpyex.android.realrandommusic.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import me.xpyex.android.realrandommusic.RrmApp;
import me.xpyex.android.realrandommusic.impl.MusicPlaybackInfo;

/**
 * 音乐通知监听服务
 * <p>
 * 通过 {@link MediaSessionManager} 直接监听系统媒体会话，
 * 不解析通知栏数据。可靠且兼容所有音乐播放器。
 */
public class MusicNotificationService extends NotificationListenerService {

    private static final String TAG = "MusicNotificationService";
    private static final int MAX_EVENT_LOG = 200;

    // ── 调试日志 ──
    private static final Deque<String> eventLog = new ConcurrentLinkedDeque<>();
    // ── 监听器 ──
    private static final CopyOnWriteArrayList<MusicListener> listeners = new CopyOnWriteArrayList<>();
    private static MusicNotificationService instance;

    // ── 主线程 Handler ──
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── 媒体会话 ──
    @Nullable
    private MediaSessionManager sessionManager;
    @Nullable
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    @Nullable
    private MediaController activeController;
    @Nullable
    private MusicPlaybackInfo currentPlayback;
    private String lastSongIdentifier = "";
    private boolean debouncePending;
    private final Runnable debouncedSync = this::doDebouncedSync;

    // ── 回调接口 ──

    public static void addListener(@NonNull MusicListener listener) {
        listeners.add(listener);
    }

    // ── 公开静态方法 ──

    public static void removeListener(@NonNull MusicListener listener) {
        listeners.remove(listener);
    }

    @Nullable
    public static MusicPlaybackInfo getCurrentPlayback() {
        return instance != null ? instance.currentPlayback : null;
    }

    /**
     * 跳过指定应用的当前播放歌曲
     *
     * @param targetPackage 目标播放器包名，为 null 则使用当前 activeController
     */
    public static boolean skipToNext(@Nullable String targetPackage) {
        MusicNotificationService svc = instance;
        if (svc == null) return false;

        // 找目标 controller：传入包名时精准匹配，否则降级用 activeController
        MediaController target = svc.activeController;
        if (targetPackage != null
                && (target == null || !targetPackage.equals(target.getPackageName()))) {
            target = findControllerByPackage(targetPackage);
        }
        if (target == null) {
            log(">>> APP切歌失败(无目标会话): " + targetPackage);
            return false;
        }

        try {
            PlaybackState state = target.getPlaybackState();
            long actions = state != null ? state.getActions() : 0;
            if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
                target.getTransportControls().skipToNext();
                log(">>> APP切歌: " + target.getPackageName());
                return true;
            } else {
                log(">>> APP切歌失败(不支持): " + target.getPackageName()
                        + " actions=" + actions);
            }
        } catch (Exception e) {
            log(">>> APP切歌异常: " + e.getMessage());
        }
        return false;
    }

    /**
     * 从活跃会话中查找指定包名的 MediaController
     */
    @Nullable
    private static MediaController findControllerByPackage(@NonNull String packageName) {
        MusicNotificationService svc = instance;
        if (svc == null || svc.sessionManager == null) return null;
        try {
            ComponentName cn = new ComponentName(svc, MusicNotificationService.class);
            List<MediaController> sessions = svc.sessionManager.getActiveSessions(cn);
            if (sessions != null) {
                for (MediaController c : sessions) {
                    if (packageName.equals(c.getPackageName())) {
                        return c;
                    }
                }
            }
        } catch (Exception e) {
            log("查找会话异常: " + e.getMessage());
        }
        return null;
    }

    @NonNull
    public static List<String> getEventLog() {
        return new ArrayList<>(eventLog);
    }

    public static void clearEventLog() {
        eventLog.clear();
    }

    public static void requestRebind(@NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName cn = new ComponentName(context, MusicNotificationService.class);
            pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
            NotificationListenerService.requestRebind(cn);
        } catch (Exception e) {
            Log.e(TAG, "重连失败", e);
        }
    }

    private static void log(String msg) {
        try {
            String line = LocalTime.now().toString().substring(0, 12) + " " + msg;
            eventLog.addLast(line);
            if (eventLog.size() > MAX_EVENT_LOG) eventLog.removeFirst();
        } catch (Exception ignored) {
            eventLog.addLast(msg);
        }
        Log.i(TAG, msg);
    }

    // ── 日志 ──

    // ── Service 生命周期 ──

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        log("服务创建");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        log("通知监听已连接，开始监听媒体会话");

        ComponentName cn = new ComponentName(this, MusicNotificationService.class);
        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);

        sessionsListener = controllers -> {
            if (controllers == null || controllers.isEmpty()) {
                log("活跃会话变化: 无会话");
                onNoActiveSession();
                return;
            }

            // 获取白名单
            Set<String> whitelist = getWhitelist();

            // 白名单为空 → 不监听任何应用
            if (whitelist.isEmpty()) {
                if (activeController != null) {
                    log("白名单为空，忽略所有会话");
                    onNoActiveSession();
                }
                return;
            }

            // 优先找正在播放的且在白名单中的，否则用第一个白名单内的
            MediaController playing = null;
            for (MediaController c : controllers) {
                if (!whitelist.contains(c.getPackageName())) continue;
                PlaybackState state = c.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    playing = c;
                    break;
                }
            }
            if (playing == null) {
                for (MediaController c : controllers) {
                    if (whitelist.contains(c.getPackageName())) {
                        playing = c;
                        break;
                    }
                }
            }
            if (playing == null) {
                log("无白名单内的活跃会话");
                onNoActiveSession();
                return;
            }

            log("活跃会话: " + controllers.size() + " 个, 选中 " + playing.getPackageName());

            if (activeController != null
                    && activeController.getSessionToken().equals(playing.getSessionToken())) {
                return; // 同一个会话，不重复注册
            }

            switchToController(playing);
        };

        sessionManager.addOnActiveSessionsChangedListener(sessionsListener, cn);
        // 立即拉取当前活跃会话
        sessionsListener.onActiveSessionsChanged(sessionManager.getActiveSessions(cn));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindController();
        if (sessionManager != null && sessionsListener != null) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
        }
        instance = null;
        currentPlayback = null;
        log("服务停止");
    }

    // ── 控制器管理 ──

    private void switchToController(@NonNull MediaController controller) {
        unbindController();
        activeController = controller;
        controller.registerCallback(mediaCallback);
        syncFromActiveController();
    }

    private void unbindController() {
        if (activeController != null) {
            activeController.unregisterCallback(mediaCallback);
            activeController = null;
        }
    }

    private void onNoActiveSession() {
        unbindController();
        if (currentPlayback != null) {
            currentPlayback = null;
            lastSongIdentifier = "";
            notifyPlaybackStopped();
        }
    }

    /**
     * 入口：debounce 300ms，等元数据稳定后再处理
     */
    private void syncFromActiveController() {
        if (debouncePending) {
            mainHandler.removeCallbacks(debouncedSync);
        }
        debouncePending = true;
        mainHandler.postDelayed(debouncedSync, 300);
    }

    /**
     * 实际处理：元数据已稳定
     */
    private void doDebouncedSync() {
        debouncePending = false;

        MediaController c = activeController;
        if (c == null) return;

        // 白名单检查：白名单为空或当前应用不在白名单中则忽略
        Set<String> whitelist = getWhitelist();
        if (whitelist.isEmpty() || !whitelist.contains(c.getPackageName())) {
            log("不在白名单中，忽略: " + c.getPackageName());
            return;
        }

        MediaMetadata metadata = c.getMetadata();
        PlaybackState state = c.getPlaybackState();

        if (metadata == null) {
            log("无元数据: " + c.getPackageName());
            return;
        }

        MusicPlaybackInfo.Builder builder = new MusicPlaybackInfo.Builder()
                                                .fromMediaMetadata(metadata)
                                                .packageName(c.getPackageName());

        if (state != null) {
            builder.fromPlaybackState(state);
        }

        MusicPlaybackInfo info = builder.build();

        if (info.getTitle() == null) {
            log("标题为空: " + c.getPackageName());
            return;
        }

        // 车载模式：title 是歌词会不停变，只用 artist 做标识
        boolean carMode = RrmApp.instance != null
                              && RrmApp.instance.configManager != null
                              && RrmApp.instance.configManager.isCarMode();
        String songId = info.getSongIdentifier(carMode);
        log("获取: " + songId + (carMode ? " 🚗" : "") + (info.isPlaying() ? " ▶" : " ⏸"));

        // 去重 & 分发
        if (currentPlayback == null) {
            currentPlayback = info;
            lastSongIdentifier = songId;
            log("回调👉 新歌: " + songId);
            notifySongChanged(info);
        } else if (!songId.equals(lastSongIdentifier)) {
            currentPlayback = info;
            lastSongIdentifier = songId;
            log("回调👉 切歌: " + songId);
            notifySongChanged(info);
        } else if (info.isPlaying() != currentPlayback.isPlaying()) {
            currentPlayback = info;
            log("回调👉 状态变化: " + songId);
            notifyPlaybackStateChanged(info);
        }
    }

    /**
     * 安全获取白名单，避免空指针
     */
    @NonNull
    private Set<String> getWhitelist() {
        if (RrmApp.instance != null && RrmApp.instance.configManager != null) {
            return RrmApp.instance.configManager.getWhitelist();
        }
        return Collections.emptySet();
    }

    /**
     * 通用监听器通知 —— 消除 try-catch 遍历样板代码
     */
    private void notifyListeners(Consumer<MusicListener> action) {
        mainHandler.post(() -> {
            for (MusicListener l : listeners) {
                try {
                    action.accept(l);
                } catch (Exception e) {
                    Log.e(TAG, "回调异常", e);
                }
            }
        });
    }    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onSessionDestroyed() {
            log("会话销毁: " +
                    (activeController != null ? activeController.getPackageName() : "null"));
            onNoActiveSession();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            log("播放状态变化: " + (state != null ? state.getState() : "null"));
            syncFromActiveController();
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            log("元数据变化: " + (metadata != null
                                      ? metadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "null"));
            syncFromActiveController();
        }
    };

    private void notifySongChanged(MusicPlaybackInfo info) {
        notifyListeners(l -> l.onSongChanged(info));
    }

    private void notifyPlaybackStateChanged(MusicPlaybackInfo info) {
        notifyListeners(l -> l.onPlaybackStateChanged(info));
    }

    private void notifyPlaybackStopped() {
        notifyListeners(l -> l.onPlaybackStopped());
    }

    public interface MusicListener {
        void onSongChanged(@NonNull MusicPlaybackInfo info);

        void onPlaybackStateChanged(@NonNull MusicPlaybackInfo info);

        void onPlaybackStopped();
    }

    // ── 从 MediaController 获取数据并分发 ──



    // ── 通知主线程回调 ──


}
