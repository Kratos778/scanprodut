package com.elizier.stockscan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.elizier.stockscan.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StockViewModel(app: Application) : AndroidViewModel(app) {
    val repo = StockRepository(app)

    val catalog = repo.catalog
    val available = repo.available
    val sold = repo.sold
    val history = repo.history
    val availableCount = repo.availableCount

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    /** Produto do catálogo seleccionado para registar unidades */
    var selectedCatalog by mutableStateOf<CatalogItem?>(null)
    var showNewCatalog by mutableStateOf(false)
    var editCatalog by mutableStateOf<CatalogItem?>(null)
    var showCustomerDialog by mutableStateOf(false)
    var lastMessage by mutableStateOf<String?>(null)

    /**
     * REGISTAR: precisa de produto do catálogo seleccionado.
     * Código novo → cria unidade com nome/preço do catálogo.
     * Código já existe → rejeita.
     */
    suspend fun handleScanEntry(code: String) {
        val cat = selectedCatalog
        if (cat == null) {
            lastMessage = "Escolhe primeiro um produto na lista"
            return
        }
        val existing = repo.getProduct(code)
        if (existing != null) {
            lastMessage = if (existing.status == "sold")
                "CÓDIGO JÁ VENDIDO — lista negra"
            else
                "CÓDIGO JÁ REGISTADO"
            return
        }
        val ok = repo.registerUnit(code, cat)
        if (ok) {
            repo.addHistory("in", listOf(HistItem(code, cat.name, cat.price)))
            lastMessage = "${cat.name} · $code registado ✓"
        } else {
            lastMessage = "Falha ao registar $code"
        }
    }

    suspend fun handleScanSale(code: String) {
        val p = repo.getProduct(code)
        when {
            p == null -> lastMessage = "Código não registado"
            p.status == "sold" -> lastMessage = "JÁ VENDIDO (${p.soldTo ?: "?"})"
            _cart.value.any { it.code == code } -> lastMessage = "Já está no carrinho"
            else -> {
                _cart.value = _cart.value + CartItem(p.code, p.name, p.price)
                lastMessage = "${p.name} adicionado"
            }
        }
    }

    suspend fun addCatalog(name: String, category: String, price: Int) {
        repo.addCatalogItem(name, category, price)
        showNewCatalog = false
        lastMessage = "Produto criado: $name"
    }

    suspend fun updateCatalog(name: String, category: String, price: Int) {
        val old = editCatalog ?: return
        repo.updateCatalogItem(old.copy(name = name, category = category, price = price))
        editCatalog = null
        lastMessage = "Actualizado"
    }

    suspend fun deleteCatalog(item: CatalogItem) {
        repo.deleteCatalogItem(item)
        if (selectedCatalog?.id == item.id) selectedCatalog = null
        lastMessage = "Removido do catálogo"
    }

    fun removeFromCart(code: String) {
        _cart.value = _cart.value.filter { it.code != code }
    }

    fun clearCart() { _cart.value = emptyList() }

    suspend fun confirmSale(customerName: String): Boolean {
        val name = customerName.trim()
        if (name.isEmpty()) {
            lastMessage = "Nome do cliente obrigatório"
            return false
        }
        val items = _cart.value
        if (items.isEmpty()) return false
        for (item in items) {
            val p = repo.getProduct(item.code)
            if (p == null || p.status != "available") {
                lastMessage = "${item.name} já não disponível"
                return false
            }
        }
        val histItems = mutableListOf<HistItem>()
        var total = 0
        for (item in items) {
            if (!repo.markSold(item.code, name)) {
                lastMessage = "Falha: ${item.name}"
                return false
            }
            histItems.add(HistItem(item.code, item.name, item.price))
            total += item.price
        }
        repo.addHistory("out", histItems, total, name)
        _cart.value = emptyList()
        showCustomerDialog = false
        lastMessage = "Venda OK — $name — $total Kz"
        return true
    }

    suspend fun deleteProduct(p: Product) {
        if (p.status == "sold") {
            lastMessage = "Não apaga produto já vendido"
            return
        }
        repo.deleteProduct(p)
        lastMessage = "Apagado"
    }

    suspend fun undoLastSale(): Boolean {
        val ok = repo.undoLastSale()
        lastMessage = if (ok) "Venda desfeita" else "Nada para desfazer"
        return ok
    }

    fun clearMessage() { lastMessage = null }
}
