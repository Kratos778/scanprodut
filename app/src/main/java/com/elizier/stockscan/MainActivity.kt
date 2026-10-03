package com.elizier.stockscan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.camera.view.PreviewView
import com.elizier.stockscan.data.*
import com.elizier.stockscan.scanner.BarcodeScannerHelper
import com.elizier.stockscan.ui.theme.StockScanTheme
import kotlinx.coroutines.launch

private val Teal = Color(0xFF0F766E)
private val Orange = Color(0xFFC2410C)
private val CardBg = Color(0xFFF0FDFA)

class MainActivity : ComponentActivity() {
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Precisa de permissão da câmara", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) requestPermission.launch(Manifest.permission.CAMERA)

        setContent {
            StockScanTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFAFAFA)) {
                    StockScanApp()
                }
            }
        }
    }
}

@Composable
fun StockScanApp(vm: StockViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val catalog by vm.catalog.collectAsState(initial = emptyList())
    val available by vm.available.collectAsState(initial = emptyList())
    val sold by vm.sold.collectAsState(initial = emptyList())
    val history by vm.history.collectAsState(initial = emptyList())
    val cart by vm.cart.collectAsState()
    val stockCount by vm.availableCount.collectAsState(initial = 0)
    val summary by vm.daySummary.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(vm.lastMessage) {
        vm.lastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Início") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Category, null) }, label = { Text("Catálogo") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.AddBox, null) }, label = { Text("Registar") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Venda") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.Inventory, null) }, label = { Text("Stock") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> DashboardScreen(summary, history, vm)
                1 -> CatalogScreen(catalog, vm)
                2 -> RegisterScreen(catalog, stockCount, vm, context)
                3 -> {
                    if (vm.pendingCode != null && vm.pendingMode == "out") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Row(
                                Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Confirma venda: 2ª vez\n${vm.pendingCode}",
                                    fontWeight = FontWeight.Medium,
                                    color = Orange
                                )
                                TextButton(onClick = { vm.cancelPending() }) { Text("Cancelar") }
                            }
                        }
                    }
                    ScannerSection(onCode = { code ->
                        scope.launch { vm.handleScanSale(code); vibrate(context) }
                    })
                    CartSection(cart, vm, onConfirmClick = { vm.showCustomerDialog = true })
                }
                4 -> StockScreen(available, sold, stockCount, vm)
            }
        }
    }

    if (vm.showNewCatalog) {
        CatalogDialog(
            title = "Novo produto",
            onSave = { n, c, p, cost, min -> scope.launch { vm.addCatalog(n, c, p, cost, min) } },
            onDismiss = { vm.showNewCatalog = false }
        )
    }
    if (vm.editCatalog != null) {
        val item = vm.editCatalog!!
        CatalogDialog(
            title = "Editar",
            initialName = item.name,
            initialCat = item.category,
            initialPrice = item.price.toString(),
            initialCost = item.cost.toString(),
            initialMin = item.minStock.toString(),
            onSave = { n, c, p, cost, min -> scope.launch { vm.updateCatalog(n, c, p, cost, min) } },
            onDismiss = { vm.editCatalog = null }
        )
    }
    if (vm.showCustomerDialog) {
        CustomerDialog(
            onConfirm = { name -> scope.launch { vm.confirmSale(name) } },
            onDismiss = { vm.showCustomerDialog = false }
        )
    }
    if (vm.showClearConfirm) {
        AlertDialog(
            onDismissRequest = { vm.showClearConfirm = false },
            title = { Text("Apagar tudo?") },
            text = { Text("Isto apaga catálogo, stock, vendas e histórico. Não dá para desfazer.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { vm.clearAllData() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Apagar tudo") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showClearConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun DashboardScreen(summary: DaySummary, history: List<HistoryEntry>, vm: StockViewModel) {
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Stock Scan", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Teal)
            Text("Resumo de hoje", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Vendas", "${summary.salesCount}", Modifier.weight(1f), Teal)
                StatCard("Unidades", "${summary.unitsSold}", Modifier.weight(1f), Orange)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Receita", fmt(summary.revenue), Modifier.weight(1f), Teal)
                StatCard("Lucro", fmt(summary.profit), Modifier.weight(1f),
                    if (summary.profit >= 0) Color(0xFF15803D) else Color.Red)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Em stock", "${summary.stockCount}", Modifier.weight(1f), Color(0xFF1E40AF))
                StatCard("Valor stock", fmt(summary.stockValue), Modifier.weight(1f), Color(0xFF1E40AF))
            }
        }
        if (summary.lowStockNames.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("⚠ Stock baixo", fontWeight = FontWeight.Bold, color = Color.Red)
                        summary.lowStockNames.forEach {
                            Text("· $it", color = Color(0xFF991B1B))
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Text("Últimos movimentos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
        }
        items(history.take(8)) { h ->
            val items = vm.repo.parseItems(h.itemsJson)
            val typeLabel = if (h.type == "in") "Entrada" else "Venda"
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val client = if (h.customerName.isNotBlank()) " · ${h.customerName}" else ""
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$typeLabel$client", fontWeight = FontWeight.Medium)
                        Text(items.joinToString { it.name }, color = Color.Gray, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (h.totalKz > 0) Text("${h.totalKz} Kz", fontWeight = FontWeight.Bold, color = Teal)
                        Text(sdf.format(java.util.Date(h.timestamp)), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { scope.launch { vm.undoLastSale() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("↩ Desfazer última venda") }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { vm.showClearConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🗑 Apagar TODOS os dados", color = Color.Red) }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier, accent: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
        }
    }
}

@Composable
fun CatalogScreen(catalog: List<CatalogItem>, vm: StockViewModel) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Catálogo", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Teal)
        Text("Define uma vez. Depois só scaneias códigos.", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.showNewCatalog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(10.dp)
        ) { Text("+ Novo produto") }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(catalog, key = { it.id }) { item ->
                val margin = item.price - item.cost
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Text("${item.category} · Venda ${item.price} · Custo ${item.cost} · Margem $margin Kz" +
                                if (item.minStock > 0) " · Mín ${item.minStock}" else "")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.editCatalog = item }) {
                                    Icon(Icons.Default.Edit, null, tint = Teal)
                                }
                                IconButton(onClick = { scope.launch { vm.deleteCatalog(item) } }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Red)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    catalog: List<CatalogItem>,
    stockCount: Int,
    vm: StockViewModel,
    context: android.content.Context
) {
    val scope = rememberCoroutineScope()
    val byCategory = catalog.groupBy { it.category }
    val sel = vm.selectedCatalog

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Registar unidades", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Teal)
        Text(
            "Escolhe o produto, scaneia 2x. Podes mudar de produto a qualquer momento.",
            color = Color.Gray, fontSize = 12.sp
        )

        if (catalog.isEmpty()) {
            Text("Cria produtos no Catálogo primeiro.", modifier = Modifier.padding(16.dp))
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (sel == null) 400.dp else 160.dp)
                .padding(vertical = 6.dp)
        ) {
            byCategory.forEach { (catName, items) ->
                item {
                    Text(
                        catName,
                        fontWeight = FontWeight.Bold,
                        color = Teal,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items, key = { it.id }) { item ->
                            FilterChip(
                                selected = sel?.id == item.id,
                                onClick = {
                                    if (sel?.id != item.id) {
                                        vm.cancelPending()
                                        vm.selectedCatalog = item
                                    }
                                },
                                label = { Text("${item.name} · ${item.price} Kz") }
                            )
                        }
                    }
                }
            }
        }

        if (sel != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("A REGISTAR", fontSize = 11.sp, color = Color.Gray)
                        Text(sel.name, fontWeight = FontWeight.Bold, color = Teal, fontSize = 18.sp)
                        Text("${sel.category} · ${sel.price} Kz")
                    }
                    TextButton(onClick = {
                        vm.cancelPending()
                        vm.selectedCatalog = null
                    }) { Text("Trocar") }
                }
            }

            if (vm.pendingCode != null && vm.pendingMode == "in") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Confirma: scaneia 2ª vez\n${vm.pendingCode}",
                            fontWeight = FontWeight.Medium,
                            color = Orange
                        )
                        TextButton(onClick = { vm.cancelPending() }) { Text("Cancelar") }
                    }
                }
            }

            ScannerSection(onCode = { code ->
                scope.launch { vm.handleScanEntry(code); vibrate(context) }
            })
        } else {
            Text(
                "Toca num produto acima para começar a registar.",
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Medium
            )
        }

        Text("Stock: $stockCount unidades", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
    }
}

@Composable
fun ScannerSection(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var torch by remember { mutableStateOf(false) }
    var scannerHelper by remember { mutableStateOf<BarcodeScannerHelper?>(null) }

    Column(Modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    val helper = BarcodeScannerHelper(ctx, lifecycleOwner, preview, onCode)
                    helper.start()
                    scannerHelper = helper
                }
            },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = {
                torch = !torch
                scannerHelper?.toggleTorch(torch)
            }) {
                Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Lanterna")
            }
        }
    }
    DisposableEffect(Unit) { onDispose { scannerHelper?.release() } }
}

@Composable
fun CartSection(cart: List<CartItem>, vm: StockViewModel, onConfirmClick: () -> Unit) {
    val total = cart.sumOf { it.price }
    val profit = cart.sumOf { it.price - it.cost }
    Column(Modifier.padding(8.dp)) {
        if (cart.isEmpty()) {
            Text("Scaneia códigos. Cada um só vende 1 vez.", color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(cart, key = { it.code }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("${item.code} · ${item.price} Kz") },
                        trailingContent = {
                            IconButton(onClick = { vm.removeFromCart(item.code) }) {
                                Icon(Icons.Default.Close, null, tint = Color.Red)
                            }
                        }
                    )
                }
            }
            Text(
                "Total: ${fmt(total)}  ·  Lucro: ${fmt(profit)}  ·  ${cart.size} un.",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Confirmar venda (${cart.size})") }
            TextButton(onClick = { vm.clearCart() }, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar")
            }
        }
    }
}

@Composable
fun StockScreen(available: List<Product>, sold: List<Product>, stockCount: Int, vm: StockViewModel) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var showSold by remember { mutableStateOf(false) }
    val list = if (showSold) sold else available
    val filtered = list.filter {
        search.isBlank() || it.name.contains(search, true) || it.code.contains(search)
    }
    val byCat = filtered.groupBy { it.category }
    val valor = available.sumOf { it.price }

    Column(Modifier.padding(12.dp)) {
        Text("Stock", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Teal)
        Text("Disponíveis: $stockCount  ·  Valor: ${fmt(valor)}", fontWeight = FontWeight.Medium)
        Text("Vendidos: ${sold.size}", color = Color.Gray)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showSold, onClick = { showSold = false }, label = { Text("Disponíveis") })
            FilterChip(selected = showSold, onClick = { showSold = true }, label = { Text("Vendidos") })
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            label = { Text("Pesquisar") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        LazyColumn {
            byCat.forEach { (cat, items) ->
                item {
                    Text(
                        "$cat (${items.size})",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        color = Teal
                    )
                }
                items(items, key = { it.code }) { p ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (p.status == "sold") Color(0xFFFEF2F2) else Color.White
                        )
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    p.name + if (p.status == "sold") " [VENDIDO]" else "",
                                    color = if (p.status == "sold") Color.Red else Color.Unspecified
                                )
                            },
                            supportingContent = {
                                Text(
                                    if (p.status == "sold")
                                        "${p.code} · ${p.price} Kz · ${p.soldTo ?: "?"}"
                                    else "${p.code} · ${p.price} Kz"
                                )
                            },
                            trailingContent = {
                                if (p.status == "available") {
                                    IconButton(onClick = { scope.launch { vm.deleteProduct(p) } }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogDialog(
    title: String,
    initialName: String = "",
    initialCat: String = "",
    initialPrice: String = "",
    initialCost: String = "0",
    initialMin: String = "0",
    onSave: (String, String, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var cat by remember { mutableStateOf(initialCat) }
    var price by remember { mutableStateOf(initialPrice) }
    var cost by remember { mutableStateOf(initialCost) }
    var min by remember { mutableStateOf(initialMin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço venda (Kz)") }, singleLine = true)
                OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Custo compra (Kz)") }, singleLine = true)
                OutlinedTextField(value = min, onValueChange = { min = it }, label = { Text("Stock mínimo (alerta)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && cat.isNotBlank())
                        onSave(
                            name.trim(), cat.trim(),
                            price.toIntOrNull() ?: 0,
                            cost.toIntOrNull() ?: 0,
                            min.toIntOrNull() ?: 0
                        )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun CustomerDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nome do cliente") },
        text = {
            Column {
                Text("Obrigatório. Códigos → lista negra.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Cliente") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.trim().isNotEmpty()) onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) { Text("Confirmar venda") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

fun fmt(n: Int): String = "%,d".format(n).replace(',', '.') + " Kz"

fun vibrate(context: android.content.Context) {
    try {
        val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}
