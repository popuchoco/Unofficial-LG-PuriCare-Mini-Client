# Unofficial LG PuriCare Mini Client

一套為 LG PuriCare Mini 隨身空氣清淨機設計的非官方 Android 客戶端，支援 Android 12 以上手機。

原廠 App 已停止維護，但既有裝置仍可繼續使用。本專案透過 Bluetooth Low Energy 直接連接裝置，讓使用者查看空氣品質、電量與濾網狀態，並控制電源、風量、Turbo、顯示燈及感測器運作時機，不需要 LG 帳號或雲端服務。

> 本專案與 LG Electronics 無關；LG、PuriCare 及相關產品名稱與商標分屬其權利人所有。

## 裝置與平台摘要

- 支援裝置：LG PuriCare Mini。
- 支援平台：Android 12（API 31）以上。
- 連線方式：Bluetooth Low Energy，本機直連。
- 帳號與網路：不需要 LG 帳號，基本功能不需要網路。

## 專案狀態

目前為可安裝與實機操作的 alpha 測試版本。掃描、連線、即時讀值、裝置控制、資訊匯出、背景連線及深淺色外觀均已建立；不同手機的背景限制、距離自動開關及裝置關機後的行為仍持續以實機驗證。

目前驗證重點：

- Android 12 與 Android 14 的背景連線生命週期。
- 裝置斷電後的有限退避重連與通知狀態。
- 依 Bluetooth 連線狀態自動開關的距離與耗電表現。

## 架構

```text
LG PuriCare Mini
      │ Bluetooth Low Energy
      ▼
Android Client
      ├── 掃描與連線生命週期
      ├── GATT 操作佇列與逾時復原
      ├── 裝置狀態解析與控制
      ├── Foreground Service 背景連線
      └── 本機資訊記錄與 JSON 匯出
```

- [系統架構](docs/ARCHITECTURE.md)
- [設計決策](docs/DESIGN_DECISIONS.md)
- [軟體設計](docs/SOFTWARE_DESIGN.md)
- [功能相容性](docs/FEATURE_COMPATIBILITY.md)
- [通訊格式](docs/PROTOCOL.md)

## 開發環境

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.7.2
- Kotlin 2.0.21
- Gradle Wrapper 8.9

請先依本機環境設定 Android SDK，然後執行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon
```

Debug APK 會產生於：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 組建已啟用 R8 與資源縮減；正式散布前仍需設定自己的簽署金鑰。

## 目前功能

### 功能全貌

| 功能面 | 使用者可以完成的事 | 狀態 |
| --- | --- | --- |
| 總覽 | 查看 PM1.0、PM2.5、PM10、電量、濾網與目前風量 | 可用 |
| 裝置連線 | 掃描、選擇及連接 PuriCare Mini | 可用 |
| 裝置控制 | 電源、Auto、Turbo、風量、顯示燈與感測器時機 | 可用 |
| 背景連線 | 以前景服務維持連線，斷線後有限次數重試 | 測試中 |
| 距離自動開關 | 設定裝置依 Bluetooth 連線狀態自動開關 | 待更多實機驗證 |
| 外觀與資訊 | 跟隨系統、淺色、深色、裝置版本與連線資訊 | 可用 |
| 資訊匯出 | 由使用者主動匯出狀態與連線記錄 JSON | 可用 |

### 總覽與裝置控制

- 顯示 PM1.0、PM2.5、PM10、電量及濾網剩餘時間。
- 正確區分目前風量、Auto 與獨立 Turbo 狀態。
- 控制後自動重新讀取裝置狀態。
- 關閉電源前顯示二次確認，避免誤觸後無法由 App 重新喚醒。
- 空氣品質感測器可設定為「當產品開啟時」或「始終開啟」。

### 裝置連線與背景運作

- 掃描附近裝置，優先標示可能的 PuriCare Mini。
- 連線未辨識裝置前會要求再次確認。
- GATT 操作採序列佇列；逾時時中止該次連線，避免操作重疊。
- 背景連線中斷後依序等待 3、6、15、30、60 秒重試，達到上限後暫停。
- 常駐通知顯示已連線、重試中或已暫停，並可點擊回到 App。
- 背景連線與距離自動開關互斥，開啟其中一項會關閉另一項。

### 外觀、資訊與隱私

- 支援跟隨系統、淺色及深色模式並記住設定。
- 顯示裝置名稱、裝置實際提供的版本及 App 版本。
- 裝置未提供版本欄位時顯示「裝置未提供」。
- 連線記錄只保存在記憶體中，最多保留最近 500 筆。
- JSON 只有在使用者主動選擇位置後才會寫出。

## 手機測試

1. 關閉其他可能正在連接 PuriCare Mini 的 App。
2. 安裝本專案產生的 Debug APK。
3. 允許「附近裝置」權限。
4. 前往「裝置」分頁，按下「掃描附近裝置」。
5. 選擇 PuriCare Mini，等待狀態顯示「已連線」。
6. 確認總覽讀值，再逐項測試風量、Auto、Turbo 與顯示燈。
7. 如需回報問題，請在「資訊」分頁匯出 JSON，並先確認內容是否適合分享。

## 疑難排解

若遇到掃描、連線或狀態更新異常，建議依序嘗試：

1. 確認 PuriCare Mini 已開機，且沒有被其他手機或 App 連接。
2. 關閉再開啟手機 Bluetooth。
3. 將裝置重新開機後再次掃描。
4. 確認 App 已取得「附近裝置」權限；背景連線另需通知權限。
5. 仍無法恢復時，匯出資訊 JSON 以便比對連線流程。

## 已知限制

- 背景連線可能受到各手機品牌的省電策略影響。
- 距離自動開關仍待更多手機、距離及裝置關機情境驗證。
- App 不提供裝置更新功能。
- 未簽署的 Release APK 不能直接作為正式散布版本。

## 資料與隱私

- 預設不連線任何雲端服務。
- 不需要位置權限，也不讀取或保存 GPS 座標。
- 已保存的上次裝置資訊位於 App 私有設定中。
- 匯出內容不包含裝置位址。
- Repository 不應包含私人裝置資料、憑證或使用者匯出內容。

## License

本專案採用 [MIT License](LICENSE)。
