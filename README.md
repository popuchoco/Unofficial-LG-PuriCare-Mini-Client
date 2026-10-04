# PuriCare Mini Next

適用於 LG PuriCare Mini 的非官方 Android 12+ 控制 App，以 Bluetooth Low Energy 直接連接附近裝置。

> 本專案與 LG Electronics 無關，PuriCare 為其商標。使用者應自行確認裝置保固與使用風險。

## 功能

- 搜尋、連接及重新整理 PuriCare Mini 狀態
- 顯示 PM1.0、PM2.5、PM10、電量與濾網剩餘時數
- 顯示目前風量，並可選擇 Auto、Turbo 或代表性手動段數
- 控制電源與顯示燈；關閉電源前會再次確認
- 「資訊」頁顯示連線能力、裝置名稱與裝置實際提供的版本
- 跟隨系統、淺色及深色三種外觀模式
- 匯出最近 500 筆連線記錄及裝置狀態，方便回報問題

## 背景連線

目前版本尚未提供持續背景連線。離開 App 或系統回收程序後，Bluetooth 連線可能中斷；App 會在「資訊」頁如實顯示這項能力。

裝置若未提供版本欄位，對應內容會顯示「裝置未提供」。

## 建置

需要 JDK 17、Android SDK 35、Gradle 8.9、Android Gradle Plugin 8.7.2 與 Kotlin 2.0.21。

```powershell
cd PuriCareMiniNext
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

產物位於 `app\build\outputs\apk\debug\app-debug.apk`。

## 文件

- [系統架構](docs/ARCHITECTURE.md)
- [設計決策](docs/DESIGN_DECISIONS.md)
- [Software Design（SD）](docs/SOFTWARE_DESIGN.md)
- [BLE 通訊筆記](docs/PROTOCOL.md)

## 使用

1. 開啟 PuriCare Mini 並保持在手機附近。
2. 安裝 APK，允許「附近裝置」權限。
3. 在「裝置」頁搜尋並選擇 PuriCare Mini。
4. 連線後可於「總覽」查看資料及操作裝置。
5. 若需回報問題，可由「資訊」頁匯出資訊檔。

## 注意事項

- 本專案目前是 alpha debug build，尚未經 Google Play 發佈流程。
- 各批次裝置可能不提供相同的標準資訊欄位。
- App 不會上傳掃描結果、裝置資料或連線記錄。
