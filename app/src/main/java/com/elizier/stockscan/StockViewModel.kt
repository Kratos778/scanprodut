package com.elizier.stockscan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elizier.stockscan.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StockViewModel(app: Application) : AndroidViewModel(app) {
    val repo = StockRepository(app)

    val catalog = repo.catalog
    val available = repo.available
    val sold = repo.sold
    val history = repo.history
    val availableCount = repo.availableCount

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _daySummary = MutableStateFlow(DaySummary())
    val daySummary: StateFlow<DaySummary> = _daySummary.asStateFlow()

    var selectedCatalog by mutableStateOf<CatalogItem?>(null)
    var showNewCatalog by mutableStateOf(false)
    var editCatalog by mutableStateOf<CatalogItem?>(null)
    var showCustomerDialog by mutableStateOf(false)
    var lastMessage by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            combine(repo.available, repo.catalog) { avail, cat ->
                avail to cat
            }.collect { (avail, cat) ->
                _daySummary.value = repo.daySummary(avail, cat)
            }
        }
    }

    suspend fun refreshDashboard() {
        // force by re-reading - collect will update
    }

    suspend fun handleScanEntry(code: String) {
        val cat = selectedCatalog
        if (cat == null) {
            lastMessage = "Escolhe primeiro um produto"
            return
        }
        val existing = repo.getProduct(code)
        if (existing != null) {
            lastMessage = if (existing.status == "sold")
                "CÓDIGO JÁ VENDIDO"
            else
                "CÓDIGO JÁ REGISTADO"
            return
        }
        val ok = repo.registerUnit(code, cat)
        if (ok) {
            repo.addHistory("in", listOf(HistItem(code, cat.name, cat.price, cat.cost)))
            lastMessage = "${cat.name} · $code ✓"
        } else {
            lastMessage = "Falha ao registar"
        }
    }

    suspend fun handleScanSale(code: String) {
        val p = repo.getProduct(code)
        when {
            p == null -> lastMessage = "Código não registado"
            p.status == "sold" -> lastMessage = "JÁ VENDIDO (${p.soldTo ?: "?"})"
            _cart.value.any { it.code == code } -> lastMessage = "Já no carrinho"
            else -> {
                _cart.value = _cart.value + CartItem(p.code, p.name, p.price, p.cost)
                lastMessage = "${p.name} +${p.price} Kz"
            }
        }
    }

    suspend fun addCatalog(name: String, category: String, price: Int, cost: Int, minStock: Int) {
        repo.addCatalogItem(name, category, price, cost, minStock)
        showNewCatalog = false
        lastMessage = "Produto criado: $name"
    }

    suspend fun updateCatalog(name: String, category: String, price: Int, cost: Int, minStock: Int) {
        val old = editCatalog ?: return
        repo.updateCatalogItem(
            old.copy(name = name, category = category, price = price, cost = cost, minStock = minStock)
        )
        editCatalog = null
        lastMessage = "Actualizado"
    }

    suspend fun deleteCatalog(item: CatalogItem) {
        repo.deleteCatalogItem(item)
        if (selectedCatalog?.id == item.id) selectedCatalog = null
        lastMessage = "Removido"
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
                lastMessage = "${item.name} indisponível"
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
            histItems.add(HistItem(item.code, item.name, item.price, item.cost))
            total += item.price
        }
        repo.addHistory("out", histItems, total, name)
        _cart.value = emptyList()
        showCustomerDialog = false
        lastMessage = "Venda OK · $name · $total Kz"
        return true
    }

    suspend fun deleteProduct(p: Product) {
        if (p.status == "sold") {
            lastMessage = "Não apaga vendido"
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
