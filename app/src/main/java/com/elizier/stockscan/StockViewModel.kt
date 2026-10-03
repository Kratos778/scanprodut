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
    var showClearConfirm by mutableStateOf(false)
    var lastMessage by mutableStateOf<String?>(null)

    /**
     * Confirmação em 2 scans:
     * 1º scan → guarda código pendente (ainda NÃO regista/vende)
     * 2º scan do MESMO código → confirma (conta 1 unidade)
     * Código diferente → cancela o pendente e pede o novo
     */
    var pendingCode by mutableStateOf<String?>(null)
    var pendingMode by mutableStateOf<String?>(null) // "in" | "out"
    private var pendingTime = 0L
    private val confirmWindowMs = 12_000L // 12 s para confirmar

    init {
        viewModelScope.launch {
            combine(repo.available, repo.catalog) { avail, cat ->
                avail to cat
            }.collect { (avail, cat) ->
                _daySummary.value = repo.daySummary(avail, cat)
                val selId = selectedCatalog?.id
                if (selId != null) {
                    selectedCatalog = cat.find { it.id == selId }
                }
            }
        }
    }

    fun cancelPending() {
        pendingCode = null
        pendingMode = null
        pendingTime = 0L
    }

    private fun isPendingExpired(): Boolean {
        return pendingCode != null && System.currentTimeMillis() - pendingTime > confirmWindowMs
    }

    /**
     * REGISTAR: 1º scan pede confirmação, 2º do mesmo código regista 1 unidade.
     */
    suspend fun handleScanEntry(code: String) {
        val raw = code.trim()
        if (raw.isEmpty()) return

        if (isPendingExpired()) cancelPending()

        // Se há pendente de VENDA, cancela (mudou de modo)
        if (pendingMode == "out") cancelPending()

        val selId = selectedCatalog?.id
        if (selId == null) {
            lastMessage = "Escolhe primeiro um produto no Catálogo"
            cancelPending()
            return
        }
        val cat = repo.getCatalogById(selId)
        if (cat == null) {
            lastMessage = "Produto do catálogo já não existe"
            selectedCatalog = null
            cancelPending()
            return
        }
        selectedCatalog = cat

        val existing = repo.getProduct(raw)
        if (existing != null) {
            lastMessage = if (existing.status == "sold")
                "CÓDIGO JÁ VENDIDO"
            else
                "CÓDIGO JÁ REGISTADO (${existing.name})"
            cancelPending()
            return
        }

        // 1º scan → pendente
        if (pendingCode == null || pendingMode != "in") {
            pendingCode = raw
            pendingMode = "in"
            pendingTime = System.currentTimeMillis()
            lastMessage = "Confirma: scaneia OUTRA VEZ o mesmo código\n$raw"
            return
        }

        // 2º scan → tem de ser o mesmo
        if (pendingCode != raw) {
            pendingCode = raw
            pendingMode = "in"
            pendingTime = System.currentTimeMillis()
            lastMessage = "Código diferente. Confirma de novo: $raw"
            return
        }

        // Confirmado — conta 1 unidade
        cancelPending()
        val ok = repo.registerUnit(raw, cat.id)
        if (ok) {
            repo.addHistory("in", listOf(HistItem(raw, cat.name, cat.price, cat.cost)))
            lastMessage = "✓ ${cat.name} · $raw registado"
        } else {
            lastMessage = "Falha ao registar $raw"
        }
    }

    /**
     * VENDA: 1º scan pede confirmação, 2º do mesmo código adiciona 1 ao carrinho.
     */
    suspend fun handleScanSale(code: String) {
        val raw = code.trim()
        if (raw.isEmpty()) return

        if (isPendingExpired()) cancelPending()

        if (pendingMode == "in") cancelPending()

        val p = repo.getProduct(raw)
        when {
            p == null -> {
                lastMessage = "Código não registado"
                cancelPending()
                return
            }
            p.status == "sold" -> {
                lastMessage = "JÁ VENDIDO (${p.soldTo ?: "?"})"
                cancelPending()
                return
            }
            _cart.value.any { it.code == p.code } -> {
                lastMessage = "Já no carrinho"
                cancelPending()
                return
            }
        }

        // 1º scan → pendente
        if (pendingCode == null || pendingMode != "out") {
            pendingCode = raw
            pendingMode = "out"
            pendingTime = System.currentTimeMillis()
            lastMessage = "Confirma venda: scaneia OUTRA VEZ\n${p!!.name} · $raw"
            return
        }

        if (pendingCode != raw) {
            pendingCode = raw
            pendingMode = "out"
            pendingTime = System.currentTimeMillis()
            lastMessage = "Código diferente. Confirma de novo: $raw"
            return
        }

        // Confirmado — 1 unidade no carrinho
        cancelPending()
        val prod = p!!
        _cart.value = _cart.value + CartItem(prod.code, prod.name, prod.price, prod.cost)
        lastMessage = "✓ ${prod.name} +${prod.price} Kz"
    }

    suspend fun addCatalog(name: String, category: String, price: Int, cost: Int, minStock: Int) {
        repo.addCatalogItem(name, category, price, cost, minStock)
        showNewCatalog = false
        lastMessage = "Produto criado: $name"
    }

    suspend fun updateCatalog(name: String, category: String, price: Int, cost: Int, minStock: Int) {
        val old = editCatalog ?: return
        val updated = old.copy(
            name = name.trim(),
            category = category.trim(),
            price = price,
            cost = cost,
            minStock = minStock
        )
        repo.updateCatalogItem(updated)
        if (selectedCatalog?.id == old.id) selectedCatalog = updated
        editCatalog = null
        lastMessage = "Actualizado"
    }

    suspend fun deleteCatalog(item: CatalogItem) {
        repo.deleteCatalogItem(item)
        if (selectedCatalog?.id == item.id) selectedCatalog = null
        lastMessage = "Removido"
    }

    suspend fun clearAllData() {
        repo.clearAllData()
        selectedCatalog = null
        _cart.value = emptyList()
        cancelPending()
        showClearConfirm = false
        lastMessage = "Todos os dados apagados"
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
        cancelPending()
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
