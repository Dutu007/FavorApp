package com.dutu007.favorapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dutu007.favorapp.data.CoupleSnapshot
import com.dutu007.favorapp.data.ScoreEventItem
import com.dutu007.favorapp.ui.theme.FavorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FavorTheme { FavorApp() } }
    }
}

@Composable
private fun FavorApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingScreen()
            !state.authenticated -> AuthScreen(state, viewModel)
            state.snapshot == null -> PairingScreen(state, viewModel)
            else -> HomeScreen(state.snapshot!!, state, viewModel)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("正在连接 FavorApp…")
    }
}

@Composable
private fun Notice(state: AppUiState) {
    val text = state.error ?: state.message ?: return
    val color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun AuthScreen(state: AppUiState, viewModel: MainViewModel) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text("FavorApp", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("把每一次心动都记下来", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.authMode == AuthMode.SIGN_IN) {
                Button(onClick = {}, enabled = false) { Text("登录") }
                OutlinedButton(onClick = { viewModel.setAuthMode(AuthMode.SIGN_UP) }) { Text("注册") }
            } else {
                OutlinedButton(onClick = { viewModel.setAuthMode(AuthMode.SIGN_IN) }) { Text("登录") }
                Button(onClick = {}, enabled = false) { Text("注册") }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.authMode == AuthMode.SIGN_UP) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::setDisplayName,
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::setEmail,
            label = { Text("账号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text("密码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Notice(state)
        Button(
            onClick = viewModel::submitAuth,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (state.authMode == AuthMode.SIGN_IN) "登录" else "创建账户")
        }
        Spacer(Modifier.height(12.dp))
        Text("账号 3-20 位；密码 8-64 位，需包含大小写字母、数字和特殊字符", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingScreen(state: AppUiState, viewModel: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接恋人") },
                actions = { TextButton(onClick = viewModel::signOut) { Text("退出") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("两个人绑定后，就能看到彼此的好感度和记录。", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("生成我的邀请码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("生成后复制给恋人，由对方在下方输入。邀请码 24 小时内有效。")
                    Spacer(Modifier.height(16.dp))
                    if (state.generatedInvite != null) {
                        Text(state.generatedInvite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.generatedInvite)) }) {
                            Text("复制邀请码")
                        }
                    } else {
                        Button(onClick = viewModel::createInvite, enabled = !state.busy) { Text("生成邀请码") }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("输入恋人的邀请码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.inviteCode,
                        onValueChange = viewModel::setInviteCode,
                        label = { Text("邀请码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::acceptInvite, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("确认匹配")
                    }
                }
            }
            Notice(state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(snapshot: CoupleSnapshot, state: AppUiState, viewModel: MainViewModel) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var showNoteHint by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FavorApp") },
                actions = {
                    TextButton(onClick = { showSettings = true }) { Text("设置") }
                    TextButton(onClick = viewModel::signOut) { Text("退出") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("你好，${snapshot.currentUserName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("今天也给对方一点好感吧。", color = MaterialTheme.colorScheme.secondary)
                Notice(state)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    snapshot.cards.forEach { ScoreCardView(it.name, it.score, Modifier.weight(1f)) }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("给 ${snapshot.partnerName} 加减分", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(-5, -1, 1, 5).forEach { delta ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.addScore(delta, note)
                                        note = ""
                                    },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (delta > 0) "+$delta" else delta.toString()) }
                            }
                        }
                        if (showNoteHint) Text("分数由数据库事务校验上下限后写入", style = MaterialTheme.typography.bodySmall)
                        else TextButton(onClick = { showNoteHint = true }) { Text("分数怎么算？") }
                    }
                }
            }
            item {
                Text("好感度记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (snapshot.events.isEmpty()) {
                item { Text("还没有记录，成为第一个加分的人吧。", color = MaterialTheme.colorScheme.secondary) }
            } else {
                items(snapshot.events, key = { it.id }) { event -> EventRow(event) }
            }
        }
    }
    if (showSettings) {
        SettingsDialog(snapshot, state.busy, viewModel) { showSettings = false }
    }
}

@Composable
private fun ScoreCardView(name: String, score: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(score.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("收到的好感度", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EventRow(event: ScoreEventItem) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${event.actorName} → ${event.targetName}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                text = if (event.delta > 0) "+${event.delta}" else event.delta.toString(),
                color = if (event.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        Text("分数变为 ${event.scoreAfter} · ${formatTime(event.createdAt)}", style = MaterialTheme.typography.bodySmall)
        if (!event.note.isNullOrBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun SettingsDialog(
    snapshot: CoupleSnapshot,
    busy: Boolean,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    var initial by rememberSaveable { mutableStateOf(snapshot.settings.initialScore.toString()) }
    var min by rememberSaveable { mutableStateOf(snapshot.settings.minScore?.toString().orEmpty()) }
    var max by rememberSaveable { mutableStateOf(snapshot.settings.maxScore?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("好感度设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("初始值只会在还没有任何记录时影响当前分数；留空上下限表示不限制。", style = MaterialTheme.typography.bodySmall)
                NumberField("初始值", initial) { initial = it }
                NumberField("最低值（可选）", min) { min = it }
                NumberField("最高值（可选）", max) { max = it }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.saveSettings(initial, min, max); onDismiss() }, enabled = !busy) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it == '-' || it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatTime(value: String): String = value.replace('T', ' ').take(16)
