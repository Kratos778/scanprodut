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

    val available = repo.available
    val sold = repo.sold
    val allProducts = repo.allProducts
    val history = repo.history
    val availableCount = repo.availableCount

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    var pendingCode by mutableStateOf("")
    var showNewProduct by mutableStateOf(false)
    var editProduct by mutableStateOf<Product?>(null)
    var showCustomerDialog by mutableStateOf(false)
    var showManualRegister by mutableStateOf(false)
    var lastMessage by mutableStateOf<String?>(null)

    /**
     * SCAN em modo ENTRADA / REGISTO:
     * - Código novo → abre formulário para registar (1x só)
     * - Código já existe (available ou sold) → ERRO, não regista de novo
     */
    suspend fun handleScanEntry(code: String) {
        val existing = repo.getProduct(code)
        if (existing != null) {
            lastMessage = if (existing.status == "sold")
                "CÓDIGO JÁ VENDIDO — está na lista negra"
            else
                "CÓDIGO JÁ REGISTADO — não podes registar 2 vezes"
            return
        }
        pendingCode = code
        showNewProduct = true
        lastMessage = null
    }

    /**
     * SCAN em modo VENDA:
     * - Código available → adiciona ao carrinho
     * - Código sold / inexistente → rejeita
     * - Já no carrinho → não duplica
     */
    suspend fun handleScanSale(code: String) {
        val p = repo.getProduct(code)
        when {
            p == null -> {
                lastMessage = "Código não registado"
                return
            }
            p.status == "sold" -> {
                lastMessage = "JÁ VENDIDO — lista negra (cliente: ${p.soldTo ?: "?"})"
                return
            }
            _cart.value.any { it.code == code } -> {
                lastMessage = "Já está no carrinho"
                return
            }
            else -> {
                _cart.value = _cart.value + CartItem(p.code, p.name, p.price)
                lastMessage = "${p.name} adicionado"
            }
        }
    }

    suspend fun registerNewProduct(name: String, cat: String, price: Int): Boolean {
        val code = pendingCode.ifBlank { return false }
        val p = Product(
            code = code,
            name = name.trim(),
            category = cat.trim(),
            price = price,
            status = "available"
        )
        val ok = repo.registerProduct(p)
        if (ok) {
            repo.addHistory("in", listOf(HistItem(code, name, price)))
            lastMessage = "Registado: $name"
        } else {
            lastMessage = "Falha: código já existe"
        }
        showNewProduct = false
        showManualRegister = false
        pendingCode = ""
        return ok
    }

    /** Registo manual: utilizador escreve o código à mão */
    suspend fun registerManual(code: String, name: String, cat: String, price: Int): Boolean {
        pendingCode = code.trim()
        return registerNewProduct(name, cat, price)
    }

    suspend fun updateProduct(name: String, cat: String, price: Int) {
        val old = editProduct ?: return
        if (old.status == "sold") {
            lastMessage = "Produto já vendido — não edita"
            editProduct = null
            return
        }
        repo.updateProduct(old.copy(name = name, category = cat, price = price))
        editProduct = null
        lastMessage = "Actualizado"
    }

    fun removeFromCart(code: String) {
        _cart.value = _cart.value.filter { it.code != code }
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    suspend fun confirmSale(customerName: String): Boolean {
        val name = customerName.trim()
        if (name.isEmpty()) {
            lastMessage = "Nome do cliente obrigatório"
            return false
        }
        val items = _cart.value
        if (items.isEmpty()) return false

        // Verificar todos ainda available
        for (item in items) {
            val p = repo.getProduct(item.code)
            if (p == null || p.status != "available") {
                lastMessage = "${item.name} já não está disponível"
                return false
            }
        }

        val histItems = mutableListOf<HistItem>()
        var total = 0
        for (item in items) {
            val ok = repo.markSold(item.code, name)
            if (!ok) {
                lastMessage = "Falha ao marcar ${item.name}"
                return false
            }
            histItems.add(HistItem(item.code, item.name, item.price))
            total += item.price
        }
        repo.addHistory("out", histItems, total, name)
        _cart.value = emptyList()
        showCustomerDialog = false
        lastMessage = "Venda OK — $name — ${total} Kz"
        return true
    }

    suspend fun deleteProduct(p: Product) {
        if (p.status == "sold") {
            lastMessage = "Não apaga produto já vendido (fica no histórico)"
            return
        }
        repo.deleteProduct(p)
        lastMessage = "Apagado"
    }

    suspend fun undoLastSale(): Boolean {
        val ok = repo.undoLastSale()
        lastMessage = if (ok) "Venda desfeita — códigos voltaram ao stock" else "Nada para desfazer"
        return ok
    }

    fun clearMessage() { lastMessage = null }
}
