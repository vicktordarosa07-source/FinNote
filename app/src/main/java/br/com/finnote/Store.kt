package br.com.finnote

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("finnote", Context.MODE_PRIVATE)
    var entries by mutableStateOf(entriesFromJson(prefs.getString("entries", "[]")))
        private set
    var categories by mutableStateOf(
        categoriesFromJson(prefs.getString("categories", null)).ifEmpty {
            listOf(Category("Gastos fixos", listOf("Aluguel", "Água", "Luz", "Internet")),
                Category("Transporte", listOf("Combustível", "Aplicativo", "Ônibus")),
                Category("Mercado", listOf("Alimentação", "Limpeza", "Higiene")), Category("Outros"),
                Category("Renda", listOf("Salário", "Renda extra")))
        }
    )
        private set
    var monthly by mutableLongStateOf(prefs.getLong("monthly", 0))
        private set
    var name by mutableStateOf(prefs.getString("name", "") ?: "")
        private set
    var phone by mutableStateOf(decrypt(prefs.getString("phone", "") ?: ""))
        private set
    var backendUrl by mutableStateOf(prefs.getString("backend_url", "") ?: "")
        private set
    var anonKey by mutableStateOf(prefs.getString("anon_key", "") ?: "")
        private set
    var accessToken by mutableStateOf(decrypt(prefs.getString("access_token", "") ?: ""))
        private set
    var refreshToken by mutableStateOf(decrypt(prefs.getString("refresh_token", "") ?: ""))
        private set
    var userId by mutableStateOf(prefs.getString("user_id", "") ?: "")
        private set

    fun saveProfile(newName: String, newPhone: String) {
        name = newName.trim().ifBlank { "Pessoa" }
        phone = newPhone.trim()
        prefs.edit().putString("name", name).putString("phone", encrypt(phone)).apply()
    }
    fun updateMonthly(value: Long) { monthly = value.coerceAtLeast(0); prefs.edit().putLong("monthly", monthly).apply() }
    fun saveBackend(url: String, key: String) {
        backendUrl = url.trim().trimEnd('/'); anonKey = key.trim()
        prefs.edit().putString("backend_url", backendUrl).putString("anon_key", anonKey).apply()
    }
    fun setSession(token: String, id: String, refresh: String) {
        accessToken = token; userId = id; refreshToken = refresh
        prefs.edit().putString("access_token", encrypt(token)).putString("refresh_token", encrypt(refresh)).putString("user_id", id).apply()
    }
    fun signOut() { accessToken = ""; refreshToken = ""; userId = ""; prefs.edit().remove("access_token").remove("refresh_token").remove("user_id").apply() }
    fun add(entry: Entry) { entries = (listOf(entry) + entries).sortedByDescending(Entry::time); persistEntries() }
    fun merge(remote: List<Entry>) {
        entries = (entries.filterNot { local -> remote.any { it.id == local.id } } + remote).sortedByDescending(Entry::time)
        persistEntries()
    }
    fun markPublished(id: String) {
        entries = entries.map { if (it.id == id) it.copy(ownerId = userId) else it }
        persistEntries()
    }
    fun addCategory(name: String) {
        val clean = name.trim(); if (clean.isBlank() || categories.any { it.name.equals(clean, true) }) return
        categories = categories + Category(clean); persistCategories()
    }
    fun addSubcategory(category: String, subcategory: String) {
        val clean = subcategory.trim(); if (clean.isBlank()) return
        categories = categories.map { c -> if (c.name == category && c.subcategories.none { it.equals(clean, true) }) c.copy(subcategories = c.subcategories + clean) else c }
        persistCategories()
    }
    private fun persistEntries() { prefs.edit().putString("entries", entriesToJson(entries)).apply() }
    private fun persistCategories() { prefs.edit().putString("categories", categoriesToJson(categories)).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun encrypt(value: String): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        "enc:" + Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrDefault("")
    private fun decrypt(value: String): String = runCatching {
        if (!value.startsWith("enc:")) return value // migrates pre-encryption local preferences on next save
        val payload = Base64.decode(value.removePrefix("enc:"), Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12); val body = payload.copyOfRange(12, payload.size)
        Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv)); String(doFinal(body), Charsets.UTF_8) }
    }.getOrDefault("")
    private companion object { const val KEY_ALIAS = "finnote_private_session" }
}
