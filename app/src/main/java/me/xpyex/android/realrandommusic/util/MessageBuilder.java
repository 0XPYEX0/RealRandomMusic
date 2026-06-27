package me.xpyex.android.realrandommusic.util;

import java.util.ArrayList;
import lombok.NoArgsConstructor;

@NoArgsConstructor(staticName = "of")
public class MessageBuilder {
    private final ArrayList<String> list = new ArrayList<>();

    public String build() {
        return String.join("\n", list);
    }

    public MessageBuilder addLine(String line) {
        list.add(line);
        return this;
    }
}
