# PuriCare Mini Next

為 LG PuriCare Mini 重製的非官方 Android 12+ 原生客戶端。App 直接透過 Bluetooth Low Energy 與機器通訊，不依賴已停止維護的 LG App、LG 帳號或雲端服務。

> 本專案與 LG Electronics 無關。PuriCare 與相關商標屬其權利人所有。

## 目前可用功能

- Android 12–15「附近裝置」權限流程，不要求 GPS 定位。
- 掃描附近 BLE 裝置，將名稱疑似 PuriCare／LG 的候選排在前面。
- 驗證裝置是否提供相容的 Nordic UART Service。
- 訂閱即時通知並讀取標準 Battery Service。
- 使用相容的 `TOAD` 通訊格式：更新全部狀態、電源、自動模式、顯示燈、風量與 Turbo。
- 顯示 PM1.0、PM2.5、PM10、電池及濾網資訊；所有原始收發封包保留在診斷頁。
- Material 3 三分頁介面：總覽、裝置、診斷。

## 專案結構

```text
PuriCareMiniNext/
├── app/src/main/...              Android App
├── app/src/test/...              協定單元測試
├── docs/PROTOCOL.md              通訊相容性與協定邊界
└── gradle/                       可重現建置 wrapper
```

## 建置

環境：JDK 17、Android SDK 35、Gradle 8.9、AGP 8.7.2、Kotlin 2.0.21。

```powershell
cd PuriCareMiniNext
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

APK：`app\build\outputs\apk\debug\app-debug.apk`

## 實機驗證順序

1. 關閉原生 LG App，確保沒有其他手機佔用裝置連線。
2. 讓 PuriCare Mini 進入藍牙配對模式。
3. 安裝 APK，允許「附近裝置」，到「裝置」分頁掃描。
4. 優先選取標示「可能是 PuriCare」的候選。
5. 連線後先按「更新裝置狀態」，確認診斷頁出現 `RX`。
6. 先測試顯示燈或風量，再測試電源；若機型行為不同，保留診斷頁的 TX/RX 十六進位資料。

## 已知邊界

- UUID、TOAD 封裝、CRC、資料 ID 與控制入口已依既有裝置通訊行為完成相容實作，但尚未在你的實機韌體上完成閉環驗證。
- 目前採保守的通用 scalar 解碼並保留 raw packet。不同韌體若採不同欄位長度，需以實機 RX 調整。
- App 不提供韌體更新、不清除機器資料、不連線外部伺服器。
- 目前是 alpha debug build，尚未建立正式簽署金鑰與 Play Store 發佈流程。
