package me.xpyex.android.realrandommusic.util

/**
 * 将字符串转为整数；若为空或解析失败则返回默认值
 */
fun String.toIntOrDefault(default: Int): Int {
    if (this.isBlank()) return default
    if (this.trim().isEmpty()) return default
    return try {
        this.toInt()
    } catch (_: NumberFormatException) {
        default
    }
}
