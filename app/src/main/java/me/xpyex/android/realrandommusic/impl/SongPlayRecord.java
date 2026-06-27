package me.xpyex.android.realrandommusic.impl;

import lombok.Getter;
import lombok.Setter;

/**
 * 歌曲播放记录
 * 包含播放次数和上次播放时间
 */
@Setter
@Getter
public class SongPlayRecord {
    private int playCount;        // 播放次数
    private long lastPlayedTime;  // 上次播放时间（毫秒时间戳）

    public SongPlayRecord() {
        this.playCount = 1;
        this.lastPlayedTime = System.currentTimeMillis();
    }

    public SongPlayRecord(int playCount, long lastPlayedTime) {
        this.playCount = playCount;
        this.lastPlayedTime = lastPlayedTime;
    }

    /**
     * 增加播放次数并更新播放时间
     */
    public void recordPlay() {
        this.playCount++;
        this.lastPlayedTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "SongPlayRecord{" +
                   "playCount=" + playCount +
                   ", lastPlayedTime=" + lastPlayedTime +
                   '}';
    }
}
