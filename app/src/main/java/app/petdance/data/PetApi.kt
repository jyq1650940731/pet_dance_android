package app.petdance.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PetApi {
    @GET("v1/meta")
    suspend fun meta(): Response<MetaDto>

    @POST("v1/auth/sms")
    suspend fun sendSms(@Body body: SmsIn): Response<SmsOut>

    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginIn): Response<TokenDto>

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshIn): Response<TokenDto>

    @POST("v1/auth/logout")
    suspend fun logout(@Body body: RefreshIn): Response<Unit>

    @GET("v1/me")
    suspend fun me(): Response<UserDto>

    @PATCH("v1/me")
    suspend fun patchMe(@Body body: MePatch): Response<UserDto>

    @DELETE("v1/me")
    suspend fun deleteMe(): Response<Unit>

    @GET("v1/credits")
    suspend fun credits(): Response<CreditDto>

    @GET("v1/templates")
    suspend fun templates(@Query("page") page: Int): Response<TemplateListDto>

    @GET("v1/templates/{id}")
    suspend fun template(@Path("id") id: String): Response<TemplateDto>

    @POST("v1/uploads/image")
    suspend fun requestUpload(@Body body: ImageUploadIn): Response<ImageUploadOut>

    @POST("v1/generations")
    suspend fun createGeneration(
        @Body body: GenerationIn,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<GenerationDto>

    @GET("v1/generations")
    suspend fun generations(@Query("page") page: Int): Response<GenerationListDto>

    @GET("v1/generations/{id}")
    suspend fun generation(@Path("id") id: String): Response<GenerationDto>

    @DELETE("v1/generations/{id}")
    suspend fun deleteGeneration(@Path("id") id: String): Response<Unit>
}
