package com.example.waybill

import android.Manifest
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.util.Calendar

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
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

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
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Параметры и Водитель", fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = state.dateString,
                        onValueChange = { },
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

                    OutlinedTextField(
                        value = state.driverName,
                        onValueChange = { viewModel.updateDriverName(it) },
                        label = { Text("ФИО Водителя") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.vehicleModel,
                            onValueChange = { viewModel.updateVehicleModel(it) },
                            label = { Text("Марка") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.vehiclePlate,
                            onValueChange = { viewModel.updateVehiclePlate(it) },
                            label = { Text("Госномер") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            OutlinedTextField(
                                value = state.startOdometer,
                                onValueChange = { viewModel.updateStartOdometer(it) },
                                label = { Text("Выезд") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.endOdometer,
                                onValueChange = { viewModel.updateEndOdometer(it) },
                                label = { Text("Возврат") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = state.directDistance,
                            onValueChange = { viewModel.updateDirectDistance(it) },
                            label = { Text("Пройдено км") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "Итого пробег: %.1f км".format(state.calculatedDistance),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Топливо (л)", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.fuelAtStart,
                            onValueChange = { viewModel.updateFuelStart(it) },
                            label = { Text("Выезд") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.fuelRefueled,
                            onValueChange = { viewModel.updateFuelRefueled(it) },
                            label = { Text("Заправка") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.avgConsumption,
                            onValueChange = { viewModel.updateAvgConsumption(it) },
                            label = { Text("Норма") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Расход: %.2f л".format(state.fuelSpent))
                        Text("Ост. возврат: %.2f л".format(state.fuelAtEnd), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Маршрут движения", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = { viewModel.addRoutePoint() }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить точку")
                }
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
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            scope.launch {
                                val addr = locationHelper.getCurrentAddress()
                                if (addr != null) viewModel.updateAddress(point.id, addr)
                            }
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = "GPS")
                        }
                        if (state.routePoints.size > 1) {
                            IconButton(onClick = { viewModel.removeRoutePoint(point.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

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
            itemsIndexed(history) { index, historyItem ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Лист от ${historyItem.dateString}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            IconButton(onClick = { viewModel.deleteHistoryItem(context, index) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Text("Водитель: ${historyItem.driverName}")
                        Text("ТС: ${historyItem.vehicleModel} (${historyItem.vehiclePlate})")
                        Text(
                            "Пробег: %.1f км | Расход: %.2f л".format(
                                historyItem.calculatedDistance,
                                historyItem.fuelSpent
                            )
                        )
                        Text("Точек маршрута: ${historyItem.routePoints.size}")

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { PdfPrinterHelper.printWaybill(context, historyItem) },
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
