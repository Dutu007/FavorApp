package com.dutu007.favorapp

import android.graphics.Color
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.delay

@Composable
fun GlobalNoticeHost(
    notice: AppNotice?,
    onDismiss: (String) -> Unit,
    onUndo: () -> Unit,
    undoEnabled: Boolean,
) {
    notice ?: return
    val hasUndo = notice.undoAgreement != null
    val accessibilityManager = LocalAccessibilityManager.current
    val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = if (notice.isError || hasUndo) 5_000L else 3_000L,
        containsIcons = false,
        containsText = true,
        containsControls = hasUndo,
    ) ?: if (notice.isError || hasUndo) 5_000L else 3_000L
    val dismiss = rememberUpdatedState(onDismiss)
    LaunchedEffect(notice.id, timeout) {
        delay(timeout)
        dismiss.value(notice.id)
    }

    // Configure the window before show(): a Compose Dialog's content is first
    // composed after attachment, too late to prevent its initial focus change.
    key(notice.id) {
        val context = LocalContext.current
        val parentComposition = rememberCompositionContext()
        val currentNotice = rememberUpdatedState(notice)
        val currentUndo = rememberUpdatedState(onUndo)
        val currentUndoEnabled = rememberUpdatedState(undoEnabled)
        val noticeWindow = remember(context, parentComposition) {
            val dialog = ComponentDialog(context, android.R.style.Theme_Material_Light_Dialog_NoActionBar)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            requireNotNull(dialog.window).apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setGravity(Gravity.CENTER)
                setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                )
                clearFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                )
                if (!hasUndo) addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                )
                attributes = attributes.apply { dimAmount = 0f }
            }
            val content = ComposeView(context).apply {
                setParentCompositionContext(parentComposition)
                setContent {
                    NoticeCard(
                        notice = currentNotice.value,
                        onDismiss = { dismiss.value(it) },
                        onUndo = { currentUndo.value() },
                        undoEnabled = currentUndoEnabled.value,
                    )
                }
            }
            // ComponentDialog supplies lifecycle and saved-state owners to its
            // decor view before attaching the ComposeView.
            dialog.setContentView(content)
            dialog to content
        }
        // The root renders this host after modal dialogs. Matching their show
        // effect keeps a newly created notice above a modal from the same frame.
        LaunchedEffect(noticeWindow) { noticeWindow.first.show() }
        DisposableEffect(noticeWindow) {
            onDispose {
                val (dialog, content) = noticeWindow
                dialog.dismiss()
                content.disposeComposition()
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: AppNotice,
    onDismiss: (String) -> Unit,
    onUndo: () -> Unit,
    undoEnabled: Boolean,
) {
    Surface(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = if (notice.isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (notice.isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(notice.text, style = MaterialTheme.typography.bodyMedium)
            if (notice.undoAgreement != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onUndo, enabled = undoEnabled) { Text("撤销") }
                    TextButton(onClick = { onDismiss(notice.id) }) { Text("关闭") }
                }
            }
        }
    }
}
