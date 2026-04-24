# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案概述

GoGoCha（前稱「花蓮計程車」）雙模式 Android App — 司機端與乘客端統一在單一 APK 中，透過 `RoleManager` 在啟動時決定進入哪端的導航圖。

- **語言**: Kotlin, **UI**: Jetpack Compose + Material3
- **架構**: MVVM（無 DI 框架，手動管理依賴）
- **本地儲存**: DataStore Preferences（無 Room/SQLite）
- **後端**: `~/Desktop/HualienTaxiServer` (Node.js + Prisma)，啟動用 `cd ~/Desktop/HualienTaxiServer && pnpm dev`

## 重要約定

1. **文檔管理**: 所有文檔更新必須寫入 README.md，禁止創建新的 `.md` 文件（CLAUDE.md 除外）
2. **資料庫安全**: 絕對禁止使用任何 `accept-data-loss` 相關指令
3. **認證方式**: Firebase Phone Authentication (SMS OTP)，不使用密碼登入
4. **套件管理**: 後端只使用 pnpm 引入套件
5. **錯誤顯示**: 所有錯誤必須完整顯示在前端 UI

## 常用開發指令

```bash
./gradlew build                  # 建置專案
./gradlew installDebug           # 安裝 Debug APK 到裝置
./gradlew assembleRelease        # 建置 Release APK（需 keystore.properties）
./gradlew bundleRelease          # 建置 Release AAB（Play Console 用）
./gradlew clean                  # 清理建置
./gradlew signingReport          # 取得 SHA-256（Firebase Auth 用）
./gradlew publishReleaseBundle   # Triple-T：build AAB + 上 Play Console alpha + 自動推 testers
```

沒有實質的單元測試或 instrumentation 測試（只有 Android Studio 生成的 stub）。

## 關鍵架構決策

### 初始化順序（MainActivity.onCreate）

必須按此順序初始化，否則 API 呼叫或認證會失敗：
1. `RetrofitClient.init(context)` — 設定 OkHttp + AuthInterceptor
2. `DataStoreManager.getInstance(context)` + `initializeTokenCache()` — 載入 token 快取
3. `FareCalculator.loadConfigFromServer()` — 從 Server 載入費率配置

### 雙角色路由

`MainActivity` → `AppContent` 讀取 `RoleManager.currentRole`：
- `DRIVER` → `MainNavigation`（NavGraph.kt）
- `PASSENGER` → `PassengerNavigation`（PassengerNavGraph.kt）
- `null` → `RoleSelectionScreen`（首次選擇角色）

兩端有獨立的 API 介面（`ApiService.kt` vs `PassengerApiService.kt`）、ViewModel、和導航圖。

### WebSocket 防重複連接（容易出 bug 的地方）

`WebSocketManager` 是單例，使用 `connectionMutex: Mutex` 防止併發連接。關鍵防護：
- ViewModel 層追蹤 `connectedDriverId` / `isConnecting`，避免重複觸發
- UI 層必須用 `LaunchedEffect(driverId)` 而非 `LaunchedEffect(Unit)`，否則 recomposition 會重複連接
- 連接前必須先呼叫 `cleanupSocket()` 完全清理舊連接
- 追蹤 `ConnectionMode.DRIVER` / `ConnectionMode.PASSENGER`，防止角色混用

### Token 自動刷新鏈

```
API 請求 → AuthInterceptor 加 Bearer Token
    → 如果 401 → TokenRefreshAuthenticator 用 Firebase SDK 刷新 ID Token
    → 更新 DataStoreManager 快取 → 重試請求（最多 3 次）
    → 刷新失敗 → 清除登入狀態，導回登入頁
```

Firebase ID Token 有效期 1 小時，`TokenRefreshAuthenticator` 透過 OkHttp Authenticator 機制自動處理。

### 混合定位策略

`HybridLocationService` 結合 GPS (Fused Location Provider) 和 Google Geolocation API：
- GPS 優先，8 秒無回應才啟用 Geolocation API 備援
- GPS 精度 >50m 時切換到 Geolocation API
- Geolocation API 有 30 秒冷卻時間防止超用（有月費用監控）
- `LocationService` 是 Foreground Service，背景持續定位用

## Server 連線

- **Base URL**: `https://api.hualientaxi.taxi/api/`（BuildConfig.SERVER_URL）
- **WebSocket**: `https://api.hualientaxi.taxi`（BuildConfig.WS_URL）
- 定義在 `app/build.gradle.kts` 的 `buildConfigField`，透過 `Constants.kt` 存取

**前後端協定**：經緯度 6 位小數、時間戳 ISO 8601、錯誤訊息中文。

## 建置配置注意

- **compileSdk / targetSdk**: 36, **minSdk**: 26
- **Release**: 啟用 R8 minify + shrinkResources，ProGuard 規則在 `proguard-rules.pro`
- **Signing**: 從 `keystore.properties` 讀取，keystore 檔在 `app/release-keystore.jks`
- **發布管道**: **Triple-T Play Publisher** (`com.github.triplet.play`) → Play Console alpha 軌道
  - Service account JSON 放 `~/.gradle/play-console-service-account.json`（不進 repo）
  - Release notes 寫在 `app/src/main/play/release-notes/zh-TW/default.txt`（500 字內）
  - 一鍵發布：`./gradlew publishReleaseBundle`
  - Firebase App Distribution plugin 已移除（2026-04-23），不再使用
- **版本號**: `app/build.gradle.kts` 中的 `versionCode` / `versionName`

## WebSocket 事件速查

| 方向 | 司機端 | 乘客端 |
|------|--------|--------|
| 發送 | `driver:online`, `driver:location`, `order:accept`, `order:reject` | `passenger:online` |
| 接收 | `order:offer`, `order:cancelled`, `order:taken`, `order:urge` | `nearby:drivers`, `order:update`, `driver:location` |

## 資料流模式

```
UI (Compose Screen)
  ↓ collectAsState()
ViewModel (StateFlow)
  ↓ suspend fun
Repository (AuthRepository / OrderRepository / PassengerRepository)
  ↓
ApiService / PassengerApiService (Retrofit) ← AuthInterceptor 自動加 Token
WebSocketManager (Socket.io) ← 單例，StateFlow 推送即時事件
```

所有外部 API 呼叫都在 Repository 層，ViewModel 負責管理 UI 狀態。沒有 UseCase 層（domain/usecase/ 目錄為空）。