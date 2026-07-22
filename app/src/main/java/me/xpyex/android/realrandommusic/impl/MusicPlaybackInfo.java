package me.xpyex.android.realrandommusic.impl;

import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * 音乐播放信息 — 从媒体通知中提取的全部有用数据
 */
@Getter(value = AccessLevel.PUBLIC)
public class MusicPlaybackInfo {

    // ── 歌曲信息 ──
    public final String title;         // 歌名
    public final String artist;        // 歌手
    public final String album;         // 专辑
    public final long durationMs;      // 总时长（毫秒）

    // ── 播放状态 ──
    public final boolean isPlaying;    // 是否正在播放
    public final long positionMs;      // 当前播放进度（毫秒）
    public final float playbackSpeed;  // 播放速度（1.0 = 正常）
    public final int stateCode;        // PlaybackState 原始状态码

    // ── 切歌判定 ──
    public final float previousProgressPercent; // 上一首歌的播放进度 (-1=无上一首/未知)

    // ── 来源信息 ──
    public final String packageName;   // 播放器包名
    public final String appName;       // 播放器应用名（可能为空）
    public final long eventTimestamp;  // 事件时间戳

    // ── 附加字段（不同播放器可能提供不同内容）──
    public final String mediaId;       // 媒体 ID（如果有）
    public final int trackNumber;      // 音轨号（如果有）

    public MusicPlaybackInfo(Builder builder) {
        this.title = builder.title;
        this.artist = builder.artist;
        this.album = builder.album;
        this.durationMs = builder.durationMs;
        this.isPlaying = builder.isPlaying;
        this.positionMs = builder.positionMs;
        this.playbackSpeed = builder.playbackSpeed;
        this.stateCode = builder.stateCode;
        this.previousProgressPercent = builder.previousProgressPercent;
        this.packageName = builder.packageName;
        this.appName = builder.appName;
        this.eventTimestamp = builder.eventTimestamp;
        this.mediaId = builder.mediaId;
        this.trackNumber = builder.trackNumber;
    }

    // ── Getters ──

    /**
     * 格式化的时长，如 "3:42"
     */
    public String getDurationFormatted() {
        long totalSec = durationMs / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    /**
     * 格式化的当前进度，如 "1:23"
     */
    public String getPositionFormatted() {
        long totalSec = positionMs / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    /**
     * 播放进度百分比 0.0 ~ 1.0，duration 未知时返回 -1
     */
    public float getProgressPercent() {
        if (durationMs <= 0) return -1f;
        return Math.min(1f, (float) positionMs / durationMs);
    }

    /**
     * 用于比较是否为同一首歌的标识（普通模式）
     */
    public String getSongIdentifier() {
        StringBuilder sb = new StringBuilder();
        if (artist != null && !artist.isEmpty()) sb.append(artist).append(" - ");
        sb.append(title != null ? title : "未知歌曲");
        return sb.toString();
    }

    /**
     * 用于比较是否为同一首歌的标识
     *
     * @param carMode 车载模式：蓝牙音频信息 title/artist 可能颠倒，仅用 artist 作为标识
     */
    public String getSongIdentifier(boolean carMode) {
        if (title == null && artist == null) return null;
        if (carMode) {
            // 车载模式：蓝牙音频信息反了，artist 字段才是歌名
            return (artist != null && !artist.isEmpty()) ? artist : title;
        }
        return getSongIdentifier();
    }

    // ── Builder ──

    @Override
    public int hashCode() {
        return Objects.hash(title, artist, packageName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MusicPlaybackInfo)) return false;
        MusicPlaybackInfo that = (MusicPlaybackInfo) o;
        return Objects.equals(title, that.title)
                   && Objects.equals(artist, that.artist)
                   && Objects.equals(packageName, that.packageName);
    }

    @Override
    public String toString() {
        return String.format(
            "MusicPlaybackInfo{title='%s', artist='%s', album='%s', pkg='%s', playing=%s, pos=%s/%s}",
            title, artist, album, packageName, isPlaying,
            getPositionFormatted(), getDurationFormatted()
        );
    }

    public static class Builder {
        public String title;
        public String artist;
        public String album;
        public long durationMs;
        public boolean isPlaying;
        public long positionMs;
        public float playbackSpeed = 1.0f;
        public int stateCode;
        public String packageName;
        public String appName;
        public long eventTimestamp = System.currentTimeMillis();
        public float previousProgressPercent = -1f;
        public String mediaId;
        public int trackNumber;

        /**
         * 从 MediaMetadata 填充歌曲信息
         */
        public Builder fromMediaMetadata(MediaMetadata metadata) {
            if (metadata == null) return this;
            this.title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            this.artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            this.album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            this.durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            this.trackNumber = (int) metadata.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
            this.mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            return this;
        }

        /**
         * 从 PlaybackState 填充播放状态
         */
        public Builder fromPlaybackState(PlaybackState state) {
            if (state == null) return this;
            this.stateCode = state.getState();
            this.isPlaying = state.getState() == PlaybackState.STATE_PLAYING;
            this.positionMs = state.getPosition();
            this.playbackSpeed = state.getPlaybackSpeed();
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder artist(String artist) {
            this.artist = artist;
            return this;
        }

        public Builder album(String album) {
            this.album = album;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder isPlaying(boolean isPlaying) {
            this.isPlaying = isPlaying;
            return this;
        }

        public Builder positionMs(long positionMs) {
            this.positionMs = positionMs;
            return this;
        }

        public Builder playbackSpeed(float speed) {
            this.playbackSpeed = speed;
            return this;
        }

        public Builder stateCode(int code) {
            this.stateCode = code;
            return this;
        }

        public Builder packageName(String pkg) {
            this.packageName = pkg;
            return this;
        }

        public Builder appName(String app) {
            this.appName = app;
            return this;
        }

        public Builder eventTimestamp(long ts) {
            this.eventTimestamp = ts;
            return this;
        }

        public MusicPlaybackInfo build() {
            return new MusicPlaybackInfo(this);
        }
    }
}
