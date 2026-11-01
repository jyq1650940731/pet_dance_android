package app.petdance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.petdance.data.AppPrefs
import app.petdance.data.MetaDto
import app.petdance.data.PetRepository
import app.petdance.data.TokenDto
import app.petdance.data.TokenHolder
import app.petdance.data.UserDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUi(
    val booting: Boolean = true,
    val consented: Boolean = false,
    val guideDone: Boolean = false,
    val user: UserDto? = null,
    val meta: MetaDto? = null,
    val petChosen: Boolean = false,
    val petType: String? = null,
    val interest: String? = null,
    val rateDismissed: Boolean = false,
    val termsVersion: String = "",
    val privacyVersion: String = "",
    val apiBase: String = "",
) {
    val loggedIn: Boolean get() = user != null
    val effectivePet: String? get() = if (petChosen) petType else user?.petType
}

class SessionViewModel(
    private val repo: PetRepository,
    private val prefs: AppPrefs,
    private val holder: TokenHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionUi())
    val state = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch { boot() }
        viewModelScope.launch {
            holder.expired.collect {
                _state.update { it.copy(user = null) }
                toast("登录已失效")
            }
        }
    }

    fun toast(text: String) {
        _messages.tryEmit(text)
    }

    private suspend fun boot() {
        val snap = prefs.snapshot()
        holder.access = snap.accessToken
        holder.refresh = snap.refreshToken
        val meta = runCatching { repo.meta() }.getOrNull()
        var user: UserDto? = null
        if (!snap.accessToken.isNullOrBlank()) {
            user = runCatching { repo.me() }.getOrNull()
        }
        var petChosen = snap.petChosen
        var petType = snap.petType
        if (!petChosen && !user?.petType.isNullOrBlank()) {
            petChosen = true
            petType = user?.petType
            prefs.savePet(petType)
        }
        val consented = when {
            meta == null -> !snap.termsVersion.isNullOrBlank() && !snap.privacyVersion.isNullOrBlank()
            else -> snap.termsVersion == meta.termsVersion && snap.privacyVersion == meta.privacyVersion
        }
        _state.value = SessionUi(
            booting = false,
            consented = consented,
            guideDone = snap.guideDone,
            user = user,
            meta = meta,
            petChosen = petChosen,
            petType = petType,
            interest = snap.interest,
            rateDismissed = snap.rateDismissed,
            termsVersion = meta?.termsVersion ?: snap.termsVersion.orEmpty(),
            privacyVersion = meta?.privacyVersion ?: snap.privacyVersion.orEmpty(),
            apiBase = snap.apiBase ?: app.petdance.BuildConfig.API_BASE_URL,
        )
    }

    suspend fun acceptConsent() {
        val meta = _state.value.meta ?: repo.meta()
        prefs.saveConsent(meta.termsVersion, meta.privacyVersion)
        _state.update {
            it.copy(
                consented = true,
                meta = meta,
                termsVersion = meta.termsVersion,
                privacyVersion = meta.privacyVersion,
            )
        }
    }

    suspend fun finishGuide(pet: String?, interest: String?) {
        if (pet != null) prefs.savePet(pet)
        if (interest != null) prefs.saveInterest(interest)
        prefs.markGuideDone()
        _state.update {
            it.copy(
                guideDone = true,
                petChosen = if (pet != null) true else it.petChosen,
                petType = pet ?: it.petType,
                interest = interest ?: it.interest,
            )
        }
    }

    fun setInterest(interest: String?) {
        viewModelScope.launch {
            prefs.saveInterest(interest)
            _state.update { it.copy(interest = interest) }
        }
    }

    fun onLoggedIn(token: TokenDto) {
        val user = token.user ?: return
        holder.update(token.accessToken, token.refreshToken)
        viewModelScope.launch {
            prefs.saveTokens(token.accessToken, token.refreshToken)
            val pet = _state.value.effectivePet
            if (_state.value.petChosen && pet != user.petType) {
                runCatching { repo.patchPet(pet) }
            }
            val fresh = runCatching { repo.me() }.getOrDefault(user)
            if (!_state.value.petChosen && !fresh.petType.isNullOrBlank()) {
                prefs.savePet(fresh.petType)
                _state.update { it.copy(petChosen = true, petType = fresh.petType, user = fresh) }
            } else {
                _state.update { it.copy(user = fresh) }
            }
            if (token.gifted) toast("已赠送 ${fresh.credits.balance.coerceAtLeast(_state.value.meta?.giftCredits ?: 20)} 点")
        }
    }

    fun refreshUser() {
        if (holder.access.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching { repo.me() }
                .onSuccess { user -> _state.update { it.copy(user = user) } }
                .onFailure { error ->
                    if (holder.access.isNullOrBlank()) _state.update { it.copy(user = null) }
                    else toast(error.message ?: "没有刷新余额")
                }
        }
    }

    fun refreshMeta() {
        viewModelScope.launch {
            val meta = runCatching { repo.meta() }.getOrNull() ?: return@launch
            val snap = prefs.snapshot()
            val ok = snap.termsVersion == meta.termsVersion && snap.privacyVersion == meta.privacyVersion
            _state.update {
                it.copy(
                    meta = meta,
                    consented = ok,
                    termsVersion = meta.termsVersion,
                    privacyVersion = meta.privacyVersion,
                )
            }
        }
    }

    fun updatePet(type: String?) {
        viewModelScope.launch {
            prefs.savePet(type)
            _state.update { it.copy(petChosen = true, petType = type) }
            if (_state.value.user != null) {
                runCatching { repo.patchPet(type) }
                    .onSuccess { user -> _state.update { it.copy(user = user) } }
                    .onFailure { toast(it.message ?: "没有保存宠物类型") }
            }
        }
    }

    fun dismissRate() {
        viewModelScope.launch {
            prefs.dismissRate()
            _state.update { it.copy(rateDismissed = true) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val refresh = holder.refresh
            holder.clear(notify = false)
            prefs.clearTokens()
            _state.update { it.copy(user = null) }
            if (!refresh.isNullOrBlank()) runCatching { repo.logout(refresh) }
            toast("已退出登录")
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteAccount()
                holder.clear(notify = false)
                prefs.clearTokens()
                _state.update { it.copy(user = null, rateDismissed = false) }
                toast("账号已注销")
                onDone()
            } catch (error: Exception) {
                toast(error.message ?: "注销失败")
            }
        }
    }

    fun saveApiBase(url: String) {
        viewModelScope.launch {
            prefs.saveApiBase(url.trim())
            toast("接口地址已保存，重新打开应用后生效")
        }
    }
}
