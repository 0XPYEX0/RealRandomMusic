package me.xpyex.android.realrandommusic.data;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class PlayedData {
    private String current;  // 启动时正在播放的歌曲标识，用于启动后放行
    private Map<String, SongPlayRecord> songs;
}
