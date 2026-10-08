package com.dutu007.favorapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dutu007.favorapp.data.AgreementItem
import com.dutu007.favorapp.data.CoupleSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

// Preserve the version seen when opening a record, including across rotation.
private val AgreementItemSaver = listSaver<AgreementItem?, String>(
    save = { item ->
        if (item == null) emptyList() else listOf(
            item.id, item.creatorId, item.creatorName, item.title, item.note,
            item.dueDate.orEmpty(), item.completed.toString(), item.completedAt.orEmpty(),
            item.createdAt, item.updatedAt, item.version.toString(),
        )
    },
    restore = { values ->
        if (values.isEmpty()) null else AgreementItem(
            id = values[0], creatorId = values[1], creatorName = values[2],
            title = values[3], note = values[4], dueDate = values[5].ifEmpty { null },
            completed = values[6].toBoolean(), completedAt = values[7].ifEmpty { null },
            createdAt = values[8], updatedAt = values[9], version = values[10].toInt(),
        )
    },
)

@Composable
fun AgreementScreen(
    state: AppUiState, snapshot: CoupleSnapshot, viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    var details by rememberSaveable(stateSaver = AgreementItemSaver) { mutableStateOf<AgreementItem?>(null) }
    var editing by rememberSaveable(stateSaver = AgreementItemSaver) { mutableStateOf<AgreementItem?>(null) }
    var deleting by rememberSaveable(stateSaver = AgreementItemSaver) { mutableStateOf<AgreementItem?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var createKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    // Runs on entering this destination and on returning to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadAgreements() }
    LaunchedEffect(state.agreementSavedFormKey, editorOpen, createKey) {
        if (editorOpen && state.agreementSavedFormKey == createKey) {
            editorOpen = false
            editing = null
            viewModel.clearAgreementConflict()
            viewModel.clearAgreementSavedFormEvent()
        }
    }
    LaunchedEffect(state.agreementDeletedId) {
        state.agreementDeletedId?.let { id ->
            if (deleting?.id == id) deleting = null
            if (details?.id == id) details = null
            viewModel.clearAgreementDeletedEvent()
        }
    }

    fun openEditor(item: AgreementItem?) {
        editing = item
        createKey = UUID.randomUUID().toString()
        viewModel.clearAgreementError()
        viewModel.clearAgreementConflict()
        editorOpen = true
    }
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("把想一起做的事，一件件变成回忆", color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AgreementCount("待完成", state.agreementsPendingCount, Modifier.weight(1f))
                        AgreementCount("已完成", state.agreementsCompletedCount, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = !state.agreementsCompletedFilter,
                            onClick = { viewModel.selectAgreementFilter(false) },
                            label = { Text("待完成") }, enabled = !state.agreementsBusy,
                        )
                        FilterChip(
                            selected = state.agreementsCompletedFilter,
                            onClick = { viewModel.selectAgreementFilter(true) },
                            label = { Text("已完成") }, enabled = !state.agreementsBusy,
                        )
                    }
                }
            }
            if (state.notice != null && details == null && !editorOpen && deleting == null) {
                item(key = "agreement_notice", span = { GridItemSpan(maxLineSpan) }) {
                    InlineNotice(state, viewModel, Modifier.fillMaxWidth())
                }
            }
            if (state.agreementsLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (state.agreements.isEmpty() && !state.agreementsLoading && state.agreementsError == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val allFinished = !state.agreementsCompletedFilter && state.agreementsCompletedCount > 0
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("♡", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (allFinished) "这些约定，都变成了我们的回忆"
                                else if (state.agreementsCompletedFilter) "还没有完成的约定"
                                else "写下第一件想一起做的事吧",
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (state.agreementsCompletedFilter) "一起完成后，回忆会留在这里"
                                else "看海、做一顿饭，或一起度过普通的一天",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                            )
                            if (!state.agreementsCompletedFilter) {
                                TextButton(onClick = { openEditor(null) }, enabled = !state.agreementsBusy) { Text("添加约定") }
                            }
                        }
                    }
                }
            }
            items(state.agreements, key = { it.id }) { item ->
                AgreementCard(
                    item, snapshot, enabled = !state.agreementsBusy && !state.agreementsLoading,
                    onOpen = { details = item },
                    onComplete = { viewModel.setAgreementCompleted(item, !item.completed) },
                )
            }
            if (state.agreementsHasMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(
                        onClick = { viewModel.loadAgreements(refresh = false) },
                        enabled = !state.agreementsLoading && !state.agreementsLoadingMore && !state.agreementsBusy,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    ) {
                        if (state.agreementsLoadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("加载更多约定")
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (!state.agreementsBusy) openEditor(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Text("＋", style = MaterialTheme.typography.titleLarge) },
            text = { Text("添加约定") }, containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
    details?.let { original ->
        val item = latestAgreement(original, state)
        AgreementDetails(
            item, snapshot, state.agreementsBusy,
            onDismiss = { details = null },
            onEdit = { details = null; openEditor(item) },
            onComplete = { details = null; viewModel.setAgreementCompleted(item, !item.completed) },
            onDelete = { viewModel.clearAgreementError(); viewModel.clearAgreementConflict(); deleting = item },
            notice = {
                if (!editorOpen && deleting == null) InlineNotice(state, viewModel, Modifier.fillMaxWidth())
            },
        )
    }
    if (editorOpen) {
        val source = editing
        val latest = (source ?: state.agreementConflictItem)?.let { latestAgreement(it, state) }
        AgreementEditor(
            formKey = createKey, item = source, latest = latest, busy = state.agreementsBusy,
            onDismiss = {
                editorOpen = false; editing = null
                viewModel.clearAgreementError(); viewModel.clearAgreementConflict()
            },
            onUseLatest = { editing = it; viewModel.clearAgreementError(); viewModel.clearAgreementConflict() },
            onSave = { title, note, date ->
                viewModel.saveAgreement(source, title, note, date.ifBlank { null }, createKey)
            },
            notice = {
                if (deleting == null) InlineNotice(state, viewModel, Modifier.fillMaxWidth())
            },
        )
    }
    deleting?.let { item ->
        val latest = latestAgreement(item, state).takeIf { it.version > item.version }
        AlertDialog(
            onDismissRequest = { if (!state.agreementsBusy) deleting = null },
            title = { Text("删除约定") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InlineNotice(state, viewModel, Modifier.fillMaxWidth())
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text("删除后，你们双方的清单都会移除这条约定，且无法恢复。")
                    if (latest != null) {
                        Text("更新后的约定：${latest.title}", fontWeight = FontWeight.SemiBold)
                        Text(latest.note, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { deleting = latest; viewModel.clearAgreementError(); viewModel.clearAgreementConflict() }) {
                            Text("已查看更新，重新确认")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAgreement(item) },
                    enabled = !state.agreementsBusy && latest == null,
                ) { Text(if (state.agreementsBusy) "正在删除…" else "确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null; viewModel.clearAgreementError(); viewModel.clearAgreementConflict() }, enabled = !state.agreementsBusy) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AgreementCount(label: String, count: Int, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.9f)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Text("$count 件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AgreementCard(
    item: AgreementItem, snapshot: CoupleSnapshot, enabled: Boolean,
    onOpen: () -> Unit, onComplete: () -> Unit,
) {
    ElevatedCard(
        onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.94f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                item.title, Modifier.heightIn(min = 44.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                item.note.ifBlank { "留一点期待，慢慢一起实现" },
                Modifier.heightIn(min = 60.dp), maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (item.completed) "完成于 ${agreementTime(item.completedAt, short = true)}"
                else item.dueDate?.let { "希望 $it" } ?: "日期待定",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${agreementCreator(item, snapshot)}添加",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.completed, onCheckedChange = { onComplete() }, enabled = enabled)
                Text(if (item.completed) "已完成" else "完成", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgreementDetails(
    item: AgreementItem, snapshot: CoupleSnapshot, busy: Boolean,
    onDismiss: () -> Unit, onEdit: () -> Unit, onComplete: () -> Unit, onDelete: () -> Unit,
    notice: @Composable () -> Unit,
) {
    AgreementPageDialog("约定详情", busy, onDismiss, topAction = {
        TextButton(onClick = onEdit, enabled = !busy) { Text("编辑") }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            notice()
            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (item.completed) "已完成 · ${agreementTime(item.completedAt)}" else "待完成", color = MaterialTheme.colorScheme.primary)
            Text(item.dueDate?.let { "希望完成日期：$it" } ?: "希望完成日期：暂未约定", color = MaterialTheme.colorScheme.secondary)
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("备注", fontWeight = FontWeight.SemiBold)
                    SelectionContainer { Text(item.note.ifBlank { "还没有备注" }, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            Text("${agreementCreator(item, snapshot)}添加于 ${agreementTime(item.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Button(onClick = onComplete, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(if (item.completed) "恢复为待完成" else "我们完成啦")
            }
            OutlinedButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text("删除约定", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgreementEditor(
    formKey: String, item: AgreementItem?, latest: AgreementItem?, busy: Boolean,
    onDismiss: () -> Unit, onUseLatest: (AgreementItem) -> Unit,
    onSave: (String, String, String) -> Unit,
    notice: @Composable () -> Unit,
) {
    var title by rememberSaveable(formKey) { mutableStateOf(item?.title.orEmpty()) }
    var note by rememberSaveable(formKey) { mutableStateOf(item?.note.orEmpty()) }
    var date by rememberSaveable(formKey) { mutableStateOf(item?.dueDate.orEmpty()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val titleLength = title.codePointCount(0, title.length)
    val noteLength = note.codePointCount(0, note.length)
    val outdated = latest != null && (item == null || latest.version > item.version)
    val changed = title != item?.title.orEmpty() || note != item?.note.orEmpty() || date != item?.dueDate.orEmpty()
    val close = { if (changed) confirmDiscard = true else onDismiss() }
    AgreementPageDialog(if (item == null) "添加约定" else "编辑约定", busy, close) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            notice()
            Text("记下已经商量好、想一起完成的事情", color = MaterialTheme.colorScheme.secondary)
            if (outdated) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("对方更新了这条约定，请选择后再保存。", fontWeight = FontWeight.SemiBold)
                        Text("最新标题：${latest.title}", style = MaterialTheme.typography.bodySmall)
                        Text(latest.note, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text((if (latest.completed) "已完成" else "待完成") + " · " + (latest.dueDate ?: "日期待定"), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            title = latest.title; note = latest.note; date = latest.dueDate.orEmpty()
                            onUseLatest(latest)
                        }, enabled = !busy) { Text("使用最新内容") }
                        TextButton(onClick = { onUseLatest(latest) }, enabled = !busy) { Text("保留我的内容继续编辑") }
                    }
                }
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("事情标题") }, placeholder = { Text("例如：一起去看海") },
                singleLine = true, enabled = !busy, shape = RoundedCornerShape(16.dp),
                isError = titleLength > AGREEMENT_TITLE_MAX_LENGTH,
                supportingText = { Text("$titleLength / $AGREEMENT_TITLE_MAX_LENGTH 字") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                label = { Text("备注（可选）") }, placeholder = { Text("写下想去的地方、计划或想对 TA 说的话…") },
                singleLine = false, minLines = 6, maxLines = 12, enabled = !busy, shape = RoundedCornerShape(16.dp),
                isError = noteLength > AGREEMENT_NOTE_MAX_LENGTH,
                supportingText = { Text("$noteLength / $AGREEMENT_NOTE_MAX_LENGTH 字，可换行") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            )
            OutlinedButton(
                onClick = { showPicker = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            ) { Text(if (date.isBlank()) "选择希望完成日期（可选）" else "希望完成日期：$date") }
            if (date.isNotBlank()) TextButton(onClick = { date = "" }, enabled = !busy) { Text("清除日期，暂不安排") }
            Button(
                onClick = { onSave(title, note, date) },
                enabled = !busy && !outdated && title.isNotBlank() && titleLength <= AGREEMENT_TITLE_MAX_LENGTH && noteLength <= AGREEMENT_NOTE_MAX_LENGTH,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("保存约定", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (showPicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    date = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }.orEmpty()
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) { DatePicker(state = picker) }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") }, text = { Text("填写的内容还没有保存。") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续填写") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgreementPageDialog(
    title: String, busy: Boolean, onDismiss: () -> Unit,
    topAction: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = true, dismissOnClickOutside = false,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = { TextButton(onClick = onDismiss, enabled = !busy) { Text("返回") } },
                        actions = topAction,
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 560.dp).fillMaxSize()) { content(padding) }
                }
            }
        }
    }
}

// A detail fetch can finish after the list snapshot. Never replace an accepted
// version with an older cached record after clearing the conflict notice.
private fun latestAgreement(item: AgreementItem, state: AppUiState): AgreementItem =
    listOfNotNull(
        item,
        state.agreements.firstOrNull { it.id == item.id },
        state.agreementConflictItem?.takeIf { it.id == item.id },
    ).maxBy { it.version }

private fun agreementCreator(item: AgreementItem, snapshot: CoupleSnapshot): String =
    if (item.creatorId == snapshot.currentUserId) "我"
    else snapshot.partnerNickname.ifBlank { item.creatorName.ifBlank { snapshot.partnerName } }

private fun agreementTime(value: String?, short: Boolean = false): String {
    if (value == null) return "刚刚"
    return runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(if (short) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm"))
    }.getOrDefault(value.take(10))
}
