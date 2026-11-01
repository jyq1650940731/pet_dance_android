package app.petdance.data

import app.petdance.domain.rewriteMediaUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.UUID

class PetRepository(
    private val api: PetApi,
    private val json: Json,
    private val uploadClient: OkHttpClient,
    private val apiBase: String,
    private val cacheDir: File,
) {
    fun media(url: String?): String? = rewriteMediaUrl(url, apiBase)

    suspend fun meta(): MetaDto = call { api.meta() }

    suspend fun sendSms(phone: String): SmsOut = call { api.sendSms(SmsIn(phone)) }

    suspend fun login(phone: String, code: String, petType: String?, terms: String, privacy: String): TokenDto =
        call {
            api.login(
                LoginIn(
                    phone = phone,
                    code = code,
                    petType = petType,
                    consent = ConsentIn(terms, privacy),
                ),
            )
        }

    suspend fun logout(refresh: String) {
        callEmpty { api.logout(RefreshIn(refresh)) }
    }

    suspend fun me(): UserDto = call { api.me() }

    suspend fun patchPet(petType: String?): UserDto = call { api.patchMe(MePatch(petType)) }

    suspend fun deleteAccount() {
        callEmpty { api.deleteMe() }
    }

    suspend fun templates(): List<TemplateDto> {
        val all = mutableListOf<TemplateDto>()
        var page = 1
        while (page <= 20) {
            val batch = call { api.templates(page) }
            all += batch.items
            if (all.size >= batch.total || batch.items.isEmpty()) break
            page += 1
        }
        return all
    }

    suspend fun template(id: String): TemplateDto = call { api.template(id) }

    suspend fun generations(): List<GenerationDto> {
        val all = mutableListOf<GenerationDto>()
        var page = 1
        while (page <= 20) {
            val batch = call { api.generations(page) }
            all += batch.items
            if (all.size >= batch.total || batch.items.isEmpty()) break
            page += 1
        }
        return all
    }

    suspend fun generation(id: String): GenerationDto = call { api.generation(id) }

    suspend fun deleteGeneration(id: String) {
        callEmpty { api.deleteGeneration(id) }
    }

    suspend fun uploadAndCreate(templateId: String, jpeg: ByteArray, idempotencyKey: String): GenerationDto {
        val slot = call { api.requestUpload(ImageUploadIn("image/jpeg", jpeg.size)) }
        val url = media(slot.uploadUrl) ?: throw ApiException("UPLOAD_FAILED", "照片上传失败")
        withContext(Dispatchers.IO) {
            val body = jpeg.toRequestBody("image/jpeg".toMediaType())
            val builder = Request.Builder().url(url).put(body)
            slot.headers.forEach { (name, value) -> builder.header(name, value) }
            uploadClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException("UPLOAD_FAILED", "照片上传失败")
                }
            }
        }
        return call {
            api.createGeneration(
                GenerationIn(templateId, slot.imageKey, ownershipConfirmed = true),
                idempotencyKey,
            )
        }
    }

    suspend fun download(url: String, name: String): File = withContext(Dispatchers.IO) {
        val target = media(url) ?: throw ApiException("NETWORK", "视频地址无效")
        val file = File(cacheDir, name)
        uploadClient.newCall(Request.Builder().url(target).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw ApiException("NETWORK", "视频暂时无法下载")
            val bytes = response.body?.bytes() ?: throw ApiException("NETWORK", "视频暂时无法下载")
            file.writeBytes(bytes)
        }
        file
    }

    fun newIdempotencyKey(): String = UUID.randomUUID().toString().replace("-", "")

    private suspend fun <T> call(block: suspend () -> Response<T>): T {
        val response = try {
            block()
        } catch (_: IOException) {
            throw ApiException("NETWORK", "网络不可用，请稍后重试")
        }
        if (response.isSuccessful) {
            return response.body() ?: throw ApiException("EMPTY", "服务没有返回内容")
        }
        throw errorOf(response)
    }

    private suspend fun callEmpty(block: suspend () -> Response<Unit>) {
        val response = try {
            block()
        } catch (_: IOException) {
            throw ApiException("NETWORK", "网络不可用，请稍后重试")
        }
        if (!response.isSuccessful) throw errorOf(response)
    }

    private fun errorOf(response: Response<*>): ApiException {
        val raw = response.errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), raw) }.getOrNull()?.error
        return ApiException(
            code = parsed?.code ?: "HTTP_${response.code()}",
            message = parsed?.message ?: "请求失败",
            retryAfter = parsed?.retryAfter,
        )
    }
}
