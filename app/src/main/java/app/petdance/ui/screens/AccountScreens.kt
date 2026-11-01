package app.petdance.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.petdance.BuildConfig
import app.petdance.data.GenerationDto
import app.petdance.data.PetRepository
import app.petdance.domain.formatWhen
import app.petdance.domain.isActiveStatus
import app.petdance.domain.petLabel
import app.petdance.domain.worksLabel
import app.petdance.ui.SessionViewModel
import app.petdance.ui.components.Banner
import app.petdance.ui.components.ErrorText
import app.petdance.ui.components.LoadingBox
import app.petdance.ui.components.MutedText
import app.petdance.ui.components.PillTone
import app.petdance.ui.components.PrimaryButton
import app.petdance.ui.components.QuietButton
import app.petdance.ui.components.StatusPill
import app.petdance.ui.theme.Line
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class WorksViewModel(private val repo: PetRepository) : ViewModel() {
    var items by mutableStateOf<List<GenerationDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            loading = items.isEmpty()
            error = null
            try {
                items = repo.generations()
            } catch (e: Exception) {
                error = e.message ?: "作品加载失败"
            } finally {
                loading = false
            }
        }
    }

    fun remove(id: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteGeneration(id)
                items = items.filterNot { it.id == id }
            } catch (e: Exception) {
                onError(e.message ?: "删除失败")
            }
        }
    }
}

@Composable
fun MeScreen(session: SessionViewModel, onLogin: () -> Unit, onWorks: () -> Unit, onOrders: () -> Unit, onSettings: () -> Unit, onBuy: () -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        if (!state.loggedIn) {
            MutedText("登录后查看点数、作品和订单。")
            PrimaryButton("登录", onClick = onLogin)
        } else {
            val credits = state.user?.credits
            Banner("${credits?.balance ?: 0} 点可用" + (credits?.frozen?.takeIf { it > 0 }?.let { "，冻结 $it 点" } ?: "") + "。点数不过期。", "购买", onBuy)
            QuietButton("我的作品", onClick = onWorks)
            HorizontalDivider(color = Line)
            QuietButton("订单", onClick = onOrders)
            HorizontalDivider(color = Line)
            QuietButton("设置", onClick = onSettings)
            MutedText(state.user?.phoneMasked.orEmpty())
        }
    }
}

@Composable
fun WorksScreen(onOpen: (GenerationDto) -> Unit, onBack: () -> Unit, onToast: (String) -> Unit) {
    val vm: WorksViewModel = koinViewModel()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietButton("返回", onClick = onBack)
        Text("我的作品", style = MaterialTheme.typography.headlineMedium)
        ErrorText(vm.error)
        when {
            vm.loading && vm.items.isEmpty() -> LoadingBox()
            vm.items.isEmpty() -> MutedText("还没有作品。选一个模板即可开始。")
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(vm.items, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.templateTitle)
                            MutedText(formatWhen(item.createdAt))
                        }
                        StatusPill(
                            worksLabel(item.status, item.playable, item.errorMessage),
                            when {
                                item.playable -> PillTone.Good
                                item.status == "failed" -> PillTone.Bad
                                isActiveStatus(item.status) -> PillTone.Warn
                                else -> PillTone.Neutral
                            },
                        )
                        QuietButton("删除") { vm.remove(item.id, onToast) }
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
fun OrdersScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QuietButton("返回", onClick = onBack)
        Text("订单", style = MaterialTheme.typography.headlineMedium)
        MutedText("支付上线后，已支付的加油包会显示在这里。当前服务端还没有订单接口。")
    }
}

private data class Pack(val name: String, val credits: Int, val price: String, val each: String)

@Composable
fun PayScreen(session: SessionViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    val packs = listOf(
        Pack("体验包", 10, "¥6", "折合 ¥6/条"),
        Pack("常用包", 60, "¥30", "折合 ¥5/条"),
        Pack("超量包", 150, "¥68", "折合 ¥4.5/条"),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietButton("返回", onClick = onBack)
        Text("购买点数", style = MaterialTheme.typography.headlineMedium)
        MutedText(
            if (state.loggedIn) "当前 ${state.user?.credits?.balance ?: 0} 点。支付成功后余额会更新。"
            else "登录后购买。",
        )
        packs.forEach { pack ->
            Banner("${pack.name} · ${pack.credits} 点，${pack.each}", pack.price)
        }
        MutedText("支付渠道按实际上架商店实现。这一版服务端还没有加油包和支付接口，确认支付不会扣款，也不会加点。")
        PrimaryButton(if (state.loggedIn) "支付尚未开放" else "登录后购买", enabled = !state.loggedIn) {
            if (!state.loggedIn) onLogin()
        }
    }
}

@Composable
fun SettingsScreen(
    session: SessionViewModel,
    onBack: () -> Unit,
    onLegal: (String) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    var api by remember(state.apiBase) { mutableStateOf(state.apiBase) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietButton("返回", onClick = onBack)
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text("宠物类型")
        listOf(null to "未填", "dog" to "狗", "cat" to "猫", "other" to "其他").forEach { (value, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                session.updatePet(value)
            }) {
                RadioButton(selected = state.effectivePet == value || (value == null && state.effectivePet.isNullOrBlank()), onClick = { session.updatePet(value) })
                Text(label)
            }
        }
        MutedText("当前：${petLabel(state.effectivePet)}。改完后首页会按新类型重新排序。")
        QuietButton("用户协议") { onLegal("terms") }
        QuietButton("隐私政策") { onLegal("privacy") }
        QuietButton("意见反馈") { feedback = true }
        if (state.loggedIn) {
            QuietButton("退出登录") {
                session.logout()
                onLoggedOut()
            }
            QuietButton("注销账号") { confirmDelete = true }
        }
        if (BuildConfig.DEBUG) {
            Text("调试接口")
            OutlinedTextField(
                value = api,
                onValueChange = { api = it },
                label = { Text("API 地址") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            MutedText("模拟器用 http://10.0.2.2:8080/ 。真机可 adb reverse 后用 http://127.0.0.1:8080/ ，或填写电脑的局域网地址。封面和成片里的 127.0.0.1 会改写成这个地址。")
            QuietButton("保存接口地址") { session.saveApiBase(api) }
        }
        MutedText("版本 ${BuildConfig.VERSION_NAME}")
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("注销账号") },
            text = { Text("将删除账号、作品索引，以及原图和成片。订单记录会按要求保留脱敏信息。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    session.deleteAccount(onLoggedOut)
                }) { Text("注销") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    if (feedback) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { feedback = false },
            title = { Text("意见反馈") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "pet_dance 反馈\n$text")
                    }
                    context.startActivity(Intent.createChooser(send, "意见反馈"))
                    feedback = false
                }) { Text("分享") }
            },
            dismissButton = { TextButton(onClick = { feedback = false }) { Text("取消") } },
        )
    }
}

@Composable
fun ReportScreen(id: String, title: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reason by remember { mutableStateOf("不像我的宠物") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QuietButton("返回", onClick = onBack)
        Text("举报", style = MaterialTheme.typography.headlineMedium)
        MutedText("举报接口还没有上线。可以选择原因，通过系统分享把内容发给客服。")
        listOf("不像我的宠物", "盗用了这只宠物", "内容不合适").forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { reason = item }) {
                RadioButton(selected = reason == item, onClick = { reason = item })
                Text(item)
            }
        }
        PrimaryButton("通过系统分享发出") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "pet_dance 举报\n作品：$id\n模板：$title\n原因：$reason")
            }
            context.startActivity(Intent.createChooser(send, "举报"))
        }
    }
}
