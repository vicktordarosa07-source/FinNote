package br.com.finnote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/** Uses only the project's public anon key; row access is enforced by the SQL RLS policies. */
class SupabaseClient(private val store: Store) {
    private fun configured() = store.backendUrl.startsWith("https://") && store.anonKey.isNotBlank()
    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, token: String? = null, prefer: String? = null): String = withContext(Dispatchers.IO) {
        check(configured()) { "Configure a URL e uma chave pública do Supabase em Ajustes." }
        val connection = (URL(store.backendUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("apikey", store.anonKey)
            setRequestProperty("Authorization", "Bearer ${token ?: store.anonKey}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 && token != null && token == store.accessToken && store.refreshToken.isNotBlank()) {
                connection.disconnect()
                refreshSession()
                return@withContext request(path, method, body, store.accessToken, prefer)
            }
            if (code !in 200..299) throw IllegalStateException("Servidor retornou $code: ${text.take(300)}")
            text
        } finally { connection.disconnect() }
    }

    suspend fun sendOtp(phone: String) {
        request("/auth/v1/otp", "POST", JSONObject().put("phone", phone).put("create_user", true))
    }

    private suspend fun refreshSession() {
        val response = JSONObject(request("/auth/v1/token?grant_type=refresh_token", "POST", JSONObject().put("refresh_token", store.refreshToken)))
        store.setSession(response.getString("access_token"), response.getJSONObject("user").getString("id"), response.optString("refresh_token", store.refreshToken))
    }

    suspend fun verifyOtp(phone: String, code: String, name: String) {
        val response = JSONObject(request("/auth/v1/verify", "POST", JSONObject().put("phone", phone).put("token", code).put("type", "sms")))
        val token = response.getString("access_token")
        val id = response.getJSONObject("user").getString("id")
        store.setSession(token, id, response.optString("refresh_token"))
        store.saveProfile(name, phone)
        request("/rest/v1/profiles?on_conflict=id", "POST", JSONObject().put("id", id).put("name", name).put("phone", phone), token, "resolution=merge-duplicates")
        sync()
    }

    suspend fun publish(entry: Entry) {
        check(store.accessToken.isNotBlank()) { "Entre com telefone para compartilhar lançamentos." }
        val data = JSONObject().apply {
            put("id", entry.id); put("user_id", store.userId); put("title", entry.title)
            put("category", entry.category); put("subcategory", entry.subcategory); put("product", entry.product)
            put("amount_cents", entry.cents); put("entry_type", if (entry.income) "income" else "expense")
            put("visibility", if (entry.isPublic) "public" else "private")
            put("author_name", store.name); put("created_at", Instant.ofEpochMilli(entry.time).toString())
        }
        request("/rest/v1/entries", "POST", data, store.accessToken, "resolution=merge-duplicates")
    }

    suspend fun sync(): Int {
        check(store.accessToken.isNotBlank()) { "Entre com telefone para sincronizar." }
        store.entries.filter { it.isPublic && it.ownerId.isBlank() }.forEach { pending ->
            publish(pending)
            store.markPublished(pending.id)
        }
        val rows = JSONArray(request("/rest/v1/entries?select=*&order=created_at.desc", token = store.accessToken))
        val remote = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            Entry(
                id = row.getString("id"), title = row.getString("title"), category = row.optString("category", "Outros"),
                subcategory = row.optString("subcategory"), product = row.optString("product", row.getString("title")),
                cents = row.optLong("amount_cents"), income = row.optString("entry_type") == "income",
                author = row.optString("author_name", "Pessoa"), ownerId = row.optString("user_id"),
                isPublic = row.optString("visibility") == "public",
                time = runCatching { Instant.parse(row.getString("created_at")).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            )
        }
        store.merge(remote)
        return remote.size
    }

    suspend fun createInvite(): String {
        check(store.accessToken.isNotBlank()) { "Entre com telefone para criar um link." }
        val result = request("/rest/v1/rpc/create_connection_invite", "POST", JSONObject(), store.accessToken)
        val code = runCatching { JSONObject(result).optString("code") }.getOrDefault("").ifBlank {
            runCatching { JSONArray(result).optJSONObject(0)?.optString("code") ?: "" }.getOrDefault("")
        }.ifBlank { result.trim().trim('"') }
        check(code.isNotBlank()) { "O servidor não retornou o código do convite." }
        return "finnote://connect?code=$code"
    }

    suspend fun acceptInvite(code: String) {
        check(store.accessToken.isNotBlank()) { "Entre com telefone para aceitar um convite." }
        val normalized = Uri.parse(code).getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: code
        request("/rest/v1/rpc/accept_connection_invite", "POST", JSONObject().put("invite_code", normalized), store.accessToken)
        sync()
    }
}
