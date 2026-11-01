package app.petdance

import android.app.Application
import app.petdance.data.AppPrefs
import app.petdance.di.appModule
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PetDanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val override = runBlocking { AppPrefs(this@PetDanceApp).snapshot().apiBase }
        val base = override?.takeIf { it.isNotBlank() } ?: BuildConfig.API_BASE_URL
        startKoin {
            androidContext(this@PetDanceApp)
            modules(appModule(base.trimEnd('/') + "/"))
        }
    }
}
