package app.petdance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.petdance.BuildConfig
import app.petdance.data.PetRepository
import app.petdance.data.TokenDto
import app.petdance.domain.normalizePhone
import app.petdance.ui.components.ErrorText
import app.petdance.ui.components.MutedText
import app.petdance.ui.components.PrimaryButton
import app.petdance.ui.components.QuietButton
import app.petdance.ui.components.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginViewModel(private val repo: PetRepository) : ViewModel() {
    var sending by mutableStateOf(false)
        private set
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var cooldown by mutableIntStateOf(0)
        private set

    fun send(phone: String) {
        val normalized = normalizePhone(phone)
        if (normalized == null) {
            error = "请输入中国大陆手机号"
            return
        }
        if (cooldown > 0 || sending) return
        viewModelScope.launch {
            sending = true
            error = null
            try {
                val result = repo.sendSms(normalized)
                cooldown = result.cooldownSec.coerceAtLeast(1)
                while (cooldown > 0) {
                    delay(1000)
                    cooldown -= 1
                }
            } catch (e: Exception) {
                error = e.message ?: "验证码发送失败"
                val retry = (e as? app.petdance.data.ApiException)?.retryAfter
                if (retry != null) {
                    cooldown = retry
                    while (cooldown > 0) {
                        delay(1000)
                        cooldown -= 1
                    }
                }
            } finally {
                sending = false
            }
        }
    }

    fun login(
        phone: String,
        code: String,
        petType: String?,
        terms: String,
        privacy: String,
        onDone: (TokenDto) -> Unit,
    ) {
        val normalized = normalizePhone(phone)
        if (normalized == null) {
            error = "请输入中国大陆手机号"
            return
        }
        if (!Regex("^\\d{6}$").matches(code.trim())) {
            error = "请输入 6 位验证码"
            return
        }
        if (terms.isBlank() || privacy.isBlank()) {
            error = "请先在价值页同意用户协议和隐私政策"
            return
        }
        viewModelScope.launch {
            submitting = true
            error = null
            try {
                onDone(repo.login(normalized, code.trim(), petType, terms, privacy))
            } catch (e: Exception) {
                error = e.message ?: "登录失败"
            } finally {
                submitting = false
            }
        }
    }
}

@Composable
fun LoginScreen(
    session: app.petdance.ui.SessionViewModel,
    onBack: () -> Unit,
    onSuccess: (TokenDto) -> Unit,
) {
    val vm: LoginViewModel = org.koin.androidx.compose.koinViewModel()
    val state by session.state.collectAsStateWithLifecycle()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietButton("返回", onClick = onBack)
        Text("登录", style = MaterialTheme.typography.headlineMedium)
        MutedText("登录后回到刚才的页面。新用户赠送 ${state.meta?.giftCredits ?: 20} 点。")
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(16) },
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            label = { Text("验证码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (BuildConfig.DEBUG) MutedText("开发环境验证码是 ${BuildConfig.DEV_SMS_CODE}")
        SecondaryButton(
            text = if (vm.cooldown > 0) "${vm.cooldown} 秒后可重发" else "获取验证码",
            enabled = vm.cooldown == 0 && !vm.sending,
            onClick = { vm.send(phone) },
        )
        ErrorText(vm.error)
        PrimaryButton("登录", loading = vm.submitting) {
            vm.login(
                phone = phone,
                code = code,
                petType = state.effectivePet,
                terms = state.termsVersion,
                privacy = state.privacyVersion,
                onDone = onSuccess,
            )
        }
    }
}
