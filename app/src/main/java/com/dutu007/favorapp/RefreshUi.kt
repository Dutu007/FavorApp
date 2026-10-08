package com.dutu007.favorapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

val RefreshIcon = ImageVector.Builder(
    name = "Refresh", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(20f, 7f)
        curveTo(17.8f, 3.2f, 12.8f, 2.5f, 8.9f, 4.7f)
        curveTo(5f, 6.9f, 3.5f, 11.6f, 5.1f, 15.5f)
        curveTo(6.8f, 19.6f, 11.5f, 21.4f, 15.7f, 19.4f)
        curveTo(18.2f, 18.2f, 19.8f, 15.8f, 20.2f, 13f)
        moveTo(20f, 3f); lineTo(20f, 7f); lineTo(16f, 7f)
    }
}.build()

val SettingsIcon = ImageVector.Builder(
    name = "Settings", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10f, 2.8f); lineTo(14f, 2.8f); lineTo(14.6f, 5.6f)
        lineTo(16.5f, 6.7f); lineTo(19.2f, 5.8f); lineTo(21.2f, 9.2f)
        lineTo(19.2f, 11.1f); lineTo(19.2f, 12.9f); lineTo(21.2f, 14.8f)
        lineTo(19.2f, 18.2f); lineTo(16.5f, 17.3f); lineTo(14.6f, 18.4f)
        lineTo(14f, 21.2f); lineTo(10f, 21.2f); lineTo(9.4f, 18.4f)
        lineTo(7.5f, 17.3f); lineTo(4.8f, 18.2f); lineTo(2.8f, 14.8f)
        lineTo(4.8f, 12.9f); lineTo(4.8f, 11.1f); lineTo(2.8f, 9.2f)
        lineTo(4.8f, 5.8f); lineTo(7.5f, 6.7f); lineTo(9.4f, 5.6f); close()
        moveTo(15.3f, 12f)
        arcTo(3.3f, 3.3f, 0f, true, true, 8.7f, 12f)
        arcTo(3.3f, 3.3f, 0f, true, true, 15.3f, 12f); close()
    }
}.build()

@Composable
fun RefreshingTitle(title: String, isRefreshing: Boolean) {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(isRefreshing) {
        dots = 1
        if (!isRefreshing) return@LaunchedEffect
        while (true) {
            delay(450)
            dots = dots % 3 + 1
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isRefreshing) {
            val style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light)
            val measurer = rememberTextMeasurer()
            val width = with(LocalDensity.current) { measurer.measure("（刷新中...）", style).size.width.toDp() }
            Spacer(Modifier.width(6.dp))
            Text(
                "（刷新中" + ".".repeat(dots) + "）",
                modifier = Modifier.width(width).alignByBaseline()
                    .clearAndSetSemantics { contentDescription = "刷新中" },
                style = style, maxLines = 1, softWrap = false,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val canRefresh by rememberUpdatedState(enabled && !isRefreshing)
    val refresh by rememberUpdatedState(onRefresh)
    // The gesture uses Material's nested scroll handling. Its loading feedback
    // is displayed by RefreshingTitle rather than a second floating indicator.
    Box(modifier.pullToRefresh(
        isRefreshing = isRefreshing, state = state, enabled = enabled,
        onRefresh = { if (canRefresh) refresh() },
    ), content = content)
}
