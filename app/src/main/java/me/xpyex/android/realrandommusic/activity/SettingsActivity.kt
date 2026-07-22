package me.xpyex.android.realrandommusic.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.xpyex.android.realrandommusic.ui.*
import me.xpyex.android.realrandommusic.ui.theme.RealRandomMusicTheme
import me.xpyex.android.realrandommusic.util.ConfigManager
import me.xpyex.android.realrandommusic.util.toIntOrDefault

class SettingsActivity : ComponentActivity() {

    private lateinit var configManager: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configManager = ConfigManager(this)

        setContent {
            RealRandomMusicTheme {
                SettingsScreen(configManager = configManager)
            }
        }
    }
}

@Composable
fun SettingsScreen(configManager: ConfigManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedCount by remember { mutableIntStateOf(configManager.getWhitelist().size) }

    // 从 AppSelectActivity 返回后刷新勾选数量
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedCount = configManager.getWhitelist().size
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var maxPlayCount by remember {
        mutableStateOf(configManager.maxPlayCount.toString())
    }
    var repeatInterval by remember {
        mutableStateOf(configManager.getRepeatIntervalSeconds().toString())
    }
    var totalPlaylistSize by remember {
        mutableStateOf(configManager.getTotalPlaylistSize().toString())
    }
    var debugMode by remember { mutableStateOf(configManager.isDebugMode()) }
    var carMode by remember { mutableStateOf(configManager.isCarMode()) }
    var allowManualSkip by remember { mutableStateOf(configManager.isAllowManualSkip) }

    Scaffold(
        topBar = {
            HyperOsTopBar(title = "设置", showBack = true)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 检测规则（同组）
            HyperOsSectionCard {
                HyperOsTextField(
                    value = maxPlayCount,
                    onValueChange = { maxPlayCount = it },
                    label = { Text("每首歌最多播放次数") },
                    singleLine = true,
                )
                Text(
                    "1 = 只播 1 次就跳过；-1 = 不限制",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )
                HyperOsTextField(
                    value = repeatInterval,
                    onValueChange = { repeatInterval = it },
                    label = { Text("重复间隔（秒）") },
                    singleLine = true,
                )
                Text(
                    "距上次播放需间隔的秒数；-1 = 不限制",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )
                HyperOsTextField(
                    value = totalPlaylistSize,
                    onValueChange = { totalPlaylistSize = it },
                    label = { Text("歌单总歌曲数（可选）") },
                    singleLine = true,
                )
                Text(
                    "歌单本轮播完后自动清空历史；-1 = 不启用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // 车载音乐兼容 + 允许手动跳过（同组）
            HyperOsSectionCard {
                HyperOsSettingRowInline(
                    title = "车载音乐兼容",
                    description = "开启后仅使用歌手名作为歌曲标识",
                ) {
                    Switch(checked = carMode, onCheckedChange = { carMode = it })
                }
                Spacer(modifier = Modifier.height(12.dp))
                HyperOsSettingRowInline(
                    title = "允许手动跳过",
                    description = "通过歌曲播放进度判断自动切歌\n若手动切歌则不跳过",
                ) {
                    Switch(checked = allowManualSkip, onCheckedChange = { allowManualSkip = it })
                }
            }

            // 选择监听的应用
            HyperOsSectionCard(
                onClick = {
                    context.startActivity(Intent(context, AppSelectActivity::class.java))
                }
            ) {
                Column {
                    Text("选择监听的应用", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (selectedCount == 0) "未选择（不监听任何应用）"
                        else "已选择 $selectedCount 个应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HyperOsPrimaryButton(onClick = {
                configManager.maxPlayCount = maxPlayCount.toIntOrDefault(1)
                configManager.repeatIntervalSeconds = repeatInterval.toIntOrDefault(-1)
                configManager.setTotalPlaylistSize(totalPlaylistSize.toIntOrDefault(-1))
                configManager.setDebugMode(debugMode)
                configManager.setCarMode(carMode)
                configManager.setAllowManualSkip(allowManualSkip)

                (context as? android.app.Activity)?.finish()
            }) {
                Text("保存设置")
            }

            // 调试模式（放最下面）
            HyperOsSectionCard {
                HyperOsSettingRowInline(
                    title = "调试模式",
                    description = "开启后跳过歌曲时弹出通知",
                ) {
                    Switch(checked = debugMode, onCheckedChange = { debugMode = it })
                }
                if (debugMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HyperOsSecondaryButton(onClick = {
                        context.startActivity(Intent(context, DebugActivity::class.java))
                    }) {
                        Text("通知调试")
                    }
                }
            }
        }
    }
}
