package me.xpyex.android.realrandommusic;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.Getter;
import me.xpyex.android.realrandommusic.data.PlayedData;
import me.xpyex.android.realrandommusic.data.SongPlayRecord;
import me.xpyex.android.realrandommusic.util.ConfigManager;

/**
 * 歌曲历史记录管理器 — JSON 持久化，线程安全。
 */
public class SongHistoryManager {
    private static final String TAG = "RealRandomMusic";
    private static final String JSON_FILE = "played_songs.json";
    private static final int MAX_HISTORY_SIZE = 1000;

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Context context;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<String, SongPlayRecord> playedSongs;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SongHistorySaver");
        t.setDaemon(true);
        return t;
    });
    @Getter
    public int skippedCount = 0;

    // ── 当前播放标识（启动后放行用）──
    private String currentSongIdentifier = null;

    /**
     * 获取上次保存时正在播放的歌曲标识，用于启动后判断是否放行。
     */
    public String getCurrentSongIdentifier() {
        return currentSongIdentifier;
    }

    /**
     * 记录当前正在播放的歌曲标识，持久化到 JSON。
     */
    public void setCurrentSongIdentifier(String identifier) {
        this.currentSongIdentifier = identifier;
        saveHistory();
    }

    // ── 调试信息 ──
    @Getter
    public String lastLoadError = null;
    @Getter
    public String lastSaveError = null;
    @Getter
    public long lastSaveTime = 0;
    @Getter
    public long fileSize = 0;

    public SongHistoryManager(Context context, ConfigManager configManager) {
        this.context = context;
        this.configManager = configManager;
        this.playedSongs = new ConcurrentHashMap<>(loadHistory());
    }

    // ── 持久化 ──

    private Map<String, SongPlayRecord> loadHistory() {
        File jsonFile = new File(context.getFilesDir(), JSON_FILE);
        if (!jsonFile.exists()) {
            lastLoadError = null;
            fileSize = 0;
            return new HashMap<>();
        }
        fileSize = jsonFile.length();
        try {
            String json = String.join("\n", Files.readAllLines(jsonFile.toPath(), StandardCharsets.UTF_8));
            PlayedData playedData = gson.fromJson(json, PlayedData.class);
            if (playedData != null) {
                currentSongIdentifier = playedData.getCurrent();
            }
            Map<String, SongPlayRecord> songs = playedData != null ? playedData.getSongs() : null;
            lastLoadError = null;
            return songs != null ? songs : new HashMap<>();
        } catch (Exception e) {
            lastLoadError = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
            Log.e(TAG, "加载历史记录失败: " + lastLoadError);
            return new HashMap<>();
        }
    }

    private void saveHistory() {
        saveExecutor.execute(() -> {
            PlayedData data = PlayedData.of()
                .setCurrent(currentSongIdentifier)
                .setSongs(new HashMap<>(playedSongs));
            String json = gson.toJson(data);
            File file = new File(context.getFilesDir(), JSON_FILE);
            try {
                Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
                lastSaveTime = System.currentTimeMillis();
                fileSize = file.length();
                lastSaveError = null;
            } catch (IOException e) {
                lastSaveError = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
                Log.e(TAG, "保存历史记录失败: " + lastSaveError);
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
        try {
            Files.deleteIfExists(new File(context.getFilesDir(), JSON_FILE).toPath());
        } catch (IOException e) {
            Log.e(TAG, "删除历史记录文件失败: " + e.getMessage());
        }
        Log.i(TAG, "已清空所有历史记录");
    }

    // ── 查询 ──

    public int getPlayedCount() {
        return playedSongs.size();
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
