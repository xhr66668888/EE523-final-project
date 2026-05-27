package com.aeroscanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

data class ScanData(
    val originCode: String?,
    val destinationCode: String?,
    val price: Double?,
    val rawText: String,
)

class PlannerViewModel : ViewModel() {
    var originInput by mutableStateOf("")
    var destinationInput by mutableStateOf("")
    var priceInput by mutableStateOf("")
    var routeInput by mutableStateOf("")
    var useMultiSegment by mutableStateOf(false)
    var selectedCurrency by mutableStateOf("CNY")
    var unitSystem by mutableStateOf(UnitSystem.METRIC)
    var baggageWeightInput by mutableStateOf("")
    var baggageAllowanceInput by mutableStateOf("")

    var result by mutableStateOf<CostTools.CostResult?>(null)
    var error by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
    var airportDbLoaded by mutableStateOf(false)

    var isLocating by mutableStateOf(false)
    var locationHint by mutableStateOf<String?>(null)
    var isScanning by mutableStateOf(false)
    var scanResult by mutableStateOf<ScanData?>(null)

    var exchangeRates by mutableStateOf(TravelDisplay.fallbackCnyRates)
    var exchangeUpdatedAt by mutableStateOf("offline estimates")
    var usingFallbackRates by mutableStateOf(true)
    var isRefreshingRates by mutableStateOf(false)

    var destinationWeather by mutableStateOf<DestinationWeather?>(null)
    var weatherError by mutableStateOf<String?>(null)
    var isLoadingWeather by mutableStateOf(false)

    fun initDb(context: android.content.Context) {
        if (airportDbLoaded) return
        viewModelScope.launch {
            try {
                AirportDatabase.load(context)
                airportDbLoaded = true
            } catch (e: Exception) {
                error = e.message ?: "Failed to load airport database."
            }
        }
    }

    fun refreshExchangeRates() {
        isRefreshingRates = true
        viewModelScope.launch {
            try {
                val snapshot = TravelApiClient().fetchCnyExchangeRates()
                exchangeRates = snapshot.rates
                exchangeUpdatedAt = snapshot.updatedAt
                usingFallbackRates = snapshot.isFallback
            } catch (_: Exception) {
                exchangeRates = TravelDisplay.fallbackCnyRates
                exchangeUpdatedAt = "offline estimates"
                usingFallbackRates = true
            } finally {
                isRefreshingRates = false
            }
        }
    }

    fun calculate() {
        error = null
        result = null
        destinationWeather = null
        weatherError = null
        isLoading = true

        viewModelScope.launch {
            try {
                val parsedRoute = if (useMultiSegment && routeInput.isNotBlank()) {
                    RouteTools.parseRoute(routeInput)
                } else {
                    val origin = RouteTools.normalizeIata(originInput)
                    val dest = RouteTools.normalizeIata(destinationInput)
                    RouteTools.parseRoute("$origin-$dest")
                }

                val priceRmb = CostTools.parseTicketPriceToCny(
                    value = priceInput,
                    currencyCode = selectedCurrency,
                    cnyToCurrencyRate = TravelDisplay.rateFor(selectedCurrency, exchangeRates),
                )
                val baggageWeightKg = TravelDisplay.parseWeightToKg(baggageWeightInput, unitSystem)
                val allowanceKg = TravelDisplay.parseWeightToKg(baggageAllowanceInput, unitSystem)
                    ?: CostTools.DEFAULT_BAGGAGE_ALLOWANCE_KG

                val costResult = CostTools.calculateManualCost(
                    parsedRoute = parsedRoute,
                    ticketPriceRmb = priceRmb,
                    baggageWeightKg = baggageWeightKg,
                    baggageAllowanceKg = allowanceKg,
                )

                result = costResult
                HistoryStore.append(
                    costResult.toCostRecord(
                        source = "planner",
                        type = "trip_plan",
                        currency = selectedCurrency,
                    )
                )
                loadDestinationWeather(costResult)
            } catch (e: Exception) {
                error = e.message ?: "Calculation failed."
            } finally {
                isLoading = false
            }
        }
    }

    fun requestLocation(context: android.content.Context) {
        isLocating = true
        locationHint = null
        viewModelScope.launch {
            try {
                val finder = NearestAirportFinder(context)
                when (val r = finder.findNearestAirport()) {
                    is NearestAirportFinder.LocationResult.Success -> {
                        originInput = r.airport.code
                        locationHint = "Nearest airport: ${r.airport.code} (${r.airport.city}), " +
                            "${TravelDisplay.formatDistance(r.distanceKm, unitSystem)} away"
                    }
                    is NearestAirportFinder.LocationResult.Error -> {
                        error = r.message
                    }
                }
            } finally {
                isLocating = false
            }
        }
    }

    fun applyScan(data: ScanData) {
        if (data.originCode != null) originInput = data.originCode
        if (data.destinationCode != null) destinationInput = data.destinationCode
        if (data.price != null) priceInput = data.price.toString()
        scanResult = data
        isScanning = false
    }

    private fun loadDestinationWeather(costResult: CostTools.CostResult) {
        val destinationCode = costResult.routeCodes.lastOrNull() ?: return
        val airport = AirportDatabase.get(destinationCode) ?: return
        isLoadingWeather = true
        viewModelScope.launch {
            try {
                destinationWeather = TravelApiClient().fetchDestinationWeather(airport)
            } catch (e: Exception) {
                weatherError = e.message ?: "Destination weather unavailable."
            } finally {
                isLoadingWeather = false
            }
        }
    }
}

private fun CostTools.CostResult.toCostRecord(
    source: String,
    type: String,
    currency: String,
) = CostRecord(
    route = route,
    routeCodes = routeCodes,
    segments = segments.map { seg ->
        CostSegmentData(
            originCode = seg.originCode,
            destinationCode = seg.destinationCode,
            originCity = seg.originCity,
            destinationCity = seg.destinationCity,
            distanceKm = seg.distanceKm,
            flightNumber = seg.flightNumber,
            departureTime = seg.departureTime,
            arrivalTime = seg.arrivalTime,
            cabin = seg.cabin,
            bookingClass = seg.bookingClass,
            fareBasis = seg.fareBasis,
        )
    },
    surfaceTransfers = surfaceTransfers.map { st ->
        SurfaceTransferData(
            originCode = st.originCode,
            destinationCode = st.destinationCode,
            originCity = st.originCity,
            destinationCity = st.destinationCity,
            distanceKm = st.distanceKm,
        )
    },
    totalDistanceKm = totalDistanceKm,
    type = type,
    source = source,
    createdAt = createdAt,
    ticketPriceRmb = ticketPriceRmb,
    totalTripCostRmb = totalTripCostRmb,
    costPerKmRmb = costPerKmRmb,
    carbonKg = carbonKg,
    baggageWeightKg = baggageWeightKg,
    baggageAllowanceKg = baggageAllowanceKg,
    baggageOverageFeeRmb = baggageOverageFeeRmb,
    priceLevel = priceLevel.name,
    currency = currency,
)

@Composable
fun CalculatorScreen(viewModel: PlannerViewModel = viewModel()) {
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (coarse || fine) {
            viewModel.requestLocation(context)
        } else {
            viewModel.error = "Location permission denied. Enter the airport code manually."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.isScanning = true
        } else {
            viewModel.error = "Camera permission is needed for OCR scanning."
        }
    }

    LaunchedEffect(Unit) {
        HistoryStore.init(context)
        viewModel.initDb(context)
        viewModel.refreshExchangeRates()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        Header()

        ApiStatusCard(
            usingFallbackRates = viewModel.usingFallbackRates,
            updatedAt = viewModel.exchangeUpdatedAt,
            isRefreshing = viewModel.isRefreshingRates,
            onRefresh = viewModel::refreshExchangeRates,
        )

        if (!viewModel.airportDbLoaded) {
            InlineProgress("Loading airport database...")
        }

        TripInputCard(
            viewModel = viewModel,
            onLocate = {
                val hasLocation = ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                if (hasLocation) {
                    viewModel.requestLocation(context)
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            },
        )

        OutlinedButton(
            onClick = {
                val hasCamera = ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCamera) {
                    viewModel.isScanning = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(Radius.sm),
            border = BorderStroke(1.dp, AeroscannerColors.Hairline),
        ) {
            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Scan boarding pass or receipt", style = AeroscannerTypography.labelMedium)
        }

        Button(
            onClick = viewModel::calculate,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !viewModel.isLoading && viewModel.airportDbLoaded,
            shape = RoundedCornerShape(Radius.sm),
            colors = ButtonDefaults.buttonColors(
                containerColor = AeroscannerColors.Primary,
                disabledContainerColor = AeroscannerColors.PrimaryDisabled,
                disabledContentColor = Color.White,
            ),
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Calculating...", style = AeroscannerTypography.labelLarge)
            } else {
                Icon(Icons.Default.FlightTakeoff, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Analyze Trip", style = AeroscannerTypography.labelLarge)
            }
        }

        AnimatedVisibility(visible = viewModel.error != null, enter = fadeIn(), exit = fadeOut()) {
            FeedbackCard(
                text = viewModel.error.orEmpty(),
                isError = true,
                actionLabel = "Dismiss",
                onAction = { viewModel.error = null },
            )
        }

        AnimatedVisibility(visible = viewModel.locationHint != null, enter = fadeIn(), exit = fadeOut()) {
            FeedbackCard(text = viewModel.locationHint.orEmpty(), isError = false)
        }

        AnimatedVisibility(visible = viewModel.scanResult != null, enter = fadeIn(), exit = fadeOut()) {
            viewModel.scanResult?.let { scan ->
                FeedbackCard(
                    text = "Scan captured ${scan.originCode ?: "?"} to ${scan.destinationCode ?: "?"} " +
                        "and ${scan.rawText.length} characters of OCR text.",
                    isError = false,
                )
            }
        }

        AnimatedVisibility(visible = viewModel.result != null, enter = fadeIn(), exit = fadeOut()) {
            viewModel.result?.let { costResult ->
                ResultCard(
                    costResult = costResult,
                    currencyCode = viewModel.selectedCurrency,
                    rates = viewModel.exchangeRates,
                    unitSystem = viewModel.unitSystem,
                )
            }
        }

        if (viewModel.isLoadingWeather) {
            InlineProgress("Fetching destination weather...")
        }

        AnimatedVisibility(visible = viewModel.destinationWeather != null, enter = fadeIn(), exit = fadeOut()) {
            viewModel.destinationWeather?.let {
                WeatherCard(weather = it, unitSystem = viewModel.unitSystem)
            }
        }

        AnimatedVisibility(visible = viewModel.weatherError != null, enter = fadeIn(), exit = fadeOut()) {
            FeedbackCard(
                text = "Weather API unavailable. Trip analysis is still saved.",
                isError = false,
                actionLabel = "Dismiss",
                onAction = { viewModel.weatherError = null },
            )
        }

        Spacer(Modifier.height(Spacing.lg))
    }

    if (viewModel.isScanning) {
        Dialog(
            onDismissRequest = { viewModel.isScanning = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoardingPassCamera(
                onResult = { scanData -> viewModel.applyScan(scanData) },
                onDismiss = { viewModel.isScanning = false },
            )
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = "AeroScanner",
            style = AeroscannerTypography.displayMedium,
            color = AeroscannerColors.Ink,
        )
        Text(
            text = "Airport-aware trip cost, distance, weather, carbon, and baggage planning",
            style = AeroscannerTypography.bodyMedium,
            color = AeroscannerColors.Muted,
        )
    }
}

@Composable
private fun ApiStatusCard(
    usingFallbackRates: Boolean,
    updatedAt: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = AeroscannerColors.SurfaceSoft),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = if (usingFallbackRates) Icons.Default.CurrencyExchange else Icons.Default.Public,
                contentDescription = null,
                tint = if (usingFallbackRates) AeroscannerColors.CostMid else AeroscannerColors.CostLow,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (usingFallbackRates) "Exchange rates: offline fallback" else "ExchangeRate-API live rates",
                    style = AeroscannerTypography.labelMedium,
                    color = AeroscannerColors.Ink,
                )
                Text(
                    text = updatedAt,
                    style = AeroscannerTypography.bodySmall,
                    color = AeroscannerColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AeroscannerColors.Primary,
                    )
                } else {
                    Text("Refresh", color = AeroscannerColors.Primary)
                }
            }
        }
    }
}

@Composable
private fun TripInputCard(
    viewModel: PlannerViewModel,
    onLocate: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = AeroscannerColors.Canvas),
        border = BorderStroke(1.dp, AeroscannerColors.Hairline),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChip(
                    selected = !viewModel.useMultiSegment,
                    onClick = { viewModel.useMultiSegment = false },
                    label = { Text("Direct") },
                    leadingIcon = { Icon(Icons.Default.FlightTakeoff, null, Modifier.size(16.dp)) },
                )
                FilterChip(
                    selected = viewModel.useMultiSegment,
                    onClick = { viewModel.useMultiSegment = true },
                    label = { Text("Multi-city") },
                    leadingIcon = { Icon(Icons.Default.Air, null, Modifier.size(16.dp)) },
                )
            }

            if (viewModel.useMultiSegment) {
                OutlinedTextField(
                    value = viewModel.routeInput,
                    onValueChange = { viewModel.routeInput = it.uppercase() },
                    label = { Text("Route") },
                    placeholder = { Text("PEK-ICN-SEA or PEK-ICN / GMP-HND") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = inputFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            } else {
                OutlinedTextField(
                    value = viewModel.originInput,
                    onValueChange = { viewModel.originInput = it.take(3).uppercase() },
                    label = { Text("Origin airport") },
                    placeholder = { Text("PEK") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = inputFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    trailingIcon = {
                        IconButton(onClick = onLocate) {
                            if (viewModel.isLocating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AeroscannerColors.Primary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Use current location",
                                    tint = AeroscannerColors.Primary,
                                )
                            }
                        }
                    },
                )
                OutlinedTextField(
                    value = viewModel.destinationInput,
                    onValueChange = { viewModel.destinationInput = it.take(3).uppercase() },
                    label = { Text("Destination airport") },
                    placeholder = { Text("ICN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = inputFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = viewModel.priceInput,
                    onValueChange = { viewModel.priceInput = it },
                    label = { Text("Ticket price") },
                    placeholder = { Text("3211") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = inputFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = {
                        Text(
                            TravelDisplay.currencySpec(viewModel.selectedCurrency).symbol,
                            color = AeroscannerColors.Muted,
                        )
                    },
                )
                CurrencyDropdown(
                    selectedCurrency = viewModel.selectedCurrency,
                    onCurrencySelected = { viewModel.selectedCurrency = it },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                UnitChip(
                    label = "km / kg",
                    selected = viewModel.unitSystem == UnitSystem.METRIC,
                    onClick = { viewModel.unitSystem = UnitSystem.METRIC },
                )
                UnitChip(
                    label = "mi / lb",
                    selected = viewModel.unitSystem == UnitSystem.IMPERIAL,
                    onClick = { viewModel.unitSystem = UnitSystem.IMPERIAL },
                )
            }

            HorizontalDivider(color = AeroscannerColors.HairlineSoft)

            Text(
                "Baggage estimator",
                style = AeroscannerTypography.labelMedium,
                color = AeroscannerColors.Ink,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = viewModel.baggageWeightInput,
                    onValueChange = { viewModel.baggageWeightInput = it },
                    label = { Text("Bag weight") },
                    placeholder = { Text(viewModel.unitSystem.weightUnit) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = inputFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = viewModel.baggageAllowanceInput,
                    onValueChange = { viewModel.baggageAllowanceInput = it },
                    label = { Text("Allowance") },
                    placeholder = { Text(TravelDisplay.formatWeight(CostTools.DEFAULT_BAGGAGE_ALLOWANCE_KG, viewModel.unitSystem)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = inputFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Straighten, null, Modifier.size(16.dp)) },
    )
}

@Composable
private fun CurrencyDropdown(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(Radius.sm),
            modifier = Modifier.height(56.dp),
            border = BorderStroke(1.dp, AeroscannerColors.Hairline),
        ) {
            Text(selectedCurrency, style = AeroscannerTypography.labelMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TravelDisplay.supportedCurrencies.forEach { spec ->
                DropdownMenuItem(
                    text = { Text("${spec.symbol} ${spec.code}") },
                    onClick = {
                        onCurrencySelected(spec.code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    costResult: CostTools.CostResult,
    currencyCode: String,
    rates: Map<String, Double>,
    unitSystem: UnitSystem,
) {
    Card(
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = AeroscannerColors.Canvas),
        border = BorderStroke(1.dp, AeroscannerColors.Hairline),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = costResult.route,
                        style = AeroscannerTypography.titleMedium,
                        color = AeroscannerColors.Ink,
                    )
                    Text(
                        text = "${costResult.segments.size} flight segment(s)",
                        style = AeroscannerTypography.bodySmall,
                        color = AeroscannerColors.Muted,
                    )
                }
                PriceBadge(costResult.priceLevel)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricBlock(
                    label = "Distance",
                    value = TravelDisplay.formatDistance(costResult.totalDistanceKm, unitSystem),
                )
                MetricBlock(
                    label = "Trip cost",
                    value = TravelDisplay.formatMoneyFromCny(costResult.totalTripCostRmb, currencyCode, rates),
                    alignEnd = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricBlock(
                    label = "Cost/${unitSystem.distanceUnit}",
                    value = TravelDisplay.formatCostRate(costResult.costPerKmRmb, currencyCode, rates, unitSystem),
                    valueColor = CostTools.priceLevelColor(costResult.priceLevel),
                )
                MetricBlock(
                    label = "CO2e estimate",
                    value = "${"%.0f".format(costResult.carbonKg)} kg",
                    alignEnd = true,
                )
            }

            HorizontalDivider(color = AeroscannerColors.HairlineSoft)

            InfoRow(
                label = "Ticket",
                value = TravelDisplay.formatMoneyFromCny(costResult.ticketPriceRmb, currencyCode, rates),
            )
            if (costResult.baggageWeightKg != null) {
                InfoRow(
                    label = "Baggage",
                    value = "${TravelDisplay.formatWeight(costResult.baggageWeightKg, unitSystem)} / " +
                        "allowance ${TravelDisplay.formatWeight(costResult.baggageAllowanceKg, unitSystem)}",
                )
                InfoRow(
                    label = "Overweight fee",
                    value = TravelDisplay.formatMoneyFromCny(costResult.baggageOverageFeeRmb, currencyCode, rates),
                )
            }

            if (costResult.segments.isNotEmpty()) {
                HorizontalDivider(color = AeroscannerColors.HairlineSoft)
                Text("Segments", style = AeroscannerTypography.labelMedium, color = AeroscannerColors.Ink)
                costResult.segments.forEach { seg ->
                    InfoRow(
                        label = "${seg.originCode} to ${seg.destinationCode}",
                        value = TravelDisplay.formatDistance(seg.distanceKm, unitSystem),
                        subLabel = "${seg.originCity} to ${seg.destinationCity}",
                    )
                }
            }

            if (costResult.surfaceTransfers.isNotEmpty()) {
                HorizontalDivider(color = AeroscannerColors.HairlineSoft)
                Text("Ground transfers", style = AeroscannerTypography.labelMedium, color = AeroscannerColors.Ink)
                costResult.surfaceTransfers.forEach { transfer ->
                    InfoRow(
                        label = "${transfer.originCode} to ${transfer.destinationCode}",
                        value = TravelDisplay.formatDistance(transfer.distanceKm, unitSystem),
                        subLabel = "${transfer.originCity} to ${transfer.destinationCity}",
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(weather: DestinationWeather, unitSystem: UnitSystem) {
    Card(
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = AeroscannerColors.SurfaceSoft),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = AeroscannerColors.Primary)
                Column {
                    Text(
                        "Destination weather",
                        style = AeroscannerTypography.titleMedium,
                        color = AeroscannerColors.Ink,
                    )
                    Text(
                        "${weather.airportCity} (${weather.airportCode}) via Open-Meteo",
                        style = AeroscannerTypography.bodySmall,
                        color = AeroscannerColors.Muted,
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricBlock(
                    label = weather.condition,
                    value = weather.temperatureC?.let { TravelDisplay.formatTemperature(it, unitSystem) } ?: "--",
                )
                MetricBlock(
                    label = "Wind",
                    value = weather.windSpeedKmh?.let { TravelDisplay.formatSpeed(it, unitSystem) } ?: "--",
                    alignEnd = true,
                )
            }

            val highLow = if (weather.maxTempC != null && weather.minTempC != null) {
                "${TravelDisplay.formatTemperature(weather.minTempC, unitSystem)} to " +
                    TravelDisplay.formatTemperature(weather.maxTempC, unitSystem)
            } else {
                "--"
            }
            InfoRow(label = "Today range", value = highLow)
            InfoRow(label = "Precipitation", value = weather.precipitationProbability?.let { "$it%" } ?: "--")
        }
    }
}

@Composable
private fun PriceBadge(level: CostTools.PriceLevel) {
    Row(
        modifier = Modifier
            .background(
                color = CostTools.priceLevelColor(level).copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.full),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(CostTools.priceLevelColor(level), RoundedCornerShape(Radius.full)),
        )
        Text(
            text = CostTools.priceLevelLabel(level),
            style = AeroscannerTypography.labelSmall,
            color = CostTools.priceLevelColor(level),
        )
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    valueColor: Color = AeroscannerColors.Ink,
    alignEnd: Boolean = false,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = AeroscannerTypography.labelMedium, color = AeroscannerColors.Muted)
        Text(value, style = AeroscannerTypography.displayMedium, color = valueColor)
    }
}

@Composable
private fun InfoRow(label: String, value: String, subLabel: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = AeroscannerTypography.bodyMedium, color = AeroscannerColors.Ink)
            if (subLabel != null) {
                Text(subLabel, style = AeroscannerTypography.bodySmall, color = AeroscannerColors.Muted)
            }
        }
        Text(value, style = AeroscannerTypography.bodyMedium, color = AeroscannerColors.Muted)
    }
}

@Composable
private fun InlineProgress(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = AeroscannerColors.Primary,
        )
        Text(text, style = AeroscannerTypography.bodyMedium, color = AeroscannerColors.Muted)
    }
}

@Composable
private fun FeedbackCard(
    text: String,
    isError: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val color = if (isError) AeroscannerColors.ErrorText else AeroscannerColors.CostLow
    Card(
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = AeroscannerTypography.bodyMedium, color = if (isError) color else AeroscannerColors.Body, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = AeroscannerColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun inputFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AeroscannerColors.Ink,
    unfocusedBorderColor = AeroscannerColors.Hairline,
    focusedLabelColor = AeroscannerColors.Ink,
    unfocusedLabelColor = AeroscannerColors.Muted,
    cursorColor = AeroscannerColors.Ink,
    focusedContainerColor = AeroscannerColors.Canvas,
    unfocusedContainerColor = AeroscannerColors.Canvas,
)
