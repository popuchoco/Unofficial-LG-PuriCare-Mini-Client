# 系統架構

## 目標

PuriCare Mini Next 在 Android 12 以上裝置直接透過 Bluetooth Low Energy 連接 PuriCare Mini，不依賴雲端帳號或遠端服務。現行版本聚焦即時讀值、近端控制、裝置資訊與可攜式問題回報。

## 元件

```text
┌──────────────────┐       BLE/GATT       ┌──────────────────────────┐
│ PuriCare Mini    │ ◀──────────────────▶ │ Android Client           │
│                  │                      │                          │
│ - UART service   │                      │ - Compose UI             │
│ - Battery        │                      │ - BLE session manager    │
│ - Device info    │                      │ - GATT operation queue   │
└──────────────────┘                      │ - frame codec            │
                                          │ - immutable UI snapshot  │
                                          │ - information exporter   │
                                          │ - foreground service     │
                                          └──────────────────────────┘
```

### Compose UI

三個底部導覽分頁分別負責總覽與控制、裝置搜尋與連線、資訊與外觀設定。UI 只讀取 `BleUiState` 快照，控制動作則呼叫 `BleManager` 的明確方法。

### BLE session manager

`BleManager` 負責掃描、建立 GATT 連線、服務探索、通知訂閱、狀態讀取與控制命令。只有找到相容 UART service 才將連線標示為可操作。

### GATT operation queue

Android GATT 的 descriptor、characteristic read 與 write 都是非同步操作。App 以單一 FIFO queue 序列化操作，等 callback 完成後才執行下一項，避免初始化讀取與控制寫入互相覆蓋。

### Frame codec

`PuriCareProtocol` 集中管理 UUID、frame builder、CRC、REPORT／ACK 解碼與狀態 ID。已辨識的讀值會合併到不可變 `AirSnapshot`；未知欄位不會讓整包資料失敗。

### Information exporter

「資訊」頁保留最近 500 筆連線記錄，並由使用者主動匯出 JSON。匯出內容包含 App／Android 版本、連線狀態、裝置資訊、目前快照及附近候選裝置；App 不會自動上傳。

### Background connection

使用 Android Foreground Service 與低干擾常駐通知保留共用 `BleManager`。意外斷線後採 3、6、15、30、60 秒的有限退避，嘗試連接 App 私有設定中的上次裝置；使用者主動中斷、關閉背景模式或達到上限時不再重連。

背景連線與依 Bluetooth 距離自動開關採單一 `ConnectionMode` 保存，因此不可能同時啟用。切換到距離模式時停止背景服務；切換到背景模式時先關閉裝置端距離模式。

## 主要資料流

```text
掃描 → 選擇裝置 → GATT 連線 → 服務探索
                                  │
                                  ├─ 啟用 UART notification
                                  ├─ 讀取電池與標準裝置資訊
                                  └─ 發送 GET ALL
                                         │
裝置通知 → frame decoder → AirSnapshot → Compose 重組畫面

使用者操作 → SET frame → GATT FIFO → 裝置 ACK／REPORT → 延遲重新整理
```

## 現行邊界

- 背景連線依賴 Foreground Service；各品牌省電策略仍可能中止程序。
- 沒有本機歷史資料庫或雲端同步。
- 不執行 Firmware 更新、濾網重設或其他難以回復的裝置操作。
- 裝置資訊只讀取 Bluetooth SIG 標準欄位；裝置未提供時不推測內容。
