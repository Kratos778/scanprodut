package com.elizier.stockscan.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

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

    suspend fun getCatalogById(id: Long) = catalogDao.getById(id)

    suspend fun addCatalogItem(name: String, category: String, price: Int, cost: Int, minStock: Int): Long {
        return catalogDao.insert(
            CatalogItem(
                name = name.trim(),
                category = category.trim(),
                price = price,
                cost = cost,
                minStock = minStock
            )
        )
    }

    /** Actualiza catálogo E sincroniza todas as unidades available desse produto */
    suspend fun updateCatalogItem(item: CatalogItem) {
        catalogDao.update(item)
        productDao.syncFromCatalog(
            catalogId = item.id,
            name = item.name,
            category = item.category,
            price = item.price,
            cost = item.cost
        )
    }

    /**
     * Apaga do catálogo + unidades available ligadas.
     * Unidades já vendidas ficam no histórico (lista negra).
     */
    suspend fun deleteCatalogItem(item: CatalogItem) {
        productDao.deleteAvailableByCatalog(item.id)
        catalogDao.delete(item)
    }

    /** Regista unidade com dados FRESCOS do catálogo (por id) */
    suspend fun registerUnit(code: String, catalogId: Long): Boolean {
        if (productDao.getByCode(code) != null) return false
        val catalog = catalogDao.getById(catalogId) ?: return false
        return try {
            productDao.insert(
                Product(
                    code = code.trim(),
                    name = catalog.name,
                    category = catalog.category,
                    price = catalog.price,
                    cost = catalog.cost,
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
        return productDao.markSold(code.trim(), System.currentTimeMillis(), customer) > 0
    }

    suspend fun deleteProduct(p: Product) = productDao.delete(p)

    suspend fun countByCatalog(catalogId: Long) = productDao.countAvailableByCatalog(catalogId)

    /** Apaga TUDO: catálogo, unidades, histórico */
    suspend fun clearAllData() {
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

    suspend fun daySummary(
        available: List<Product>,
        catalog: List<CatalogItem>
    ): DaySummary {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis

        val sales = historyDao.salesSince(from)
        var units = 0
        var revenue = 0
        var profit = 0
        for (s in sales) {
            val items = parseItems(s.itemsJson)
            units += items.size
            revenue += s.totalKz
            profit += items.sumOf { it.price - it.cost }
        }

        val low = mutableListOf<String>()
        for (c in catalog) {
            if (c.minStock <= 0) continue
            val n = productDao.countAvailableByCatalog(c.id)
            if (n <= c.minStock) low.add("${c.name} ($n)")
        }

        return DaySummary(
            salesCount = sales.size,
            unitsSold = units,
            revenue = revenue,
            profit = profit,
            stockCount = available.size,
            stockValue = available.sumOf { it.price },
            lowStockNames = low
        )
    }
}
