package me.xpyex.android.realrandommusic.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;

/**
 * 通知工具类
 */
public class NotificationUtils {
    /**
     * 创建通知渠道（自动处理 Android O 以下兼容性）
     *
     * @param context     上下文
     * @param channelId   渠道 ID
     * @param name        渠道名称（用户可见）
     * @param description 渠道描述（可为 null）
     * @param importance  重要性等级（如 {@link NotificationManager#IMPORTANCE_LOW}）
     */
    public static void createNotificationChannel(Context context, String channelId,
                                                 String name, @Nullable String description,
                                                 int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(channelId, name, importance);
                if (description != null) {
                    channel.setDescription(description);
                }
                nm.createNotificationChannel(channel);
            }
        }
    }
}
