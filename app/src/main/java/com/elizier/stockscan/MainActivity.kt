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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                Surface(modifier = Modifier.fillMaxSize()) { StockScanApp() }
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
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Category, null) }, label = { Text("Catálogo") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.AddBox, null) }, label = { Text("Registar") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Venda") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Inventory, null) }, label = { Text("Stock") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.History, null) }, label = { Text("Histórico") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> CatalogScreen(catalog, vm)
                1 -> RegisterScreen(catalog, stockCount, vm, context)
                2 -> {
                    ScannerSection(onCode = { code ->
                        scope.launch { vm.handleScanSale(code); vibrate(context) }
                    })
                    CartSection(cart, vm, onConfirmClick = { vm.showCustomerDialog = true })
                }
                3 -> StockScreen(available, sold, stockCount, vm)
                4 -> HistoryScreen(history, vm)
            }
        }
    }

    if (vm.showNewCatalog) {
        CatalogDialog(
            title = "Novo produto",
            onSave = { name, cat, price -> scope.launch { vm.addCatalog(name, cat, price) } },
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
            onSave = { name, cat, price -> scope.launch { vm.updateCatalog(name, cat, price) } },
            onDismiss = { vm.editCatalog = null }
        )
    }
    if (vm.showCustomerDialog) {
        CustomerDialog(
            onConfirm = { name -> scope.launch { vm.confirmSale(name) } },
            onDismiss = { vm.showCustomerDialog = false }
        )
    }
}

@Composable
fun CatalogScreen(catalog: List<CatalogItem>, vm: StockViewModel) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Define o produto 1 vez (nome + preço). Depois só scaneias códigos.",
            color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        Button(onClick = { vm.showNewCatalog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Novo produto")
        }
        LazyColumn {
            items(catalog, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("${item.category} · ${item.price} Kz") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.editCatalog = item }) {
                                Icon(Icons.Default.Edit, null)
                            }
                            IconButton(onClick = { scope.launch { vm.deleteCatalog(item) } }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red)
                            }
                        }
                    }
                )
                HorizontalDivider()
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
    Column(Modifier.fillMaxSize()) {
        Text("1. Escolhe o produto  2. Scaneia vários códigos",
            modifier = Modifier.padding(8.dp), color = Color.Gray)

        if (catalog.isEmpty()) {
            Text("Primeiro cria produtos na aba Catálogo.",
                modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            return
        }

        LazyColumn(modifier = Modifier.heightIn(max = 140.dp).padding(horizontal = 8.dp)) {
            items(catalog, key = { it.id }) { item ->
                val selected = vm.selectedCatalog?.id == item.id
                FilterChip(
                    selected = selected,
                    onClick = { vm.selectedCatalog = item },
                    label = { Text("${item.name} · ${item.price} Kz") },
                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                )
            }
        }

        val sel = vm.selectedCatalog
        if (sel != null) {
            Text("A registar: ${sel.name} (${sel.price} Kz)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(8.dp),
                color = Color(0xFF0F766E)
            )
            ScannerSection(onCode = { code ->
                scope.launch { vm.handleScanEntry(code); vibrate(context) }
            })
        } else {
            Text("Selecciona um produto em cima para começar a scanear.",
                modifier = Modifier.padding(16.dp))
        }

        Text("Stock disponível: $stockCount unidades",
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun ScannerSection(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var torch by remember { mutableStateOf(false) }
    var scannerHelper by remember { mutableStateOf<BarcodeScannerHelper?>(null) }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
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
    Column(Modifier.padding(8.dp)) {
        if (cart.isEmpty()) {
            Text("Scaneia códigos disponíveis. Cada um só vende 1 vez.", color = Color.Gray)
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
                "Total: ${"%,d".format(total).replace(',', '.')} Kz  (${cart.size} un.)",
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C))
            ) { Text("Confirmar venda (${cart.size})") }
            TextButton(onClick = { vm.clearCart() }, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar lista")
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
    val valorStock = available.sumOf { it.price }

    Column(Modifier.padding(8.dp)) {
        Text("Disponíveis: $stockCount  ·  Valor: ${"%,d".format(valorStock).replace(',', '.')} Kz",
            fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Vendidos (lista negra): ${sold.size}", color = Color.Gray)
        Row(Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = !showSold, onClick = { showSold = false }, label = { Text("Disponíveis") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = showSold, onClick = { showSold = true }, label = { Text("Vendidos") })
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            label = { Text("Pesquisar") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        LazyColumn {
            byCat.forEach { (cat, items) ->
                item {
                    Text("$cat (${items.size})", fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(items, key = { it.code }) { p ->
                    ListItem(
                        headlineContent = {
                            Text(p.name + if (p.status == "sold") "  [VENDIDO]" else "",
                                color = if (p.status == "sold") Color.Red else Color.Unspecified)
                        },
                        supportingContent = {
                            Text(if (p.status == "sold")
                                "${p.code} · ${p.price} Kz · ${p.soldTo ?: "?"}"
                            else "${p.code} · ${p.price} Kz")
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

@Composable
fun HistoryScreen(history: List<HistoryEntry>, vm: StockViewModel) {
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(8.dp)) {
        Button(onClick = { scope.launch { vm.undoLastSale() } }, modifier = Modifier.fillMaxWidth()) {
            Text("↩ Desfazer última venda")
        }
        LazyColumn {
            items(history) { h ->
                val items = vm.repo.parseItems(h.itemsJson)
                val typeLabel = if (h.type == "in") "Registo" else "Venda"
                val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                val client = if (h.customerName.isNotBlank()) " · ${h.customerName}" else ""
                ListItem(
                    headlineContent = { Text("$typeLabel$client · ${sdf.format(java.util.Date(h.timestamp))}") },
                    supportingContent = { Text(items.joinToString { "${it.name} (${it.code})" }) },
                    trailingContent = { if (h.totalKz > 0) Text("${h.totalKz} Kz") }
                )
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
    onSave: (String, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var cat by remember { mutableStateOf(initialCat) }
    var price by remember { mutableStateOf(initialPrice) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome (ex: Gin)") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço (Kz)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && cat.isNotBlank())
                    onSave(name.trim(), cat.trim(), price.toIntOrNull() ?: 0)
            }) { Text("Guardar") }
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
                Text("Obrigatório. Códigos passam para lista negra.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Cliente") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (name.trim().isNotEmpty()) onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty()) { Text("Confirmar venda") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

fun vibrate(context: android.content.Context) {
    try {
        val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}
