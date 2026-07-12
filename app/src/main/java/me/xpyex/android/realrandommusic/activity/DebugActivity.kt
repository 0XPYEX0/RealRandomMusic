package me.xpyex.android.realrandommusic.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.xpyex.android.realrandommusic.RrmApp
import me.xpyex.android.realrandommusic.ui.HyperOsSectionCard
import me.xpyex.android.realrandommusic.ui.HyperOsTopBar
import me.xpyex.android.realrandommusic.ui.theme.RealRandomMusicTheme
import me.xpyex.android.realrandommusic.util.MusicNotificationService
import java.text.SimpleDateFormat
import java.util.*

class DebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RealRandomMusicTheme {
                DebugScreen()
            }
        }
    }
}

@Composable
fun DebugScreen() {
    val context = LocalContext.current
    var eventLog by remember { mutableStateOf(emptyList<String>()) }
    var lastRefresh by remember { mutableStateOf(System.currentTimeMillis()) }

    Scaffold(
        topBar = {
            HyperOsTopBar(
                title = "通知调试",
                showBack = true,
                actions = {
                    TextButton(onClick = {
                        MusicNotificationService.clearEventLog()
                        eventLog = emptyList()
                    }) {
                        Text("清空")
                    }
                    TextButton(onClick = {
                        val text = eventLog.joinToString("\n")
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("debug log", text))
                    }) {
                        Text("复制")
                    }
                    TextButton(onClick = {
                        eventLog = MusicNotificationService.getEventLog()
                        lastRefresh = System.currentTimeMillis()
                    }) {
                        Text("刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LaunchedEffect(Unit) {
                eventLog = MusicNotificationService.getEventLog()
            }

            val serviceAlive = MusicNotificationService.getCurrentPlayback() != null
                    || eventLog.isNotEmpty()

            // 服务状态卡片
            HyperOsSectionCard(
                containerColor = if (serviceAlive)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    if (serviceAlive) "Service: 运行中" else "Service: 可能未启动 — 检查通知权限",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // ── 历史记录状态卡片 ──
            val history = remember { RrmApp.instance.historyManager }
            val loadError = history.lastLoadError
            val saveError = history.lastSaveError
            val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

            HyperOsSectionCard(
                containerColor = if (loadError != null || saveError != null)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surface,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "历史记录 (played_songs.json)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "已记录: ${history.playedCount} 首  |  已跳过: ${history.skippedCount} 次",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "文件大小: ${if (history.fileSize > 0) "${history.fileSize / 1024} KB" else "无文件"}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (history.lastSaveTime > 0) {
                        Text(
                            "最近保存: ${dateFormat.format(Date(history.lastSaveTime))}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (loadError != null) {
                        Text(
                            "⚠ 加载失败: $loadError",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (saveError != null) {
                        Text(
                            "⚠ 保存失败: $saveError",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (loadError == null && saveError == null) {
                        Text(
                            "✓ JSON 读写正常",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            Text(
                "事件日志 (最近 ${eventLog.size} 条) — 刷新: ${lastRefresh}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 日志卡片
            HyperOsSectionCard {
                Column {
                    if (eventLog.isEmpty()) {
                        Text(
                            "暂无日志 — 播放音乐后刷新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    eventLog.forEach { line ->
                        val color = when {
                            line.contains("回调") -> MaterialTheme.colorScheme.primary
                            line.contains("服务创建") -> MaterialTheme.colorScheme.tertiary
                            line.contains("活跃会话") || line.contains("通知监听已连接") ->
                                MaterialTheme.colorScheme.tertiary

                            line.contains("获取:") -> MaterialTheme.colorScheme.onSurface
                            line.contains("无") -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
