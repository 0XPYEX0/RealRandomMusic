package me.xpyex.android.realrandommusic.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * 配置管理器
 * 用于管理应用的配置项
 */
public class ConfigManager {

    private static final String PREFS_NAME = "RealRandomMusic";
    private static final String KEY_DEBUG_MODE = "DebugMode";
    private static final String KEY_CAR_MODE = "CarMode";
    private static final String KEY_WHITELIST = "Whitelist";
    private static final String KEY_MAX_PLAY_COUNT = "MaxPlayCount";
    private static final String KEY_REPEAT_INTERVAL_SECONDS = "RepeatIntervalSeconds";
    private static final String KEY_TOTAL_PLAYLIST_SIZE = "TotalPlaylistSize";
    private static final String KEY_PAUSED = "Paused";

    private final SharedPreferences preferences;

    public ConfigManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取调试模式状态
     */
    public boolean isDebugMode() {
        return preferences.getBoolean(KEY_DEBUG_MODE, false);
    }

    /**
     * 设置调试模式状态
     */
    public void setDebugMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply();
    }

    /**
     * 获取车载音乐兼容模式状态
     */
    public boolean isCarMode() {
        return preferences.getBoolean(KEY_CAR_MODE, false);
    }

    /**
     * 设置车载音乐兼容模式状态
     */
    public void setCarMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_CAR_MODE, enabled).apply();
    }

    /**
     * 获取白名单列表
     */
    public Set<String> getWhitelist() {
        return preferences.getStringSet(KEY_WHITELIST, new HashSet<>());
    }

    /**
     * 添加应用到白名单
     */
    public void addToWhitelist(String packageName) {
        Set<String> whitelist = new HashSet<>(getWhitelist());
        whitelist.add(packageName);
        preferences.edit().putStringSet(KEY_WHITELIST, whitelist).apply();
    }

    /**
     * 从白名单移除应用
     */
    public void removeFromWhitelist(String packageName) {
        Set<String> whitelist = new HashSet<>(getWhitelist());
        whitelist.remove(packageName);
        preferences.edit().putStringSet(KEY_WHITELIST, whitelist).apply();
    }

    /**
     * 检查应用是否在白名单中
     */
    public boolean isInWhitelist(String packageName) {
        return getWhitelist().contains(packageName);
    }

    /**
     * 获取最多播放次数上限
     *
     * @return 同一首歌最多播放次数，-1 = 不限制
     */
    public int getMaxPlayCount() {
        return preferences.getInt(KEY_MAX_PLAY_COUNT, 1);
    }

    /**
     * 设置最多播放次数上限
     *
     * @param count 同一首歌最多播放次数，-1 = 不限制
     */
    public void setMaxPlayCount(int count) {
        preferences.edit().putInt(KEY_MAX_PLAY_COUNT, count).apply();
    }

    /**
     * 获取重复间隔时间（秒）
     *
     * @return 间隔秒数，负数表示不限制
     */
    public int getRepeatIntervalSeconds() {
        return preferences.getInt(KEY_REPEAT_INTERVAL_SECONDS, -1);
    }

    /**
     * 设置重复间隔时间（秒）
     *
     * @param seconds 间隔秒数，负数表示不限制
     */
    public void setRepeatIntervalSeconds(int seconds) {
        preferences.edit().putInt(KEY_REPEAT_INTERVAL_SECONDS, seconds).apply();
    }

    /**
     * 获取歌单总歌曲数（用户手动填写）
     *
     * @return 歌单歌曲数量，-1 = 未设置，不启用自动重置
     */
    public int getTotalPlaylistSize() {
        return preferences.getInt(KEY_TOTAL_PLAYLIST_SIZE, -1);
    }

    /**
     * 设置歌单总歌曲数
     *
     * @param size 歌单歌曲数量，-1 = 不启用自动重置
     */
    public void setTotalPlaylistSize(int size) {
        preferences.edit().putInt(KEY_TOTAL_PLAYLIST_SIZE, size).apply();
    }

    /**
     * 获取暂停状态（持久化，重启后保持）
     */
    public boolean isPaused() {
        return preferences.getBoolean(KEY_PAUSED, false);
    }

    /**
     * 设置暂停状态（持久化，重启后保持）
     */
    public void setPaused(boolean paused) {
        preferences.edit().putBoolean(KEY_PAUSED, paused).apply();
    }
}
