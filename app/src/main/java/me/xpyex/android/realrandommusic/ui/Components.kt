package me.xpyex.android.realrandommusic.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 通用返回按钮 —— 点击后 finish 当前 Activity
 */
@Composable
fun BackNavigationIcon() {
    val context = LocalContext.current
    IconButton(onClick = {
        (context as? android.app.Activity)?.finish()
    }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
}
