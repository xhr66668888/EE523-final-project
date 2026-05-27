package com.aeroscanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import java.util.UUID

class SmartScaleScanner(private val context: Context) {

    sealed class BleState {
        object Idle : BleState()
        object Scanning : BleState()
        object Connecting : BleState()
        data class Connected(val deviceName: String, val weightKg: Float) : BleState()
        data class Error(val message: String) : BleState()
    }

    companion object {
        val WEIGHT_SCALE_SERVICE_UUID = UUID.fromString("0000181D-0000-1000-8000-00805F9B34FB")
        val WEIGHT_MEASUREMENT_CHAR_UUID = UUID.fromString("00002A9D-0000-1000-8000-00805F9B34FB")
        val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    private val _state = mutableStateOf<BleState>(BleState.Idle)
    val uiState: State<BleState> = _state

    private var bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasBluetoothPermission()) {
                _state.value = BleState.Error("Bluetooth permission required.")
                return
            }
            val device = result.device
            val name = device.name ?: "SmartScale"
            if (name.contains("SmartScale", ignoreCase = true) ||
                name.contains("Scale", ignoreCase = true)
            ) {
                stopScanning()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = BleState.Error("Bluetooth scan failed (code $errorCode).")
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasBluetoothPermission()) {
                _state.value = BleState.Error("Bluetooth permission required.")
                closeGatt()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = BleState.Connecting
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.value = BleState.Error("Connection lost. Tap to reconnect.")
                closeGatt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!hasBluetoothPermission()) {
                _state.value = BleState.Error("Bluetooth permission required.")
                closeGatt()
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = BleState.Error("Failed to discover scale services.")
                return
            }

            val service = gatt.getService(WEIGHT_SCALE_SERVICE_UUID)
            if (service == null) {
                _state.value = BleState.Error("Connected device is not a compatible SmartScale.")
                return
            }

            val characteristic = service.getCharacteristic(WEIGHT_MEASUREMENT_CHAR_UUID)
            if (characteristic == null) {
                _state.value = BleState.Error("Weight measurement not available on this device.")
                return
            }

            val enabled = gatt.setCharacteristicNotification(characteristic, true)
            if (!enabled) {
                _state.value = BleState.Error("Could not enable weight notifications.")
                return
            }

            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (!hasBluetoothPermission()) {
                _state.value = BleState.Error("Bluetooth permission required.")
                return
            }
            if (characteristic.uuid == WEIGHT_MEASUREMENT_CHAR_UUID) {
                val weightKg = parseWeightValue(value)
                val deviceName = gatt.device.name ?: "SmartScale"
                _state.value = BleState.Connected(deviceName, weightKg)
            }
        }
    }

    fun startScanning() {
        if (!hasBluetoothPermission()) {
            _state.value = BleState.Error("Bluetooth permission required.")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = BleState.Error("Bluetooth is off. Enable Bluetooth to find SmartScale.")
            return
        }

        val scanner = bluetoothLeScanner
        if (scanner == null) {
            _state.value = BleState.Error("Bluetooth LE scanning not available.")
            return
        }

        _state.value = BleState.Scanning

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(WEIGHT_SCALE_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            _state.value = BleState.Error("Bluetooth permission required.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        if (_state.value is BleState.Scanning) {
            _state.value = BleState.Idle
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        _state.value = BleState.Connecting
        closeGatt()
        try {
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            _state.value = BleState.Error("Bluetooth permission required for connection.")
        }
    }

    fun disconnect() {
        closeGatt()
        _state.value = BleState.Idle
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
    }

    private fun parseWeightValue(value: ByteArray): Float {
        if (value.size < 3) return 0f

        // BLE Weight Measurement: byte 0 = flags, bytes 1-4 = weight (depending on flags)
        val flags = value[0].toInt() and 0xFF
        val isImperial = (flags and 0x01) != 0   // bit 0: 0=SI(kg), 1=imperial(lb)

        // Weight is in bytes 1-2 (uint16 LE) or 1-4 (float)
        val weightRaw = if (value.size >= 5) {
            // IEEE-11073 32-bit float: first 2 bytes = int16, next 2 = exponent
            val mantissa = ((value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)).toShort().toInt()
            val exponent = ((value[3].toInt() and 0xFF) or ((value[4].toInt() and 0xFF) shl 8)).toShort().toInt()
            mantissa.toFloat() * Math.pow(10.0, exponent.toDouble()).toFloat()
        } else {
            // Simplified: uint16 LE in bytes 1-2, resolution 0.005 kg or 0.01 kg
            val raw = ((value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8))
            raw * 0.01f  // common Chinese scale resolution
        }

        return if (isImperial) weightRaw * 0.453592f else weightRaw
    }

    fun release() {
        stopScanning()
        closeGatt()
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) ==
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
