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
import me.xpyex.android.realrandommusic.ui.HyperOsSectionCard
import me.xpyex.android.realrandommusic.ui.HyperOsTopBar
import me.xpyex.android.realrandommusic.ui.theme.RealRandomMusicTheme
import me.xpyex.android.realrandommusic.util.MusicNotificationService

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
