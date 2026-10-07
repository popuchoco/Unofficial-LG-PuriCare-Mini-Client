# Software Design（SD）

## 1. 範圍

Android Client 負責 PuriCare Mini 的 BLE 搜尋、連線、即時狀態解碼、近端控制、標準裝置資訊讀取、外觀設定與資訊匯出。

## 2. 模組

| 模組 | 檔案 | 責任 |
|---|---|---|
| Protocol | `PuriCareProtocol.kt` | UUID、frame、CRC、REPORT／ACK parser、狀態模型 |
| BLE session | `BleManager.kt` | 掃描、連線、服務探索、GATT queue、通知與控制 |
| UI | `MainActivity.kt` | 三分頁導覽、狀態呈現、控制確認、外觀設定 |
| Export | `DiagnosticExport.kt` | 產生具 schema version 的 JSON 資訊檔 |
| Feature settings | `FeaturePreferences.kt` | 保存互斥連線模式與感測器 timing |
| Filter life | `FilterLife.kt` | 計算百分比、已用時數與提醒門檻策略 |
| Filter notification | `FilterReminderNotifier.kt` | 管理通知頻道、權限檢查與每週期一次通知 |
| Background | `BackgroundConnectionService.kt` | 常駐通知、維持連線與恢復上次裝置 |
| Unit tests | `app/src/test/...` | frame、CRC、實機 fixture、狀態合併與匯出格式 |

## 3. 狀態模型

`BleUiState` 是 Compose 可觀察的不可變快照：

- `phase`、`scanning`、`connected`：連線流程及 UI 可用性。
- `deviceName`、`candidates`：目前裝置與掃描候選。
- `snapshot`：PM、電量、電源、風量、Turbo、顯示燈、濾網剩餘與總時數。
- `deviceDetails`：裝置實際提供的版本字串。
- `logs`：最新在前，最多 500 筆的連線記錄。

目前狀態只存在程序記憶體；重新啟動 App 後需重新連線取得。

## 4. 連線生命週期

1. 取得 Android 附近裝置權限並開始掃描。
2. 依名稱提示相容候選，但仍顯示其他 BLE 裝置供使用者判斷。
3. 使用者選擇後停止掃描並建立 GATT。
4. 服務探索成功後驗證 UART service。
5. 啟用 RX notification，將後續 read 排入 FIFO。
6. 讀取 Battery 與 Device Information 中實際存在的 characteristic。
7. 發送 GET ALL，持續將 REPORT／ACK 合併到畫面狀態。
8. 未啟用背景模式時，Activity 結束會中斷 GATT；背景模式則交由 Foreground Service 持有。
9. 回呼先核對目前 GATT 實例；舊連線的晚到回呼直接忽略。常駐通知同步呈現已連線、重試中與已暫停三種狀態。

## 5. GATT concurrency

`queue` 保存待執行操作，`operationRunning` 保證同一時間只有一項工作。Characteristic read、write 及 descriptor write callback 都會呼叫 `completeOperation()` 推進下一項。斷線時清空 queue 並重設 running 狀態。

控制命令先排入 write；送出後延遲排入 GET ALL。這個設計讓 ACK 與完整狀態回報都有時間抵達，UI 則由裝置回報收斂。

## 6. 資料解碼

- 接受 `TOAP` 與 `TOAD` address。
- 只解析 message type `04`（REPORT）與 `10`（ACK）。
- scalar header 包含 10-bit ID、長度格式及 inline 值。
- 多位元組整數以 big-endian 合併。
- Battery status 的第一個資料 byte 為百分比，後續 byte 不當成電量。
- 未知 ID 保留在連線記錄，但不修改已知狀態。

詳細 frame 與 ID 表見 [PROTOCOL.md](PROTOCOL.md)。

## 7. UI 與互動

- 總覽：主要 PM2.5 讀值、控制、其他感測值、電量與濾網時數。
- 資訊／濾網管理：計算濾網壽命百分比，顯示剩餘、已用與總時數，並設定一次性的更換提醒門檻。
- 裝置：搜尋、候選選擇、連線／中斷與相容性說明。
- 資訊：裝置版本、連線能力、外觀模式、連線記錄與 JSON 匯出。
- 互動元件至少 48 dp；狀態除色彩外也使用文字表達。
- 電源關閉是破壞性較高的遠端動作，必須二次確認。

## 8. 資訊匯出

JSON 使用 `schemaVersion`，未取得欄位輸出 `null`。schema 3 在快照加入 `filterTotal`、`filterPercent`，並在能力設定加入 `filterReminderThresholdPercent`。匯出由 Android Storage Access Framework 建立文件，只有使用者選定位置後才寫入；App 不要求廣泛儲存權限。

## 9. 濾網壽命與提醒

計算規則：

```text
total = deviceTotal > 0 ? deviceTotal : 2000
remaining = clamp(deviceRemaining, 0, total)
percent = floor(remaining × 100 ÷ total)
percent = remaining > 0 ? max(percent, 1) : 0
used = max(total - remaining, 0)
```

提醒預設關閉。啟用時預設門檻為 10%，之後可切換為 3%、5%、10% 或 20%。每次收到 ID 853 或 ID 854 都重新計算；若百分比不高於門檻且該門檻尚未通知，建立本機通知並保存已通知門檻。百分比重新高於門檻時清除旗標，供更換濾網後的下一週期使用。

Android 13 以上在啟用提醒時要求 `POST_NOTIFICATIONS`。前景連線取得讀值即可評估；若要在離開 App 後仍持續接收裝置讀值，使用者必須另外啟用背景連線。

若 ID 854 未回報，畫面會明示總時數來自 2,000 小時的相容性備援。門檻停用時，門檻值與已通知旗標以同一次偏好交易更新，避免程序中止造成半套狀態。

## 10. 測試策略

- JVM unit tests：GET／SET frame、CRC、REPORT fixture、Battery／filter 解碼、濾網百分比與提醒策略、Auto 值、Turbo 與風量獨立性、JSON escaping 與 null。
- Build verification：`testDebugUnitTest` 後執行 `assembleDebug` 與 `assembleRelease`，確認 R8 與資源縮減規則可用。
- 實機驗證：掃描、連線、通知、控制 ACK、控制後狀態、版本欄位與深淺色可讀性。
- 新增裝置回報格式時，先以去識別化 fixture 建立 regression test，再擴充 parser。

## 11. 未實作項目

- 開機自動恢復背景服務。
- 歷史資料庫、圖表與雲端同步。
- Firmware 更新、濾網重設與裝置帳號功能。
- Room／SQLite 歷史資料庫、transactional outbox 與外部同步 worker。
- 受保護的唯讀 API、Home Assistant、Node-RED 與 Dashboard bridge。
- 具有獨立授權、到期與稽核的遠端量測 Touch API。

上述項目屬社群可選配的擴充方向；資料模型、介面邊界與安全要求見 [EXTENSIBILITY.md](EXTENSIBILITY.md)。
