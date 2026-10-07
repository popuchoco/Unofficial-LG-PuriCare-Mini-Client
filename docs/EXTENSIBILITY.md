# 可擴充範圍

## 定位

PuriCare Mini Next 的核心維持「手機與裝置本機直連、預設不使用雲端」。本文件描述可由開源社群選配的延伸架構，不代表目前版本已實作，也不是既定版本承諾。擴充者可依 IoT、Smart Home、研究或長期觀測情境，選擇只加入需要的元件。

## 建議分層

```text
PuriCare Mini ─BLE─▶ Android Client
                         │
                         ├─ Local database
                         │   ├─ observations / device state
                         │   ├─ automation events
                         │   └─ transactional outbox
                         │
                         └─ Optional sync worker ─HTTPS─▶ Self-hosted bridge
                                                        ├─ protected read-only API
                                                        ├─ Home Assistant / MQTT
                                                        ├─ Node-RED
                                                        ├─ Dashboard
                                                        └─ Touch command queue
```

Android App 不宜預設長期開放 HTTP server。建議由 App 主動將 outbox 事件送往使用者自行設定的 HTTPS bridge，再由 bridge 提供區域網路或遠端整合介面。沒有設定端點與認證時，App 應只保存本機資料且不連線外部服務。

## 本機資料庫與 transactional outbox

可使用 Room／SQLite 建立下列邏輯資料：

| 資料集合 | 用途 |
| --- | --- |
| `device_observations` | PM1.0、PM2.5、PM10、電量、風量、濾網與量測時間 |
| `device_state_events` | 電源、Auto、Turbo、感測器與連線狀態變化 |
| `automation_events` | 本機規則觸發、通知與外部整合結果 |
| `outbox` | 尚待同步的不可變事件、重試次數與下一次嘗試時間 |
| `remote_commands` | 選配 Touch 命令、到期時間、冪等鍵與執行結果 |

一次有效狀態寫入與對應 outbox event 必須在同一個 SQLite transaction 完成，避免「本機有資料但沒有同步事件」或反向的半套狀態。同步端以 `eventId` 冪等接收；成功確認後才標記完成，離線或伺服器錯誤採有上限的退避重試。資料保留期、最大 outbox 容量與捨棄策略必須可設定並在 UI 揭露。

## 受保護的唯讀 API

唯讀介面應與命令介面分離，最小範例：

| Method | Path | 用途 |
| --- | --- | --- |
| `GET` | `/api/v1/devices` | 已授權裝置及連線摘要 |
| `GET` | `/api/v1/devices/{id}/state` | 最新裝置狀態與空氣品質 |
| `GET` | `/api/v1/devices/{id}/observations` | 限定時間範圍的歷史資料 |
| `GET` | `/api/v1/devices/{id}/filter` | 濾網剩餘百分比與時數 |

API 應使用不透明裝置 ID、HTTPS、可撤銷的 read-only token、固定最大查詢範圍、分頁、速率限制與不含密鑰的稽核記錄。唯讀 token 不得呼叫任何會改變裝置或觸發 BLE 工作的 endpoint。

## Home Assistant 整合

可選擇以下方式，不必全部實作：

- RESTful sensor：定期讀取受保護的最新狀態 API。
- MQTT bridge：由 outbox consumer 發布 availability、PM、電量、風量與濾網 sensor。
- Custom integration：建立 device registry、sensor、binary sensor 與診斷資料。

建議先提供唯讀 entity。電源、風量等遠端控制屬較高風險能力，必須使用獨立寫入權限、明確 opt-in 與實機失敗回報，不能沿用 read-only token。

## Node-RED 自動化

可提供匯入式 flow 範例，讓使用者從 HTTPS／MQTT 取得結構化事件，建立以下流程：

- PM2.5 超過門檻時通知或啟動其他空氣設備。
- 電量過低或濾網接近門檻時提醒。
- 將狀態寫入 InfluxDB、TimescaleDB 或其他研究資料庫。
- 在裝置離線或資料過期時標記 unavailable，避免把舊數值當成即時資料。

範例 flow 不應內嵌 token、私人網址、裝置識別資訊或真實環境資料。

## 裝置與空氣品質 Dashboard

Dashboard 可使用唯讀 API 顯示即時狀態、24 小時／7 天／30 天趨勢、多裝置比較、濾網預估與資料新鮮度。圖表必須標示時間、單位、時區、資料缺口與最後更新時間；裝置未連線時不得把最後一次值呈現成即時讀值。

長期歷史與多裝置聚合建議放在外部資料庫，手機端只保存可控期間，避免無限制增加 App 私有儲存空間。

## 遠端量測觸發（Touch）API

Touch API 是「請手機立即重新讀取一次裝置狀態」的命令入口，不等同直接把 BLE 暴露到網路。建議 contract：

| Method | Path | 行為 |
| --- | --- | --- |
| `POST` | `/api/v1/devices/{id}/touch` | 建立具有到期時間的量測命令，回傳 `commandId` |
| `GET` | `/api/v1/commands/{commandId}` | 查詢 queued／running／succeeded／failed／expired |

命令至少包含 `commandId`、冪等鍵、建立時間、到期時間與要求來源。手機端以單一 FIFO 執行，離線命令不得無限累積或在恢復連線後瞬間全部補做。伺服器接受命令不代表量測成功；只有手機連上裝置、完成 GET ALL 並回報新快照後，狀態才能成為 `succeeded`。

Touch 與任何控制 API 都應預設關閉，使用獨立 command token、最小權限、速率限制、短到期時間、重試上限及完整稽核。若未來加入電源或風量控制，必須另立 endpoint 與權限 scope，不得把任意協定 payload 當成公開 API。

## 安全與隱私基線

- 公開 build 不內建伺服器 URL、API key、憑證或預設雲端。
- 同步與遠端命令必須由使用者明確啟用，且能立即停用及撤銷 token。
- upload、read-only 與 command credential 分離；伺服器只保存不可逆雜湊時，不記錄原始 token。
- 不在 log、匯出檔或 Repository 寫入 token、私人 endpoint、裝置位址或家庭拓撲。
- API 回應包含 `observedAt` 與 `receivedAt`，整合端可判斷資料是否過期。
- 所有 schema 變更使用 migration；同步 contract 需要版本欄位與相容性測試。
- 遠端服務故障不得阻塞 BLE、UI 或本機資料寫入。

## 建議實作順序

1. Room／SQLite observation schema、migration 與資料保留策略。
2. 狀態寫入與 outbox 建立的單一 transaction，以及冪等同步測試。
3. 自架 bridge 的 ingestion 與受保護唯讀 API。
4. Home Assistant、Node-RED 與 Dashboard 的唯讀整合。
5. 完成威脅模型、命令佇列與稽核後，才評估 Touch API。

