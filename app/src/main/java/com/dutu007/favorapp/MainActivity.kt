package com.dutu007.favorapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dutu007.favorapp.data.CoupleSnapshot
import com.dutu007.favorapp.data.PendingRuleRequest
import com.dutu007.favorapp.data.ScoreEventItem
import com.dutu007.favorapp.data.ScorePreset
import com.dutu007.favorapp.ui.theme.FavorTheme
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val PinkGradient = Brush.verticalGradient(
    listOf(Color(0xFFFFE0ED), Color(0xFFF5EEFC), Color(0xFFFFFAFC)),
)

// Server caps a query at 200 events; render them in pages of this size.
private const val RECORDS_PAGE_SIZE = 50
private const val RECORDS_SERVER_LIMIT = 200

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FavorTheme { FavorApp() } }
    }
}

@Composable
private fun FavorApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> LoadingScreen()
            !state.authenticated -> AuthScreen(state, viewModel)
            state.snapshot == null -> PairingScreen(state, viewModel)
            else -> HomeScreen(state.snapshot!!, state, viewModel)
        }
    }
}

@Composable
private fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(PinkGradient)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 76.dp, y = (-58).dp)
                .size(190.dp)
                .background(Color.White.copy(alpha = 0.24f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-94).dp, y = 68.dp)
                .size(220.dp)
                .background(Color(0xFFFFB6D7).copy(alpha = 0.22f), CircleShape),
        )
        content()
    }
}

@Composable
private fun LoadingScreen() {
    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("♥", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("正在打开你们的专属空间…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Notice(state: AppUiState, onDismiss: () -> Unit) {
    val persistent = state.error != null || state.message != null
    val text = state.error ?: state.message ?: state.flash ?: return
    val isError = if (persistent) state.error != null else state.flashError
    val duration = if (persistent) 10_000L else 1_000L
    LaunchedEffect(text, isError) {
        kotlinx.coroutines.delay(duration)
        onDismiss()
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
            Text(text, color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 11.dp))
            TextButton(onClick = onDismiss) { Text("×", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun AuthScreen(state: AppUiState, viewModel: MainViewModel) {
    var passwordVisible by rememberSaveable(state.authMode) { mutableStateOf(false) }
    BackHandler(enabled = state.authMode == AuthMode.SIGN_UP && !state.busy) { viewModel.setAuthMode(AuthMode.SIGN_IN) }
    AppBackground {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 480.dp).fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (state.authMode == AuthMode.SIGN_IN) 32.dp else 12.dp))
            Text("♥", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text("把心动记下来", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("让每一份喜欢，都有回应", color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(28.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.94f)),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        if (state.authMode == AuthMode.SIGN_IN) "欢迎回来" else "创建你们的空间",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (state.authMode == AuthMode.SIGN_IN) "登录后继续记录每一次心动" else "注册一个账号，开始记录你们的故事",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                    Spacer(Modifier.height(24.dp))
                    if (state.authMode == AuthMode.SIGN_UP) {
                        AppTextField(value = state.displayName, onValueChange = viewModel::setDisplayName, label = "昵称", leading = "♡")
                        Spacer(Modifier.height(12.dp))
                    }
                    AppTextField(value = state.email, onValueChange = viewModel::setEmail, label = "账号", leading = "@", keyboardType = KeyboardType.Ascii)
                    Spacer(Modifier.height(12.dp))
                    AppTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = "密码",
                        leading = "✦",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "隐藏" else "显示") }
                        },
                    )
                    if (state.authMode == AuthMode.SIGN_UP) {
                        Spacer(Modifier.height(12.dp))
                        AppTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::setConfirmPassword,
                            label = "确认密码",
                            leading = "✦",
                            keyboardType = KeyboardType.Password,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Notice(state, viewModel::clearNotice)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = viewModel::submitAuth,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (state.authMode == AuthMode.SIGN_IN) "登录 FavorApp" else "开始记录心动", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = !state.busy,
                        onClick = {
                            viewModel.setAuthMode(if (state.authMode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN)
                        },
                    ) {
                        Text(if (state.authMode == AuthMode.SIGN_IN) "没有账号？立即注册" else "已有账号？返回登录")
                    }
                    Spacer(Modifier.height(15.dp))
                    if (state.authMode == AuthMode.SIGN_UP) Text("账号 3–20 位，以字母开头\n密码 8–64 位，包含大小写字母、数字和特殊字符", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("只属于你们两个人的好感记录", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leading: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable (() -> Unit))? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Text(leading, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        trailingIcon = trailing,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingScreen(state: AppUiState, viewModel: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    var receiveInvite by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable(state.generatedInvite) { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("连接恋人", fontWeight = FontWeight.Bold) },
                    actions = { TextButton(onClick = { showAccount = true }, enabled = !state.busy) { Text("设置") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AvatarBubble("我")
                    Text("♡", color = MaterialTheme.colorScheme.primary, fontSize = 32.sp)
                    AvatarBubble("你")
                }
                Spacer(Modifier.height(24.dp))
                Text("让两颗心，住进同一个空间", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !receiveInvite, onClick = { receiveInvite = false; viewModel.clearNotice() }, label = { Text("邀请对方") }, enabled = !state.busy)
                    FilterChip(selected = receiveInvite, onClick = { receiveInvite = true; viewModel.clearNotice() }, label = { Text("输入邀请码") }, enabled = !state.busy)
                }
                Spacer(Modifier.height(16.dp))
                if (!receiveInvite) {
                            PairingCard("发出一份专属邀请", "生成邀请码，复制后发送给你的恋人。") {
                        if (state.generatedInvite != null) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
                                Text(state.generatedInvite, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center, fontSize = 26.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(20.dp))
                            PrimaryAction(if (copied) "已复制，再复制一次" else "复制邀请码", false) {
                                clipboard.setText(AnnotatedString(state.generatedInvite))
                                copied = true
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("邀请码有效期为 24 小时，只能使用一次。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(18.dp))
                            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("已发出邀请", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("对方输入邀请码后，点击下方按钮进入你们的空间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            PrimaryAction("进入我们的空间", state.busy, viewModel::enterCouple)
                        } else {
                            Text("匹配规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            NumberField("初始分数", state.initialScore, viewModel::setInitialScore)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                NumberField("总分下限（可选）", state.minScore, viewModel::setMinScore, Modifier.weight(1f))
                                NumberField("总分上限（可选）", state.maxScore, viewModel::setMaxScore, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(20.dp))
                            PrimaryAction("生成专属邀请码", state.busy, viewModel::createInvite)
                        }
                    }
                } else {
                    PairingCard("接受恋人的邀请", "粘贴对方的邀请码，开启你们的共同记录。") {
                        AppTextField(state.inviteCode, viewModel::setInviteCode, "邀请码", "♡", KeyboardType.Ascii)
                        Spacer(Modifier.height(24.dp))
                        PrimaryAction("连接我们的空间", state.busy, viewModel::acceptInvite)
                    }
                }
                Notice(state, viewModel::clearNotice)
                Spacer(Modifier.height(24.dp))
                Text("一起收藏小事 · 认真回应喜欢", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
    if (showAccount) SettingsScreen(state, viewModel) { showAccount = false }
}

@Composable
private fun PrimaryAction(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        else Text(label, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
private fun PairingCard(title: String, description: String?, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.94f))) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(snapshot: CoupleSnapshot, state: AppUiState, viewModel: MainViewModel) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var records by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var direction by rememberSaveable { mutableStateOf(0) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var visibleLimit by rememberSaveable { mutableStateOf(RECORDS_PAGE_SIZE) }
    var pendingDelta by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingNote by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = records) { records = false }
    val partnerDisplay = snapshot.partnerNickname.ifBlank { snapshot.partnerName }
    val visibleEvents = snapshot.events.filter { event ->
        val mine = if (event.actorId.isNotBlank()) event.actorId == snapshot.currentUserId else event.actorName == snapshot.currentUserName
        val directionOk = when (direction) { 1 -> mine; 2 -> !mine; else -> true }
        val changeOk = when (filter) { 1 -> event.delta > 0; 2 -> event.delta < 0; else -> true }
        directionOk && changeOk
    }
    val shownEvents = visibleEvents.take(visibleLimit)
    LaunchedEffect(records, keyword, date) {
        if (!records) {
            // Returning home: drop the records-tab filters so the recent-records card is complete.
            if (keyword.isNotBlank() || date.isNotBlank()) {
                kotlinx.coroutines.delay(400)
                viewModel.refreshSnapshot()
            }
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(500)
        viewModel.refreshSnapshot(keyword = keyword, date = date)
    }
    LaunchedEffect(records, keyword, date, filter, direction) { visibleLimit = RECORDS_PAGE_SIZE }
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(modifier = Modifier.height(60.dp), containerColor = Color.White, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = !records, onClick = { records = false }, icon = { Text("♡", fontSize = 24.sp) })
                    NavigationBarItem(selected = records, onClick = { records = true }, icon = { Text("☰", fontSize = 24.sp) })
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text(if (records) "好感度记录" else "我们的空间", fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(onClick = { viewModel.refreshSnapshot(notify = true) }, enabled = !state.busy) { Text("刷新") }
                        TextButton(onClick = { showSettings = true }, enabled = !state.busy) { Text("设置") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            // Separate lists keep each destination's scroll position.
            androidx.compose.runtime.key(records) {
                LazyColumn(
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(padding).imePadding(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item { Notice(state, viewModel::clearNotice) }
                    if (!records) {
                        item { CoupleHeroCard(snapshot, viewModel) }
                        item {
                            PairingCard("记录一次", null) {
                                AppTextField(note, { note = it }, "备注（可选）", "✎")
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    NumberField("加分 / 扣分值", state.manualDelta, viewModel::setManualDelta, Modifier.weight(1f))
                                    Button(
                                        onClick = {
                                            val delta = viewModel.validateManualDelta()
                                            if (delta != null) { pendingDelta = delta; pendingNote = note.trim() }
                                        },
                                        enabled = !state.busy, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(16.dp),
                                    ) { Text("记录") }
                                }
                                if (state.scorePresets.isNotEmpty()) {
                                    Spacer(Modifier.height(18.dp))
                                    Text("常用记录", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.height(8.dp))
                                    state.scorePresets.forEach { preset ->
                                        PresetRow(preset) {
                                            if (viewModel.validatePresetDelta(preset.delta)) {
                                                pendingDelta = preset.delta
                                                pendingNote = note.trim().ifBlank { preset.label }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("正数为加分，负数为扣分；确认后才会保存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("最近的好感度记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { records = true }) { Text("查看记录  ›") }
                            }
                        }
                        if (snapshot.events.isEmpty()) item { EmptyEventsCard() }
                        else items(snapshot.events.take(3), key = { it.id }) { EventCard(it, snapshot) }
                    } else {
                        item {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                            ) {
                                Column(Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("查找记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        if (keyword.isNotBlank() || date.isNotBlank() || filter != 0 || direction != 0) {
                                            TextButton(onClick = { keyword = ""; date = ""; filter = 0; direction = 0 }, enabled = !state.busy) { Text("清空筛选") }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    AppTextField(keyword, { keyword = it }, "搜索备注内容", "⌕")
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { showDatePicker = true },
                                        shape = RoundedCornerShape(17.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    ) {
                                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("日", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                if (date.isBlank()) "按日期筛选" else date,
                                                modifier = Modifier.weight(1f).padding(vertical = 11.dp),
                                                color = if (date.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            )
                                            if (date.isNotBlank()) {
                                                Text("×", fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.clickable { date = "" }.padding(8.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    FilterChipsRow(listOf("全部", "我→对方", "对方→我"), direction) { direction = it }
                                    Spacer(Modifier.height(8.dp))
                                    FilterChipsRow(listOf("全部", "加分", "减分"), filter) { filter = it }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (visibleEvents.size >= RECORDS_SERVER_LIMIT) "最多显示最近 $RECORDS_SERVER_LIMIT 条记录，可用筛选缩小范围" else "共 ${visibleEvents.size} 条记录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                        item { Notice(state, viewModel::clearNotice) }
                        if (visibleEvents.isEmpty()) {
                            item {
                                val filtersActive = keyword.isNotBlank() || date.isNotBlank() || filter != 0 || direction != 0
                                if (snapshot.events.isEmpty() && !filtersActive) EmptyEventsCard()
                                else PairingCard("这里暂时没有记录", "试试切换其他筛选，看看你们的日常。") {}
                            }
                        } else {
                            items(shownEvents, key = { it.id }) { EventCard(it, snapshot) }
                            if (visibleLimit < visibleEvents.size) {
                                item {
                                    OutlinedButton(
                                        onClick = { visibleLimit += RECORDS_PAGE_SIZE },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        enabled = !state.busy,
                                    ) { Text("加载更多（还有 ${visibleEvents.size - visibleLimit} 条）") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSettings) SettingsScreen(state, viewModel) { showSettings = false }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = parsePickerDate(date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    date = pickerState.selectedDateMillis?.let(::formatPickerDate).orEmpty()
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
    pendingDelta?.let { delta ->
        ScoreConfirmDialog(partnerDisplay, delta, pendingNote, state.busy, onDismiss = { pendingDelta = null }) {
            pendingDelta = null
            viewModel.addScore(delta, pendingNote)
            viewModel.setManualDelta("")
            note = ""
            pendingNote = ""
        }
    }
}

@Composable
private fun CoupleHeroCard(snapshot: CoupleSnapshot, viewModel: MainViewModel) {
    val myCard = snapshot.cards.firstOrNull { it.userId == snapshot.currentUserId }
    val partnerCard = snapshot.cards.firstOrNull { it.userId != snapshot.currentUserId }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("T O G E T H E R", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarBubble(snapshot.currentUserName, myCard?.let { viewModel.avatarUrlFor(it.userId, it.avatarVersion) }, viewModel.authToken)
                    Spacer(Modifier.height(8.dp))
                    Text(snapshot.currentUserName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("我", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text("♥", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarBubble(snapshot.partnerNickname.ifBlank { snapshot.partnerName }, partnerCard?.let { viewModel.avatarUrlFor(it.userId, it.avatarVersion) }, viewModel.authToken)
                    Spacer(Modifier.height(8.dp))
                    Text(snapshot.partnerNickname.ifBlank { snapshot.partnerName }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("恋人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                snapshot.cards.sortedBy { it.userId != snapshot.currentUserId }.forEach {
                    ScoreCardView(if (it.userId == snapshot.currentUserId) "我的分数" else "恋人的分数", it.score, Modifier.weight(1f))
                }
            }
        }
    }
}
@Composable
private fun AvatarBubble(name: String, avatarUrl: String? = null, token: String = "", size: Dp = 76.dp) {
    val initial = name.trim().firstOrNull()?.toString() ?: "♡"
    Box(modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        // Letter stays composed underneath: if the image fails to load it shows through.
        Text(initial, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // Overlay ring: drawn after the children so a full-bleed image cannot cover it.
        Box(modifier = Modifier.fillMaxSize().border(4.dp, Color.White, CircleShape))
    }
}

@Composable
private fun ScoreCardView(name: String, score: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(score.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("当前分数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyEventsCard() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.74f)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("♡", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text("还没有记录", fontWeight = FontWeight.Bold)
            Text("成为第一个给对方加分的人吧", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EventCard(event: ScoreEventItem, snapshot: CoupleSnapshot) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.9f))) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♥", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text("${partnerAwareName(snapshot, event.actorName)} → ${partnerAwareName(snapshot, event.targetName)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (event.delta > 0) "+${event.delta}" else event.delta.toString(), color = if (event.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Text("分数变为 ${event.scoreAfter} · ${formatTime(event.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 7.dp))
            if (!event.note.isNullOrBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun partnerAwareName(snapshot: CoupleSnapshot, name: String): String =
    if (name == snapshot.partnerName && snapshot.partnerNickname.isNotBlank()) snapshot.partnerNickname else name

@Composable
private fun PresetRow(preset: ScorePreset, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = if (preset.delta > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(preset.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (preset.delta > 0) "+${preset.delta}" else preset.delta.toString(), color = if (preset.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: AppUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("list") }
    BackHandler { if (page != "list") page = "list" else onDismiss() }
    if (page == "avatar") { AvatarSettings(state, viewModel) { page = "list" }; return }
    if (page == "nickname") { NicknameSettings(state, viewModel) { page = "list" }; return }
    if (page == "rules") { RulesSettings(state, viewModel) { page = "list" }; return }
    if (page == "presets") { PresetSettings(state, viewModel) { page = "list" }; return }
    SettingsPageScaffold("设置", onDismiss) { padding ->
        LazyColumn(modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Notice(state, viewModel::clearNotice) }
            item { SettingsRow("我的头像", "更换或移除") { page = "avatar" } }
            item {
                SettingsRow("恋人昵称", "${state.snapshot?.partnerNickname?.ifBlank { "未设置" } ?: "未设置"}") { page = "nickname" }
            }
            item {
                val settings = state.snapshot?.settings
                val rulesSubtitle = if (state.snapshot?.pendingRules != null) "有待处理的修改请求"
                else settings?.let { "单次加分 ${it.addMin}~${it.addMax} · 单次扣分 ${it.subtractMin}~${it.subtractMax}" } ?: "未匹配"
                SettingsRow("记分规则", rulesSubtitle) { page = "rules" }
            }
            item { SettingsRow("添加记录预设", "${state.scorePresets.size}/$PRESET_MAX_COUNT 项") { page = "presets" } }
            item {
                Spacer(Modifier.height(10.dp))
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.9f)) {
                    TextButton(onClick = { viewModel.signOut(); onDismiss() }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.92f)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun FilterChipsRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(option) })
        }
    }
}

// Opaque scaffold for settings pages; drawn over HomeScreen, so it must fully cover it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageScaffold(title: String, onBack: () -> Unit, content: @Composable BoxScope.(PaddingValues) -> Unit) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("‹", fontSize = 30.sp) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) { content(padding) }
        }
    }
}

@Composable private fun AvatarSettings(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val myCard = state.snapshot?.cards?.firstOrNull { it.userId == state.snapshot?.currentUserId }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadAvatarFromUri(context, uri)
    }
    SettingsPageScaffold("我的头像", onBack) { padding ->
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (myCard != null) {
                AvatarBubble(state.snapshot.currentUserName, viewModel.avatarUrlFor(myCard.userId, myCard.avatarVersion), viewModel.authToken, size = 120.dp)
            }
            Notice(state, viewModel::clearNotice)
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("更换头像") }
            if ((myCard?.avatarVersion ?: 0) > 0) {
                OutlinedButton(onClick = viewModel::deleteAvatar, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("移除头像") }
            }
            Text("头像只有你们两人可见；会自动裁成方形并压缩后上传。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun NicknameSettings(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(state.snapshot?.partnerNickname.orEmpty()) }
    SettingsPageScaffold("恋人昵称", onBack) { padding ->
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("这是你对 TA 的专属称呼，只对你自己可见。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(nickname, { nickname = it.take(8) }, "昵称（最多 8 个字）", "♡")
            Notice(state, viewModel::clearNotice)
            PrimaryAction("保存", state.busy) { if (viewModel.savePartnerNickname(nickname)) onBack() }
            Text("留空保存即清除昵称，界面会恢复显示对方的名字。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun RulesSettings(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val pending = state.snapshot?.pendingRules
    // Pending requests arrive through snapshot refreshes; fetch fresh state on entry.
    LaunchedEffect(Unit) { viewModel.refreshSnapshot() }
    SettingsPageScaffold("记分规则", onBack) { padding ->
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Notice(state, viewModel::clearNotice)
            if (pending != null) {
                PendingRulesCard(state, pending, viewModel)
            } else {
                RulesForm(state, viewModel)
            }
        }
    }
}

@Composable private fun PendingRulesCard(state: AppUiState, pending: PendingRuleRequest, viewModel: MainViewModel) {
    val mine = pending.requesterId == state.snapshot?.currentUserId
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.94f)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (mine) "等待对方同意" else "对方发来修改请求", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "单次加分 ${pending.addMin}~${pending.addMax} · 单次扣分 ${pending.subtractMin}~${pending.subtractMax}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (mine) {
                Text("不想等了可以撤销，撤销后可重新发起。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = viewModel::cancelRulesRequest, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("撤销请求") }
            } else {
                Text("同意后新规则立即生效，双方共用一套规则。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::acceptRulesRequest, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("同意") }
                    OutlinedButton(onClick = viewModel::rejectRulesRequest, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("拒绝") }
                }
            }
        }
    }
}

@Composable private fun RulesForm(state: AppUiState, viewModel: MainViewModel) {
    val settings = state.snapshot?.settings
    var addMin by rememberSaveable { mutableStateOf((settings?.addMin ?: 1).toString()) }
    var addMax by rememberSaveable { mutableStateOf((settings?.addMax ?: 5).toString()) }
    var subtractMin by rememberSaveable { mutableStateOf((settings?.subtractMin ?: 1).toString()) }
    var subtractMax by rememberSaveable { mutableStateOf((settings?.subtractMax ?: 5).toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("修改需要对方同意后才会生效，双方共用一套规则。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("单次加分范围（正数1~100）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("最小", addMin, onValueChange = { addMin = it }, Modifier.weight(1f))
            NumberField("最大", addMax, onValueChange = { addMax = it }, Modifier.weight(1f))
        }
        Text("单次扣分范围（正数1~100）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("最小", subtractMin, onValueChange = { subtractMin = it }, Modifier.weight(1f))
            NumberField("最大", subtractMax, onValueChange = { subtractMax = it }, Modifier.weight(1f))
        }
        PrimaryAction("请求修改", state.busy) {
            viewModel.requestRulesChange(addMin, addMax, subtractMin, subtractMax)
        }
    }
}

@Composable private fun PresetSettings(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    SettingsPageScaffold("添加记录预设", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().imePadding().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
            item { AppTextField(state.presetLabel, viewModel::setPresetLabel, "备注，例如：乖乖早睡", "♡") }
            item { Text("备注最多 $NOTE_MAX_LENGTH 个字，常用记录最多保存 $PRESET_MAX_COUNT 条（当前 ${state.scorePresets.size}/$PRESET_MAX_COUNT）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { NumberField("分值（正数加分，负数扣分）", state.presetDelta, viewModel::setPresetDelta, Modifier.weight(1f)); Button(onClick = viewModel::addPreset, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("添加") } } }
            item { Notice(state, viewModel::clearNotice) }
            items(state.scorePresets, key = { it.id }) { preset -> Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.9f)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(preset.label, Modifier.weight(1f)); Text(if (preset.delta > 0) "+${preset.delta}" else preset.delta.toString(), fontWeight = FontWeight.Bold); TextButton(onClick = { viewModel.removePreset(preset.id) }) { Text("删除") } } } }
        }
    }
}

@Composable
private fun ScoreConfirmDialog(partnerName: String, delta: Int, note: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val isAdd = delta > 0
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        title = { Text("确认记录", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("要给 $partnerName ${if (isAdd) "加" else "扣"} ${kotlin.math.abs(delta)} 分吗？")
                if (note.isNotBlank()) Text("备注：$note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !busy, shape = RoundedCornerShape(13.dp)) { Text("确认${if (isAdd) "加分" else "扣分"}") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = { input -> onValueChange(input.filter { it == '-' || it.isDigit() }) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(15.dp), modifier = modifier.fillMaxWidth())
}

private fun formatTime(value: String): String = runCatching {
    val parsed = OffsetDateTime.parse(value)
    parsed.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(value.replace('T', ' ').take(16))

private fun formatPickerDate(millis: Long): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.timeInMillis = millis
    return String.format(Locale.US, "%04d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
}

private fun parsePickerDate(value: String): Long? {
    if (value.length != 10) return null
    return runCatching {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(value.substring(0, 4).toInt(), value.substring(5, 7).toInt() - 1, value.substring(8, 10).toInt())
        calendar.timeInMillis
    }.getOrNull()
}

