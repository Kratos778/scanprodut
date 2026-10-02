package com.elizier.stockscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val code: String,
    val name: String,
    val category: String,
    var quantity: Int = 0,
    val price: Int = 0,       // Kz
    val minStock: Int = 0
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,         // "in" ou "out"
    val itemsJson: String,    // JSON da lista de itens
    val totalKz: Int = 0
)

data class CartItem(
    val code: String,
    val name: String,
    var qty: Int,
    val price: Int
)

data class HistItem(
    val code: String,
    val name: String,
    val qty: Int,
    val price: Int = 0
)
