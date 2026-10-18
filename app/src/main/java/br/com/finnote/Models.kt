package br.com.finnote

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Category(val name: String, val subcategories: List<String> = emptyList())

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val subcategory: String = "",
    val product: String = title,
    val cents: Long,
    val income: Boolean,
    val author: String,
    val ownerId: String = "",
    val isPublic: Boolean = false,
    val time: Long = System.currentTimeMillis()
)

fun categoriesToJson(categories: List<Category>): String = JSONArray().apply {
    categories.forEach { category -> put(JSONObject().apply {
        put("name", category.name)
        put("subcategories", JSONArray(category.subcategories))
    }) }
}.toString()

fun categoriesFromJson(raw: String?): List<Category> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val subs = item.optJSONArray("subcategories") ?: JSONArray()
        Category(item.getString("name"), (0 until subs.length()).map(subs::getString))
    }
}.getOrDefault(emptyList())

fun entriesToJson(entries: List<Entry>): String = JSONArray().apply {
    entries.forEach { entry -> put(JSONObject().apply {
        put("id", entry.id); put("title", entry.title); put("category", entry.category)
        put("subcategory", entry.subcategory); put("product", entry.product); put("cents", entry.cents)
        put("income", entry.income); put("author", entry.author); put("owner_id", entry.ownerId)
        put("is_public", entry.isPublic); put("time", entry.time)
    }) }
}.toString()

fun entriesFromJson(raw: String?): List<Entry> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        Entry(
            id = item.optString("id", UUID.randomUUID().toString()),
            title = item.optString("title"), category = item.optString("category", "Outros"),
            subcategory = item.optString("subcategory"), product = item.optString("product", item.optString("title")),
            cents = item.optLong("cents"), income = item.optBoolean("income"),
            author = item.optString("author", "Pessoa"), ownerId = item.optString("owner_id"),
            isPublic = item.optBoolean("is_public"), time = item.optLong("time", System.currentTimeMillis())
        )
    }.sortedByDescending(Entry::time)
}.getOrDefault(emptyList())
