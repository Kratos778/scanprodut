package com.elizier.stockscan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.elizier.stockscan.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StockViewModel(app: Application) : AndroidViewModel(app) {
    val repo = StockRepository(app)

    val products = repo.products
    val history = repo.history

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    var scanQty by mutableIntStateOf(1)
    var currentCategory by mutableStateOf("")
    var pendingCode by mutableStateOf("")
    var showNewProduct by mutableStateOf(false)
    var editProduct by mutableStateOf<Product?>(null)
    var showCustomerDialog by mutableStateOf(false)

    suspend fun handleScan(code: String, isSale: Boolean) {
        val p = repo.getProduct(code)
        if (isSale) {
            if (p == null) return // código não registado — não adiciona
            val existing = _cart.value.toMutableList()
            val idx = existing.indexOfFirst { it.code == code }
            if (idx >= 0) {
                existing[idx] = existing[idx].copy(qty = existing[idx].qty + scanQty)
            } else {
                existing.add(CartItem(code, p.name, scanQty, p.price))
            }
            _cart.value = existing
        } else {
            // Entrada: se o código JÁ existe → só aumenta quantidade (nunca cria duplicado)
            if (p != null) {
                repo.adjustQuantity(code, scanQty)
                repo.addHistory("in", listOf(HistItem(code, p.name, scanQty, p.price)))
            } else {
                pendingCode = code
                showNewProduct = true
            }
        }
    }

    suspend fun saveNewProduct(name: String, cat: String, price: Int, min: Int) {
        // Código é PrimaryKey → Room impede duplicados automaticamente
        val existing = repo.getProduct(pendingCode)
        if (existing != null) {
            // já existe — só actualiza qty
            repo.adjustQuantity(pendingCode, scanQty)
            showNewProduct = false
            pendingCode = ""
            return
        }
        val p = Product(pendingCode, name, cat, scanQty, price, min)
        repo.upsertProduct(p)
        repo.addHistory("in", listOf(HistItem(pendingCode, name, scanQty, price)))
        currentCategory = cat
        showNewProduct = false
        pendingCode = ""
    }

    suspend fun updateProduct(name: String, cat: String, price: Int, min: Int) {
        val old = editProduct ?: return
        repo.upsertProduct(old.copy(name = name, category = cat, price = price, minStock = min))
        editProduct = null
    }

    fun adjustCart(code: String, delta: Int) {
        val list = _cart.value.toMutableList()
        val idx = list.indexOfFirst { it.code == code }
        if (idx < 0) return
        val newQty = list[idx].qty + delta
        if (newQty <= 0) list.removeAt(idx)
        else list[idx] = list[idx].copy(qty = newQty)
        _cart.value = list
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    /** Chamar depois de o utilizador preencher o nome do cliente */
    suspend fun confirmSale(customerName: String): Boolean {
        val name = customerName.trim()
        if (name.isEmpty()) return false
        val items = _cart.value
        if (items.isEmpty()) return false
        for (item in items) {
            val p = repo.getProduct(item.code) ?: return false
            if (p.quantity < item.qty) return false
        }
        val histItems = mutableListOf<HistItem>()
        var total = 0
        for (item in items) {
            repo.adjustQuantity(item.code, -item.qty)
            histItems.add(HistItem(item.code, item.name, item.qty, item.price))
            total += item.qty * item.price
        }
        repo.addHistory("out", histItems, total, name)
        _cart.value = emptyList()
        showCustomerDialog = false
        return true
    }

    suspend fun adjustStock(code: String, delta: Int) {
        repo.adjustQuantity(code, delta)
    }

    suspend fun deleteProduct(p: Product) {
        repo.deleteProduct(p)
    }

    suspend fun undoLastSale(): Boolean = repo.undoLastSale()
}
