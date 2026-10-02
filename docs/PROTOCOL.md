# LG PuriCare Mini BLE 協定筆記

本文件記錄 PuriCare Mini 的 BLE 通訊相容性實作，以及目前可確認的協定邊界。

## 已確認的通訊結構

主要 GATT profile：

| 用途 | UUID |
|---|---|
| Nordic UART Service | `6a400001-b5a3-f393-e0a9-e50e24dcca9e` |
| App → 裝置 write | `6a400002-b5a3-f393-e0a9-e50e24dcca9e` |
| 裝置 → App notify | `6a400003-b5a3-f393-e0a9-e50e24dcca9e` |
| Battery Service | `0000180f-0000-1000-8000-00805f9b34fb` |
| Battery Level | `00002a19-0000-1000-8000-00805f9b34fb` |

相容性資料另包含 `7F30/7F40`、Nordic DFU 與標準 Device Information UUID；它們分別用於配對、Air Purifier profile、模組資訊或韌體流程，不應全部當成 Mini 的主控制通道。

## TOAD frame

封包結構如下：

```text
[04] [54 4F 41 44] [messageType] [00] [payloadLength] [payload...] [CRC16 BE]
```

- address 長度 `04`
- address ASCII `TOAD`
- message type：`01=SET`、`02=GET`、`04=REPORT`、`10=ACK`
- CRC：`C0820f.m5505M()`；結果以四位 hex 轉為兩個 big-endian bytes。

短 scalar 欄位將 10-bit ID 與 `00 + 4-bit value` 合成兩 bytes。例如：

- GET ALL：ID `501`、value/format `2` → payload `7D 42`
- POWER ON：ID `503`、value `1` → payload `7D C1`

## 已知 IDU

| ID | 協定名稱 | App 用途 |
|---:|---|---|
| 501 | `IDU_GET_ALL` | 讀取全部狀態 |
| 503 | `IDU_ON_OFF` | 電源 |
| 506 | `IDU_WIND_SETTING` | 風量 |
| 531 | `IDU_AUTO_ON_OFF` | 自動模式 |
| 590 | `IDU_LIGHT` | 顯示燈 |
| 623 | `IDU_BATTERY_STATUS` | 電池 |
| 701 | `IDU_RESET_FILTER` | 重設濾網（本 App 不實作） |
| 819 | `IDU_PM_1_SENSOR` | PM1.0 |
| 820 | `IDU_PM_2_SENSOR` | PM2.5 |
| 821 | `IDU_PM_10_SENSOR` | PM10 |
| 823 | `SENSOR_MONITORING` | 監測設定 |
| 853 | `IDU_FILTER_REMAIN_TIME` | 濾網剩餘 |
| 863 | `IDU_WIND_SETTING_TURBO` | Turbo |

## 驗證界線

現有相容性資料仍無法保證所有硬體 revision 都使用相同欄位長度或數值語義。因此本 App：

- 不實作濾網重設或韌體寫入等難以回復操作。
- 診斷頁保存每一筆 TX/RX raw hex。
- 只有 service discovery 找到 Nordic UART Service 才啟用控制。
- 實機驗證後應把收到的 REPORT fixture 加入單元測試，再收斂 decoder。
