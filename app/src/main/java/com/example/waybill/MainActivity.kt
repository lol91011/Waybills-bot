package com.example.waybill

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

// --- 1. МОДЕЛИ ДАННЫХ И VIEWMODEL ---

enum class DistanceInputMode { ODOMETER, DIRECT_DISTANCE }

data class RoutePointItem(
    val id: String = UUID.randomUUID().toString(),
    val address: String = ""
)

data class WaybillUiState(
    val driverName: String = "Иванов И.И.",
    val vehicleModel: String = "ГАЗель NEXT",
    val vehiclePlate: String = "А 777 АА 777",
    val inputMode: DistanceInputMode = DistanceInputMode.ODOMETER,
    val startOdometer: String = "120000",
    val endOdometer: String = "120150",
    val directDistance: String = "150",
    val fuelAtStart: String = "20.0",
    val fuelRefueled: String = "30.0",
    val avgConsumption: String = "12.0",
    val routePoints: List<RoutePointItem> = listOf(
        RoutePointItem(address = "г. Москва, ул. Ленина, д. 1"),
        RoutePointItem(address = "г. Москва, ул. Тверская, д. 10")
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

// --- 3. ГЕНЕРАТОР PDF (ФОРМА №3) ---

object Form3HtmlBuilder {
    fun buildHtml(state: WaybillUiState): String {
        val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date())
        val routeRows = state.routePoints.mapIndexed { i, p ->
            "<tr><td>${i + 1}</td><td>${p.address}</td><td>—</td><td>—</td></tr>"
        }.joinToString("\n")

        return """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <style>
            body { font-family: sans-serif; font-size: 11pt; margin: 15px; }
            .h { text-align: center; font-weight: bold; font-size: 13pt; }
            table { width: 100%; border-collapse: collapse; margin-top: 10px; }
            th, td { border: 1px solid black; padding: 4px; text-align: center; font-size: 9pt; }
            th { background: #eee; }
        </style></head><body>
            <div class="h">ПУТЕВОЙ ЛИСТ ЛЕГКОВОГО АВТОМОБИЛЯ № _____</div>
            <div style="text-align:center;font-size:10pt;">от $currentDate г. (Форма № 3)</div>
            <p><b>Водитель:</b> ${state.driverName}<br><b>Автомобиль:</b> ${state.vehicleModel} (${state.vehiclePlate})</p>
            <table>
                <tr><th>Выезд (км)</th><th>Возврат (км)</th><th>Пробег</th><th>Топливо нач.</th><th>Заправка</th><th>Расход</th><th>Топливо кон.</th></tr>
                <tr><td>${state.startOdometer}</td><td>${state.endOdometer}</td><td><b>${"%.1f".format(state.calculatedDistance)}</b></td>
                <td>${state.fuelAtStart}</td><td>${state.fuelRefueled}</td><td>${"%.2f".format(state.fuelSpent)}</td><td><b>${"%.2f".format(state.fuelAtEnd)}</b></td></tr>
            </table>
            <h3>Маршрут движения</h3>
            <table><tr><th>№</th><th>Адрес / Пункт</th><th>Выезд</th><th>Приезд</th></tr>$routeRows</table>
        </body></html>
        """.trimIndent()
    }
}

// --- 4. MAIN ACTIVITY И COMPOSE UI ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WaybillScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaybillScreen(viewModel: WaybillViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    Scaffold(topBar = { TopAppBar(title = { Text("Путевой лист № 3") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Водитель и ТС", fontWeight = FontWeight.Bold)
                        OutlinedTextField(state.driverName, viewModel::updateDriverName, label = { Text("ФИО Водителя") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(state.vehicleModel, viewModel::updateVehicleModel, label = { Text("Марка") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(state.vehiclePlate, viewModel::updateVehiclePlate, label = { Text("Госномер") }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Пробег (км)", fontWeight = FontWeight.Bold)
                        Row {
                            FilterChip(selected = state.inputMode == DistanceInputMode.ODOMETER, onClick = { viewModel.setInputMode(DistanceInputMode.ODOMETER) }, label = { Text("Одометр") })
                            Spacer(Modifier.width(8.dp))
                            FilterChip(selected = state.inputMode == DistanceInputMode.DIRECT_DISTANCE, onClick = { viewModel.setInputMode(DistanceInputMode.DIRECT_DISTANCE) }, label = { Text("Расстояние") })
                        }
                        if (state.inputMode == DistanceInputMode.ODOMETER) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(state.startOdometer, viewModel::updateStartOdometer, label = { Text("Выезд") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                OutlinedTextField(state.endOdometer, viewModel::updateEndOdometer, label = { Text("Возврат") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            }
                        } else {
                            OutlinedTextField(state.directDistance, viewModel::updateDirectDistance, label = { Text("Пройдено км") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        }
                        Text("Итого пробег: %.1f км".format(state.calculatedDistance), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Топливо (л)", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(state.fuelAtStart, viewModel::updateFuelStart, label = { Text("Выезд") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(state.fuelRefueled, viewModel::updateFuelRefueled, label = { Text("Заправка") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(state.avgConsumption, viewModel::updateAvgConsumption, label = { Text("Норма") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                        Text("Остаток при возврате: %.2f л".format(state.fuelAtEnd), fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Маршрут", fontWeight = FontWeight.Bold)
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
        }
    }
}
