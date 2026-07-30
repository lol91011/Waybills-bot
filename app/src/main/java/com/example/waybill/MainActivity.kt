package com.example.waybill

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

// --- 1. МОДЕЛИ ДАННЫХ И ХРАНИЛИЩЕ ---

enum class DistanceInputMode { ODOMETER, DIRECT_DISTANCE }

data class RoutePointItem(
    val id: String = UUID.randomUUID().toString(),
    val address: String = ""
)

data class WaybillUiState(
    val dateString: String = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date()),
    val driverName: String = "Барыльченко В.А.",
    val vehicleModel: String = "Lada Largus",
    val vehiclePlate: String = "H541ME134",
    val inputMode: DistanceInputMode = DistanceInputMode.ODOMETER,
    val startOdometer: String = "200000",
    val endOdometer: String = "200150",
    val directDistance: String = "150",
    val fuelAtStart: String = "20.0",
    val fuelRefueled: String = "0.0",
    val avgConsumption: String = "13.78",
    val routePoints: List<RoutePointItem> = listOf(
        RoutePointItem(address = "г. Волгоград, Раздольная улица, 1"),
        RoutePointItem(address = "г. Волгоград, пр. Ленина, 10")
    )
) {
    val calculatedDistance: Double
        get() = if (inputMode == DistanceInputMode.ODOMETER) {
            val start = startOdometer.toDoubleOrNull() ?: 0.0
            val end = endOdometer.toDoubleOrNull() ?: 0.0
            (end - start).coerceAtLeast(0.0)
        } else directDistance.toDoubleOrNull() ?: 0.0

    val fuelSpent: Double
        get() = (calculatedDistance * (avgConsumption.toDoubleOrNull() ?: 0.0)) / 100.0

    val fuelAtEnd: Double
        get() = ((fuelAtStart.toDoubleOrNull() ?: 0.0) + (fuelRefueled.toDoubleOrNull() ?: 0.0) - fuelSpent).coerceAtLeast(0.0)
}

class WaybillViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WaybillUiState())
    val uiState: StateFlow<WaybillUiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<WaybillUiState>>(emptyList())
    val history: StateFlow<List<WaybillUiState>> = _history.asStateFlow()

    fun updateDate(date: String) = _uiState.update { it.copy(dateString = date) }
    fun updateDriverName(name: String) = _uiState.update { it.copy(driverName = name) }
    fun updateVehicleModel(model: String) = _uiState.update { it.copy(vehicleModel = model) }
    fun updateVehiclePlate(plate: String) = _uiState.update { it.copy(vehiclePlate = plate) }
    fun setInputMode(mode: DistanceInputMode) = _uiState.update { it.copy(inputMode = mode) }
    fun updateStartOdometer(v: String) = _uiState.update { it.copy(startOdometer = v) }
    fun updateEndOdometer(v: String) = _uiState.update { it.copy(endOdometer = v) }
    fun updateDirectDistance(v: String) = _uiState.update { it.copy(directDistance = v) }
    fun updateFuelStart(v: String) = _uiState.update { it.copy(fuelAtStart = v) }
    fun updateFuelRefueled(v: String) = _uiState.update { it.copy(fuelRefueled = v) }
    fun updateAvgConsumption(v: String) = _uiState.update { it.copy(avgConsumption = v) }

    fun updateAddress(id: String, newAddress: String) {
        _uiState.update { state ->
            state.copy(routePoints = state.routePoints.map { if (it.id == id) it.copy(address = newAddress) else it })
        }
    }

    fun addRoutePoint(address: String = "") {
        _uiState.update { it.copy(routePoints = it.routePoints + RoutePointItem(address = address)) }
    }

    fun removeRoutePoint(id: String) {
        _uiState.update { if (it.routePoints.size > 1) it.copy(routePoints = it.routePoints.filterNot { p -> p.id == id }) else it }
    }

    fun saveToHistory(context: Context) {
        val current = _uiState.value
        val newList = _history.value + current
        _history.value = newList
        saveHistoryToPrefs(context, newList)
    }

    fun loadHistory(context: Context) {
        _history.value = loadHistoryFromPrefs(context)
    }

    fun deleteHistoryItem(context: Context, index: Int) {
        val newList = _history.value.toMutableList().apply { removeAt(index) }
        _history.value = newList
        saveHistoryToPrefs(context, newList)
    }

    private fun saveHistoryToPrefs(context: Context, list: List<WaybillUiState>) {
        val prefs = context.getSharedPreferences("waybill_prefs", Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("date", item.dateString)
                put("driver", item.driverName)
                put("model", item.vehicleModel)
                put("plate", item.vehiclePlate)
                put("startOdo", item.startOdometer)
                put("endOdo", item.endOdometer)
                put("distance", item.calculatedDistance)
                put("fuelStart", item.fuelAtStart)
                put("fuelRefueled", item.fuelRefueled)
                put("avgConsumption", item.avgConsumption)
                put("fuelSpent", item.fuelSpent)
                put("fuelEnd", item.fuelAtEnd)
                val pointsArr = JSONArray()
                item.routePoints.forEach { pointsArr.put(it.address) }
                put("points", pointsArr)
            }
            array.put(obj)
        }
        prefs.edit().putString("history_json", array.toString()).apply()
    }

    private fun loadHistoryFromPrefs(context: Context): List<WaybillUiState> {
        val prefs = context.getSharedPreferences("waybill_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("history_json", null) ?: return emptyList()
        val list = mutableListOf<WaybillUiState>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pointsArr = obj.getJSONArray("points")
                val pts = mutableListOf<RoutePointItem>()
                for (j in 0 until pointsArr.length()) {
                    pts.add(RoutePointItem(address = pointsArr.getString(j)))
                }
                list.add(
                    WaybillUiState(
                        dateString = obj.optString("date", ""),
                        driverName = obj.optString("driver", ""),
                        vehicleModel = obj.optString("model", ""),
                        vehiclePlate = obj.optString("plate", ""),
                        startOdometer = obj.optString("startOdo", "0"),
                        endOdometer = obj.optString("endOdo", "0"),
                        directDistance = obj.optDouble("distance", 0.0).toString(),
                        fuelAtStart = obj.optString("fuelStart", "0"),
                        fuelRefueled = obj.optString("fuelRefueled", "0"),
                        avgConsumption = obj.optString("avgConsumption", "0"),
                        routePoints = pts
                    )
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }
}

// --- 2. GPS И GEOCODER ---

class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentAddress(): String? = withContext(Dispatchers.IO) {
        val location = suspendCancellableCoroutine<Location?> { continuation ->
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
            continuation.invokeOnCancellation { cts.cancel() }
        } ?: return@withContext null

        return@withContext try {
            val geocoder = Geocoder(context, Locale("ru", "RU"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val a = addresses[0]
                listOfNotNull(a.locality ?: a.subAdminArea, a.thoroughfare, a.subThoroughfare).joinToString(", ")
            } else "%.4f, %.4f".format(location.latitude, location.longitude)
        } catch (e: Exception) {
            "%.4f, %.4f".format(location.latitude, location.longitude)
        }
    }
}

// --- 3. ГЕНЕРАТОР PDF И ПЕЧАТЬ ---

object PdfPrinterHelper {
    fun printWaybill(context: Context, state: WaybillUiState) {
        val htmlContent = buildOfficialForm3Html(state)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Путевой_лист_${state.dateString}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun buildOfficialForm3Html(state: WaybillUiState): String {
        val routeRows = state.routePoints.mapIndexed { i, p ->
            """
            <tr>
                <td>${i + 1}</td>
                <td style="text-align:left;">${p.address}</td>
                <td>—</td>
                <td>—</td>
                <td>—</td>
                <td>—</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                @page { size: A4 portrait; margin: 8mm; }
                body { font-family: 'Times New Roman', serif; font-size: 8.5pt; color: #000; line-height: 1.1; }
                .top-right { float: right; text-align: right; font-size: 7.5pt; }
                .okud-table { float: right; border-collapse: collapse; margin-top: 3px; font-size: 7.5pt; }
                .okud-table td { border: 1px solid #000; padding: 1px 4px; text-align: center; }
                .clear { clear: both; }
                .title { text-align: center; font-weight: bold; font-size: 11pt; margin-top: 5px; }
                .subtitle { text-align: center; font-size: 9pt; margin-bottom: 8px; }
                table.main-grid { width: 100%; border-collapse: collapse; margin-top: 5px; }
                table.main-grid th, table.main-grid td { border: 1px solid #000; padding: 2px 3px; text-align: center; font-size: 8pt; }
                table.main-grid th { background-color: #f2f2f2; font-weight: bold; }
                .line-field { border-bottom: 1px solid #000; display: inline-block; padding: 0 4px; font-weight: bold; }
                .section-header { font-weight: bold; font-size: 8.5pt; margin-top: 8px; margin-bottom: 3px; background: #e8e8e8; padding: 2px 4px; border: 1px solid #000; }
                .sig-block { margin-top: 10px; font-size: 8pt; }
                .sig-row { display: flex; justify-content: space-between; margin-bottom: 4px; }
            </style>
        </head>
        <body>
            <div class="top-right">
                Типовая межотраслевая форма № 3<br>
                Утверждена постановлением Госкомстата России от 28.11.97 № 78
                <table class="okud-table">
                    <tr><td>Форма по ОКУД</td><td><b>0345001</b></td></tr>
                </table>
            </div>
            <div class="clear"></div>
            <div class="title">ПУТЕВОЙ ЛИСТ ЛЕГКОВОГО АВТОМОБИЛЯ № _____</div>
            <div class="subtitle">«<span class="line-field">${state.dateString}</span>» г.</div>

            <table style="width:100%; font-size:8.5pt; margin-bottom:6px;">
                <tr>
object PdfPrinterHelper {

    fun printWaybill(context: Context, state: WaybillUiState) {
        // 1. Чтение HTML-шаблона из папки assets
        val rawTemplate = try {
            context.assets.open("form3_template.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            "<html><body><h3>Ошибка чтения шаблона form3_template.html из assets</h3></body></html>"
        }

        // 2. Формирование строк маршрута для оборотной стороны (8 колонок)
        val routeRows = if (state.routePoints.size > 1) {
            // Если введено несколько точек, разбиваем их по парам (Откуда -> Куда)
            val distPerSegment = state.calculatedDistance / (state.routePoints.size - 1)
            state.routePoints.zipWithNext().mapIndexed { i, (from, to) ->
                """
                <tr>
                    <td>${i + 1}</td>
                    <td>—</td>
                    <td style="text-align:left;">${from.address}</td>
                    <td style="text-align:left;">${to.address}</td>
                    <td>08:00</td>
                    <td>17:00</td>
                    <td>${"%.1f".format(distPerSegment)}</td>
                    <td></td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        } else {
            // Запасной вариант для одной точки
            state.routePoints.mapIndexed { i, p ->
                """
                <tr>
                    <td>${i + 1}</td>
                    <td>—</td>
                    <td style="text-align:left;">${p.address}</td>
                    <td style="text-align:left;">г. Волгоград</td>
                    <td>08:00</td>
                    <td>17:00</td>
                    <td>${"%.1f".format(state.calculatedDistance)}</td>
                    <td></td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        // 3. Автозамена всех меток шаблона на реальные данные из формы
        val filledHtml = rawTemplate
            .replace("{{DATE}}", state.dateString)
            .replace("{{MODEL}}", state.vehicleModel)
            .replace("{{PLATE}}", state.vehiclePlate)
            .replace("{{DRIVER}}", state.driverName)
            .replace("{{START_ODO}}", state.startOdometer)
            .replace("{{END_ODO}}", state.endOdometer)
            .replace("{{DISTANCE}}", "%.1f".format(state.calculatedDistance))
            .replace("{{FUEL_START}}", state.fuelAtStart)
            .replace("{{FUEL_REFUELED}}", state.fuelRefueled)
            .replace("{{FUEL_SPENT}}", "%.2f".format(state.fuelSpent))
            .replace("{{FUEL_END}}", "%.2f".format(state.fuelAtEnd))
            .replace("{{ROUTE_ROWS}}", routeRows)

        // 4. Отправка заполненного HTML в системный печатный движок Android
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Путевой_лист_${state.dateString}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, filledHtml, "text/html", "UTF-8", null)
    }
}


// --- 4. MAIN ACTIVITY И COMPOSE UI ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: WaybillViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadHistory(context)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Путевой лист № 3", fontWeight = FontWeight.Bold) })
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Новый лист") },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("История") },
                        icon = { Icon(Icons.Default.History, contentDescription = null) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedTab == 0) {
                WaybillFormScreen(viewModel = viewModel)
            } else {
                HistoryScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun WaybillFormScreen(viewModel: WaybillViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Календарь для выбора даты
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate = "%02d.%02d.%d".format(dayOfMonth, month + 1, year)
            viewModel.updateDate(formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Дата и Инфо
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Параметры и Водитель", fontWeight = FontWeight.Bold)
                    
                    // Поле выбора даты
                    OutlinedTextField(
                        value = state.dateString,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Дата путевого листа") },
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Выбрать дату")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() }
                    )

                    OutlinedTextField(state.driverName, viewModel::updateDriverName, label = { Text("ФИО Водителя") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(state.vehicleModel, viewModel::updateVehicleModel, label = { Text("Марка") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(state.vehiclePlate, viewModel::updateVehiclePlate, label = { Text("Госномер") }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Пробег
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пробег (км)", fontWeight = FontWeight.Bold)
                    Row {
                        FilterChip(
                            selected = state.inputMode == DistanceInputMode.ODOMETER,
                            onClick = { viewModel.setInputMode(DistanceInputMode.ODOMETER) },
                            label = { Text("Одометр") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = state.inputMode == DistanceInputMode.DIRECT_DISTANCE,
                            onClick = { viewModel.setInputMode(DistanceInputMode.DIRECT_DISTANCE) },
                            label = { Text("Расстояние") }
                        )
                    }
                    if (state.inputMode == DistanceInputMode.ODOMETER) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(state.startOdometer, viewModel::updateStartOdometer, label = { Text("Выезд") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(state.endOdometer, viewModel::updateEndOdometer, label = { Text("Возврат") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                    } else {
                        OutlinedTextField(state.directDistance, viewModel::updateDirectDistance, label = { Text("Пройдено км") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                    Text("Итого пробег: %.1f км".format(state.calculatedDistance), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Топливо
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Топливо (л)", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(state.fuelAtStart, viewModel::updateFuelStart, label = { Text("Выезд") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(state.fuelRefueled, viewModel::updateFuelRefueled, label = { Text("Заправка") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(state.avgConsumption, viewModel::updateAvgConsumption, label = { Text("Норма") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Расход: %.2f л".format(state.fuelSpent))
                        Text("Ост. возврат: %.2f л".format(state.fuelAtEnd), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Маршрут
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Маршрут движения", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = { viewModel.addRoutePoint() }) { Icon(Icons.Default.Add, null) }
            }
        }

        itemsIndexed(state.routePoints) { index, point ->
            OutlinedTextField(
                value = point.address,
                onValueChange = { viewModel.updateAddress(point.id, it) },
                label = { Text("Точка ${index + 1}") },
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            scope.launch {
                                val addr = locationHelper.getCurrentAddress()
                                if (addr != null) viewModel.updateAddress(point.id, addr)
                            }
                        }) { Icon(Icons.Default.MyLocation, "GPS") }
                        if (state.routePoints.size > 1) {
                            IconButton(onClick = { viewModel.removeRoutePoint(point.id) }) { Icon(Icons.Default.Delete, "Удалить") }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // КНОПКА СФОРМИРОВАТЬ И СОХРАНИТЬ
        item {
            Button(
                onClick = {
                    viewModel.saveToHistory(context)
                    PdfPrinterHelper.printWaybill(context, state)
                    Toast.makeText(context, "Сохранено в историю!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Сформировать и печать PDF", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: WaybillViewModel) {
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current

    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("История путевых листов пуста", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(history) { index, item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Лист от ${item.dateString}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { viewModel.deleteHistoryItem(context, index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text("Водитель: ${item.driverName}")
                        Text("ТС: ${item.vehicleModel} (${item.vehiclePlate})")
                        Text("Пробег: %.1f км | Расход: %.2f л".format(item.calculatedDistance, item.fuelSpent))
                        Text("Точек маршрута: ${item.routePoints.size}")

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { PdfPrinterHelper.printWaybill(context, item) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Открыть / Печать PDF")
                        }
                    }
                }
            }
        }
    }
}
      
