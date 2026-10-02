package com.elizier.stockscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cada registo = 1 unidade física com código ÚNICO.
 * - Regista 1 vez só
 * - Ao vender → status = "sold" (lista negra)
 * - Não volta a entrar nem a vender
 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val code: String,       // código de barras / QR único
    val name: String,
    val category: String,
    val price: Int = 0,                 // Kz
    val status: String = "available",   // "available" | "sold"
    val registeredAt: Long = System.currentTimeMillis(),
    val soldAt: Long? = null,
    val soldTo: String? = null          // nome do cliente que comprou
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,                   // "in" (registo) ou "out" (venda)
    val itemsJson: String,
    val totalKz: Int = 0,
    val customerName: String = ""
)

/** Item no carrinho de venda (1 unidade = 1 código) */
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
