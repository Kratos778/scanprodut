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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
        if (!granted) {
            Toast.makeText(this, "Precisa de permissão da câmara", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
        setContent {
            StockScanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StockScanApp()
                }
            }
        }
    }
}

@Composable
fun StockScanApp(vm: StockViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val products by vm.products.collectAsState(initial = emptyList())
    val history by vm.history.collectAsState(initial = emptyList())
    val cart by vm.cart.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.AddBox, null) }, label = { Text("Entrada") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Venda") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Inventory, null) }, label = { Text("Stock") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.History, null) }, label = { Text("Histórico") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0, 1 -> {
                    ScannerSection(
                        qty = vm.scanQty,
                        onQtyChange = { vm.scanQty = it },
                        onCode = { code ->
                            scope.launch {
                                vm.handleScan(code, tab == 1)
                                vibrate(context)
                            }
                        }
                    )
                    if (tab == 0) {
                        EntryList(products, vm.currentCategory, onCategory = { vm.currentCategory = it })
                    } else {
                        CartSection(
                            cart = cart,
                            vm = vm,
                            onConfirmClick = { vm.showCustomerDialog = true }
                        )
                    }
                }
                2 -> StockScreen(products, vm)
                3 -> HistoryScreen(history, vm)
            }
        }
    }

    if (vm.showNewProduct) {
        NewProductDialog(
            code = vm.pendingCode,
            onSave = { name, cat, price, min ->
                scope.launch { vm.saveNewProduct(name, cat, price, min) }
            },
            onDismiss = { vm.showNewProduct = false }
        )
    }
    if (vm.editProduct != null) {
        EditProductDialog(
            product = vm.editProduct!!,
            onSave = { name, cat, price, min ->
                scope.launch { vm.updateProduct(name, cat, price, min) }
            },
            onDismiss = { vm.editProduct = null }
        )
    }
    if (vm.showCustomerDialog) {
        CustomerDialog(
            onConfirm = { customerName ->
                scope.launch {
                    val ok = vm.confirmSale(customerName)
                    Toast.makeText(
                        context,
                        if (ok) "Venda registada ✓ ($customerName)"
                        else "Falha: stock insuficiente ou nome vazio",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { vm.showCustomerDialog = false }
        )
    }
}

@Composable
fun CustomerDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nome do cliente") },
        text = {
            Column {
                Text("Obrigatório para registar a venda.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Cliente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) onConfirm(name.trim())
                },
                enabled = name.trim().isNotEmpty()
            ) { Text("Confirmar venda") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ScannerSection(qty: Int, onQtyChange: (Int) -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torch by remember { mutableStateOf(false) }
    var scannerHelper by remember { mutableStateOf<BarcodeScannerHelper?>(null) }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    val helper = BarcodeScannerHelper(ctx, lifecycleOwner, preview) { code ->
                        onCode(code)
                    }
                    helper.start()
                    scannerHelper = helper
                }
            },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 2, 6, 12).forEach { q ->
                FilterChip(
                    selected = qty == q,
                    onClick = { onQtyChange(q) },
                    label = { Text("×$q") }
                )
            }
            OutlinedTextField(
                value = if (qty in listOf(1, 2, 6, 12)) "" else qty.toString(),
                onValueChange = {
                    val v = it.toIntOrNull()
                    if (v != null && v > 0) onQtyChange(v)
                },
                modifier = Modifier.width(70.dp),
                singleLine = true,
                label = { Text("Qtd") }
            )
            IconButton(onClick = {
                torch = !torch
                scannerHelper?.toggleTorch(torch)
            }) {
                Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Lanterna")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { scannerHelper?.release() }
    }
}

@Composable
fun EntryList(products: List<Product>, category: String, onCategory: (String) -> Unit) {
    val filtered = if (category.isBlank()) products else products.filter { it.category == category }
    Column(Modifier.padding(8.dp)) {
        OutlinedTextField(
            value = category,
            onValueChange = onCategory,
            label = { Text("Categoria (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (category.isNotBlank()) {
            Text(
                "Total em $category: ${filtered.sumOf { it.quantity }}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        LazyColumn {
            items(filtered) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${p.code} · ${p.price} Kz") },
                    trailingContent = { Text("${p.quantity}", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                )
            }
        }
    }
}

@Composable
fun CartSection(cart: List<CartItem>, vm: StockViewModel, onConfirmClick: () -> Unit) {
    val total = cart.sumOf { it.qty * it.price }
    Column(Modifier.padding(8.dp)) {
        if (cart.isEmpty()) {
            Text("Scaneia os produtos. Depois carrega Confirmar e indica o nome do cliente.", color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(cart) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("${item.price} Kz") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.adjustCart(item.code, -1) }) {
                                    Icon(Icons.Default.Remove, null)
                                }
                                Text("${item.qty}", fontWeight = FontWeight.Bold)
                                IconButton(onClick = { vm.adjustCart(item.code, 1) }) {
                                    Icon(Icons.Default.Add, null)
                                }
                            }
                        }
                    )
                }
            }
            Text(
                "Total: ${"%,d".format(total).replace(',', '.')} Kz",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C))
            ) {
                Text("Confirmar venda (${cart.sumOf { it.qty }})")
            }
            TextButton(onClick = { vm.clearCart() }, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar lista")
            }
        }
    }
}

@Composable
fun StockScreen(products: List<Product>, vm: StockViewModel) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    val filtered = products.filter {
        search.isBlank() || it.name.contains(search, true) || it.code.contains(search)
    }
    val byCat = filtered.groupBy { it.category }

    Column(Modifier.padding(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Pesquisar") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        LazyColumn {
            byCat.forEach { (cat, list) ->
                item {
                    Text(
                        "$cat  (${list.sumOf { it.quantity }})",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(list) { p ->
                    val low = p.minStock > 0 && p.quantity <= p.minStock
                    ListItem(
                        headlineContent = {
                            Text(
                                p.name + if (low) "  ⚠ baixo" else "",
                                color = if (low) Color.Red else Color.Unspecified
                            )
                        },
                        supportingContent = { Text("${p.code} · ${p.price} Kz") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { scope.launch { vm.adjustStock(p.code, -1) } }) {
                                    Icon(Icons.Default.Remove, null)
                                }
                                Text("${p.quantity}", fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterVertically))
                                IconButton(onClick = { scope.launch { vm.adjustStock(p.code, 1) } }) {
                                    Icon(Icons.Default.Add, null)
                                }
                                IconButton(onClick = { vm.editProduct = p }) {
                                    Icon(Icons.Default.Edit, null)
                                }
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
    val context = LocalContext.current
    Column(Modifier.padding(8.dp)) {
        Button(
            onClick = {
                scope.launch {
                    val ok = vm.undoLastSale()
                    Toast.makeText(
                        context,
                        if (ok) "Venda desfeita ✓" else "Nenhuma venda para desfazer",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("↩ Desfazer última venda")
        }
        LazyColumn {
            items(history) { h ->
                val items = vm.repo.parseItems(h.itemsJson)
                val typeLabel = if (h.type == "in") "Entrada" else "Venda"
                val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                val client = if (h.customerName.isNotBlank()) " · ${h.customerName}" else ""
                ListItem(
                    headlineContent = {
                        Text("$typeLabel$client · ${sdf.format(java.util.Date(h.timestamp))}")
                    },
                    supportingContent = {
                        Text(items.joinToString { "${it.name} ×${it.qty}" })
                    },
                    trailingContent = {
                        if (h.totalKz > 0) Text("${h.totalKz} Kz")
                    }
                )
            }
        }
    }
}

@Composable
fun NewProductDialog(
    code: String,
    onSave: (String, String, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Produto novo") },
        text = {
            Column {
                Text("Código: $code", style = MaterialTheme.typography.bodySmall)
                Text("Este código ainda não existe. Preenche os dados.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço (Kz)") }, singleLine = true)
                OutlinedTextField(value = min, onValueChange = { min = it }, label = { Text("Stock mínimo") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && cat.isNotBlank()) {
                    onSave(name.trim(), cat.trim(), price.toIntOrNull() ?: 0, min.toIntOrNull() ?: 0)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EditProductDialog(
    product: Product,
    onSave: (String, String, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var cat by remember { mutableStateOf(product.category) }
    var price by remember { mutableStateOf(product.price.toString()) }
    var min by remember { mutableStateOf(product.minStock.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar produto") },
        text = {
            Column {
                Text("Código: ${product.code} (não muda)", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço (Kz)") }, singleLine = true)
                OutlinedTextField(value = min, onValueChange = { min = it }, label = { Text("Stock mínimo") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(name.trim(), cat.trim(), price.toIntOrNull() ?: 0, min.toIntOrNull() ?: 0)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

fun vibrate(context: android.content.Context) {
    try {
        val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}
