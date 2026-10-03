package com.elizier.stockscan.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

class StockRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val catalogDao = db.catalogDao()
    private val productDao = db.productDao()
    private val historyDao = db.historyDao()
    private val gson = Gson()

    val catalog: Flow<List<CatalogItem>> = catalogDao.getAll()
    val available: Flow<List<Product>> = productDao.getAvailable()
    val sold: Flow<List<Product>> = productDao.getSold()
    val allProducts: Flow<List<Product>> = productDao.getAll()
    val history: Flow<List<HistoryEntry>> = historyDao.getRecent()
    val availableCount: Flow<Int> = productDao.countAvailable()

    suspend fun getProduct(code: String) = productDao.getByCode(code)

    suspend fun addCatalogItem(name: String, category: String, price: Int): Long {
        return catalogDao.insert(CatalogItem(name = name.trim(), category = category.trim(), price = price))
    }

    suspend fun updateCatalogItem(item: CatalogItem) = catalogDao.update(item)

    suspend fun deleteCatalogItem(item: CatalogItem) = catalogDao.delete(item)

    /** Regista unidade com código único ligado ao catálogo. Falha se código já existir. */
    suspend fun registerUnit(code: String, catalog: CatalogItem): Boolean {
        if (productDao.getByCode(code) != null) return false
        return try {
            productDao.insert(
                Product(
                    code = code,
                    name = catalog.name,
                    category = catalog.category,
                    price = catalog.price,
                    status = "available",
                    catalogId = catalog.id
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun markSold(code: String, customer: String): Boolean {
        return productDao.markSold(code, System.currentTimeMillis(), customer) > 0
    }

    suspend fun deleteProduct(p: Product) = productDao.delete(p)

    suspend fun clearAll() {
        productDao.deleteAll()
        catalogDao.deleteAll()
        historyDao.deleteAll()
    }

    suspend fun addHistory(
        type: String,
        items: List<HistItem>,
        totalKz: Int = 0,
        customerName: String = ""
    ) {
        historyDao.insert(
            HistoryEntry(
                timestamp = System.currentTimeMillis(),
                type = type,
                itemsJson = gson.toJson(items),
                totalKz = totalKz,
                customerName = customerName.trim()
            )
        )
    }

    fun parseItems(json: String): List<HistItem> {
        val type = object : TypeToken<List<HistItem>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    suspend fun undoLastSale(): Boolean {
        val last = historyDao.getLastSale() ?: return false
        val items = parseItems(last.itemsJson)
        for (item in items) {
            val p = productDao.getByCode(item.code) ?: continue
            productDao.update(p.copy(status = "available", soldAt = null, soldTo = null))
        }
        historyDao.delete(last)
        return true
    }
}
