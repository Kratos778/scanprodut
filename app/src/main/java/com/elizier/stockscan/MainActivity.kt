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
                Surface(Modifier = Modifier.fillMaxSize()) { StockScanApp() }
            }
        }
    }
}

@Composable
fun StockScanApp(vm: StockViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
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
                    icon = { Icon(Icons.Default.AddBox, null) }, label = { Text("Registar") })
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
                0 -> {
                    ScannerSection(onCode = { code ->
                        scope.launch {
                            vm.handleScanEntry(code)
                            vibrate(context)
                        }
                    })
                    Button(
                        onClick = { vm.showManualRegister = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Text("Registar à mão (escrever código)")
                    }
                    Text(
                        "Stock disponível: $stockCount unidades",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                    Text(
                        "Cada código só se regista 1 vez. Se já existir ou já foi vendido, a app rejeita.",
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                1 -> {
                    ScannerSection(onCode = { code ->
                        scope.launch {
                            vm.handleScanSale(code)
                            vibrate(context)
                        }
                    })
                    CartSection(cart, vm, onConfirmClick = { vm.showCustomerDialog = true })
                }
                2 -> StockScreen(available, sold, stockCount, vm)
                3 -> HistoryScreen(history, vm)
            }
        }
    }

    if (vm.showNewProduct) {
        NewProductDialog(
            code = vm.pendingCode,
            onSave = { name, cat, price -> scope.launch { vm.registerNewProduct(name, cat, price) } },
            onDismiss = { vm.showNewProduct = false; vm.pendingCode = "" }
        )
    }
    if (vm.showManualRegister) {
        ManualRegisterDialog(
            onSave = { code, name, cat, price -> scope.launch { vm.registerManual(code, name, cat, price) } },
            onDismiss = { vm.showManualRegister = false }
        )
    }
    if (vm.editProduct != null) {
        EditProductDialog(
            product = vm.editProduct!!,
            onSave = { name, cat, price -> scope.launch { vm.updateProduct(name, cat, price) } },
            onDismiss = { vm.editProduct = null }
        )
    }
    if (vm.showCustomerDialog) {
        CustomerDialog(
            onConfirm = { customerName -> scope.launch { vm.confirmSale(customerName) } },
            onDismiss = { vm.showCustomerDialog = false }
        )
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
            Text("Scaneia 1 código de cada vez. Cada unidade só pode ser vendida 1 vez.", color = Color.Gray)
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
            label = { Text("Pesquisar nome ou código") },
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
                                Row {
                                    IconButton(onClick = { vm.editProduct = p }) {
                                        Icon(Icons.Default.Edit, null)
                                    }
                                    IconButton(onClick = { scope.launch { vm.deleteProduct(p) } }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
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
fun NewProductDialog(code: String, onSave: (String, String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registar produto") },
        text = {
            Column {
                Text("Código: $code", fontWeight = FontWeight.Bold)
                Text("Este código só pode ser registado UMA vez.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço (Kz)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && cat.isNotBlank()) onSave(name.trim(), cat.trim(), price.toIntOrNull() ?: 0)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ManualRegisterDialog(onSave: (String, String, String, Int) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registar à mão") },
        text = {
            Column {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código de barras") }, singleLine = true)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço (Kz)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (code.isNotBlank() && name.isNotBlank() && cat.isNotBlank())
                    onSave(code.trim(), name.trim(), cat.trim(), price.toIntOrNull() ?: 0)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun EditProductDialog(product: Product, onSave: (String, String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(product.name) }
    var cat by remember { mutableStateOf(product.category) }
    var price by remember { mutableStateOf(product.price.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar") },
        text = {
            Column {
                Text("Código: ${product.code} (não muda)")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço (Kz)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), cat.trim(), price.toIntOrNull() ?: 0) }) { Text("Guardar") }
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
                Text("Obrigatório. Os códigos passam para a lista negra.")
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
