package com.elizier.stockscan.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

class StockRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val productDao = db.productDao()
    private val historyDao = db.historyDao()
    private val gson = Gson()

    val products: Flow<List<Product>> = productDao.getAll()
    val categories: Flow<List<String>> = productDao.getCategories()
    val history: Flow<List<HistoryEntry>> = historyDao.getRecent()

    suspend fun getProduct(code: String) = productDao.getByCode(code)

    suspend fun upsertProduct(p: Product) = productDao.upsert(p)

    suspend fun adjustQuantity(code: String, delta: Int) = productDao.adjustQty(code, delta)

    suspend fun deleteProduct(p: Product) = productDao.delete(p)

    suspend fun clearAll() {
        productDao.deleteAll()
        historyDao.deleteAll()
    }

    suspend fun addHistory(type: String, items: List<HistItem>, totalKz: Int = 0) {
        val json = gson.toJson(items)
        historyDao.insert(HistoryEntry(
            timestamp = System.currentTimeMillis(),
            type = type,
            itemsJson = json,
            totalKz = totalKz
        ))
    }

    fun parseItems(json: String): List<HistItem> {
        val type = object : TypeToken<List<HistItem>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    suspend fun undoLastSale(): Boolean {
        val last = historyDao.getLastSale() ?: return false
        val items = parseItems(last.itemsJson)
        items.forEach { item ->
            productDao.adjustQty(item.code, item.qty)
        }
        historyDao.delete(last)
        return true
    }

    fun exportJson(products: List<Product>, history: List<HistoryEntry>): String {
        val map = mapOf("products" to products, "history" to history)
        return gson.toJson(map)
    }
}
