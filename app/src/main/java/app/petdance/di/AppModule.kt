package app.petdance.di

import app.petdance.data.AppPrefs
import app.petdance.data.PetApi
import app.petdance.data.PetRepository
import app.petdance.data.TokenHolder
import app.petdance.data.TokenRefresher
import app.petdance.media.VideoCache
import app.petdance.ui.SessionViewModel
import app.petdance.ui.screens.CreateViewModel
import app.petdance.ui.screens.DetailViewModel
import app.petdance.ui.screens.GeneratingViewModel
import app.petdance.ui.screens.HomeViewModel
import app.petdance.ui.screens.LoginViewModel
import app.petdance.ui.screens.ResultViewModel
import app.petdance.ui.screens.WelcomeViewModel
import app.petdance.ui.screens.WorksViewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

fun appModule(apiBase: String) = module {
    single { TokenHolder() }
    single { AppPrefs(androidContext()) }
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = true
        }
    }
    single { TokenRefresher(apiBase, get(), get(), get()) }
    single { VideoCache(androidContext()) }
    single(qualifier = org.koin.core.qualifier.named("upload")) {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    single {
        val refresher = get<TokenRefresher>()
        val holder = get<TokenHolder>()
        val logging = HttpLoggingInterceptor().apply {
            level = if (app.petdance.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request()
                val token = holder.access
                val path = request.url.encodedPath
                val authed = token != null && !path.contains("/auth/")
                val next = if (authed) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }
                chain.proceed(next)
            }
            .authenticator(RefreshAuthenticator(holder, refresher))
            .build()
    }
    single {
        val json = get<Json>()
        Retrofit.Builder()
            .baseUrl(apiBase)
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PetApi::class.java)
    }
    single {
        PetRepository(
            api = get(),
            json = get(),
            uploadClient = get(org.koin.core.qualifier.named("upload")),
            apiBase = apiBase,
            cacheDir = androidContext().cacheDir,
        )
    }
    viewModel { SessionViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { (id: String) -> DetailViewModel(id, get()) }
    viewModel { (id: String) -> CreateViewModel(id, get()) }
    viewModel { (id: String) -> GeneratingViewModel(id, get()) }
    viewModel { (id: String) -> ResultViewModel(id, get()) }
    viewModel { WorksViewModel(get()) }
    viewModel { WelcomeViewModel(get()) }
}

private class RefreshAuthenticator(
    private val holder: TokenHolder,
    private val refresher: TokenRefresher,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val failed = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        synchronized(holder) {
            val current = holder.access
            if (!current.isNullOrBlank() && current != failed) {
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            if (!refresher.refreshBlocking()) return null
            val fresh = holder.access ?: return null
            return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count += 1
            prior = prior.priorResponse
        }
        return count
    }
}
