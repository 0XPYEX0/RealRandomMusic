package me.xpyex.android.realrandommusic.activity

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import me.xpyex.android.realrandommusic.ui.BackNavigationIcon
import me.xpyex.android.realrandommusic.ui.theme.RealRandomMusicTheme
import me.xpyex.android.realrandommusic.util.ConfigManager

/**
 * 已安装应用信息
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val iconBitmap: Bitmap?
)

/**
 * 选择需要监听的应用
 */
class AppSelectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configManager = ConfigManager(this)
        val installedApps = getInstalledApps()

        setContent {
            RealRandomMusicTheme {
                AppSelectScreen(
                    apps = installedApps,
                    configManager = configManager
                )
            }
        }
    }

    /** 获取所有有桌面图标的 App（含系统预装的音乐 App，排除纯后台系统服务） */
    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val launcherPkgs = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName in launcherPkgs }
            .map {
                AppInfo(
                    packageName = it.packageName,
                    appName = it.loadLabel(pm).toString(),
                    iconBitmap = it.loadIcon(pm).toBitmap()
                )
            }
            .sortedBy { it.appName }
    }

    /** Drawable → Bitmap */
    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = if (intrinsicWidth > 0) intrinsicWidth else 96
        val h = if (intrinsicHeight > 0) intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(apps: List<AppInfo>, configManager: ConfigManager) {
    val whitelist = remember { configManager.getWhitelist() }

    var selected by remember {
        mutableStateOf(whitelist.toMutableSet())
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.appName.contains(searchQuery, ignoreCase = true)
                || it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择监听的应用") },
                navigationIcon = { BackNavigationIcon() }
            )
        }
    ) { padding ->
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("未找到第三方应用")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索应用名或包名") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    Text(
                        if (searchQuery.isBlank()) "勾选需要监听音乐播放的应用"
                        else "找到 ${filteredApps.size} 个应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                item {
                    Text(
                        "未勾选任何应用时，不监听音乐播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(filteredApps) { app ->
                    val isChecked = selected.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSelected = if (isChecked) {
                                    configManager.removeFromWhitelist(app.packageName)
                                    selected.minus(app.packageName).toMutableSet()
                                } else {
                                    configManager.addToWhitelist(app.packageName)
                                    selected.plus(app.packageName).toMutableSet()
                                }
                                selected = newSelected
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    configManager.addToWhitelist(app.packageName)
                                    selected = selected.plus(app.packageName).toMutableSet()
                                } else {
                                    configManager.removeFromWhitelist(app.packageName)
                                    selected = selected.minus(app.packageName).toMutableSet()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // App 图标
                        if (app.iconBitmap != null) {
                            Image(
                                bitmap = app.iconBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(app.appName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
