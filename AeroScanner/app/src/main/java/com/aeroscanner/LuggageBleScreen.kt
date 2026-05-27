package com.aeroscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class LuggageViewModel : ViewModel() {
    private val _defaultState = mutableStateOf<SmartScaleScanner.BleState>(SmartScaleScanner.BleState.Idle)
    val bleState: androidx.compose.runtime.State<SmartScaleScanner.BleState>
        get() = scanner?.uiState ?: _defaultState

    private var scanner: SmartScaleScanner? = null

    fun init(context: android.content.Context) {
        if (scanner == null) {
            scanner = SmartScaleScanner(context)
        }
    }

    fun startScan() {
        scanner?.startScanning()
    }

    fun disconnect() {
        scanner?.disconnect()
    }

    fun retry() {
        scanner?.startScanning()
    }

    override fun onCleared() {
        super.onCleared()
        scanner?.release()
    }
}

@Composable
fun LuggageBleScreen(viewModel: LuggageViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.bleState
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasBlePermissions(context)) {
            viewModel.startScan()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.base)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SmartScale",
                style = AeroscannerTypography.displayMedium,
                color = AeroscannerColors.Ink,
            )

            // Connection status dot
            val dotColor = when (state) {
                is SmartScaleScanner.BleState.Connected -> AeroscannerColors.CostLow
                is SmartScaleScanner.BleState.Connecting -> AeroscannerColors.CostMid
                is SmartScaleScanner.BleState.Scanning -> AeroscannerColors.MutedSoft
                is SmartScaleScanner.BleState.Error -> AeroscannerColors.ErrorText
                is SmartScaleScanner.BleState.Idle -> AeroscannerColors.MutedSoft
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        Text(
            text = "Bluetooth luggage scale",
            style = AeroscannerTypography.bodyMedium,
            color = AeroscannerColors.Muted,
        )

        if (!hasBlePermissions(context)) {
            Spacer(Modifier.height(Spacing.base))
            Card(
                shape = RoundedCornerShape(Radius.sm),
                colors = CardDefaults.cardColors(containerColor = AeroscannerColors.SurfaceSoft),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AeroscannerColors.Primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Bluetooth permission required",
                            style = AeroscannerTypography.labelMedium,
                            color = AeroscannerColors.Ink,
                        )
                        Text(
                            "Grant permission to scan for a BLE weight scale.",
                            style = AeroscannerTypography.bodySmall,
                            color = AeroscannerColors.Muted,
                        )
                    }
                    TextButton(onClick = { permissionLauncher.launch(blePermissions()) }) {
                        Text("Grant", color = AeroscannerColors.Primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        // Weight display card
        Card(
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = AeroscannerColors.Canvas),
            border = CardDefaults.outlinedCardBorder().copy(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.SolidColor(AeroscannerColors.Hairline),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                // Scale visual — Rausch orb (Airbnb search orb style)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                is SmartScaleScanner.BleState.Connected -> AeroscannerColors.Primary
                                else -> AeroscannerColors.SurfaceStrong
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Scale,
                        contentDescription = null,
                        tint = when (state) {
                            is SmartScaleScanner.BleState.Connected ->
                                androidx.compose.ui.graphics.Color.White
                            else -> AeroscannerColors.MutedSoft
                        },
                        modifier = Modifier.size(40.dp),
                    )
                }

                // Weight value
                when (val s = state) {
                    is SmartScaleScanner.BleState.Connected -> {
                        Text(
                            text = String.format("%.1f", s.weightKg),
                            style = AeroscannerTypography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = AeroscannerColors.Ink,
                        )
                        Text(
                            text = "kg",
                            style = AeroscannerTypography.bodyLarge,
                            color = AeroscannerColors.Muted,
                        )
                        Text(
                            text = TravelDisplay.formatWeight(s.weightKg.toDouble(), UnitSystem.IMPERIAL),
                            style = AeroscannerTypography.bodyMedium,
                            color = AeroscannerColors.Muted,
                        )

                        Spacer(Modifier.height(Spacing.sm))

                        Text(
                            text = s.deviceName,
                            style = AeroscannerTypography.bodyMedium,
                            color = AeroscannerColors.MutedSoft,
                        )
                    }
                    is SmartScaleScanner.BleState.Scanning -> {
                        Text(
                            text = "---",
                            style = AeroscannerTypography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = AeroscannerColors.MutedSoft,
                        )
                        Text(
                            text = "kg",
                            style = AeroscannerTypography.bodyLarge,
                            color = AeroscannerColors.MutedSoft,
                        )

                        Spacer(Modifier.height(Spacing.sm))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AeroscannerColors.Primary,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                "Searching for SmartScale...",
                                style = AeroscannerTypography.bodyMedium,
                                color = AeroscannerColors.Muted,
                            )
                        }
                    }
                    is SmartScaleScanner.BleState.Connecting -> {
                        Text(
                            text = "---",
                            style = AeroscannerTypography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = AeroscannerColors.MutedSoft,
                        )
                        Text(
                            text = "kg",
                            style = AeroscannerTypography.bodyLarge,
                            color = AeroscannerColors.MutedSoft,
                        )

                        Spacer(Modifier.height(Spacing.sm))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AeroscannerColors.CostMid,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                "Connecting...",
                                style = AeroscannerTypography.bodyMedium,
                                color = AeroscannerColors.Muted,
                            )
                        }
                    }
                    is SmartScaleScanner.BleState.Error -> {
                        Text(
                            text = "---",
                            style = AeroscannerTypography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = AeroscannerColors.MutedSoft,
                        )
                        Text(
                            text = "kg",
                            style = AeroscannerTypography.bodyLarge,
                            color = AeroscannerColors.MutedSoft,
                        )
                    }
                    is SmartScaleScanner.BleState.Idle -> {
                        Text(
                            text = "---",
                            style = AeroscannerTypography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = AeroscannerColors.MutedSoft,
                        )
                        Text(
                            text = "kg",
                            style = AeroscannerTypography.bodyLarge,
                            color = AeroscannerColors.MutedSoft,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // Action buttons
        when (val s = state) {
            is SmartScaleScanner.BleState.Connected -> {
                Button(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AeroscannerColors.SurfaceStrong,
                        contentColor = AeroscannerColors.Ink,
                    ),
                    enabled = false,
                ) {
                    Text("Connected", style = AeroscannerTypography.labelLarge)
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    Text(
                        "Disconnect",
                        style = AeroscannerTypography.labelMedium,
                        color = AeroscannerColors.ErrorText,
                    )
                }
            }
            is SmartScaleScanner.BleState.Scanning -> {
                Button(
                    onClick = {
                        if (hasBlePermissions(context)) {
                            viewModel.startScan()
                        } else {
                            permissionLauncher.launch(blePermissions())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = AeroscannerColors.Primary),
                    enabled = false,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Scanning...",
                        style = AeroscannerTypography.labelLarge,
                    )
                }
            }
            is SmartScaleScanner.BleState.Connecting -> {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = AeroscannerColors.CostMid),
                    enabled = false,
                ) {
                    Text(
                        "Connecting...",
                        style = AeroscannerTypography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            is SmartScaleScanner.BleState.Error -> {
                // Error card
                Card(
                    shape = RoundedCornerShape(Radius.sm),
                    colors = CardDefaults.cardColors(
                        containerColor = AeroscannerColors.ErrorText.copy(alpha = 0.08f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = AeroscannerColors.ErrorText,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            s.message,
                            style = AeroscannerTypography.bodyMedium,
                            color = AeroscannerColors.ErrorText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Button(
                    onClick = {
                        if (hasBlePermissions(context)) {
                            viewModel.retry()
                        } else {
                            permissionLauncher.launch(blePermissions())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = AeroscannerColors.Primary),
                ) {
                    Text(
                        "Retry",
                        style = AeroscannerTypography.labelLarge,
                    )
                }
            }
            is SmartScaleScanner.BleState.Idle -> {
                Button(
                    onClick = {
                        if (hasBlePermissions(context)) {
                            viewModel.startScan()
                        } else {
                            permissionLauncher.launch(blePermissions())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = AeroscannerColors.Primary),
                ) {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Scan for SmartScale",
                        style = AeroscannerTypography.labelLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // Tips section
        Card(
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = AeroscannerColors.SurfaceSoft),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "How to connect",
                    style = AeroscannerTypography.titleMedium,
                    color = AeroscannerColors.Ink,
                )
                Text(
                    "1. Ensure your SmartScale is powered on\n" +
                    "2. Put the scale in pairing mode\n" +
                    "3. Tap 'Scan for SmartScale' above\n" +
                    "4. Wait for the weight to appear",
                    style = AeroscannerTypography.bodySmall,
                    color = AeroscannerColors.Muted,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

private fun blePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}

private fun hasBlePermissions(context: android.content.Context): Boolean =
    blePermissions().all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
