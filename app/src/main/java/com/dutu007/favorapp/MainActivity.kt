package com.dutu007.favorapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dutu007.favorapp.data.CoupleSnapshot
import com.dutu007.favorapp.data.ScoreEventItem
import com.dutu007.favorapp.data.ScorePreset
import com.dutu007.favorapp.ui.theme.FavorTheme

private val PinkGradient = Brush.verticalGradient(
    listOf(Color(0xFFFFE0ED), Color(0xFFF5EEFC), Color(0xFFFFFAFC)),
)

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
    val text = state.error ?: state.message ?: return
    val isError = state.error != null
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
    var pendingDelta by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = records) { records = false }
    val deltas = listOf(-snapshot.settings.subtractMax, -snapshot.settings.subtractMin, snapshot.settings.addMin, snapshot.settings.addMax).distinct()
    val visibleEvents = snapshot.events.filter {
        when (filter) { 1 -> it.delta > 0; 2 -> it.delta < 0; else -> true }
    }
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = !records, onClick = { records = false }, icon = { Text("♡", fontSize = 26.sp) }, label = { Text("我们的空间") })
                    NavigationBarItem(selected = records, onClick = { records = true }, icon = { Text("≡", fontSize = 26.sp) }, label = { Text("心动记录") })
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text(if (records) "心动记录" else "我们的空间", fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(onClick = viewModel::refreshSnapshot, enabled = !state.busy) { Text("刷新") }
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
                        item { CoupleHeroCard(snapshot) }
                        item {
                            PairingCard("记录一次", null) {
                                AppTextField(note, { note = it }, "备注（可选）", "✎")
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    NumberField("加分 / 扣分值", state.manualDelta, viewModel::setManualDelta, Modifier.weight(1f))
                                    Button(onClick = { pendingDelta = viewModel.validateManualDelta() }, enabled = !state.busy, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("记录") }
                                }
                                if (state.scorePresets.isNotEmpty()) {
                                    Spacer(Modifier.height(18.dp))
                                    Text("常用记录", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.height(8.dp))
                                    state.scorePresets.forEach { preset ->
                                        PresetRow(preset) { pendingDelta = preset.delta }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("正数为加分，负数为扣分；确认后才会保存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("最近的心动", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { records = true }) { Text("查看记录  ›") }
                            }
                        }
                        if (snapshot.events.isEmpty()) item { EmptyEventsCard() }
                        else items(snapshot.events.take(3), key = { it.id }) { EventCard(it) }
                    } else {
                        item {
                            Text("把日常，写成我们的故事", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("最近 " + snapshot.events.size + " 条记录 · 新的心动排在前面", color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("全部", "加分", "减分").forEachIndexed { index, label ->
                                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
                                }
                            }
                        }
                        if (visibleEvents.isEmpty()) {
                            item {
                                if (snapshot.events.isEmpty()) EmptyEventsCard()
                                else PairingCard("这里暂时没有记录", "试试切换其他分类，看看你们的日常。") {}
                            }
                        } else items(visibleEvents, key = { it.id }) { EventCard(it) }
                    }
                }
            }
        }
    }
    if (showSettings) SettingsScreen(state, viewModel) { showSettings = false }
    pendingDelta?.let { delta ->
        ScoreConfirmDialog(snapshot.partnerName, delta, note, state.busy, onDismiss = { pendingDelta = null }) {
            pendingDelta = null
            viewModel.addScore(delta, note)
            note = ""
        }
    }
}

@Composable
private fun CoupleHeroCard(snapshot: CoupleSnapshot) {
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
                    AvatarBubble(snapshot.currentUserName)
                    Spacer(Modifier.height(8.dp))
                    Text(snapshot.currentUserName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("我", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text("♥", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarBubble(snapshot.partnerName)
                    Spacer(Modifier.height(8.dp))
                    Text(snapshot.partnerName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("恋人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                snapshot.cards.sortedBy { it.userId != snapshot.currentUserId }.forEach {
                    ScoreCardView(if (it.userId == snapshot.currentUserId) "我收到的好感" else "对方收到的好感", it.score, Modifier.weight(1f))
                }
            }
        }
    }
}
@Composable
private fun AvatarBubble(name: String) {
    val initial = name.trim().firstOrNull()?.toString() ?: "♡"
    Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).border(4.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Text(initial, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScoreCardView(name: String, score: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(score.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("收到的好感", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun EventCard(event: ScoreEventItem) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.9f))) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♥", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text("${event.actorName} → ${event.targetName}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (event.delta > 0) "+${event.delta}" else event.delta.toString(), color = if (event.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Text("分数变为 ${event.scoreAfter} · ${formatTime(event.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 7.dp))
            if (!event.note.isNullOrBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PresetRow(preset: ScorePreset, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = if (preset.delta > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(preset.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(if (preset.delta > 0) "+${preset.delta}" else preset.delta.toString(), color = if (preset.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: AppUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    BackHandler { onDismiss() }
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("设置", fontWeight = FontWeight.Bold) },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("‹", fontSize = 30.sp) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            LazyColumn(modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text("记录预设", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item { Text("把常用的加分和扣分整理成快捷项，主界面点击后确认即可记录。", color = MaterialTheme.colorScheme.secondary) }
                item {
                    PairingCard("添加预设项", null) {
                        AppTextField(state.presetLabel, viewModel::setPresetLabel, "名称，例如：主动报备", "♡")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberField("分值（正数加分，负数扣分）", state.presetDelta, viewModel::setPresetDelta, Modifier.weight(1f))
                            Button(onClick = viewModel::addPreset, enabled = !state.busy, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("添加") }
                        }
                    }
                }
                item { Notice(state, viewModel::clearNotice) }
                if (state.scorePresets.isEmpty()) item { EmptyPresetCard() }
                else items(state.scorePresets, key = { it.id }) { preset ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.9f)) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(if (preset.delta > 0) "+${preset.delta}" else preset.delta.toString(), color = if (preset.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { viewModel.removePreset(preset.id) }) { Text("删除") }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.9f)) {
                        TextButton(onClick = { viewModel.signOut(); onDismiss() }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPresetCard() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.72f)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有预设项", fontWeight = FontWeight.SemiBold)
            Text("添加后会显示在首页的“常用记录”中", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AccountDialog(busy: Boolean, viewModel: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text("设置", fontWeight = FontWeight.Bold) },
        text = { Text("匹配规则在创建邀请码时确定，当前空间会按这套规则记录分数。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } },
        dismissButton = { TextButton(onClick = { viewModel.signOut(); onDismiss() }, enabled = !busy) { Text("退出登录") } },
    )
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
private fun SettingsDialog(snapshot: CoupleSnapshot, busy: Boolean, viewModel: MainViewModel, onDismiss: () -> Unit) {
    var initial by rememberSaveable { mutableStateOf(snapshot.settings.initialScore.toString()) }
    var min by rememberSaveable { mutableStateOf(snapshot.settings.minScore?.toString().orEmpty()) }
    var max by rememberSaveable { mutableStateOf(snapshot.settings.maxScore?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text("好感度设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("初始值只会在还没有记录时影响当前分数；留空上下限表示不限制。", style = MaterialTheme.typography.bodySmall)
                NumberField("初始值", initial, onValueChange = { initial = it })
                NumberField("最低值（可选）", min, onValueChange = { min = it })
                NumberField("最高值（可选）", max, onValueChange = { max = it })
            }
        },
        confirmButton = { Button(onClick = { viewModel.saveSettings(initial, min, max); onDismiss() }, enabled = !busy, shape = RoundedCornerShape(13.dp)) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = { input -> onValueChange(input.filter { it == '-' || it.isDigit() }) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(15.dp), modifier = modifier.fillMaxWidth())
}

private fun formatTime(value: String): String = value.replace('T', ' ').take(16)
