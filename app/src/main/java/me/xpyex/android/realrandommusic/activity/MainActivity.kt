package me.xpyex.android.realrandommusic.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.xpyex.android.realrandommusic.RrmApp
import me.xpyex.android.realrandommusic.ui.theme.RealRandomMusicTheme

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

// ── UI ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = RrmApp.instance
    val historyManager = app.historyManager

    var permissionGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var paused by remember { mutableStateOf(app.isPaused) }
    var playedCount by remember { mutableIntStateOf(historyManager.playedCount) }
    var skippedCount by remember { mutableIntStateOf(historyManager.skippedCount) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            permissionGranted = isNotificationListenerEnabled(context)
            playedCount = historyManager.playedCount
            skippedCount = historyManager.skippedCount
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("RealRandomMusic") }
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
            // 监听权限状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (permissionGranted)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (permissionGranted) "监听已开启" else "需要通知监听权限",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (permissionGranted)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (permissionGranted)
                                "正在监听音乐播放通知"
                            else
                                "请在系统设置中授予通知读取权限",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (permissionGranted)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }

                    if (!permissionGranted) {
                        Button(
                            onClick = { openNotificationAccessSettings(context) }
                        ) {
                            Text("去授权")
                        }
                    }
                }
            }

            Text(
                text = "真·随机播放",
                style = MaterialTheme.typography.headlineSmall
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("功能说明", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "• 自动检测重复播放的歌曲\n• 通过通知监控音乐播放\n• 兼容所有音乐软件",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("统计信息", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已记录歌曲: $playedCount 首",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "已跳过重复: $skippedCount 次",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Button(
                        onClick = {
                            historyManager.clearAll()
                            playedCount = 0
                            skippedCount = 0
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清空历史记录")
                    }
                }
            }

            Button(
                onClick = {
                    paused = !paused
                    app.setPaused(paused)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (paused)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (paused) "已暂停 — 点击继续" else "工作中 — 点击暂停")
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("设置")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("使用提示", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. 首次使用需授权通知监听权限\n2. 打开任意音乐应用播放歌曲\n3. 检测到重复音乐时自动跳过\n4. 进入设置调整检测规则",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
