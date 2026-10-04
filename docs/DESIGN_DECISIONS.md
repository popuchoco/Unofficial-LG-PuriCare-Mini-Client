# 設計決策

本文件記錄會影響相容性、安全性與後續維護的重要取捨。狀態分為「採用」與「待評估」。

## DD-001：Android 12+ 為最低平台（採用）

使用 `BLUETOOTH_SCAN` 與 `BLUETOOTH_CONNECT` 的新權限模型，避免把 BLE 掃描包裝成定位功能。代價是 Android 11 以下裝置不在支援範圍。

## DD-002：裝置直連，不設預設雲端（採用）

核心功能全部在手機與裝置之間完成。App 不要求帳號，也不內建 API endpoint 或憑證，降低服務停止與個人資料外流風險。

## DD-003：所有 GATT 操作共用單一 FIFO（採用）

Descriptor write、characteristic read 與 write 必須等待各自 callback 才完成。這會犧牲少量初始化速度，但可避免 Android BLE stack 因並行操作遺失工作。

## DD-004：控制後重新讀取狀態（採用）

SET 命令送出後約 750 ms 再發送 GET ALL。畫面最終以裝置回報為準，不預先假設控制一定成功。

## DD-005：Auto 與 Turbo 採不同狀態（採用）

目前風量使用 ID 506；其中值 `8` 代表 Auto。Turbo 使用 ID 863 的獨立布林狀態。介面不得以 Turbo 覆蓋原始風量，也不得把 ID 531 顯示成風量 Auto。

## DD-006：關閉電源需要二次確認（採用）

遠端關閉後可能無法再由 App 喚醒裝置，因此開啟動作可直接執行，關閉則顯示風險說明與確認視窗。

## DD-007：裝置版本只讀標準欄位（採用）

依序讀取 Device Information service 的製造商、型號、Firmware、Hardware 與 Software revision。缺少 characteristic 時顯示「裝置未提供」。另提供「裝置版本」摘要：優先顯示 Firmware，裝置只提供 Software 時則顯示 Software；原始欄位仍分開保留，不猜測缺少的版本。

## DD-008：外觀設定保存在 App 私有偏好（採用）

提供跟隨系統、淺色與深色，使用 `SharedPreferences` 保存。配色使用 Material 3 語意色，避免功能元件依賴固定淺色背景。

## DD-009：背景連線採能力揭露，不假裝支援（採用）

目前沒有 Foreground Service、自動重連或省電策略適配，因此「資訊」頁明確顯示目前不支援。若未來實作，需另行設計常駐通知、重連退避與各品牌省電驗證。

## DD-010：高風險命令不納入第一版（採用）

濾網重設、Firmware 寫入及其他無法輕易復原的操作不提供 UI，也不由一般狀態流程觸發。
