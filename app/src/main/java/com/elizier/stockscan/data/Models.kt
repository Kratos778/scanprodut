package com.elizier.stockscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catálogo: define o TIPO de produto uma vez (nome + preço + categoria).
 * Não tem código de barras — serve de modelo para registar várias unidades.
 */
@Entity(tableName = "catalog")
data class CatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val price: Int = 0   // Kz
)

/**
 * Unidade física com código ÚNICO.
 * - Regista 1 vez só (ligado a um item do catálogo)
 * - Ao vender → status = "sold" (lista negra)
 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val code: String,
    val name: String,
    val category: String,
    val price: Int = 0,
    val status: String = "available",   // "available" | "sold"
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
    val price: Int
)

data class HistItem(
    val code: String,
    val name: String,
    val price: Int = 0
)
