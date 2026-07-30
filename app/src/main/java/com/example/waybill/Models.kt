package com.example.waybill

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class DistanceInputMode { ODOMETER, DIRECT_DISTANCE }

data class RoutePointItem(
    val id: String = UUID.randomUUID().toString(),
    val address: String = ""
)

data class WaybillUiState(
    val dateString: String = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date()),
    val driverName: String = "Барыльченко В.А.",
    val vehicleModel: String = "LADA LARGUS",
    val vehiclePlate: String = "Н659МЕ 134",
    val inputMode: DistanceInputMode = DistanceInputMode.ODOMETER,
    val startOdometer: String = "200000",
    val endOdometer: String = "200150",
    val directDistance: String = "150",
    val fuelAtStart: String = "20.0",
    val fuelRefueled: String = "30.0",
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
