package dev.dazaike.cadence.cloud

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.cloudDataStore by preferencesDataStore(name = "cadence_cloud")

// Legacy hostname: the deployed Worker still carries the pre-rename name "heartwood-api". Keep it until the Worker is renamed.
const val DEFAULT_SERVER_URL = "https://heartwood-api.dazaike.workers.dev"

/** Everything the Cloud sync UI needs from disk. Never contains the session token. */
data class CloudSettings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val email: String = "",
    val signedIn: Boolean = false,
    val autoSync: Boolean = true,
    val lastSyncAt: Long = 0L,
    val lastSyncOk: Boolean = true,
    val lastSyncMessage: String? = null,
    /** Why the app signed itself out (expired session, unreadable token), shown until next sign-in. */
    val signedOutReason: String? = null,
)

/**
 * Opt-in cloud sync state. Off until the user signs in: with no stored token nothing is ever sent.
 * The session token is kept only as Keystore-sealed ciphertext (see [TokenVault]).
 */
class CloudPreferences(private val context: Context) {

    private val SERVER_URL = stringPreferencesKey("server_url")
    private val EMAIL = stringPreferencesKey("email")
    private val TOKEN = stringPreferencesKey("token_enc")
    private val AUTO_SYNC = booleanPreferencesKey("auto_sync")
    private val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    private val LAST_SYNC_OK = booleanPreferencesKey("last_sync_ok")
    private val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
    private val SIGNED_OUT_REASON = stringPreferencesKey("signed_out_reason")
    private val SYNCED_DAYS = stringPreferencesKey("synced_days")
    private val GOALS_PUSHED = stringPreferencesKey("goals_pushed")
    private val LAST_OPEN_SYNC = longPreferencesKey("last_open_sync")

    val settings: Flow<CloudSettings> = context.cloudDataStore.data.map { p ->
        CloudSettings(
            serverUrl = p[SERVER_URL] ?: DEFAULT_SERVER_URL,
            email = p[EMAIL].orEmpty(),
            signedIn = p[TOKEN] != null,
            autoSync = p[AUTO_SYNC] ?: true,
            lastSyncAt = p[LAST_SYNC_AT] ?: 0L,
            lastSyncOk = p[LAST_SYNC_OK] ?: true,
            lastSyncMessage = p[LAST_SYNC_MESSAGE],
            signedOutReason = p[SIGNED_OUT_REASON],
        )
    }

    /**
     * The decrypted session token, or null when signed out. A blob that no longer decrypts (device
     * restore, keystore reset) is discarded so the app falls back to a clean signed-out state.
     */
    suspend fun token(): String? {
        val blob = context.cloudDataStore.data.first()[TOKEN] ?: return null
        TokenVault.decrypt(blob)?.let { return it }
        clearSession("Your saved sign-in could not be read on this device. Please sign in again.")
        return null
    }

    suspend fun saveSignIn(serverUrl: String, email: String, token: String) {
        val sealed = TokenVault.encrypt(token)
        context.cloudDataStore.edit {
            it[SERVER_URL] = serverUrl
            it[EMAIL] = email
            it[TOKEN] = sealed
            it.remove(SIGNED_OUT_REASON)
            it.remove(LAST_SYNC_AT)
            it.remove(LAST_SYNC_OK)
            it.remove(LAST_SYNC_MESSAGE)
            it.remove(SYNCED_DAYS)
            it.remove(GOALS_PUSHED)
            it.remove(LAST_OPEN_SYNC)
        }
    }

    /** Forgets the session and all incremental-sync bookkeeping (a new account needs a fresh backfill). */
    suspend fun clearSession(reason: String? = null) {
        context.cloudDataStore.edit {
            it.remove(TOKEN)
            it.remove(SYNCED_DAYS)
            it.remove(GOALS_PUSHED)
            it.remove(LAST_SYNC_AT)
            it.remove(LAST_SYNC_OK)
            it.remove(LAST_SYNC_MESSAGE)
            if (reason != null) it[SIGNED_OUT_REASON] = reason else it.remove(SIGNED_OUT_REASON)
        }
    }

    suspend fun setAutoSync(value: Boolean) {
        context.cloudDataStore.edit { it[AUTO_SYNC] = value }
    }

    suspend fun recordSync(ok: Boolean, message: String, at: Long = System.currentTimeMillis()) {
        context.cloudDataStore.edit {
            it[LAST_SYNC_OK] = ok
            it[LAST_SYNC_MESSAGE] = message
            if (ok) it[LAST_SYNC_AT] = at
        }
    }

    /** Last fully synced local day (ISO date) per source+metric; absent means "needs a 365-day backfill". */
    suspend fun syncedDays(): Map<String, String> {
        val raw = context.cloudDataStore.data.first()[SYNCED_DAYS] ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    suspend fun setSyncedDays(days: Map<String, String>) {
        context.cloudDataStore.edit { it[SYNCED_DAYS] = JSONObject(days).toString() }
    }

    suspend fun goalsPushed(): String? = context.cloudDataStore.data.first()[GOALS_PUSHED]

    suspend fun setGoalsPushed(fingerprint: String) {
        context.cloudDataStore.edit { it[GOALS_PUSHED] = fingerprint }
    }

    suspend fun lastOpenSync(): Long = context.cloudDataStore.data.first()[LAST_OPEN_SYNC] ?: 0L

    suspend fun setLastOpenSync(at: Long) {
        context.cloudDataStore.edit { it[LAST_OPEN_SYNC] = at }
    }
}
