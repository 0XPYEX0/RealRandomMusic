package me.xpyex.android.realrandommusic;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.xpyex.android.realrandommusic.impl.SongPlayRecord;
import me.xpyex.android.realrandommusic.util.ConfigManager;

/**
 * 歌曲历史记录管理器 — JSON 持久化，线程安全。
 */
public class SongHistoryManager {

    private static final String TAG = "RealRandomMusic";
    private static final String JSON_FILE = "played_songs.json";
    private static final String LEGACY_TXT_FILE = "played_songs.txt";
    private static final int MAX_HISTORY_SIZE = 1000;

    private final Gson gson = new Gson();
    private final Context context;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<String, SongPlayRecord> playedSongs;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SongHistorySaver");
        t.setDaemon(true);
        return t;
    });
    private int skippedCount = 0;

    public SongHistoryManager(Context context, ConfigManager configManager) {
        this.context = context;
        this.configManager = configManager;
        this.playedSongs = new ConcurrentHashMap<>(loadHistory());
    }

    // ── 持久化 ──

    private Map<String, SongPlayRecord> loadHistory() {
        // txt → json 迁移
        File legacyFile = new File(context.getFilesDir(), LEGACY_TXT_FILE);
        File jsonFile = new File(context.getFilesDir(), JSON_FILE);
        if (legacyFile.exists() && !jsonFile.exists()) {
            migrateTxtToJson(legacyFile, jsonFile);
        }
        if (!jsonFile.exists()) {
            return new HashMap<>();
        }
        try (FileReader reader = new FileReader(jsonFile)) {
            Type type = new TypeToken<Map<String, SongPlayRecord>>() {}.getType();
            Map<String, SongPlayRecord> songs = gson.fromJson(reader, type);
            return songs != null ? songs : new HashMap<>();
        } catch (Exception e) {
            Log.e(TAG, "加载历史记录失败: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private void migrateTxtToJson(File txtFile, File jsonFile) {
        try {
            Map<String, SongPlayRecord> songs = new HashMap<>();
            for (String line : Files.readAllLines(txtFile.toPath())) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    try {
                        String key = parts[0];
                        int count = Integer.parseInt(parts[1]);
                        long time = Long.parseLong(parts[2]);
                        songs.put(key, new SongPlayRecord(count, time));
                    } catch (NumberFormatException ignored) {}
                }
            }
            try (FileWriter writer = new FileWriter(jsonFile)) {
                gson.toJson(songs, writer);
            }
            if (!txtFile.delete()) {
                Log.w(TAG, "无法删除旧 txt 文件");
            }
            Log.i(TAG, "已从 txt 迁移 " + songs.size() + " 条记录到 json");
        } catch (IOException e) {
            Log.e(TAG, "迁移历史记录失败: " + e.getMessage());
        }
    }

    private void saveHistory() {
        saveExecutor.execute(() -> {
            String json = gson.toJson(playedSongs);
            try (FileWriter writer = new FileWriter(
                    new File(context.getFilesDir(), JSON_FILE))) {
                writer.write(json);
            } catch (IOException e) {
                Log.e(TAG, "保存历史记录失败: " + e.getMessage());
            }
        });
    }

    // ── 核心逻辑 ──

    /**
     * @return true 表示应跳过（重复），false 表示允许播放
     */
    public boolean checkAndRecord(String songIdentifier) {
        if (songIdentifier == null || songIdentifier.isEmpty()) {
            return false;
        }

        String key = songIdentifier.trim();

        // 读
        SongPlayRecord existing = playedSongs.get(key);

        if (existing != null) {
            int maxPlay = configManager.getMaxPlayCount();
            int intervalSec = configManager.getRepeatIntervalSeconds();
            int totalSongs = configManager.getTotalPlaylistSize();

            if (totalSongs > 0 && playedSongs.size() >= totalSongs) {
                Log.i(TAG, "歌单已完整播放一轮 (" + playedSongs.size() + " 首)，自动重置历史");
                playedSongs.clear();
                playedSongs.put(key, new SongPlayRecord());
                saveHistory();
                return false;
            }

            if (maxPlay >= 0 && existing.getPlayCount() >= maxPlay) {
                Log.w(TAG, "已达到最大播放次数: " + songIdentifier
                        + " (已播放 " + existing.getPlayCount() + " 次, 限制 " + maxPlay + " 次)");
                skippedCount++;
                return true;
            }

            if (intervalSec >= 0) {
                long elapsed = System.currentTimeMillis() - existing.getLastPlayedTime();
                if (elapsed < intervalSec * 1000L) {
                    long remaining = (intervalSec * 1000L - elapsed) / 1000;
                    Log.w(TAG, "在间隔期内: " + songIdentifier + " (还需等待 " + remaining + " 秒)");
                    skippedCount++;
                    return true;
                }
            }

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

    private void pruneOldest() {
        Log.i(TAG, "历史记录过多，清理最旧的一半...");
        int keep = playedSongs.size() / 2;

        playedSongs.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getLastPlayedTime()))
                .limit(playedSongs.size() - keep)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
                .forEach(playedSongs::remove);

        saveHistory();
        Log.i(TAG, "清理完成，剩余: " + playedSongs.size() + " 首");
    }

    public void clearAll() {
        playedSongs.clear();
        skippedCount = 0;
        File jsonFile = new File(context.getFilesDir(), JSON_FILE);
        if (jsonFile.exists()) jsonFile.delete();
        Log.i(TAG, "已清空所有历史记录");
    }

    // ── 查询 ──

    public int getPlayedCount() {
        return playedSongs.size();
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public SongPlayRecord getPlayRecord(String songIdentifier) {
        String key = normalizeKey(songIdentifier);
        return key != null ? playedSongs.get(key) : null;
    }

    public boolean contains(String songIdentifier) {
        String key = normalizeKey(songIdentifier);
        return key != null && playedSongs.containsKey(key);
    }

    private String normalizeKey(String id) {
        return id == null ? null : id.trim();
    }
}
