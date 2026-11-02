package com.johnny.wxscript.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/** 状态点：就绪 / 进行中 / 失败 / 未知。 */
enum class DotState { OK, RUNNING, FAIL, IDLE }

@Composable
fun StatusDot(state: DotState, modifier: Modifier = Modifier) {
    val color = when (state) {
        DotState.OK -> Color(0xFF2E7D32)
        DotState.RUNNING -> Color(0xFFF9A825)
        DotState.FAIL -> Color(0xFFC62828)
        DotState.IDLE -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier
            .size(9.dp)
            .background(color, CircleShape)
    )
}

/** 分区标题（列表内的 sticky header 也用它）。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 空数据 / 失败 / 加载中 —— 三种状态统一出口，避免各页各写一套。 */
@Composable
fun StateBox(
    emoji: String,
    title: String,
    detail: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
fun LoadingBox(text: String = "加载中…") {
    Row(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** 单列列表搜索框（名称 / 描述 / 作者 全覆盖由调用方过滤）。 */
@Composable
fun SearchField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "清空")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
    )
}

/** 两档分段控件（已启用 / 全部）。 */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
}

/**
 * 左滑露出操作按钮的行容器。
 *
 * 不依赖 material3 的 SwipeToDismissBox（各版本参数差异大），用最朴素的手势 + offset：
 * 手指横向拖动时内容层左移，最多露出 [revealWidth]；松手后按半程自动吸附。
 */
@Composable
fun SwipeRevealRow(
    revealWidth: androidx.compose.ui.unit.Dp = 156.dp,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxRevealPx = with(density) { revealWidth.toPx() }
    var offsetX by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(maxRevealPx) {
                    var dragging = false
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = false },
                        onDragEnd = {
                            offsetX = if (offsetX < -maxRevealPx / 2f) -maxRevealPx else 0f
                        },
                        onHorizontalDrag = { _, drag ->
                            offsetX = (offsetX + drag).coerceIn(-maxRevealPx, 0f)
                            if (drag < 0f) dragging = true
                        },
                    )
                },
        ) {
            content()
        }
    }
}
