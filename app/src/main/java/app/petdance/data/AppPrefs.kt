package app.petdance.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("pet_dance")

data class PrefSnapshot(
    val termsVersion: String?,
    val privacyVersion: String?,
    val guideDone: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val petChosen: Boolean,
    val petType: String?,
    val interest: String?,
    val rateDismissed: Boolean,
    val apiBase: String?,
)

class AppPrefs(private val context: Context) {
    private val store = context.dataStore

    suspend fun snapshot(): PrefSnapshot {
        val prefs = store.data.first()
        return PrefSnapshot(
            termsVersion = prefs[TERMS],
            privacyVersion = prefs[PRIVACY],
            guideDone = prefs[GUIDE_DONE] == true,
            accessToken = prefs[ACCESS],
            refreshToken = prefs[REFRESH],
            petChosen = prefs[PET_CHOSEN] == true,
            petType = prefs[PET],
            interest = prefs[INTEREST],
            rateDismissed = prefs[RATE] == true,
            apiBase = prefs[API_BASE],
        )
    }

    suspend fun saveConsent(terms: String, privacy: String) {
        store.edit {
            it[TERMS] = terms
            it[PRIVACY] = privacy
        }
    }

    suspend fun markGuideDone() {
        store.edit { it[GUIDE_DONE] = true }
    }

    suspend fun saveTokens(access: String, refresh: String) {
        store.edit {
            it[ACCESS] = access
            it[REFRESH] = refresh
        }
    }

    suspend fun clearTokens() {
        store.edit {
            it.remove(ACCESS)
            it.remove(REFRESH)
        }
    }

    suspend fun savePet(type: String?) {
        store.edit {
            it[PET_CHOSEN] = true
            if (type.isNullOrBlank()) it.remove(PET) else it[PET] = type
        }
    }

    suspend fun saveInterest(interest: String?) {
        store.edit {
            if (interest.isNullOrBlank()) it.remove(INTEREST) else it[INTEREST] = interest
        }
    }

    suspend fun dismissRate() {
        store.edit { it[RATE] = true }
    }

    suspend fun saveApiBase(url: String?) {
        store.edit {
            if (url.isNullOrBlank()) it.remove(API_BASE) else it[API_BASE] = url
        }
    }

    private companion object {
        val TERMS = stringPreferencesKey("terms_version")
        val PRIVACY = stringPreferencesKey("privacy_version")
        val GUIDE_DONE = booleanPreferencesKey("guide_done")
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val PET_CHOSEN = booleanPreferencesKey("pet_chosen")
        val PET = stringPreferencesKey("pet_type")
        val INTEREST = stringPreferencesKey("interest")
        val RATE = booleanPreferencesKey("rate_dismissed")
        val API_BASE = stringPreferencesKey("api_base")
    }
}
