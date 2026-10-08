package com.dutu007.favorapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
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
import com.dutu007.favorapp.data.GiftItem
import com.dutu007.favorapp.data.PendingRuleRequest
import com.dutu007.favorapp.data.ScoreEventItem
import com.dutu007.favorapp.data.ScorePreset
import com.dutu007.favorapp.ui.theme.FavorTheme
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val PinkGradient = Brush.verticalGradient(
    listOf(Color(0xFFFFE0ED), Color(0xFFF5EEFC), Color(0xFFFFFAFC)),
)

private val AgreementNavIcon = ImageVector.Builder(
    name = "AgreementList", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(5f, 21f); close()
        moveTo(8f, 8f); lineTo(9f, 9f); lineTo(11f, 7f)
        moveTo(14f, 8f); lineTo(16f, 8f)
        moveTo(8f, 14f); lineTo(9f, 15f); lineTo(11f, 13f)
        moveTo(14f, 14f); lineTo(16f, 14f)
    }
}.build()

// Server caps a query at 200 events; render them in pages of this size.
private const val RECORDS_PAGE_SIZE = 50
private const val RECORDS_SERVER_LIMIT = 200

// A gift stays "active" until every timeline node is done (received) or cancelled.
private const val GIFT_REQUESTED = "requested"
private const val GIFT_ACTIVE = "active"
private const val GIFT_RECEIVED = "received"
private val GIFT_FINISHED = setOf(GIFT_RECEIVED, "cancelled")

// Line-art gift icon for the bottom bar, drawn to match the text-glyph icons.
private val GiftNavIcon: ImageVector = ImageVector.Builder(
    name = "GiftLine",
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // bow: two loops
        moveTo(11.3f, 5.9f)
        arcTo(2.1f, 2.1f, 0f, true, true, 7.1f, 5.9f)
        arcTo(2.1f, 2.1f, 0f, true, true, 11.3f, 5.9f)
        moveTo(16.9f, 5.9f)
        arcTo(2.1f, 2.1f, 0f, true, true, 12.7f, 5.9f)
        arcTo(2.1f, 2.1f, 0f, true, true, 16.9f, 5.9f)
        // lid
        moveTo(4.5f, 8.2f)
        horizontalLineTo(19.5f)
        verticalLineTo(11.2f)
        horizontalLineTo(4.5f)
        close()
        // box
        moveTo(6f, 11.2f)
        lineTo(18f, 11.2f)
        lineTo(18f, 20.2f)
        lineTo(6f, 20.2f)
        close()
        // ribbon
        moveTo(12f, 8.2f)
        lineTo(12f, 20.2f)
    }
}.build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { FavorTheme { FavorApp() } }
    }
}

@Composable
private fun FavorApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val screenState = if (state.updateAvailable != null || state.updateDownloading) state.copy(notice = null) else state
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> LoadingScreen()
            !state.authenticated -> AuthScreen(screenState, viewModel)
            state.snapshot == null -> PairingScreen(screenState, viewModel)
            else -> HomeScreen(state.snapshot!!, screenState, viewModel)
        }
    }
    UpdateDialogs(state, viewModel)
    NoticeExpiryEffect(state.notice, viewModel::dismissNotice)
}

@Composable
private fun UpdateDialogs(state: AppUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    // Hand the downloaded package to the system installer once it is ready.
    LaunchedEffect(state.updateReadyPath) {
        val path = state.updateReadyPath ?: return@LaunchedEffect
        viewModel.consumeReadyUpdate()
        runCatching { launchApkInstaller(context, path) }
    }
    val release = state.updateAvailable
    when {
        state.updateDownloading -> AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(26.dp),
            title = { Text("正在下载更新", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(progress = { state.updateProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.updateProgress}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            },
            confirmButton = {},
        )
        release != null -> AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            shape = RoundedCornerShape(26.dp),
            title = { Text("发现新版本 ${release.versionName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (release.notes.isNotBlank()) Text(release.notes)
                    if (release.size > 0) Text("安装包约 ${release.size / 1024 / 1024 + 1} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    InlineNotice(state, viewModel)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            viewModel.noteInstallPermissionNeeded()
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        } else {
                            viewModel.downloadUpdate(context)
                        }
                    },
                    shape = RoundedCornerShape(13.dp),
                ) { Text("立即更新") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdate) { Text("稍后") } },
        )
    }
}

private fun launchApkInstaller(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
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
                    InlineNotice(state, viewModel)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = viewModel::submitAuth,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (state.authMode == AuthMode.SIGN_IN) "登录 Favor" else "开始记录心动", fontWeight = FontWeight.Bold)
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
                    title = { RefreshingTitle("连接恋人", state.snapshotRefreshing) },
                    actions = {
                        IconButton(onClick = { viewModel.refreshSnapshot() }, enabled = !state.busy && !state.snapshotRefreshing) {
                            Icon(RefreshIcon, contentDescription = "刷新", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showAccount = true }, enabled = !state.busy) {
                            Icon(SettingsIcon, contentDescription = "设置", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            RefreshableContent(
                isRefreshing = state.snapshotRefreshing,
                onRefresh = { viewModel.refreshSnapshot() }, enabled = !state.busy,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
                    if (!showAccount) InlineNotice(state, viewModel)
                    Spacer(Modifier.height(24.dp))
                    Text("一起收藏小事 · 认真回应喜欢", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
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
    var showGiftHistory by rememberSaveable { mutableStateOf(false) }
    var giftDeleteCandidate by remember { mutableStateOf<GiftItem?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var direction by rememberSaveable { mutableStateOf(0) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var visibleLimit by rememberSaveable { mutableStateOf(RECORDS_PAGE_SIZE) }
    var pendingDelta by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingNote by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = showGiftHistory) { showGiftHistory = false }
    BackHandler(enabled = tab != 0) { tab = 0 }
    val partnerDisplay = snapshot.partnerNickname.ifBlank { snapshot.partnerName }
    val visibleEvents = snapshot.events.filter { event ->
        val mine = if (event.actorId.isNotBlank()) event.actorId == snapshot.currentUserId else event.actorName == snapshot.currentUserName
        val directionOk = when (direction) { 1 -> mine; 2 -> !mine; else -> true }
        val changeOk = when (filter) { 1 -> event.delta > 0; 2 -> event.delta < 0; else -> true }
        directionOk && changeOk
    }
    val shownEvents = visibleEvents.take(visibleLimit)
    val navigationBarHeight = 60.dp + NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
    val refreshing = when (tab) {
        3 -> state.agreementsLoading
        2 -> state.giftsRefreshing || state.snapshotRefreshing
        else -> state.snapshotRefreshing
    }
    val canRefresh = !state.busy && (tab != 3 || (!state.agreementsBusy && !state.agreementsLoadingMore))
    val refresh = {
        when (tab) {
            3 -> viewModel.loadAgreements()
            2 -> viewModel.refreshGifts()
            1 -> viewModel.refreshSnapshot(keyword = keyword, date = date)
            else -> viewModel.refreshSnapshot()
        }
    }
    LaunchedEffect(tab, keyword, date, showSettings, showGiftHistory) {
        if (showSettings || showGiftHistory) return@LaunchedEffect
        when (tab) {
            2, 3 -> return@LaunchedEffect
            0 -> {
                // Returning home: drop the records-tab filters so the recent-records card is complete.
                if (keyword.isNotBlank() || date.isNotBlank()) {
                    kotlinx.coroutines.delay(400)
                    viewModel.refreshSnapshot()
                }
                return@LaunchedEffect
            }
        }
        kotlinx.coroutines.delay(500)
        viewModel.refreshSnapshot(keyword = keyword, date = date)
    }
    LaunchedEffect(tab) { if (tab == 2) viewModel.loadGifts() }
    LaunchedEffect(showGiftHistory) { if (showGiftHistory) viewModel.loadGiftHistory(true) }
    LaunchedEffect(tab, keyword, date, filter, direction) { visibleLimit = RECORDS_PAGE_SIZE }
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(modifier = Modifier.height(navigationBarHeight), containerColor = Color.White, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("♡", fontSize = 24.sp) })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(AgreementNavIcon, contentDescription = "约定清单", modifier = Modifier.size(24.dp)) })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(GiftNavIcon, contentDescription = null, modifier = Modifier.size(24.dp)) })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("☰", fontSize = 24.sp) })
                }
            },
            topBar = {
                TopAppBar(
                    title = { RefreshingTitle(when (tab) { 1 -> "好感度记录"; 2 -> "阶段性奖励"; 3 -> "约定清单"; else -> "我们的空间" }, refreshing) },
                    actions = {
                        IconButton(onClick = refresh, enabled = canRefresh && !refreshing) {
                            Icon(RefreshIcon, contentDescription = "刷新", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showSettings = true }, enabled = !state.busy) {
                            Icon(SettingsIcon, contentDescription = "设置", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            // Separate lists keep each destination's scroll position.
            androidx.compose.runtime.key(tab) {
                if (tab == 3) {
                    androidx.compose.runtime.key(snapshot.coupleId) {
                        AgreementScreen(
                            state = if (showSettings || showGiftHistory) state.copy(notice = null) else state,
                            snapshot = snapshot,
                            viewModel = viewModel,
                            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(padding).consumeWindowInsets(padding),
                        )
                    }
                } else RefreshableContent(
                    isRefreshing = refreshing, onRefresh = refresh, enabled = canRefresh,
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
                ) {
                    LazyColumn(
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (state.notice != null && !showSettings && !showGiftHistory) {
                            item(key = "page_notice") { InlineNotice(state, viewModel) }
                        }
                        if (tab == 0) {
                            item(key = "couple_hero") { CoupleHeroCard(snapshot, viewModel) }
                            item(key = "score_form") {
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
                            item(key = "recent_events_title") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("最近的好感度记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { tab = 1 }) { Text("查看记录  ›") }
                                }
                            }
                            if (snapshot.events.isEmpty()) item(key = "recent_events_empty") { EmptyEventsCard() }
                            else items(snapshot.events.take(3), key = { it.id }) { EventCard(it, snapshot) }
                        } else if (tab == 1) {
                            item(key = "events_filter") {
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
                            if (visibleEvents.isEmpty()) {
                                item(key = "events_empty") {
                                    val filtersActive = keyword.isNotBlank() || date.isNotBlank() || filter != 0 || direction != 0
                                    if (snapshot.events.isEmpty() && !filtersActive) EmptyEventsCard()
                                    else PairingCard("这里暂时没有记录", "试试切换其他筛选，看看你们的日常。") {}
                                }
                            } else {
                                items(shownEvents, key = { it.id }) { EventCard(it, snapshot) }
                                if (visibleLimit < visibleEvents.size) {
                                    item(key = "events_more") {
                                        OutlinedButton(
                                            onClick = { visibleLimit += RECORDS_PAGE_SIZE },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            enabled = !state.busy,
                                        ) { Text("加载更多（还有 ${visibleEvents.size - visibleLimit} 条）") }
                                    }
                                }
                            }
                        } else {
                            item(key = "gift_goal") { GiftGoalCard(state, viewModel) }
                            val gifts = state.gifts?.gifts.orEmpty()
                            // Waiting-for-acceptance wishes count as in flight too:
                            // only received/cancelled gifts are history.
                            val mine = gifts.firstOrNull { it.requesterId == snapshot.currentUserId && it.status != GIFT_RECEIVED && it.status != "cancelled" }
                            val theirs = gifts.firstOrNull { it.requesterId != snapshot.currentUserId && it.status != GIFT_RECEIVED && it.status != "cancelled" }
                            if (mine == null) item(key = "gift_redeem") { GiftRedeemCard(state, viewModel) }
                            if (mine != null) item(key = mine.id) { GiftTrackingCard(mine, state, viewModel) }
                            if (theirs != null) item(key = theirs.id) { GiftTrackingCard(theirs, state, viewModel) }
                            val history = gifts.filter { it.status in GIFT_FINISHED }
                            val historyTotal = state.gifts?.historyTotal ?: history.size
                            if (history.isNotEmpty()) {
                                item(key = "gift_history_title") { Text("过往礼物", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                                items(history.take(3), key = { it.id }) { GiftHistoryCard(it, onClick = { giftDeleteCandidate = it }) }
                                if (historyTotal > 3) {
                                    item(key = "gift_history_more") {
                                        TextButton(onClick = { showGiftHistory = true }, modifier = Modifier.fillMaxWidth()) { Text("查看完整历史 ›") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSettings) SettingsScreen(state, viewModel) { showSettings = false }
    if (showGiftHistory) GiftHistoryScreen(state, viewModel) { showGiftHistory = false }
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
    GiftDeleteFlow(giftDeleteCandidate, onDismiss = { giftDeleteCandidate = null }, onConfirmDelete = { gift ->
        giftDeleteCandidate = null
        viewModel.deleteGift(gift)
    })
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
        Column(Modifier.fillMaxWidth()) {
            // The artwork keeps its two vignette circles at fixed fractions of the width,
            // so the live avatars and names stay glued to them on any screen size.
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1920f / 1200f)) {
                val heroW = maxWidth
                val heroH = maxHeight
                val avatarSize = heroW * 0.25f
                val nameWidth = heroW * 0.42f
                Image(
                    painter = painterResource(R.drawable.home_hero_bg),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize(),
                )
                AvatarBubble(
                    snapshot.currentUserName,
                    myCard?.let { viewModel.avatarUrlFor(it.userId, it.avatarVersion) },
                    viewModel.authToken,
                    size = avatarSize,
                    modifier = Modifier.offset(heroW * 0.2370f - avatarSize / 2, heroH * 0.4917f - avatarSize / 2),
                )
                AvatarBubble(
                    snapshot.partnerNickname.ifBlank { snapshot.partnerName },
                    partnerCard?.let { viewModel.avatarUrlFor(it.userId, it.avatarVersion) },
                    viewModel.authToken,
                    size = avatarSize,
                    modifier = Modifier.offset(heroW * 0.7760f - avatarSize / 2, heroH * 0.4875f - avatarSize / 2),
                )
                CoupleHeroName(snapshot.currentUserName, "我", heroW * 0.2370f, heroH * 0.73f, nameWidth)
                CoupleHeroName(snapshot.partnerNickname.ifBlank { snapshot.partnerName }, "恋人", heroW * 0.7760f, heroH * 0.73f, nameWidth)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                snapshot.cards.sortedBy { it.userId != snapshot.currentUserId }.forEach {
                    ScoreCardView(if (it.userId == snapshot.currentUserId) "我的分数" else "恋人的分数", it.score, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CoupleHeroName(name: String, role: String, centerX: Dp, topY: Dp, width: Dp) {
    Column(
        modifier = Modifier.offset(centerX - width / 2, topY).width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}
@Composable
private fun AvatarBubble(name: String, avatarUrl: String? = null, token: String = "", size: Dp = 76.dp, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.toString() ?: "♡"
    Box(modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
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
private fun GiftGoalCard(state: AppUiState, viewModel: MainViewModel) {
    val snapshot = state.snapshot ?: return
    val goals = state.gifts?.goals.orEmpty()
    val partnerName = snapshot.partnerNickname.ifBlank { snapshot.partnerName }
    val partnerGoal = goals.firstOrNull { it.userId != snapshot.currentUserId }?.targetScore
    var showEditor by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("阶段性目标", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("目标由对方为你设置，达到后就能向 TA 兑换心意礼物", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                TextButton(onClick = { viewModel.setGiftGoalDraft(partnerGoal?.toString() ?: ""); showEditor = true }, enabled = !state.busy) {
                    Text(if (partnerGoal == null) "设目标" else "修改")
                }
            }
            snapshot.cards.forEach { card ->
                val isMe = card.userId == snapshot.currentUserId
                val name = if (isMe) snapshot.currentUserName else partnerName
                val goal = goals.firstOrNull { it.userId == card.userId }?.targetScore
                val reached = goal != null && card.score >= goal
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (goal == null) "未设置目标" else "${card.score}/$goal" + if (reached) " · 可兑换" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            fontWeight = if (reached) FontWeight.Bold else null,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { if (goal == null) 0f else (card.score.toFloat() / goal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text("给 $partnerName 的目标", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("TA 的分数达到这个目标后，就可以向你要一份礼物，随时可调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    NumberField("目标分数", state.giftGoalDraft, viewModel::setGiftGoalDraft)
                }
            },
            confirmButton = { Button(onClick = { showEditor = false; viewModel.saveGiftGoal() }, enabled = !state.busy, shape = RoundedCornerShape(13.dp)) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun GiftRedeemCard(state: AppUiState, viewModel: MainViewModel) {
    val snapshot = state.snapshot ?: return
    val myGoal = state.gifts?.goals.orEmpty().firstOrNull { it.userId == snapshot.currentUserId }?.targetScore
    val myScore = snapshot.cards.firstOrNull { it.userId == snapshot.currentUserId }?.score ?: 0
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.94f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("兑换礼物", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (myGoal == null || myScore < myGoal) {
                val text = if (myGoal == null) "对方还没给你设置目标，提醒 TA 一下吧。" else "再攒 ${myGoal - myScore} 分就可以兑换礼物啦，继续加油！"
                Text(text, color = MaterialTheme.colorScheme.secondary)
            } else {
                Text("达到 $myGoal 分啦，选一份想要的礼物告诉 TA 吧", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                AppTextField(state.giftTitle, viewModel::setGiftTitle, "想要什么礼物", "🎁")
                AppTextField(state.giftNote, viewModel::setGiftNote, "备注（可选）", "✎")
                PrimaryAction("提交兑换", state.busy, viewModel::submitGiftRedemption)
                Text("提交后等对方同意，TA 会安排准备进度。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GiftTrackingCard(gift: GiftItem, state: AppUiState, viewModel: MainViewModel) {
    val snapshot = state.snapshot ?: return
    val mine = gift.requesterId == snapshot.currentUserId
    val requesterName = partnerAwareName(snapshot, gift.requesterName)
    val giverName = if (mine) snapshot.partnerNickname.ifBlank { snapshot.partnerName } else snapshot.currentUserName
    var showProgressDialog by remember { mutableStateOf(false) }
    var showStepsEditor by remember { mutableStateOf(false) }
    val roleText = when {
        gift.status == "cancelled" -> if (mine) "我的心愿 · 已取消" else "${requesterName}的心愿 · 已取消"
        gift.status == GIFT_RECEIVED -> if (mine) "我的心愿 · 已完成" else "${requesterName}的心愿 · 已完成"
        gift.status == GIFT_REQUESTED -> if (mine) "我的心愿 · 等待 $giverName 同意" else "${requesterName}的心愿 · 等你同意"
        gift.steps.isEmpty() -> if (mine) "我的心愿 · $giverName 已同意，正在安排进度" else "${requesterName}的心愿 · 我来准备"
        mine -> "我的心愿 · $giverName 正在准备"
        else -> "${requesterName}的心愿 · 我来准备"
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(gift.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(roleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (gift.status == "cancelled") {
                    Text("已取消", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            if (!gift.note.isNullOrBlank()) Text("备注：${gift.note}", style = MaterialTheme.typography.bodyMedium)
            GiftStepper(gift)
            when {
                gift.status == "requested" && mine -> {
                    OutlinedButton(onClick = { viewModel.cancelGift(gift) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("取消兑换", color = MaterialTheme.colorScheme.error)
                    }
                }
                gift.status == "requested" -> {
                    Button(onClick = { viewModel.acceptGift(gift) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("同意这个心愿")
                    }
                    Text("同意后由你编辑进度节点，保存后对方就能同步看到准备进度。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                gift.status == GIFT_ACTIVE && mine -> {
                    OutlinedButton(onClick = { viewModel.cancelGift(gift) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("取消兑换", color = MaterialTheme.colorScheme.error)
                    }
                }
                gift.status == GIFT_ACTIVE && gift.steps.isEmpty() -> {
                    Button(onClick = { showStepsEditor = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("安排进度节点")
                    }
                    Text("编辑并保存节点后，对方就能同步看到准备进度。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                gift.status == GIFT_ACTIVE -> {
                    if (gift.currentStep < gift.steps.size) {
                        val nextLabel = gift.steps[gift.currentStep].label
                        Button(onClick = { viewModel.setGiftProgress(gift, gift.currentStep + 1) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Text(if (gift.currentStep + 1 == gift.steps.size) "完成最后一步：「$nextLabel」" else "完成下一步：「$nextLabel」")
                        }
                    } else {
                        Button(onClick = { viewModel.setGiftProgress(gift, gift.steps.size) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Text("标记为已收到 🎉")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showProgressDialog = true }, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("调整进度") }
                        OutlinedButton(onClick = { showStepsEditor = true }, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("编辑节点") }
                    }
                }
            }
        }
    }
    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { showProgressDialog = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text("调整进度", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("选择目前完成到哪一步，双方都会看到。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    GiftProgressOption("还没开始（全部未完成）", gift.currentStep == 0) { showProgressDialog = false; viewModel.setGiftProgress(gift, 0) }
                    gift.steps.forEachIndexed { index, step ->
                        GiftProgressOption("完成到「${step.label}」", gift.currentStep == index + 1) { showProgressDialog = false; viewModel.setGiftProgress(gift, index + 1) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showProgressDialog = false }) { Text("关闭") } },
        )
    }
    if (showStepsEditor) {
        val labels = remember(gift.id, gift.steps) {
            mutableStateListOf<String>().apply {
                addAll(gift.steps.map { it.label }.ifEmpty { listOf("已采购/已下单", "准备中", "已发货", "已收到礼物") })
            }
        }
        AlertDialog(
            onDismissRequest = { showStepsEditor = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text("编辑进度节点", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (gift.steps.isEmpty()) "给这份礼物安排进度节点（1-8 个，每个最多 12 个字），保存后对方就能看到进度。" else "节点数量和文字都可以自定义（1-8 个，每个最多 12 个字）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    labels.forEachIndexed { index, _ ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = labels[index],
                                onValueChange = { labels[index] = it.take(12) },
                                label = { Text("节点 ${index + 1}") },
                                singleLine = true,
                                shape = RoundedCornerShape(15.dp),
                                modifier = Modifier.weight(1f),
                            )
                            if (labels.size > 1) {
                                TextButton(onClick = { labels.removeAt(index) }, enabled = !state.busy) { Text("删除") }
                            }
                        }
                    }
                    if (labels.size < 8) {
                        TextButton(onClick = { labels.add("") }, enabled = !state.busy) { Text("+ 添加节点") }
                    }
                }
            },
            confirmButton = { Button(onClick = { showStepsEditor = false; viewModel.updateGiftSteps(gift, labels.toList()) }, enabled = !state.busy, shape = RoundedCornerShape(13.dp)) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showStepsEditor = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun GiftProgressOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GiftStepper(gift: GiftItem) {
    if (gift.steps.isEmpty()) return
    Column {
        gift.steps.forEachIndexed { index, step ->
            val done = index < gift.currentStep
            val inProgress = gift.status == GIFT_ACTIVE && index == gift.currentStep
            val color = if (done || inProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        if (done) {
                            Box(Modifier.fillMaxSize().background(color, CircleShape))
                            Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Box(Modifier.fillMaxSize().border(2.dp, color, CircleShape))
                            if (inProgress) Box(Modifier.size(8.dp).background(color, CircleShape))
                        }
                    }
                    if (index < gift.steps.lastIndex) {
                        Box(
                            Modifier.width(2.dp).height(16.dp)
                                .background(if (index + 1 < gift.currentStep) color.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 2.dp)) {
                    Text(
                        step.label + if (inProgress) "（进行中）" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (done || inProgress) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (done || inProgress) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (step.at != null) Text(formatTime(step.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun GiftHistoryCard(gift: GiftItem, onClick: (() -> Unit)? = null) {
    val received = gift.status == GIFT_RECEIVED
    ElevatedCard(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.9f))) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (received) "🎉" else "🎁", modifier = Modifier.padding(end = 8.dp))
                Text(gift.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (received) "已完成" else "已取消",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (received) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                if (onClick != null) Text(" ›", fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary)
            }
            val at = gift.steps.lastOrNull()?.at ?: gift.cancelledAt ?: gift.createdAt
            Text("${gift.requesterName}的心愿 · ${formatTime(at)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun GiftHistoryScreen(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    var deleteCandidate by remember { mutableStateOf<GiftItem?>(null) }
    SettingsPageScaffold(
        "礼物历史", onBack, isRefreshing = state.giftHistoryRefreshing,
        onRefresh = { viewModel.loadGiftHistory(true) }, refreshEnabled = !state.busy && !state.giftHistoryLoadingMore,
    ) { padding ->
        LazyColumn(modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(padding).consumeWindowInsets(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.notice != null) item { InlineNotice(state, viewModel) }
            if (state.giftHistory.isEmpty()) {
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.74f)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎁", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(6.dp))
                            Text("还没有完成过的礼物", fontWeight = FontWeight.Bold)
                            Text("完成或取消的心愿会记录在这里", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(state.giftHistory, key = { it.id }) { GiftHistoryCard(it, onClick = { deleteCandidate = it }) }
                if (state.giftHistoryHasMore) {
                    item {
                        OutlinedButton(onClick = { viewModel.loadGiftHistory(false) }, enabled = !state.busy && !state.giftHistoryRefreshing && !state.giftHistoryLoadingMore, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            if (state.giftHistoryLoadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("加载更多记录")
                        }
                    }
                }
            }
        }
    }
    GiftDeleteFlow(deleteCandidate, onDismiss = { deleteCandidate = null }, onConfirmDelete = { gift ->
        deleteCandidate = null
        viewModel.deleteGift(gift)
    })
}

// Two-step delete: pick the record, then confirm the irreversible removal.
@Composable
private fun GiftDeleteFlow(candidate: GiftItem?, onDismiss: () -> Unit, onConfirmDelete: (GiftItem) -> Unit) {
    var confirming by remember(candidate) { mutableStateOf(false) }
    if (candidate == null) return
    if (!confirming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(26.dp),
            title = { Text("删除记录", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("🎁 ${candidate.title}", fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (candidate.status == GIFT_RECEIVED) "已完成" else "已取消") + " · " + formatTime(candidate.steps.lastOrNull()?.at ?: candidate.cancelledAt ?: candidate.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("要删除这条礼物记录吗？")
                }
            },
            confirmButton = {
                Button(onClick = { confirming = true }, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除记录") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(26.dp),
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("删除后你们双方都看不到这条记录，且无法恢复。真的要删除吗？") },
            confirmButton = {
                Button(onClick = { onConfirmDelete(candidate) }, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
        )
    }
}

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
    SettingsPageScaffold(
        "设置", onDismiss, isRefreshing = state.snapshotRefreshing,
        onRefresh = { viewModel.refreshSnapshot() }, refreshEnabled = !state.busy,
    ) { padding ->
        LazyColumn(modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(padding).consumeWindowInsets(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.notice != null) item { InlineNotice(state, viewModel) }
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
            item { SettingsRow("检查更新", "当前版本 ${viewModel.currentVersionName}") { viewModel.checkForUpdate(manual = true) } }
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
private fun SettingsPageScaffold(
    title: String, onBack: () -> Unit, isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null, refreshEnabled: Boolean = true,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { RefreshingTitle(title, isRefreshing) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("‹", fontSize = 30.sp) } },
                    actions = {
                        if (onRefresh != null) IconButton(onClick = onRefresh, enabled = refreshEnabled && !isRefreshing) {
                            Icon(RefreshIcon, contentDescription = "刷新", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            RefreshableContent(
                isRefreshing = isRefreshing, onRefresh = { onRefresh?.invoke() },
                enabled = onRefresh != null && refreshEnabled, modifier = Modifier.fillMaxSize(),
            ) { content(padding) }
        }
    }
}

@Composable private fun AvatarSettings(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val myCard = state.snapshot?.cards?.firstOrNull { it.userId == state.snapshot?.currentUserId }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadAvatarFromUri(context, uri)
    }
    SettingsPageScaffold(
        "我的头像", onBack, isRefreshing = state.snapshotRefreshing,
        onRefresh = { viewModel.refreshSnapshot() }, refreshEnabled = !state.busy,
    ) { padding ->
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (myCard != null) {
                AvatarBubble(state.snapshot.currentUserName, viewModel.avatarUrlFor(myCard.userId, myCard.avatarVersion), viewModel.authToken, size = 120.dp)
            }
            InlineNotice(state, viewModel)
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
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("这是你对 TA 的专属称呼，只对你自己可见。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(nickname, { nickname = it.take(8) }, "昵称（最多 8 个字）", "♡")
            InlineNotice(state, viewModel)
            PrimaryAction("保存", state.busy) { if (viewModel.savePartnerNickname(nickname)) onBack() }
            Text("留空保存即清除昵称，界面会恢复显示对方的名字。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun RulesSettings(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val pending = state.snapshot?.pendingRules
    val settings = state.snapshot?.settings
    var addMin by rememberSaveable { mutableStateOf((settings?.addMin ?: 1).toString()) }
    var addMax by rememberSaveable { mutableStateOf((settings?.addMax ?: 5).toString()) }
    var subtractMin by rememberSaveable { mutableStateOf((settings?.subtractMin ?: 1).toString()) }
    var subtractMax by rememberSaveable { mutableStateOf((settings?.subtractMax ?: 5).toString()) }
    var draftEdited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings) {
        if (!draftEdited) {
            addMin = (settings?.addMin ?: 1).toString()
            addMax = (settings?.addMax ?: 5).toString()
            subtractMin = (settings?.subtractMin ?: 1).toString()
            subtractMax = (settings?.subtractMax ?: 5).toString()
        }
    }
    // Pending requests arrive through snapshot refreshes; fetch fresh state on entry.
    LaunchedEffect(Unit) { viewModel.refreshSnapshot() }
    SettingsPageScaffold(
        "记分规则", onBack, isRefreshing = state.snapshotRefreshing,
        onRefresh = { viewModel.refreshSnapshot() }, refreshEnabled = !state.busy,
    ) { padding ->
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InlineNotice(state, viewModel)
            if (pending != null) {
                PendingRulesCard(state, pending, viewModel)
            } else {
                RulesForm(
                    state, viewModel, addMin, addMax, subtractMin, subtractMax,
                    onAddMin = { draftEdited = true; addMin = it }, onAddMax = { draftEdited = true; addMax = it },
                    onSubtractMin = { draftEdited = true; subtractMin = it }, onSubtractMax = { draftEdited = true; subtractMax = it },
                )
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

@Composable private fun RulesForm(
    state: AppUiState, viewModel: MainViewModel,
    addMin: String, addMax: String, subtractMin: String, subtractMax: String,
    onAddMin: (String) -> Unit, onAddMax: (String) -> Unit,
    onSubtractMin: (String) -> Unit, onSubtractMax: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("修改需要对方同意后才会生效，双方共用一套规则。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("单次加分范围（正数1~100）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("最小", addMin, onValueChange = onAddMin, Modifier.weight(1f))
            NumberField("最大", addMax, onValueChange = onAddMax, Modifier.weight(1f))
        }
        Text("单次扣分范围（正数1~100）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("最小", subtractMin, onValueChange = onSubtractMin, Modifier.weight(1f))
            NumberField("最大", subtractMax, onValueChange = onSubtractMax, Modifier.weight(1f))
        }
        PrimaryAction("请求修改", state.busy) {
            viewModel.requestRulesChange(addMin, addMax, subtractMin, subtractMax)
        }
    }
}

@Composable private fun PresetSettings(state: AppUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    SettingsPageScaffold("添加记录预设", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
            item { AppTextField(state.presetLabel, viewModel::setPresetLabel, "备注，例如：乖乖早睡", "♡") }
            item { Text("备注最多 $NOTE_MAX_LENGTH 个字，常用记录最多保存 $PRESET_MAX_COUNT 条（当前 ${state.scorePresets.size}/$PRESET_MAX_COUNT）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { NumberField("分值（正数加分，负数扣分）", state.presetDelta, viewModel::setPresetDelta, Modifier.weight(1f)); Button(onClick = viewModel::addPreset, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("添加") } } }
            if (state.notice != null) item { InlineNotice(state, viewModel) }
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

