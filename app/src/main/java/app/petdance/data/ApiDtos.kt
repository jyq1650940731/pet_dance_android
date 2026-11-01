package app.petdance.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
data class ErrorBody(
    val code: String = "HTTP_ERROR",
    val message: String = "请求失败",
    @SerialName("retry_after") val retryAfter: Int? = null,
)

class ApiException(
    val code: String,
    override val message: String,
    val retryAfter: Int? = null,
) : Exception(message)

@Serializable
data class MetaDto(
    val maintenance: Boolean = false,
    @SerialName("maintenance_message") val maintenanceMessage: String = "",
    @SerialName("terms_version") val termsVersion: String,
    @SerialName("privacy_version") val privacyVersion: String,
    @SerialName("cheapest_credit_cost") val cheapestCreditCost: Int? = null,
    @SerialName("gift_credits") val giftCredits: Int = 20,
)

@Serializable
data class ConsentIn(
    @SerialName("terms_version") val termsVersion: String,
    @SerialName("privacy_version") val privacyVersion: String,
)

@Serializable
data class SmsIn(val phone: String)

@Serializable
data class SmsOut(@SerialName("cooldown_sec") val cooldownSec: Int = 60)

@Serializable
data class LoginIn(
    val phone: String,
    val code: String,
    @SerialName("pet_type") val petType: String? = null,
    val consent: ConsentIn,
)

@Serializable
data class RefreshIn(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class CreditDto(val balance: Int = 0, val frozen: Int = 0)

@Serializable
data class UserDto(
    val id: String,
    @SerialName("phone_masked") val phoneMasked: String = "",
    @SerialName("pet_type") val petType: String? = null,
    val status: String = "active",
    val credits: CreditDto = CreditDto(),
    @SerialName("successful_count") val successfulCount: Int = 0,
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int = 0,
    val gifted: Boolean = false,
    val user: UserDto? = null,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class MePatch(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("pet_type")
    val petType: String? = null,
)

@Serializable
data class TemplateDto(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val category: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("duration_sec") val durationSec: Int = 5,
    @SerialName("aspect_ratio") val aspectRatio: String = "9:16",
    @SerialName("credit_cost") val creditCost: Int = 10,
    @SerialName("pet_scope") val petScope: String = "any",
    val sort: Int = 0,
)

@Serializable
data class TemplateListDto(
    val items: List<TemplateDto> = emptyList(),
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val total: Int = 0,
    @SerialName("featured_id") val featuredId: String? = null,
)

@Serializable
data class ImageUploadIn(
    @SerialName("content_type") val contentType: String,
    @SerialName("content_length") val contentLength: Int,
)

@Serializable
data class ImageUploadOut(
    @SerialName("image_key") val imageKey: String,
    @SerialName("upload_url") val uploadUrl: String,
    val headers: Map<String, String> = emptyMap(),
    @SerialName("expires_in") val expiresIn: Int = 600,
)

@Serializable
data class GenerationIn(
    @SerialName("template_id") val templateId: String,
    @SerialName("image_key") val imageKey: String,
    @SerialName("ownership_confirmed") val ownershipConfirmed: Boolean,
)

@Serializable
data class GenerationDto(
    val id: String,
    val status: String,
    @SerialName("stage_label") val stageLabel: String = "",
    @SerialName("template_id") val templateId: String,
    @SerialName("template_title") val templateTitle: String = "",
    @SerialName("duration_sec") val durationSec: Int = 5,
    @SerialName("credit_cost") val creditCost: Int = 0,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    val playable: Boolean = false,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("finished_at") val finishedAt: String? = null,
)

@Serializable
data class GenerationListDto(
    val items: List<GenerationDto> = emptyList(),
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val total: Int = 0,
)
