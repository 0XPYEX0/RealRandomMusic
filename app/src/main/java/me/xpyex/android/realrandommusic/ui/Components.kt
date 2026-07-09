package me.xpyex.android.realrandommusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ──────────────────────────────────────────────
// 通用返回按钮
// ──────────────────────────────────────────────

@Composable
fun BackNavigationIcon() {
    val context = LocalContext.current
    IconButton(onClick = {
        (context as? android.app.Activity)?.finish()
    }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
}

// ──────────────────────────────────────────────
// MIUI 区段卡片 — 白底，微阴影，在浅灰页面上形成层次
// ──────────────────────────────────────────────

@Composable
fun HyperOsSectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    val shape = MaterialTheme.shapes.medium

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            elevation = elevation,
            shape = shape,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            elevation = elevation,
            shape = shape,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

// ──────────────────────────────────────────────
// MIUI 设置行：标题 + 描述 + 尾部控件（蓝色开关等）
// ──────────────────────────────────────────────

@Composable
fun HyperOsSettingRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    HyperOsSectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            control()
        }
    }
}

// ──────────────────────────────────────────────
// MIUI 顶栏 — 白底，较大标题
// ──────────────────────────────────────────────

@Composable
fun HyperOsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            BackNavigationIcon()
            Spacer(modifier = Modifier.size(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

// ──────────────────────────────────────────────
// MIUI 主按钮 — 蓝底白字，圆角 24dp
// ──────────────────────────────────────────────

@Composable
fun HyperOsPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        content = content,
    )
}

// ──────────────────────────────────────────────
// MIUI 次按钮 — 蓝色描边，圆角 24dp
// ──────────────────────────────────────────────

@Composable
fun HyperOsSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = containerColor,
        ),
        content = content,
    )
}

// ──────────────────────────────────────────────
// MIUI 文本输入 — 白底，灰色描边，聚焦时变蓝
// ──────────────────────────────────────────────

@Composable
fun HyperOsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        shape = MaterialTheme.shapes.small,
    )
}
