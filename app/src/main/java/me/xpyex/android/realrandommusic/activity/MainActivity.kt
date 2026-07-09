package me.xpyex.android.realrandommusic.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.xpyex.android.realrandommusic.RrmApp
import me.xpyex.android.realrandommusic.ui.HyperOsSecondaryButton
import me.xpyex.android.realrandommusic.ui.HyperOsSectionCard
import me.xpyex.android.realrandommusic.ui.HyperOsTopBar
import me.xpyex.android.realrandommusic.ui.theme.RealRandomMusicTheme
import me.xpyex.android.realrandommusic.util.MusicNotificationService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RealRandomMusicTheme {
                MainScreen()
            }
        }
    }
}

// ── 权限检测 ──

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.contains(context.packageName) == true
}

fun openNotificationAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

// ── MiiuX 风格打勾圆圈（右下角装饰图案）──

@Composable
private fun CheckmarkCircle(
    accentColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val ringWidth = w * 0.09f
        val checkWidth = w * 0.15f

        drawCircle(color = fillColor)
        drawCircle(color = accentColor, style = Stroke(width = ringWidth))

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.52f)
            lineTo(w * 0.43f, h * 0.70f)
            lineTo(w * 0.75f, h * 0.30f)
        }
        drawPath(path, color = accentColor, style = Stroke(width = checkWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun PauseCircle(
    accentColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val ringWidth = w * 0.09f
        val barWidth = w * 0.12f
        val barHeight = h * 0.50f
        val barTop = (h - barHeight) / 2f

        drawCircle(color = fillColor)
        drawCircle(color = accentColor, style = Stroke(width = ringWidth))

        // 左竖条
        drawRoundRect(
            color = accentColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.32f - barWidth / 2, barTop),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
        )
        // 右竖条
        drawRoundRect(
            color = accentColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.68f - barWidth / 2, barTop),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
        )
    }
}

@Composable
private fun CrossCircle(
    accentColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val ringWidth = w * 0.09f
        val crossWidth = w * 0.14f
        val inset = w * 0.27f

        drawCircle(color = fillColor)
        drawCircle(color = accentColor, style = Stroke(width = ringWidth))

        // X 的两条线
        drawLine(
            color = accentColor,
            start = androidx.compose.ui.geometry.Offset(inset, inset),
            end = androidx.compose.ui.geometry.Offset(w - inset, h - inset),
            strokeWidth = crossWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accentColor,
            start = androidx.compose.ui.geometry.Offset(w - inset, inset),
            end = androidx.compose.ui.geometry.Offset(inset, h - inset),
            strokeWidth = crossWidth,
            cap = StrokeCap.Round,
        )
    }
}

// ── UI ──

/** 状态按钮的三种配色 */
private enum class StatusIcon { CHECK, PAUSE, CROSS }

private data class StatusButtonStyle(
    val bgColor: Color,
    val accentColor: Color,
    val title: String,
    val subtitle: String,
    val icon: StatusIcon,
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = RrmApp.instance
    val historyManager = app.historyManager

    var permissionGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var paused by remember { mutableStateOf(app.isPaused) }
    var playedCount by remember { mutableIntStateOf(historyManager.playedCount) }
    var skippedCount by remember { mutableIntStateOf(historyManager.skippedCount) }
    var currentSong by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }
    var clearTapCount by remember { mutableIntStateOf(0) }

    // 每秒刷新 UI 状态
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            permissionGranted = isNotificationListenerEnabled(context)
            playedCount = historyManager.playedCount
            skippedCount = historyManager.skippedCount

            val playback = MusicNotificationService.getCurrentPlayback()
            currentSong = if (playback != null) {
                val artist = playback.artist
                val title = playback.title
                if (!artist.isNullOrEmpty()) "$artist - $title" else title ?: ""
            } else ""
        }
    }

    // 三击清空计数器超时重置
    LaunchedEffect(clearTapCount) {
        if (clearTapCount > 0) {
            kotlinx.coroutines.delay(2000)
            clearTapCount = 0
        }
    }

    // 左侧状态按钮样式
    val statusStyle = when {
        !permissionGranted -> StatusButtonStyle(
            bgColor = Color(0xFFFFE5E5),
            accentColor = Color(0xFFDF6353),
            title = "无权限",
            subtitle = "点击授权",
            icon = StatusIcon.CROSS,
        )
        paused -> StatusButtonStyle(
            bgColor = Color(0xFFFFF0E0),
            accentColor = Color(0xFFE0883A),
            title = "已暂停",
            subtitle = "点击继续",
            icon = StatusIcon.PAUSE,
        )
        else -> StatusButtonStyle(
            bgColor = Color(0xFFDEF9E3),
            accentColor = Color(0xFF35D267),
            title = "工作中",
            subtitle = "点击暂停",
            icon = StatusIcon.CHECK,
        )
    }

    // ── 关于弹窗 ──
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Text("🎵", style = MaterialTheme.typography.headlineLarge)
            },
            title = {
                Text("关于 RealRandomMusic")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "RealRandomMusic 是一款音乐防重复播放工具\n" +
                                "通过监听系统媒体通知，自动检测并跳过重复播放的歌曲\n" +
                                "理论上兼容所有主流音乐应用。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "支持选择应用，和小部分全局自定义",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) {
                    ""
                }
            }
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "RealRandomMusic",
                    style = MaterialTheme.typography.headlineLarge,
                )
                versionName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        containerColor = Color(0xFFF5F5F5),  // 淡淡淡灰背景
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ════════════════════════════════════════════
            // 「口吕」布局：左侧正方形 + 右侧两个矩形
            // ════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),  // 宽:高=2:1 → 高度=半屏宽=正方形边长
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 左侧正方形按钮 ──
                Card(
                    onClick = {
                        if (!permissionGranted) {
                            openNotificationAccessSettings(context)
                        } else {
                            paused = !paused
                            app.setPaused(paused)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = statusStyle.bgColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = statusStyle.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.Black,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = statusStyle.subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black.copy(alpha = 0.55f),
                            )
                        }

                        // 右下角状态图标
                        val iconModifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(44.dp)
                        when (statusStyle.icon) {
                            StatusIcon.CHECK -> CheckmarkCircle(
                                accentColor = statusStyle.accentColor,
                                fillColor = statusStyle.bgColor,
                                modifier = iconModifier,
                            )
                            StatusIcon.PAUSE -> PauseCircle(
                                accentColor = statusStyle.accentColor,
                                fillColor = statusStyle.bgColor,
                                modifier = iconModifier,
                            )
                            StatusIcon.CROSS -> CrossCircle(
                                accentColor = statusStyle.accentColor,
                                fillColor = statusStyle.bgColor,
                                modifier = iconModifier,
                            )
                        }
                    }
                }

                // ── 右侧两个矩形卡片 ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 上方矩形：已记录歌曲（三击清空历史）
                    HyperOsSectionCard(
                        modifier = Modifier.weight(1f),
                        containerColor = Color.White,
                        onClick = {
                            clearTapCount++
                            if (clearTapCount >= 3) {
                                historyManager.clearAll()
                                playedCount = 0
                                skippedCount = 0
                                clearTapCount = 0
                                Toast.makeText(context, "历史记录已清空", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "已记录(点三次清空)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "$playedCount 首",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "已跳过 $skippedCount 次重复",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 下方矩形：当前播放
                    HyperOsSectionCard(
                        modifier = Modifier.weight(1f),
                        containerColor = Color.White,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "当前播放",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentSong.ifEmpty { "暂无播放" },
                                style = if (currentSong.isNotEmpty())
                                    MaterialTheme.typography.titleMedium
                                else
                                    MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════
            // 底部按钮
            // ════════════════════════════════════════════
            HyperOsSecondaryButton(
                onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                containerColor = Color.White,
            ) {
                Text("⚙ 设置")
            }

            HyperOsSecondaryButton(
                onClick = { showAboutDialog = true },
                containerColor = Color.White,
            ) {
                Text("ℹ 关于")
            }

            // ════════════════════════════════════════════
            // 使用提示
            // ════════════════════════════════════════════
            HyperOsSectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("使用提示", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. 首次使用需授权通知监听权限\n" +
                                "2. 打开任意音乐应用播放歌曲\n" +
                                "3. 检测到重复音乐时自动跳过\n" +
                                "4. 进入设置调整检测规则\n" +
                                "5.⭐请关闭应用省电、允许自启动、在最近任务里锁定应用，防止被系统杀死",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── GitHub ──
            Card(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/XPYEX/RealRandomMusic"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("GitHub", style = MaterialTheme.typography.titleLarge)
                    Text("🖇", style = MaterialTheme.typography.headlineLarge)
                }
            }

            // ── QQ ──
            Card(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/VAQdqEW6Ms"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("QQ 1723275529", style = MaterialTheme.typography.titleLarge)
                    Text("🖇", style = MaterialTheme.typography.headlineLarge)
                }
            }
        }
    }
}
