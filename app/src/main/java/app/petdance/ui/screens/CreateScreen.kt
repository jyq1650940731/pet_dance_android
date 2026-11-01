package app.petdance.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.petdance.data.ApiException
import app.petdance.data.PetRepository
import app.petdance.data.TemplateDto
import app.petdance.media.decodeOriented
import app.petdance.media.exportPortraitJpeg
import app.petdance.ui.SessionViewModel
import app.petdance.ui.components.ErrorText
import app.petdance.ui.components.LoadingBox
import app.petdance.ui.components.MutedText
import app.petdance.ui.components.PrimaryButton
import app.petdance.ui.components.QuietButton
import app.petdance.ui.components.SecondaryButton
import app.petdance.ui.theme.Chip
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class CreateViewModel(private val templateId: String, private val repo: PetRepository) : ViewModel() {
    var template by mutableStateOf<TemplateDto?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var jpeg by mutableStateOf<ByteArray?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    private var idempotency: String? = null

    init {
        viewModelScope.launch {
            try {
                template = repo.template(templateId)
            } catch (e: Exception) {
                loadError = e.message ?: "模板不存在"
            }
        }
    }

    fun replacePhoto(bytes: ByteArray?) {
        jpeg = bytes
        idempotency = null
        error = null
    }

    fun submit(onDone: (String) -> Unit) {
        val bytes = jpeg ?: return
        if (submitting) return
        submitting = true
        error = null
        val key = idempotency ?: repo.newIdempotencyKey().also { idempotency = it }
        viewModelScope.launch {
            try {
                val task = repo.uploadAndCreate(templateId, bytes, key)
                onDone(task.id)
            } catch (e: Exception) {
                error = e.message ?: "提交失败"
                val code = (e as? ApiException)?.code
                if (code != null && code != "NETWORK" && code != "UPLOAD_FAILED") idempotency = null
            } finally {
                submitting = false
            }
        }
    }
}

@Composable
fun CreateScreen(
    id: String,
    session: SessionViewModel,
    onBack: () -> Unit,
    onLogin: (Int) -> Unit,
    onPay: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val vm: CreateViewModel = koinViewModel { org.koin.core.parameter.parametersOf(id) }
    val state by session.state.collectAsStateWithLifecycle()
    val template = vm.template
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var ownership by remember { mutableStateOf(false) }
    var ownershipError by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun adopt(uri: Uri) {
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeOriented(context, uri) }
                source = bitmap
                userScale = 1f
                offset = Offset.Zero
                vm.replacePhoto(null)
            } catch (e: Exception) {
                session.toast(e.message ?: "无法读取这张照片")
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) adopt(uri)
    }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::adopt)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            session.toast("需要相机权限才能拍照")
            return@rememberLauncherForActivityResult
        }
        val file = File(context.cacheDir, "capture.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        capture.launch(uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietButton("返回", onClick = onBack)
        when {
            template == null && vm.loadError == null -> LoadingBox()
            template == null -> ErrorText(vm.loadError)
            else -> {
                Text(template.title, style = MaterialTheme.typography.headlineMedium)
                MutedText("将生成一条 ${template.durationSec} 秒竖屏短视频。可以离开页面，完成后在我的作品查看。")
                if (state.meta?.maintenance == true) {
                    MutedText(state.meta?.maintenanceMessage?.ifBlank { null } ?: "生成通道维护中，暂时不能新建。")
                }
                val cropped = vm.jpeg
                when {
                    source != null && cropped == null -> {
                        CropBox(
                            bitmap = source!!,
                            scale = userScale,
                            offset = offset,
                            onTransform = { zoom, pan ->
                                userScale = (userScale * zoom).coerceIn(1f, 4f)
                                offset += pan
                            },
                            onFrame = { frame = it },
                        )
                        MutedText("一只宠物、正面或三分之二侧面、光线清楚、少遮挡。双指缩放，拖动调整位置。")
                        PrimaryButton("使用这个裁剪") {
                            val bitmap = source ?: return@PrimaryButton
                            if (frame.width < 2 || frame.height < 2) return@PrimaryButton
                            try {
                                vm.replacePhoto(
                                    exportPortraitJpeg(
                                        bitmap,
                                        frame.width.toFloat(),
                                        frame.height.toFloat(),
                                        userScale,
                                        offset.x,
                                        offset.y,
                                    ),
                                )
                            } catch (e: Exception) {
                                session.toast(e.message ?: "裁剪失败")
                            }
                        }
                    }
                    cropped != null -> {
                        AsyncImage(
                            model = cropped,
                            contentDescription = "已裁剪的照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp)),
                        )
                        QuietButton("重新选图") {
                            source = null
                            vm.replacePhoto(null)
                        }
                    }
                    else -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Chip),
                        )
                        MutedText("一只宠物、正面或三分之二侧面、光线清楚、少遮挡。")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("相册", modifier = Modifier.weight(1f), onClick = {
                        pick.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    })
                    SecondaryButton("拍照", modifier = Modifier.weight(1f), onClick = {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    })
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = ownership, onCheckedChange = {
                        ownership = it
                        ownershipError = false
                    })
                    Text("这是我饲养或已获同意使用的宠物，照片不含需要授权的人物主体。", style = MaterialTheme.typography.bodySmall)
                }
                if (ownershipError) ErrorText("请先确认对照片有使用权。")
                ErrorText(vm.error)
                val balance = state.user?.credits?.balance
                val cost = template.creditCost
                val label = when {
                    state.user == null -> "登录后生成"
                    state.meta?.maintenance == true -> "维护中"
                    balance != null && balance < cost -> "点数不足，去购买"
                    else -> "生成 · $cost 点"
                }
                PrimaryButton(label, loading = vm.submitting, enabled = state.meta?.maintenance != true) {
                    if (vm.jpeg == null) {
                        session.toast("请先选择照片并完成裁剪")
                        return@PrimaryButton
                    }
                    if (!ownership) {
                        ownershipError = true
                        return@PrimaryButton
                    }
                    when {
                        state.user == null -> onLogin(cost)
                        balance != null && balance < cost -> onPay()
                        else -> vm.submit { taskId ->
                            session.refreshUser()
                            onCreated(taskId)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropBox(
    bitmap: Bitmap,
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit,
    onFrame: (IntSize) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(18.dp))
            .background(Chip)
            .onSizeChanged(onFrame)
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan) }
            },
    ) {
        val cover = max(size.width / bitmap.width, size.height / bitmap.height) * scale.coerceAtLeast(1f)
        val dw = bitmap.width * cover
        val dh = bitmap.height * cover
        val maxX = ((dw - size.width) / 2f).coerceAtLeast(0f)
        val maxY = ((dh - size.height) / 2f).coerceAtLeast(0f)
        val left = (size.width - dw) / 2f + offset.x.coerceIn(-maxX, maxX)
        val top = (size.height - dh) / 2f + offset.y.coerceIn(-maxY, maxY)
        drawImage(
            image = image,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
        )
    }
}
