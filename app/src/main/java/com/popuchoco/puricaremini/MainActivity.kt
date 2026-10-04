package com.popuchoco.puricaremini

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.*

private val Ink = Color(0xFF17211E)
private val Mist = Color(0xFFF4F7F3)
private val Teal = Color(0xFF087E6A)
private val TealSoft = Color(0xFFDCEFE9)
private val Line = Color(0xFFD8E0DC)
private val Muted = Color(0xFF60706A)

class MainActivity : ComponentActivity() {
    private lateinit var ble: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ble = BleManager(applicationContext)
        setContent { PuriCareTheme { PuriCareApp(ble) } }
    }

    override fun onDestroy() {
        ble.disconnect()
        super.onDestroy()
    }
}

@Composable
private fun PuriCareTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Teal, onPrimary = Color.White, primaryContainer = TealSoft,
        background = Mist, onBackground = Ink, surface = Color(0xFFFAFCFA), onSurface = Ink,
        outline = Line, error = Color(0xFFB3261E)
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("總覽", Icons.Outlined.Air), Device("裝置", Icons.Outlined.Bluetooth), Diagnostics("診斷", Icons.Outlined.Terminal)
}

@Composable
private fun PuriCareApp(ble: BleManager) {
    val state = ble.state
    var tab by remember { mutableStateOf(Tab.Home) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) ble.startScan()
    }

    Scaffold(
        containerColor = Mist,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFFAFCFA), tonalElevation = 0.dp) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item, onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = TealSoft)
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(state)
            when (tab) {
                Tab.Home -> HomeScreen(state, ble, onConnect = { tab = Tab.Device })
                Tab.Device -> DeviceScreen(state, ble) {
                    permission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                }
                Tab.Diagnostics -> DiagnosticsScreen(state)
            }
        }
    }
}

@Composable
private fun Header(state: BleUiState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("PURICARE MINI", fontSize = 12.sp, letterSpacing = 1.8.sp, color = Muted)
            Text("我的空氣", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        Surface(shape = RoundedCornerShape(50), color = if (state.connected) TealSoft else Color(0xFFE8ECE9)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (state.connected) Teal else Muted, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(if (state.connected) "已連線" else "離線", fontSize = 13.sp, color = Ink)
            }
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun HomeScreen(state: BleUiState, ble: BleManager, onConnect: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!state.connected) {
            EmptyConnection(onConnect)
        } else {
            AirReading(state.snapshot)
            ControlPanel(state.snapshot, ble)
            SnapshotGrid(state.snapshot)
            OutlinedButton(onClick = ble::refresh, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("更新裝置狀態")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyConnection(onConnect: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(96.dp), shape = CircleShape, color = TealSoft) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Air, null, tint = Teal, modifier = Modifier.size(48.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        Text("讓舊機器重新呼吸", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("直接透過藍牙連線，不需要 LG 帳號或雲端服務。", color = Muted)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Icon(Icons.Outlined.BluetoothSearching, null); Spacer(Modifier.width(8.dp)); Text("連接 PuriCare Mini")
        }
    }
}

@Composable
private fun AirReading(snapshot: AirSnapshot) {
    val pm = snapshot.pm25
    val quality = when {
        pm == null -> "等待資料"
        pm <= 15 -> "良好"
        pm <= 35 -> "普通"
        pm <= 54 -> "敏感族群注意"
        else -> "空氣品質不佳"
    }
    val accent = when {
        pm == null -> Muted
        pm <= 15 -> Teal
        pm <= 35 -> Color(0xFFAE7800)
        else -> Color(0xFFB54A3B)
    }
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFFAFCFA), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PM2.5", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(quality, color = accent, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(pm?.toString() ?: "—", fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Light, color = Ink)
                Spacer(Modifier.width(8.dp))
                Text("µg/m³", color = Muted, modifier = Modifier.padding(bottom = 10.dp))
            }
            snapshot.updatedAt?.let {
                Text("更新於 ${SimpleDateFormat("HH:mm", Locale.TAIWAN).format(Date(it))}", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ControlPanel(snapshot: AirSnapshot, ble: BleManager) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("控制", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFAFCFA), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column {
                ControlSwitch(Icons.Outlined.PowerSettingsNew, "電源", snapshot.power == true) { ble.setPower(it) }
                HorizontalDivider(Modifier.padding(start = 64.dp), color = Line)
                ControlSwitch(Icons.Outlined.AutoAwesome, "自動模式", snapshot.auto == true) { ble.setAuto(it) }
                HorizontalDivider(Modifier.padding(start = 64.dp), color = Line)
                ControlSwitch(Icons.Outlined.LightMode, "清淨顯示燈", snapshot.light == true) { ble.setLight(it) }
            }
        }
        Text("風量", color = Muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "弱", 2 to "中", 3 to "強").forEach { (level, label) ->
                FilterChip(
                    selected = snapshot.fan == level, onClick = { ble.setFan(level) },
                    label = { Text(label) }, modifier = Modifier.weight(1f).height(48.dp),
                    leadingIcon = if (snapshot.fan == level) {{ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }} else null
                )
            }
            FilterChip(selected = snapshot.fan == 15, onClick = { ble.setTurbo(true) }, label = { Text("Turbo") }, modifier = Modifier.weight(1.2f).height(48.dp))
        }
    }
}

@Composable
private fun ControlSwitch(icon: ImageVector, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Teal)
        Spacer(Modifier.width(20.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SnapshotGrid(snapshot: AirSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("即時資訊", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("PM1.0", snapshot.pm1?.toString() ?: "—", "µg/m³", Modifier.weight(1f))
            Metric("PM10", snapshot.pm10?.toString() ?: "—", "µg/m³", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("電池", snapshot.battery?.toString() ?: "—", "%", Modifier.weight(1f))
            Metric("濾網剩餘", snapshot.filterRemaining?.toString() ?: "—", "% / 小時", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color(0xFFFAFCFA), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Text(unit, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DeviceScreen(state: BleUiState, ble: BleManager, requestScan: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("裝置連線", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(state.phase, color = if (state.connected) Teal else Muted)
        if (state.connected) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFAFCFA), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Air, null, tint = Teal, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { Text(state.deviceName ?: "PuriCare Mini", fontWeight = FontWeight.SemiBold); Text("Bluetooth Low Energy", color = Muted, fontSize = 13.sp) }
                    Icon(Icons.Outlined.CheckCircle, "已連線", tint = Teal)
                }
            }
            OutlinedButton(onClick = ble::disconnect, modifier = Modifier.fillMaxWidth()) { Text("中斷連線") }
        } else {
            Button(onClick = requestScan, enabled = !state.scanning, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (state.scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Outlined.BluetoothSearching, null)
                Spacer(Modifier.width(8.dp)); Text(if (state.scanning) "正在掃描…" else "掃描附近裝置")
            }
            if (state.candidates.isNotEmpty()) {
                Text("附近裝置", fontWeight = FontWeight.SemiBold)
                state.candidates.forEach { candidate -> DeviceRow(candidate) { ble.connect(candidate) } }
            } else if (!state.scanning) {
                Text("請讓 PuriCare Mini 進入配對模式，再開始掃描。", color = Muted)
            }
        }
        HorizontalDivider(color = Line)
        Text("隱私", fontWeight = FontWeight.SemiBold)
        Text("App 僅使用 Android 的「附近裝置」權限，資料留在手機端，不需要位置、LG 帳號或網路連線。", color = Muted, lineHeight = 21.sp)
    }
}

@Composable
private fun DeviceRow(candidate: DeviceCandidate, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = Color(0xFFFAFCFA), border = androidx.compose.foundation.BorderStroke(1.dp, if (candidate.likely) Teal.copy(.45f) else Line)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bluetooth, null, tint = if (candidate.likely) Teal else Muted)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(candidate.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("訊號 ${candidate.rssi} dBm${if (candidate.likely) " · 可能是 PuriCare" else ""}", color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun DiagnosticsScreen(state: BleUiState) {
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(pendingExport ?: error("沒有可匯出的診斷內容"))
            } ?: error("無法開啟目的檔案")
        }
        Toast.makeText(
            context,
            if (result.isSuccess) "診斷紀錄已匯出" else "匯出失敗：${result.exceptionOrNull()?.message}",
            Toast.LENGTH_LONG,
        ).show()
        pendingExport = null
    }

    fun exportDiagnostics() {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        pendingExport = DiagnosticExport.toJson(
            state = state,
            generatedAt = OffsetDateTime.now().toString(),
            appVersion = version,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.TAIWAN).format(Date())
        createDocument.launch("puricare-mini-diagnostics-$stamp.json")
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BLE 診斷", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("保留最近 ${state.logs.size} 筆事件與原始封包。", color = Muted)
            }
            FilledTonalButton(onClick = ::exportDiagnostics) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("匯出")
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp), color = Color(0xFF101714)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (state.logs.isEmpty()) Text("尚無紀錄", color = Color(0xFF91A49C), fontFamily = FontFamily.Monospace)
                state.logs.forEach { Text(it, color = Color(0xFFB9D4C9), fontSize = 11.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace); Spacer(Modifier.height(5.dp)) }
            }
        }
    }
}
