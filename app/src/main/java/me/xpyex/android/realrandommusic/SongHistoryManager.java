package me.xpyex.android.realrandommusic;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.xpyex.android.realrandommusic.impl.SongPlayRecord;
import me.xpyex.android.realrandommusic.util.ConfigManager;

/**
 * 歌曲历史记录管理器
 * <p>
 * 记录已播放的歌曲，检测重复播放。
 * 线程安全，异步持久化。
 */
public class SongHistoryManager {

    private static final String TAG = "RealRandomMusic";
    private static final String HISTORY_FILE = "played_songs.txt";
    private static final int MAX_HISTORY_SIZE = 1000;

    private final Context context;
    private final ConfigManager configManager;
    private final Map<String, SongPlayRecord> playedSongs = new HashMap<>();
    /**
     * 单线程写文件，避免并发写乱
     */
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SongHistorySaver");
        t.setDaemon(true);
        return t;
    });
    private int skippedCount = 0;

    public SongHistoryManager(Context context, ConfigManager configManager) {
        this.context = context;
        this.configManager = configManager;
        this.playedSongs.putAll(loadHistory());
    }

    // ── 持久化 ──

    private Map<String, SongPlayRecord> loadHistory() {
        File file = new File(context.getFilesDir(), HISTORY_FILE);
        if (!file.exists()) {
            return new HashMap<>();
        }

        Map<String, SongPlayRecord> songs = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file.toPath())) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    try {
                        String key = parts[0];
                        int count = Integer.parseInt(parts[1]);
                        long time = Long.parseLong(parts[2]);
                        songs.put(key, new SongPlayRecord(count, time));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "解析历史记录失败: " + line);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "加载历史记录失败: " + e.getMessage());
        }

        Log.d(TAG, "已加载 " + songs.size() + " 首历史歌曲");
        return songs;
    }

    private void saveHistory() {
        // 先在 synchronized 块里快照数据，再异步写文件，避免 ConcurrentModificationException
        String data;
        synchronized (playedSongs) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, SongPlayRecord> entry : playedSongs.entrySet()) {
                if (sb.length() > 0) sb.append("\n");
                SongPlayRecord r = entry.getValue();
                sb.append(entry.getKey()).append("|")
                    .append(r.getPlayCount()).append("|")
                    .append(r.getLastPlayedTime());
            }
            data = sb.toString();
        }

        saveExecutor.execute(() -> {
            try {
                Files.write(new File(context.getFilesDir(), HISTORY_FILE).toPath(),
                    data.getBytes());
            } catch (IOException e) {
                Log.e(TAG, "保存历史记录失败: " + e.getMessage());
            }
        });
    }

    // ── 核心逻辑 ──

    /**
     * @return true 表示应跳过（重复），false 表示允许播放
     */
    public synchronized boolean checkAndRecord(String songIdentifier) {
        if (songIdentifier == null || songIdentifier.isEmpty()) {
            return false;
        }

        // 统一标准化：去首尾空格
        String key = songIdentifier.trim();

        SongPlayRecord existing = playedSongs.get(key);
        if (existing != null) {
            // 已存在 → 按规则判断是否允许
            int maxPlay = configManager.getMaxPlayCount();
            int intervalSec = configManager.getRepeatIntervalSeconds();
            int totalSongs = configManager.getTotalPlaylistSize();

            // 如果歌单里的歌已经全播过一遍，自动重置历史
            if (totalSongs > 0 && playedSongs.size() >= totalSongs) {
                Log.i(TAG, "歌单已完整播放一轮 (" + playedSongs.size() + " 首)，自动重置历史");
                playedSongs.clear();
                playedSongs.put(key, new SongPlayRecord());
                saveHistory();
                return false;
            }

            // maxPlayCount: 同一首歌最多播放次数（1 = 只播 1 次，2 = 最多播 2 次，-1 = 不限）
            if (maxPlay >= 0 && existing.getPlayCount() >= maxPlay) {
                Log.w(TAG, "已达到最大播放次数: " + songIdentifier
                               + " (已播放 " + existing.getPlayCount() + " 次, 限制 " + maxPlay + " 次)");
                skippedCount++;
                return true;
            }

            // repeatIntervalSeconds: 距上次播放需间隔的秒数（-1 = 不限）
            if (intervalSec >= 0) {
                long elapsed = System.currentTimeMillis() - existing.getLastPlayedTime();
                if (elapsed < intervalSec * 1000L) {
                    long remaining = (intervalSec * 1000L - elapsed) / 1000;
                    Log.w(TAG, "在间隔期内: " + songIdentifier + " (还需等待 " + remaining + " 秒)");
                    skippedCount++;
                    return true;
                }
            }

            // 允许播放
            existing.recordPlay();
            Log.i(TAG, "重复播放（允许）: " + songIdentifier
                           + " (已播放 " + existing.getPlayCount() + " 次)");
            saveHistory();
            return false;
        }

        // 新歌曲
        playedSongs.put(key, new SongPlayRecord());
        Log.i(TAG, "新歌曲: " + songIdentifier + " (总计: " + playedSongs.size() + " 首)");

        if (playedSongs.size() > MAX_HISTORY_SIZE) {
            pruneOldest();
        }

        saveHistory();
        return false;
    }

    // ── 清理 ──

    /**
     * 按 lastPlayedTime 排序，保留最近的一半
     */
    private void pruneOldest() {
        Log.i(TAG, "历史记录过多，清理最旧的一半...");
        int keep = playedSongs.size() / 2;

        playedSongs.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> e.getValue().getLastPlayedTime()))
            .limit(playedSongs.size() - keep)
            .map(Map.Entry::getKey)
            .toList()  // 先收集再删除，避免并发修改
            .forEach(playedSongs::remove);

        saveHistory();
        Log.i(TAG, "清理完成，剩余: " + playedSongs.size() + " 首");
    }

    public synchronized void clearAll() {
        playedSongs.clear();
        skippedCount = 0;
        File file = new File(context.getFilesDir(), HISTORY_FILE);
        if (file.exists()) file.delete();
        Log.i(TAG, "已清空所有历史记录");
    }

    // ── 查询 ──

    public synchronized int getPlayedCount() {
        return playedSongs.size();
    }

    public synchronized int getSkippedCount() {
        return skippedCount;
    }

    /**
     * key 匹配逻辑与 checkAndRecord 一致
     */
    private String normalizeKey(String id) {
        return id == null ? null : id.trim();
    }

    public synchronized SongPlayRecord getPlayRecord(String songIdentifier) {
        String key = normalizeKey(songIdentifier);
        return key != null ? playedSongs.get(key) : null;
    }

    public synchronized boolean contains(String songIdentifier) {
        String key = normalizeKey(songIdentifier);
        return key != null && playedSongs.containsKey(key);
    }
}
