package app.petdance.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TokenRefresher(
    private val baseUrl: String,
    private val holder: TokenHolder,
    private val prefs: AppPrefs,
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val lock = Any()

    fun refreshBlocking(): Boolean = synchronized(lock) {
        val refresh = holder.refresh ?: return false
        val payload = json.encodeToString(RefreshIn.serializer(), RefreshIn(refresh))
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/auth/refresh")
            .post(payload.toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    holder.clear(notify = true)
                    runBlocking { prefs.clearTokens() }
                    return false
                }
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(TokenDto.serializer(), body)
                holder.update(parsed.accessToken, parsed.refreshToken)
                runBlocking { prefs.saveTokens(parsed.accessToken, parsed.refreshToken) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
