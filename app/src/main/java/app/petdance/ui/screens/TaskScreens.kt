package app.petdance.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.petdance.data.GenerationDto
import app.petdance.data.PetRepository
import app.petdance.domain.GENERATION_STEPS
import app.petdance.domain.StepMark
import app.petdance.domain.formatWhen
import app.petdance.domain.generationStep
import app.petdance.media.saveVideoToGallery
import app.petdance.ui.components.Banner
import app.petdance.ui.components.ErrorText
import app.petdance.ui.components.LoadingBox
import app.petdance.ui.components.LoopVideo
import app.petdance.ui.components.MutedText
import app.petdance.ui.components.PrimaryButton
import app.petdance.ui.components.QuietButton
import app.petdance.ui.components.SecondaryButton
import app.petdance.ui.theme.Accent
import app.petdance.ui.theme.Good
import app.petdance.ui.theme.Ink
import app.petdance.ui.theme.Muted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

class GeneratingViewModel(private val id: String, private val repo: PetRepository) : ViewModel() {
    var item by mutableStateOf<GenerationDto?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val next = repo.generation(id)
                    item = next
                    error = null
                    if (next.status == "succeeded" || next.status == "failed") break
                } catch (e: Exception) {
                    error = e.message ?: "网络不可用"
                }
                delay(3_000)
            }
        }
    }
}

@Composable
fun GeneratingScreen(id: String, onWorks: () -> Unit, onSucceeded: () -> Unit, onFailed: () -> Unit) {
    val vm: GeneratingViewModel = koinViewModel { parametersOf(id) }
    val item = vm.item
    LaunchedEffect(item?.status) {
        when (item?.status) {
            "succeeded" -> onSucceeded()
            "failed" -> onFailed()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("生成中", style = MaterialTheme.typography.headlineMedium)
        Text(item?.templateTitle ?: "正在查询", style = MaterialTheme.typography.titleMedium)
        if (item == null && vm.error == null) LoadingBox()
        GENERATION_STEPS.forEachIndexed { index, label ->
            val mark = generationStep(item?.status ?: "queued", index)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "●",
                    color = when (mark) {
                        StepMark.Done -> Good
                        StepMark.Current -> Accent
                        StepMark.Pending -> Muted
                    },
                )
                Text(label, color = if (mark == StepMark.Pending) Muted else Ink)
            }
        }
        MutedText("可以离开此页，不需要把应用留在前台。完成后到我的作品查看。")
        ErrorText(vm.error)
        PrimaryButton("去我的作品", onClick = onWorks)
    }
}

class ResultViewModel(private val id: String, private val repo: PetRepository) : ViewModel() {
    var item by mutableStateOf<GenerationDto?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                item = repo.generation(id)
            } catch (e: Exception) {
                error = e.message ?: "作品不存在"
            }
        }
    }

    fun file(onReady: (File) -> Unit, onError: (String) -> Unit) {
        val url = item?.videoUrl
        if (url.isNullOrBlank()) {
            onError("视频暂时无法下载")
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                onReady(repo.download(url, "pet-$id.mp4"))
            } catch (e: Exception) {
                onError(e.message ?: "视频暂时无法下载")
            } finally {
                busy = false
            }
        }
    }

    fun remove(onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteGeneration(id)
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "删除失败")
            }
        }
    }
}

@Composable
fun ResultScreen(
    id: String,
    showRate: Boolean,
    onBackHome: () -> Unit,
    onAgain: (String) -> Unit,
    onReport: () -> Unit,
    onRate: () -> Unit,
    onDismissRate: () -> Unit,
    onDeleted: () -> Unit,
    onToast: (String) -> Unit,
) {
    val vm: ResultViewModel = koinViewModel { parametersOf(id) }
    val repo: PetRepository = koinInject()
    val context = LocalContext.current
    var playError by remember { mutableStateOf<String?>(null) }
    val item = vm.item
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save(vm, context, onToast) else onToast("需要存储权限才能保存到相册")
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            item == null && vm.error == null -> LoadingBox()
            item == null -> ErrorText(vm.error)
            else -> {
                Text(item.templateTitle, style = MaterialTheme.typography.headlineMedium)
                MutedText(formatWhen(item.finishedAt ?: item.createdAt))
                val video = repo.media(item.videoUrl)
                if (item.playable && !video.isNullOrBlank() && playError == null) {
                    LoopVideo(
                        url = video,
                        muted = false,
                        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
                        onError = { playError = it },
                    )
                } else {
                    Text(playError ?: item.errorMessage ?: "视频暂时无法播放")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("保存到相册", modifier = Modifier.weight(1f), enabled = item.playable && !vm.busy) {
                        if (Build.VERSION.SDK_INT <= 28 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            save(vm, context, onToast)
                        }
                    }
                    SecondaryButton("分享", modifier = Modifier.weight(1f), enabled = item.playable && !vm.busy) {
                        vm.file(onReady = { file -> shareVideo(context, file) }, onError = onToast)
                    }
                }
                if (showRate && item.playable) {
                    Banner("这条还满意吗？去应用商店评分。只问这一次。")
                    Row {
                        QuietButton("去评分", onClick = onRate)
                        QuietButton("关闭", onClick = onDismissRate)
                    }
                }
                PrimaryButton("用同一模板再做一条") { onAgain(item.templateId) }
                QuietButton("返回首页", onClick = onBackHome)
                QuietButton("举报", onClick = onReport)
                QuietButton("删除作品") { vm.remove(onDeleted, onToast) }
            }
        }
    }
}

private fun save(vm: ResultViewModel, context: android.content.Context, onToast: (String) -> Unit) {
    vm.file(
        onReady = { file ->
            try {
                saveVideoToGallery(context, file)
                onToast("已保存到相册")
            } catch (e: Exception) {
                onToast(e.message ?: "保存失败")
            }
        },
        onError = onToast,
    )
}

private fun shareVideo(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

@Composable
fun FailedScreen(id: String, onRetry: (String) -> Unit, onHome: () -> Unit) {
    val vm: ResultViewModel = koinViewModel { parametersOf(id) }
    val item = vm.item
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("没有生成成功", style = MaterialTheme.typography.headlineMedium)
        Text(item?.errorMessage ?: vm.error ?: "生成失败，点数已退回")
        MutedText("这次没有扣除可用点数。再试一次会按新任务计费。")
        if (item == null && vm.error == null) LoadingBox()
        PrimaryButton("再试一次", enabled = !item?.templateId.isNullOrBlank()) {
            onRetry(item?.templateId.orEmpty())
        }
        QuietButton("返回首页", onClick = onHome)
    }
}
