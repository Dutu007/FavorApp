package com.dutu007.favorapp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// One timer at the app root; changing pages never restarts the same notice.
@Composable
fun NoticeExpiryEffect(notice: AppNotice?, onDismiss: (String) -> Unit) {
    notice ?: return
    val accessibilityManager = LocalAccessibilityManager.current
    val baseTimeout = if (notice.isError || notice.undoAgreement != null) 5_000L else 3_000L
    val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = baseTimeout,
        containsIcons = false,
        containsText = true,
        containsControls = true,
    ) ?: baseTimeout
    val dismiss = rememberUpdatedState(onDismiss)
    LaunchedEffect(notice.id, timeout) {
        delay(timeout)
        dismiss.value(notice.id)
    }
}

@Composable
fun InlineNotice(state: AppUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val notice = state.notice ?: return
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = if (notice.isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (notice.isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                notice.text,
                modifier = Modifier.weight(1f).padding(vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (notice.undoAgreement != null) {
                TextButton(onClick = viewModel::undoLastAgreementCompletion, enabled = !state.agreementsBusy) {
                    Text("撤销")
                }
            }
            TextButton(onClick = { viewModel.dismissNotice(notice.id) }) { Text("×", fontSize = 20.sp) }
        }
    }
}
