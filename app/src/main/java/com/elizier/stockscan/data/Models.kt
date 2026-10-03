package com.elizier.stockscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Catálogo: define o produto 1 vez */
@Entity(tableName = "catalog")
data class CatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val price: Int = 0,      // preço de venda Kz
    val cost: Int = 0,       // custo de compra Kz
    val minStock: Int = 0    // alerta se disponíveis <= isto
)

/** Unidade física com código único */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val code: String,
    val name: String,
    val category: String,
    val price: Int = 0,
    val cost: Int = 0,
    val status: String = "available",
    val catalogId: Long = 0,
    val registeredAt: Long = System.currentTimeMillis(),
    val soldAt: Long? = null,
    val soldTo: String? = null
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val itemsJson: String,
    val totalKz: Int = 0,
    val customerName: String = ""
)

data class CartItem(
    val code: String,
    val name: String,
    val price: Int,
    val cost: Int = 0
)

data class HistItem(
    val code: String,
    val name: String,
    val price: Int = 0,
    val cost: Int = 0
)

/** Resumo do dia para o Dashboard */
data class DaySummary(
    val salesCount: Int = 0,
    val unitsSold: Int = 0,
    val revenue: Int = 0,
    val profit: Int = 0,
    val stockCount: Int = 0,
    val stockValue: Int = 0,
    val lowStockNames: List<String> = emptyList()
)
