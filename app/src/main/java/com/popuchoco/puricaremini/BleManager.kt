package com.popuchoco.puricaremini

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.ArrayDeque

data class DeviceCandidate(val name: String, val address: String, val rssi: Int, val likely: Boolean)

data class DeviceDetails(
    val manufacturer: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val hardware: String? = null,
    val software: String? = null,
)

data class BleUiState(
    val phase: String = "尚未連線",
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String? = null,
    val candidates: List<DeviceCandidate> = emptyList(),
    val snapshot: AirSnapshot = AirSnapshot(),
    val deviceDetails: DeviceDetails = DeviceDetails(),
    val logs: List<String> = emptyList(),
)

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    var state by mutableStateOf(BleUiState())
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private var gatt: BluetoothGatt? = null
    private val queue = ArrayDeque<() -> Unit>()
    private var operationRunning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull() ?: "未命名 BLE 裝置"
            val likely = name.contains("Puri", true) || name.contains("LG", true) || name.contains("Mini", true)
            val candidate = DeviceCandidate(name, device.address, result.rssi, likely)
            val updated = (state.candidates.filterNot { it.address == candidate.address } + candidate)
                .sortedWith(compareByDescending<DeviceCandidate> { it.likely }.thenByDescending { it.rssi })
                .take(30)
            state = state.copy(candidates = updated)
        }

        override fun onScanFailed(errorCode: Int) {
            state = state.copy(scanning = false, phase = "掃描失敗（$errorCode）")
            log("SCAN failed code=$errorCode")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    state = state.copy(phase = "正在讀取服務…", connected = false, deviceName = runCatching { g.device.name }.getOrNull())
                    log("GATT connected; discovering services")
                    handler.postDelayed({ g.discoverServices() }, 400)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    operationRunning = false; queue.clear()
                    state = state.copy(phase = "連線已中斷", connected = false)
                    log("GATT disconnected status=$status")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            handler.post {
                val uart = g.getService(PuriCareProtocol.UART_SERVICE)
                if (status != BluetoothGatt.GATT_SUCCESS || uart == null) {
                    val services = g.services.joinToString { it.uuid.toString() }
                    state = state.copy(phase = "找不到 PuriCare 通訊服務", connected = false)
                    log("Unsupported GATT. Services: $services")
                    return@post
                }
                state = state.copy(phase = "已連線", connected = true)
                log("PuriCare UART service ready")
                enableNotifications(g, uart.getCharacteristic(PuriCareProtocol.UART_RX))
                g.getService(PuriCareProtocol.BATTERY_SERVICE)?.getCharacteristic(PuriCareProtocol.BATTERY_LEVEL)?.let { battery ->
                    enqueue { g.readCharacteristic(battery) }
                }
                val deviceInfo = g.getService(PuriCareProtocol.DEVICE_INFORMATION_SERVICE)
                if (deviceInfo == null) log("Device information service not provided")
                listOf(
                    PuriCareProtocol.MANUFACTURER_NAME,
                    PuriCareProtocol.MODEL_NUMBER,
                    PuriCareProtocol.FIRMWARE_REVISION,
                    PuriCareProtocol.HARDWARE_REVISION,
                    PuriCareProtocol.SOFTWARE_REVISION,
                ).forEach { uuid ->
                    val characteristic = deviceInfo?.getCharacteristic(uuid)
                    if (deviceInfo != null && characteristic == null) {
                        log("Device info ${deviceInfoLabel(uuid)} not provided")
                    } else if (characteristic != null) {
                        enqueue { g.readCharacteristic(characteristic) }
                    }
                }
                handler.postDelayed({ refresh() }, 900)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleValue(characteristic.uuid, value)
        }

        @Deprecated("Legacy callback for Android 12")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleValue(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(characteristic.uuid, value)
            else handler.post { log("Read ${deviceInfoLabel(characteristic.uuid)} failed status=$status") }
            completeOperation()
        }

        @Deprecated("Legacy callback for Android 12")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(characteristic.uuid, characteristic.value ?: byteArrayOf())
            else handler.post { log("Read ${deviceInfoLabel(characteristic.uuid)} failed status=$status") }
            completeOperation()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.post { log("TX complete status=$status"); completeOperation() }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post { log("Notifications ${if (status == 0) "enabled" else "failed ($status)"}"); completeOperation() }
        }
    }

    fun startScan() {
        if (!adapter.isEnabled) { state = state.copy(phase = "請先開啟藍牙"); return }
        stopScan()
        state = state.copy(scanning = true, phase = "正在尋找附近裝置…", candidates = emptyList())
        adapter.bluetoothLeScanner.startScan(scanCallback)
        handler.postDelayed({ stopScan() }, 12_000)
        log("BLE scan started")
    }

    fun stopScan() {
        runCatching { adapter.bluetoothLeScanner?.stopScan(scanCallback) }
        if (state.scanning) state = state.copy(scanning = false, phase = if (state.connected) "已連線" else "選擇你的 PuriCare Mini")
    }

    fun connect(candidate: DeviceCandidate) {
        stopScan(); disconnect()
        state = state.copy(phase = "正在連線 ${candidate.name}…", deviceName = candidate.name)
        log("Connecting ${candidate.name} (${candidate.address.take(8)}•••)")
        gatt = adapter.getRemoteDevice(candidate.address).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        operationRunning = false; queue.clear()
        gatt?.disconnect(); gatt?.close(); gatt = null
        state = state.copy(connected = false)
    }

    fun refresh() = write(PuriCareProtocol.getAll(), "GET ALL")
    fun setPower(on: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_POWER, on), "POWER ${if (on) "ON" else "OFF"}")
    fun setAuto(on: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_AUTO, on), "AUTO ${if (on) "ON" else "OFF"}")
    fun setLight(on: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_LIGHT, on), "LIGHT ${if (on) "ON" else "OFF"}")
    fun setFan(level: Int) = writeAndRefresh(PuriCareProtocol.setByte(PuriCareProtocol.ID_FAN, level), "FAN $level")
    fun setFanAuto() = writeAndRefresh(PuriCareProtocol.setByte(PuriCareProtocol.ID_FAN, 8), "FAN AUTO")
    fun setTurbo(on: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_TURBO, on), "TURBO ${if (on) "ON" else "OFF"}")

    private fun writeAndRefresh(bytes: ByteArray, label: String) {
        write(bytes, label)
        handler.postDelayed({ if (state.connected) refresh() }, 750)
    }

    private fun write(bytes: ByteArray, label: String) {
        val g = gatt ?: return
        val tx = g.getService(PuriCareProtocol.UART_SERVICE)?.getCharacteristic(PuriCareProtocol.UART_TX) ?: return
        enqueue {
            log("TX $label: ${PuriCareProtocol.hex(bytes)}")
            val result = g.writeCharacteristic(tx, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            if (result != BluetoothStatusCodes.SUCCESS) completeOperation()
        }
    }

    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null || !g.setCharacteristicNotification(characteristic, true)) return
        val descriptor = characteristic.getDescriptor(PuriCareProtocol.CCCD) ?: return
        enqueue {
            val result = g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (result != BluetoothStatusCodes.SUCCESS) completeOperation()
        }
    }

    private fun enqueue(action: () -> Unit) {
        queue += action
        if (!operationRunning) runNext()
    }

    private fun runNext() {
        if (queue.isEmpty()) { operationRunning = false; return }
        val next = queue.removeFirst()
        operationRunning = true; next()
    }

    private fun completeOperation() = handler.post { operationRunning = false; runNext() }

    private fun handleValue(uuid: java.util.UUID, bytes: ByteArray) = handler.post {
        val text = bytes.toString(Charsets.UTF_8).trim('\u0000', ' ')
        when (uuid) {
            PuriCareProtocol.MANUFACTURER_NAME -> updateDeviceInfo("Manufacturer", text) { copy(manufacturer = it) }
            PuriCareProtocol.MODEL_NUMBER -> updateDeviceInfo("Model", text) { copy(model = it) }
            PuriCareProtocol.FIRMWARE_REVISION -> updateDeviceInfo("Firmware", text) { copy(firmware = it) }
            PuriCareProtocol.HARDWARE_REVISION -> updateDeviceInfo("Hardware", text) { copy(hardware = it) }
            PuriCareProtocol.SOFTWARE_REVISION -> updateDeviceInfo("Software", text) { copy(software = it) }
            PuriCareProtocol.BATTERY_LEVEL -> if (bytes.isNotEmpty()) {
            state = state.copy(snapshot = state.snapshot.copy(battery = bytes[0].toInt() and 0xff, updatedAt = System.currentTimeMillis()))
            log("Battery ${bytes[0].toInt() and 0xff}%")
            }
            else -> {
            val decoded = PuriCareProtocol.decodeReport(bytes)
            state = state.copy(snapshot = state.snapshot.with(decoded))
            log("RX ${PuriCareProtocol.hex(bytes)}${if (decoded.isEmpty()) "" else " → " + decoded.joinToString { "${it.id}=${it.value}" }}")
            }
        }
    }

    private fun updateDeviceInfo(label: String, text: String, update: DeviceDetails.(String?) -> DeviceDetails) {
        val value = text.ifBlank { null }
        state = state.copy(deviceDetails = state.deviceDetails.update(value))
        log("Device info $label=${value ?: "empty"}")
    }

    private fun deviceInfoLabel(uuid: java.util.UUID): String = when (uuid) {
        PuriCareProtocol.MANUFACTURER_NAME -> "Manufacturer"
        PuriCareProtocol.MODEL_NUMBER -> "Model"
        PuriCareProtocol.FIRMWARE_REVISION -> "Firmware"
        PuriCareProtocol.HARDWARE_REVISION -> "Hardware"
        PuriCareProtocol.SOFTWARE_REVISION -> "Software"
        PuriCareProtocol.BATTERY_LEVEL -> "Battery"
        else -> uuid.toString()
    }

    private fun log(message: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.TAIWAN).format(java.util.Date())
        state = state.copy(logs = (listOf("$stamp  $message") + state.logs).take(500))
    }
}
