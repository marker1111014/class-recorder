# 課時錄音記（ClassRecorder）

一款專為學生設計的 **Android 原生（Kotlin + Jetpack Compose）自動排程課堂錄音與每日總結筆記 App**。

## 核心功能

1. **週課表排程自動錄音（`10:10~11:00`、`11:10~12:00` 分段存檔）**
   - 內建學校標準 1～9 節時間表（早 1～4 節 `08:10~12:00`、午休後第 5 節起 `12:50~17:40`，每節 50 分鐘、課間 10 分鐘），可自由新增／修改／套用預設節次時間。
   - 在週課表（週一～週五，可選開週末）支援**「批次填課（畫筆連選模式）」**與**「單格快速套用已有課程 + 連 2 / 3 節一鍵填滿」**，時間到自動開錄、下課自動分段存檔（`.m4a` AAC 格式）。
   - 支援「今日跳過」（調課／請假免改永久課表）、「手動立即錄音」與「上下課前後緩衝秒數」。

2. **各節課獨立筆記與放學後「今日總結」時間軸**
   - 每段錄音內建播放器（支援 `0.75x / 1.0x / 1.25x / 1.5x / 2.0x` 倍速播放與進度拖曳）。
   - 每節課可記錄「上課重點」、「作業／考試提醒」與「單節 AI 摘要」。
   - 依日期彙整成「今日總結」與歷史時間軸，支援關鍵字搜尋。

3. **純本機儲存 + 放學一鍵匯出給 AI 總結**
   - 音檔依日期與節次自動命名（如 `2026-11-25_P03_1010-1100_國文.m4a`）並存於本機私有目錄。
   - 放學後可一鍵：
     - 分享今日全部音檔＋自動帶入課表與筆記的 **AI 總結提示詞** 給 Gemini / ChatGPT / NotebookLM。
     - 複製 AI 提示詞或每日 Markdown 報告。
     - 打包匯出 `.zip`（含音檔 + 提示詞 + Markdown 時間軸）或直接儲存至手機 `下載 / ClassRecorder / YYYY-MM-DD`。

## Android 14 / 15 背景麥克風限制設計說明

Android 14（API 34+）起禁止 App 在背景冷啟動 `FOREGROUND_SERVICE_TYPE_MICROPHONE`。本專案採用雙重穩定策略：
- **排程守候模式（推薦）**：在 App 畫面開啟「排程守候」後，系統啟動常駐 `microphone` 前景服務並搭配 `AlarmManager` 精準鬧鐘；課間休息不佔用麥克風與電量，上課時間到立即精準開錄、下課自動存檔。
- **備援通知喚醒**：若手機重開機或未開啟守候時錯過背景啟動權限，鬧鐘觸發時會改發高優先順序通知「立即開始錄音」，點擊通知即可合規取得前景麥克風授權並接續當日排程。

## 建置與測試

```bash
./gradlew testDebugUnitTest assembleDebug
```

產出的 Debug APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。

## GitHub Actions 自動建置與 Release 發佈

本專案包含兩個 GitHub Actions 工作流程：
- [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)：每次推送至 `main` 或發起 PR 時自動執行單元測試與 Debug APK 建置。
- [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml)：推送 `v*` 標籤（例如 `v1.0.0`）或手動執行 Workflow 時，自動建置 APK 並發佈至 GitHub **Releases** 頁面（若於 Repository Secrets 設定 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，亦會同步簽章產出 Release 版 APK）。

```bash
git tag v1.0.0
git push origin v1.0.0
```
