package com.popuchoco.puricaremini

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.ArrayDeque

data class DeviceCandidate(val name: String, val address: String, val rssi: Int, val likely: Boolean)

enum class BackgroundConnectionStatus { CONNECTED, RETRYING, PAUSED }

data class DeviceDetails(
    val version: String? = null,
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

    var backgroundStatusListener: ((BackgroundConnectionStatus, String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val filterReminderNotifier = FilterReminderNotifier(context)
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var lastCandidate: DeviceCandidate? = null
    private var backgroundConnectionEnabled = false
    private var manualDisconnect = false
    private var reconnectAttempt = 0
    private var protocolBatterySeen = false
    private val reconnectRunnable = Runnable {
        if (!backgroundConnectionEnabled || manualDisconnect || gatt != null) return@Runnable
        val candidate = lastCandidate ?: return@Runnable
        if (BackgroundReconnectPolicy.delayForAttempt(reconnectAttempt) == null) {
            state = state.copy(phase = "背景重連已暫停，請開啟 App 後重試")
            log("Background reconnect paused")
            backgroundStatusListener?.invoke(BackgroundConnectionStatus.PAUSED, state.phase)
            return@Runnable
        }
        reconnectAttempt++
        startConnection(candidate)
    }
    private val refreshAfterControl = Runnable { if (state.connected) refresh() }
    private val queue = ArrayDeque<() -> Unit>()
    private var operationRunning = false
    private var operationToken = 0
    private var pendingWriteFailure: (() -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull() ?: "未命名 BLE 裝置"
                val likely = name.contains("Puri", true) || name.contains("LG", true)
                val candidate = DeviceCandidate(name, device.address, result.rssi, likely)
                val updated = (state.candidates.filterNot { it.address == candidate.address } + candidate)
                    .sortedWith(compareByDescending<DeviceCandidate> { it.likely }.thenByDescending { it.rssi })
                    .take(30)
                state = state.copy(candidates = updated)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post {
                state = state.copy(scanning = false, phase = "掃描失敗（$errorCode）")
                log("SCAN failed code=$errorCode")
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (g !== gatt) {
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) g.close()
                    return@post
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    state = state.copy(phase = "正在讀取服務…", connected = false, deviceName = runCatching { g.device.name }.getOrNull())
                    log("GATT connected; discovering services")
                    handler.postDelayed({ g.discoverServices() }, 400)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    operationRunning = false; queue.clear()
                    operationToken++
                    g.close()
                    gatt = null
                    state = state.copy(phase = if (status == BluetoothGatt.GATT_SUCCESS) "連線已中斷" else "連線錯誤（$status）", connected = false)
                    log("GATT disconnected status=$status")
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            handler.post {
                if (!isCurrentConnection(gatt, g)) { log("Ignored callback from previous connection"); return@post }
                val uart = g.getService(PuriCareProtocol.UART_SERVICE)
                if (status != BluetoothGatt.GATT_SUCCESS || uart == null) {
                    val services = g.services.joinToString { it.uuid.toString() }
                    state = state.copy(phase = "找不到 PuriCare 通訊服務", connected = false)
                    log("Unsupported GATT. Services: $services")
                    return@post
                }
                if (!enableNotifications(g, uart.getCharacteristic(PuriCareProtocol.UART_RX))) return@post
                state = state.copy(phase = "已連線", connected = true)
                reconnectAttempt = 0
                backgroundStatusListener?.invoke(BackgroundConnectionStatus.CONNECTED, "已連線至 ${state.deviceName ?: "PuriCare Mini"}")
                log("PuriCare UART service ready")
                g.getService(PuriCareProtocol.BATTERY_SERVICE)?.getCharacteristic(PuriCareProtocol.BATTERY_LEVEL)?.let { battery ->
                    enqueue { g.readCharacteristic(battery) }
                }
                val deviceInfo = g.getService(PuriCareProtocol.DEVICE_INFORMATION_SERVICE)
                if (deviceInfo == null) log("Device information service not provided")
                val version = deviceInfo?.getCharacteristic(PuriCareProtocol.SOFTWARE_REVISION)
                if (deviceInfo != null && version == null) {
                    log("Device version not provided")
                } else if (version != null) {
                    enqueue { g.readCharacteristic(version) }
                }
                handler.postDelayed({ refresh() }, 900)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (!isCurrentConnection(gatt, g)) { handler.post { log("Ignored callback from previous connection") }; return }
            handleValue(characteristic.uuid, value)
        }

        @Deprecated("Legacy callback for Android 12")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (!isCurrentConnection(gatt, g)) { handler.post { log("Ignored callback from previous connection") }; return }
            handleValue(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (!isCurrentConnection(gatt, g)) { handler.post { log("Ignored callback from previous connection") }; return }
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(characteristic.uuid, value)
            else handler.post { log("Read ${deviceInfoLabel(characteristic.uuid)} failed status=$status") }
            completeOperation(g)
        }

        @Deprecated("Legacy callback for Android 12")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!isCurrentConnection(gatt, g)) { handler.post { log("Ignored callback from previous connection") }; return }
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(characteristic.uuid, characteristic.value ?: byteArrayOf())
            else handler.post { log("Read ${deviceInfoLabel(characteristic.uuid)} failed status=$status") }
            completeOperation(g)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.post {
                if (!isCurrentConnection(gatt, g)) { log("Ignored callback from previous connection"); return@post }
                log("TX complete status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) pendingWriteFailure?.invoke()
                pendingWriteFailure = null
                completeOperation(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post {
                if (!isCurrentConnection(gatt, g)) { log("Ignored callback from previous connection"); return@post }
                log("Notifications ${if (status == 0) "enabled" else "failed ($status)"}")
                if (status != BluetoothGatt.GATT_SUCCESS) state = state.copy(phase = "無法訂閱裝置通知", connected = false)
                completeOperation(g)
            }
        }
    }

    fun startScan() {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) { state = state.copy(phase = "此手機不支援 Bluetooth"); return }
        if (!bluetoothAdapter.isEnabled) { state = state.copy(phase = "請先開啟藍牙"); return }
        stopScan()
        state = state.copy(scanning = true, phase = "正在尋找附近裝置…", candidates = emptyList())
        bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
            ?: run { state = state.copy(scanning = false, phase = "無法啟動 Bluetooth 掃描"); return }
        handler.postDelayed({ stopScan() }, 12_000)
        log("BLE scan started")
    }

    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (state.scanning) state = state.copy(scanning = false, phase = if (state.connected) "已連線" else "選擇你的 PuriCare Mini")
    }

    fun connect(candidate: DeviceCandidate) {
        reconnectAttempt = 0
        handler.removeCallbacks(reconnectRunnable)
        stopScan()
        if (gatt != null) {
            disconnect()
            handler.postDelayed({ startConnection(candidate) }, 500)
        } else {
            startConnection(candidate)
        }
    }

    private fun startConnection(candidate: DeviceCandidate) {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            state = state.copy(phase = "此手機不支援 Bluetooth", connected = false)
            return
        }
        manualDisconnect = false
        protocolBatterySeen = false
        lastCandidate = candidate
        context.getSharedPreferences("ble_device", Context.MODE_PRIVATE).edit()
            .putString("address", candidate.address)
            .putString("name", candidate.name)
            .apply()
        state = state.copy(
            phase = "正在連線 ${candidate.name}…",
            connected = false,
            deviceName = candidate.name,
            snapshot = AirSnapshot(),
            deviceDetails = DeviceDetails(),
        )
        log("Connecting ${candidate.name} (${candidate.address.take(8)}•••)")
        gatt = runCatching {
            bluetoothAdapter.getRemoteDevice(candidate.address).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            state = state.copy(phase = "無法建立連線", connected = false)
            log("Connection start failed: ${it.javaClass.simpleName}")
            null
        }
        if (gatt == null) scheduleReconnect()
    }

    fun disconnect() {
        manualDisconnect = true
        handler.removeCallbacks(reconnectRunnable)
        operationRunning = false; operationToken++; queue.clear()
        pendingWriteFailure = null
        gatt?.disconnect()
        state = state.copy(connected = false)
    }

    fun refresh() = write(PuriCareProtocol.getAll(), "GET ALL")
    fun evaluateFilterReminder() = filterReminderNotifier.evaluate(state.snapshot)
    fun setPower(on: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_POWER, on), "POWER ${if (on) "ON" else "OFF"}")
    fun setAuto(on: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_AUTO, on), "AUTO ${if (on) "ON" else "OFF"}")
    fun setSensorMonitoring(alwaysOn: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_MONITORING, alwaysOn), "SENSOR ${if (alwaysOn) "ALWAYS" else "NORMAL"}")
    fun setLightLevel(level: Int) {
        val safeLevel = level.coerceIn(0, 4)
        val previousLevel = state.snapshot.lightLevel
        state = state.copy(snapshot = state.snapshot.withLocalLightLevel(safeLevel))
        writeAndRefresh(
            PuriCareProtocol.setByte(PuriCareProtocol.ID_LIGHT, safeLevel),
            "LIGHT LEVEL $safeLevel",
        ) {
            val rollbackLevel = lightLevelAfterFailedWrite(
                currentLevel = state.snapshot.lightLevel,
                attemptedLevel = safeLevel,
                previousLevel = previousLevel,
            )
            if (rollbackLevel != state.snapshot.lightLevel) {
                state = state.copy(snapshot = state.snapshot.copy(lightLevel = rollbackLevel, updatedAt = System.currentTimeMillis()))
                log("Light level restored after TX failure")
            } else {
                log("Kept newer light level after earlier TX failure")
            }
        }
    }
    fun setFan(level: Int) = writeAndRefresh(PuriCareProtocol.setByte(PuriCareProtocol.ID_FAN, level), "FAN $level")
    fun setFanAuto() = writeAndRefresh(PuriCareProtocol.setByte(PuriCareProtocol.ID_FAN, 8), "FAN AUTO")
    fun setTurbo(on: Boolean) = writeAndRefresh(PuriCareProtocol.setBoolean(PuriCareProtocol.ID_TURBO, on), "TURBO ${if (on) "ON" else "OFF"}")

    fun setBackgroundConnectionEnabled(enabled: Boolean) {
        backgroundConnectionEnabled = enabled
        if (enabled) {
            manualDisconnect = false
            reconnectAttempt = 0
        } else {
            handler.removeCallbacks(reconnectRunnable)
        }
        log("Background connection ${if (enabled) "enabled" else "disabled"}")
    }

    fun resumeSavedConnection() {
        if (state.connected || gatt != null) return
        val preferences = context.getSharedPreferences("ble_device", Context.MODE_PRIVATE)
        val address = preferences.getString("address", null) ?: return
        val name = preferences.getString("name", null) ?: "PuriCare Mini"
        connect(DeviceCandidate(name, address, 0, true))
    }

    fun reportPermissionDenied() {
        state = state.copy(scanning = false, phase = "需要附近裝置權限才能掃描與連線")
        log("Nearby devices permission denied")
    }

    private fun writeAndRefresh(bytes: ByteArray, label: String, onFailure: (() -> Unit)? = null) {
        write(bytes, label, onFailure)
        handler.removeCallbacks(refreshAfterControl)
        handler.postDelayed(refreshAfterControl, 750)
    }

    private fun write(bytes: ByteArray, label: String, onFailure: (() -> Unit)? = null) {
        val g = gatt ?: run { onFailure?.invoke(); return }
        val tx = g.getService(PuriCareProtocol.UART_SERVICE)?.getCharacteristic(PuriCareProtocol.UART_TX)
            ?: run { onFailure?.invoke(); return }
        enqueue {
            pendingWriteFailure = onFailure
            log("TX $label: ${PuriCareProtocol.hex(bytes)}")
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(tx, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                tx.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(tx)
            }
            if (!started) {
                log("TX could not start")
                pendingWriteFailure?.invoke()
                pendingWriteFailure = null
                completeOperation(g)
            }
        }
    }

    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic?): Boolean {
        if (characteristic == null) {
            log("Notification characteristic not provided")
            state = state.copy(phase = "找不到裝置通知通道", connected = false)
            return false
        }
        if (!g.setCharacteristicNotification(characteristic, true)) {
            log("Local notification registration failed")
            state = state.copy(phase = "無法啟用裝置通知", connected = false)
            return false
        }
        val descriptor = characteristic.getDescriptor(PuriCareProtocol.CCCD)
        if (descriptor == null) {
            log("Notification descriptor not provided")
            state = state.copy(phase = "找不到通知描述元", connected = false)
            return false
        }
        enqueue {
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
            if (!started) { log("Notification write could not start"); completeOperation(g) }
        }
        return true
    }

    private fun enqueue(action: () -> Unit) {
        queue += action
        if (!operationRunning) runNext()
    }

    private fun runNext() {
        if (queue.isEmpty()) { operationRunning = false; return }
        val next = queue.removeFirst()
        operationRunning = true
        val token = ++operationToken
        next()
        handler.postDelayed({
            if (operationRunning && token == operationToken) {
                log("GATT operation timed out")
                operationRunning = false
                operationToken++
                queue.clear()
                pendingWriteFailure?.invoke()
                pendingWriteFailure = null
                state = state.copy(phase = "裝置回應逾時，正在重新連線", connected = false)
                gatt?.disconnect()
            }
        }, 4_000)
    }

    private fun completeOperation(sourceGatt: BluetoothGatt) = handler.post {
        if (!isCurrentConnection(gatt, sourceGatt)) {
            log("Ignored callback from previous connection")
            return@post
        }
        if (!operationRunning) {
            log("Ignored late GATT callback")
            return@post
        }
        operationRunning = false
        operationToken++
        runNext()
    }

    private fun handleValue(uuid: java.util.UUID, bytes: ByteArray) = handler.post {
        val text = bytes.toString(Charsets.UTF_8).trim('\u0000', ' ')
        when (uuid) {
            PuriCareProtocol.SOFTWARE_REVISION -> {
                val version = text.ifBlank { null }
                state = state.copy(deviceDetails = DeviceDetails(version))
                log("Device version=${version ?: "empty"}")
            }
            PuriCareProtocol.BATTERY_LEVEL -> if (bytes.isNotEmpty() && !protocolBatterySeen) {
            state = state.copy(snapshot = state.snapshot.copy(battery = bytes[0].toInt() and 0xff, updatedAt = System.currentTimeMillis()))
            log("Battery ${bytes[0].toInt() and 0xff}%")
            }
            else -> {
            val decoded = PuriCareProtocol.decodeReport(bytes)
            if (decoded.any { it.id == PuriCareProtocol.ID_BATTERY }) protocolBatterySeen = true
            state = state.copy(snapshot = state.snapshot.with(decoded))
            if (decoded.any { it.id == PuriCareProtocol.ID_FILTER_REMAIN || it.id == PuriCareProtocol.ID_FILTER_TOTAL }) {
                filterReminderNotifier.evaluate(state.snapshot)
            }
            val decodeNote = when {
                bytes.size >= 11 && !PuriCareProtocol.hasValidCrc(bytes) -> " → CRC mismatch"
                decoded.isEmpty() -> ""
                else -> " → " + decoded.joinToString {
                    if (it.id == PuriCareProtocol.ID_BATTERY && it.secondaryValue != null) {
                        "${it.id}=${it.value},charge=${it.secondaryValue}"
                    } else "${it.id}=${it.value}"
                }
            }
            log("RX ${PuriCareProtocol.hex(bytes)}$decodeNote")
            }
        }
    }

    private fun deviceInfoLabel(uuid: java.util.UUID): String = when (uuid) {
        PuriCareProtocol.SOFTWARE_REVISION -> "Device version"
        PuriCareProtocol.BATTERY_LEVEL -> "Battery"
        else -> uuid.toString()
    }

    private fun log(message: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.TAIWAN).format(java.util.Date())
        state = state.copy(logs = (listOf("$stamp  $message") + state.logs).take(500))
    }

    private fun scheduleReconnect() {
        handler.removeCallbacks(reconnectRunnable)
        if (!backgroundConnectionEnabled || manualDisconnect || gatt != null) return
        val delay = BackgroundReconnectPolicy.delayForAttempt(reconnectAttempt)
        if (delay == null) {
            state = state.copy(phase = "背景重連已暫停，請開啟 App 後重試")
            log("Background reconnect limit reached")
            backgroundStatusListener?.invoke(BackgroundConnectionStatus.PAUSED, state.phase)
            return
        }
        state = state.copy(phase = "連線中斷，${delay / 1_000} 秒後重試", connected = false)
        backgroundStatusListener?.invoke(BackgroundConnectionStatus.RETRYING, state.phase)
        handler.postDelayed(reconnectRunnable, delay)
    }
}

internal fun lightLevelAfterFailedWrite(
    currentLevel: Int?,
    attemptedLevel: Int,
    previousLevel: Int?,
): Int? = if (currentLevel == attemptedLevel) previousLevel else currentLevel
