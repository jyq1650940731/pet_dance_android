package app.petdance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.petdance.data.PetRepository
import app.petdance.data.TemplateDto
import app.petdance.ui.SessionViewModel
import app.petdance.ui.components.ErrorText
import app.petdance.ui.components.MutedText
import app.petdance.ui.components.PrimaryButton
import app.petdance.ui.components.ScreenColumn
import app.petdance.ui.theme.Chip
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class WelcomeViewModel(private val repo: PetRepository) : ViewModel() {
    var covers by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            covers = runCatching { repo.templates() }
                .getOrDefault(emptyList())
                .mapNotNull { repo.media(it.coverUrl) }
                .take(6)
        }
    }
}

@Composable
fun WelcomeScreen(session: SessionViewModel, onStart: () -> Unit, onLegal: (String) -> Unit) {
    val vm: WelcomeViewModel = org.koin.androidx.compose.koinViewModel()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ScreenColumn(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Mosaic(vm.covers)
        Text("你的宠物，一条短视频就够了", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        MutedText("选一个模板，上传一张照片，生成可以保存和分享的竖屏视频。")
        ErrorText(error)
        PrimaryButton(if (busy) "请稍候" else "开始", onClick = {
            scope.launch {
                busy = true
                error = null
                try {
                    session.acceptConsent()
                    onStart()
                } catch (e: Exception) {
                    error = e.message ?: "暂时无法继续"
                } finally {
                    busy = false
                }
            }
        }, loading = busy)
        MutedText("继续即表示同意《用户协议》和《隐私政策》。")
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.petdance.ui.components.QuietButton("用户协议") { onLegal("terms") }
            app.petdance.ui.components.QuietButton("隐私政策") { onLegal("privacy") }
        }
    }
}

@Composable
private fun Mosaic(covers: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        covers.chunked(3).forEach { row ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(10.dp)).background(Chip),
                    )
                }
                repeat(3 - row.size) {
                    Box(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(10.dp)).background(Chip))
                }
            }
        }
        if (covers.isEmpty()) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { Box(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(10.dp)).background(Chip)) }
            }
        }
    }
}

@Composable
fun GuideScreen(session: SessionViewModel, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var pet by remember { mutableStateOf(session.state.value.petType) }
    var interest by remember { mutableStateOf(session.state.value.interest) }
    val scope = rememberCoroutineScope()
    val title = if (step == 1) "你家是哪种宠物？" else "想先看哪一类？"
    val hint = if (step == 1) {
        "用来把合适的模板排在前面。其他宠物可以做，不单独保证效果。每次一张照片、一只入镜。"
    } else {
        "只定位首页分类，模板都还在。"
    }
    val options = if (step == 1) {
        listOf("dog" to "狗", "cat" to "猫", "other" to "其他")
    } else {
        listOf("dance" to "跳舞", "comedy" to "搞怪", "daily" to "日常")
    }
    val selected = if (step == 1) pet else interest
    ScreenColumn(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MutedText("第 $step / 2 步")
            app.petdance.ui.components.QuietButton("跳过") {
                scope.launch {
                    session.finishGuide(pet, interest)
                    onDone()
                }
            }
        }
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        MutedText(hint)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { (value, label) ->
                val on = selected == value
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1.1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) app.petdance.ui.theme.Accent else Chip)
                        .clickable {
                            if (step == 1) pet = value else interest = value
                        }
                        .padding(12.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) app.petdance.ui.theme.OnAccent else app.petdance.ui.theme.Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        PrimaryButton("继续", enabled = selected != null, onClick = {
            if (step == 1) {
                step = 2
            } else {
                scope.launch {
                    session.finishGuide(pet, interest)
                    onDone()
                }
            }
        })
    }
}

@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val title = if (doc == "privacy") "隐私政策" else "用户协议"
    ScreenColumn(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        app.petdance.ui.components.QuietButton("返回", onClick = onBack)
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        MutedText("以下为产品草案，上线前由顾问定稿。点价值页的「开始」即记录同意当前版本，之后才出现手机号。")
        Text(if (doc == "privacy") PRIVACY else TERMS, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

private val TERMS = """
pet_dance 让你用一张宠物照片和视频模板生成竖屏短视频。

你需要确认照片里的宠物由你饲养，或你已经获得使用同意。照片不应包含需要授权的人物主体。产品不提供指定某个真人的能力。

点数购买后不过期，不设订阅。生成时先冻结点数，成功后扣减，失败退回。

应用内不展示虚构用户评价。商店评分只在第一次成功出片后询问。
""".trimIndent()

private val PRIVACY = """
原图和成片只用于生成、向你展示、保存和客服排障，不用于对外销售训练数据。

手机号用于登录和同一号码仅一次的新用户赠送。日志不保留手机号全文。

你可以删除作品。注销账号会删除账号、作品索引，以及对象存储中的原图和成片。订单记录按财税需要保留脱敏后的交易号与金额。

同意发生在收集手机号之前。
""".trimIndent()
