package app.petdance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.petdance.data.PetRepository
import app.petdance.data.TemplateDto
import app.petdance.domain.formatDuration
import app.petdance.ui.components.ErrorText
import app.petdance.ui.components.LoadingBox
import app.petdance.ui.components.LoopVideo
import app.petdance.ui.components.MutedText
import app.petdance.ui.components.PrimaryButton
import app.petdance.ui.components.QuietButton
import app.petdance.ui.theme.AppBackground
import kotlinx.coroutines.launch

class DetailViewModel(private val id: String, private val repo: PetRepository) : ViewModel() {
    var template by mutableStateOf<TemplateDto?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                template = repo.template(id)
            } catch (e: Exception) {
                error = e.message ?: "模板不存在"
            }
        }
    }

    fun previewUrl(): String? = repo.media(template?.previewUrl)
}

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, onCreate: (TemplateDto) -> Unit) {
    val vm: DetailViewModel = org.koin.androidx.compose.koinViewModel { org.koin.core.parameter.parametersOf(id) }
    val template = vm.template
    var muted by remember { mutableStateOf(true) }
    var playError by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietButton("返回", onClick = onBack)
        when {
            template == null && vm.error == null -> LoadingBox()
            template == null -> ErrorText(vm.error)
            else -> {
                val preview = vm.previewUrl()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(18.dp))
                        .then(Modifier),
                ) {
                    if (preview != null && playError == null) {
                        LoopVideo(
                            url = preview,
                            muted = muted,
                            modifier = Modifier.fillMaxSize(),
                            onError = { playError = it },
                        )
                    } else {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                            Text(playError ?: "暂无预览片", color = app.petdance.ui.theme.Ink)
                        }
                    }
                    Text(
                        "预览片",
                        modifier = Modifier
                            .padding(10.dp)
                            .background(app.petdance.ui.theme.Ink, RoundedCornerShape(99.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = AppBackground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(template.title, style = MaterialTheme.typography.headlineMedium)
                MutedText(template.subtitle.ifBlank { "一条 ${template.durationSec} 秒的竖屏短视频" })
                MutedText("${formatDuration(template.durationSec)} · ${template.creditCost} 点")
                QuietButton(if (muted) "打开声音" else "关闭声音") { muted = !muted }
                ErrorText(playError)
                PrimaryButton("用这张照片生成") { onCreate(template) }
            }
        }
    }
}
