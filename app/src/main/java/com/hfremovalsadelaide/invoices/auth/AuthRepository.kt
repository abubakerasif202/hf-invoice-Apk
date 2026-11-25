package com.hfremovalsadelaide.invoices.auth

import com.hfremovalsadelaide.invoices.network.ApiClient
import com.hfremovalsadelaide.invoices.network.ApiResult
import com.hfremovalsadelaide.invoices.offline.EncryptedStore
import com.hfremovalsadelaide.invoices.offline.OfflineSyncQueue
import org.json.JSONArray
import org.json.JSONObject

enum class UserRole(val serverKey: String, val displayLabel: String) {
    OWNER("owner", "Owner"),
    ADMIN("admin", "Administrator"),
    DISPATCHER("dispatcher", "Dispatcher"),
    DRIVER("driver", "Driver / Team Leader"),
    MOVER("mover", "Mover"),
    SUBCONTRACTOR("subcontractor", "Subcontractor");

    val isAdmin: Boolean get() = this == OWNER || this == ADMIN
    val isDispatcherOrAbove: Boolean get() = isAdmin || this == DISPATCHER
    val isWorkerOnly: Boolean get() = this == DRIVER || this == MOVER || this == SUBCONTRACTOR

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.serverKey.equals(raw?.trim(), ignoreCase = true) } ?: DRIVER
    }
}

data class SessionUser(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val isLegacyAdmin: Boolean,
    val permissions: Set<String>
) {
    fun can(permission: String): Boolean = permission in permissions

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("email", email)
        .put("role", role.serverKey)
        .put("isLegacyAdmin", isLegacyAdmin)
        .put("permissions", JSONArray(permissions.toList()))

    companion object {
        fun fromJson(o: JSONObject): SessionUser {
            val permsArr = o.optJSONArray("permissions") ?: JSONArray()
            val perms = buildSet {
                for (i in 0 until permsArr.length()) {
                    permsArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
            return SessionUser(
                id = o.optString("id"),
                name = o.optString("name", "User"),
                email = o.optString("email", ""),
                role = UserRole.from(o.optString("role")),
                isLegacyAdmin = o.optBoolean("isLegacyAdmin", false),
                permissions = perms
            )
        }
    }
}

class AuthRepository(
    private val store: EncryptedStore,
    private val queue: OfflineSyncQueue,
    private val onAccountChanged: () -> Unit = {}
) {
    fun cachedUser(): SessionUser? = readCachedUser(store)

    private fun rememberUser(user: SessionUser) {
        val previous = cachedUser()
        if (previous == null || previous.id != user.id || previous.role != user.role || previous.permissions != user.permissions) {
            onAccountChanged()
            previous?.let { store.delete(snapshotKey(it.id)) }
        }
        store.writeText(KEY_USER, user.toJson().toString())
        queue.bindAccount(user.id)
    }

    suspend fun fetchMe(): ApiResult<SessionUser> {
        val res = ApiClient.get("/api/auth/me") { body ->
            SessionUser.fromJson(JSONObject(body).getJSONObject("user"))
        }
        if (res is ApiResult.Success) {
            rememberUser(res.data)
        } else if (res is ApiResult.AuthRequired || res is ApiResult.Forbidden) {
            clearLocalSession()
        }
        return res
    }

    suspend fun loginAdmin(password: String): ApiResult<SessionUser> {
        val res = ApiClient.post(
            "/api/auth/login",
            JSONObject().put("mode", "admin").put("password", password)
        ) { body ->
            SessionUser.fromJson(JSONObject(body).getJSONObject("user"))
        }
        if (res is ApiResult.Success) {
            rememberUser(res.data)
        }
        return res
    }

    suspend fun loginStaff(email: String, password: String, device: String = "Android"): ApiResult<SessionUser> {
        val res = ApiClient.post(
            "/api/auth/login",
            JSONObject().put("mode", "staff").put("email", email.trim()).put("password", password).put("device", device)
        ) { body ->
            SessionUser.fromJson(JSONObject(body).getJSONObject("user"))
        }
        if (res is ApiResult.Success) {
            rememberUser(res.data)
        }
        return res
    }

    suspend fun acceptInvite(token: String, password: String, device: String = "Android"): ApiResult<SessionUser> {
        val res = ApiClient.post(
            "/api/auth/invite/accept",
            JSONObject().put("token", token.trim()).put("password", password).put("device", device)
        ) { body ->
            SessionUser.fromJson(JSONObject(body).getJSONObject("user"))
        }
        if (res is ApiResult.Success) {
            rememberUser(res.data)
        }
        return res
    }

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Boolean> =
        ApiClient.post(
            "/api/auth/reset-password",
            JSONObject().put("token", token.trim()).put("password", newPassword)
        ) { true }

    suspend fun logout(): Boolean {
        val revoked = ApiClient.post("/api/auth/logout", JSONObject()) { true } is ApiResult.Success
        ApiClient.clearSessionCookies()
        clearLocalSession()
        return revoked
    }

    private fun clearLocalSession() {
        onAccountChanged()
        queue.clear()
        queue.bindAccount(null)
        store.clearAll()
    }

    companion object {
        private const val KEY_USER = "session_user"

        internal fun snapshotKey(id: String) = "jobs_snapshot_cache_$id"

        internal fun readCachedUser(store: EncryptedStore): SessionUser? {
            val raw = store.readText(KEY_USER) ?: return null
            return runCatching { SessionUser.fromJson(JSONObject(raw)) }.getOrNull()
        }
    }
}
