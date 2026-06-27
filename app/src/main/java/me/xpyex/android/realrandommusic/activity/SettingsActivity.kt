package me.xpyex.android.realrandommusic.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.xpyex.android.realrandommusic.ui.BackNavigationIcon
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { BackNavigationIcon() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = maxPlayCount,
                onValueChange = { maxPlayCount = it },
                label = { Text("每首歌最多播放次数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "1 = 只播 1 次就跳过；-1 = 不限制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = repeatInterval,
                onValueChange = { repeatInterval = it },
                label = { Text("重复间隔（秒）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "距上次播放需间隔的秒数；-1 = 不限制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = totalPlaylistSize,
                onValueChange = { totalPlaylistSize = it },
                label = { Text("歌单总歌曲数（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "歌单本轮播完后自动清空历史；-1 = 不启用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("调试模式", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后跳过歌曲时弹出通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = debugMode, onCheckedChange = { debugMode = it })
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("车载音乐兼容", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后仅使用歌手名作为歌曲标识",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = carMode, onCheckedChange = { carMode = it })
                }
            }

            // ── 选择监听的应用 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(Intent(context, AppSelectActivity::class.java))
                }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("选择监听的应用", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (selectedCount == 0) "未选择（不监听任何应用）"
                            else "已选择 $selectedCount 个应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(context, DebugActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("通知调试")
            }

            Button(
                onClick = {
                    configManager.maxPlayCount = maxPlayCount.toIntOrDefault(1)
                    configManager.repeatIntervalSeconds = repeatInterval.toIntOrDefault(-1)
                    configManager.setTotalPlaylistSize(totalPlaylistSize.toIntOrDefault(-1))
                    configManager.setDebugMode(debugMode)
                    configManager.setCarMode(carMode)

                    (context as? android.app.Activity)?.finish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }
        }
    }
}
