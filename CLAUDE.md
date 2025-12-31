# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案概述

花蓮計程車雙模式 Android App - 統一的司機端和乘客端應用程式

- **語言**: Kotlin
- **UI 框架**: Jetpack Compose + Material3
- **架構**: MVVM + Clean Architecture
- **版本**: v1.2.3-MVP
- **後端位置**: `~/Desktop/HualienTaxiServer` (自建 Node.js 伺服器)

## 重要約定

1. **文檔管理**: 所有文檔更新必須寫入 README.md，禁止創建新的 `.md` 文件
2. **資料庫安全**: 絕對禁止使用任何 `accept-data-loss` 相關指令
3. **Firebase 驗證**: 專案已全面改用 Firebase Phone Authentication (SMS OTP)，不再使用密碼登入

## 常用開發指令

### 建置與執行
```bash
# 建置專案
./gradlew build

# 安裝 Debug 版本
./gradlew installDebug

# 清理建置
./gradlew clean

# 查看 signing report (取得 SHA-256 用於 Firebase)
./gradlew signingReport
```

### 專案結構查看
```bash
# 查看專案檔案結構
ls -la app/src/main/java/com/hualien/taxidriver/

# 查看 Kotlin 檔案
find app/src/main/java/com/hualien/taxidriver -name "*.kt"
```

## 關鍵架構設計

### 1. 雙角色系統架構

專案支援司機端和乘客端兩種角色，通過統一的 `RoleManager` 管理：

- **司機端**: 接收訂單、更新定位、回報行程狀態
- **乘客端**: 叫車、查看司機位置、追蹤訂單

**關鍵檔案**:
- `MainActivity.kt`: 根據 `RoleManager` 決定顯示司機或乘客界面
- `utils/RoleManager.kt`: 角色切換與狀態管理
- `navigation/NavGraph.kt`: 司機端導航
- `navigation/PassengerNavGraph.kt`: 乘客端導航

### 2. WebSocket 連接管理

WebSocket 使用 Socket.io-client，通過 `WebSocketManager` 單例管理：

**重要設計原則**:
- 使用 `Mutex` 防止重複連接（避免內存洩漏）
- ViewModel 層面追蹤連接狀態（`connectedDriverId`, `isConnecting`）
- 分離司機端和乘客端事件監聽器
- 連接前必須完全清理舊連接 (`cleanupSocket()`)
- 追踪連接模式 (`ConnectionMode.DRIVER` / `ConnectionMode.PASSENGER`)
- UI 層使用 `LaunchedEffect(driverId)` 而非 `LaunchedEffect(Unit)` 防止重組時重複連接

**關鍵事件**:
- 司機端: `driver:online`, `order:offer`, `order:status`, `driver:location`
- 乘客端: `passenger:online`, `nearby:drivers`, `order:update`, `driver:location`

**檔案**:
- `data/remote/WebSocketManager.kt`
- `viewmodel/HomeViewModel.kt` (防重複連接邏輯)

### 3. 混合定位系統

專案使用智能混合定位策略，結合 GPS 和 Google Geolocation API：

**定位優先級**:
1. 優先使用 GPS (Fused Location Provider) - 精度高
2. 如果 GPS 在 8 秒內無回應，啟用 Geolocation API 備援
3. GPS 精度差 (>50m) 時，使用 Geolocation API 輔助
4. Geolocation API 有 30 秒冷卻時間，避免過度調用

**Geolocation API 用量監控**（v1.2.2 新增）:
- 自動記錄每次 API 調用
- 統計每日/累計調用次數
- 估算月成本（每次約 $0.005 USD）
- 達到 80%/95% 免費額度時輸出警告
- 用量數據持久化存儲（SharedPreferences）
- 每日自動重置計數器
- 使用 `getUsageStats()` 查詢用量統計

**關鍵檔案**:
- `service/HybridLocationService.kt`: 混合定位核心邏輯
- `service/GeolocationApiService.kt`: WiFi/基站定位 + 用量監控
- `service/LocationService.kt`: 前景服務（背景定位）

### 4. 認證與 Token 管理

**Firebase Phone Authentication**:
- 司機端: `ui/screens/auth/DriverPhoneLoginScreen.kt`
- 乘客端: `ui/screens/auth/PassengerPhoneLoginScreen.kt`
- ViewModel: `viewmodel/PhoneAuthViewModel.kt`

**Token 自動攜帶與刷新**:
- `data/remote/AuthInterceptor.kt`: 所有 API 請求自動添加 `Authorization: Bearer <token>`
- `data/remote/TokenRefreshAuthenticator.kt`: 自動檢測 401 錯誤並刷新 Firebase ID Token
  - 使用 OkHttp Authenticator 機制
  - 自動重試失敗的請求（最多 3 次）
  - Firebase ID Token 有效期 1 小時，過期自動刷新
  - 刷新失敗時自動清除登入狀態
- `utils/DataStoreManager.kt`: 持久化存儲 token 和用戶資訊
  - `updateToken()`: 更新 token（用於自動刷新）
  - `getCachedToken()`: 非阻塞讀取緩存 token
- `data/remote/RetrofitClient.kt`: 必須在 `MainActivity.onCreate()` 中初始化

### 5. 智能訂單管理

**GPS 自動偵測系統**:
- `manager/SmartOrderManager.kt`: 根據 GPS 位置自動判斷到達上車點/目的地
- `ui/screens/SimplifiedDriverScreen.kt`: 一鍵操作介面（針對中老年司機）
- `utils/VoiceAssistant.kt`: 語音提示（TTS）

### 6. Google Maps 整合

專案使用多個 Google Maps API：
- **Places API**: 地址搜尋與自動完成
- **Directions API**: 路線計算與導航
- **Distance Matrix API**: 距離和時間計算
- **Geolocation API**: WiFi/基站定位
- **Maps SDK**: 地圖顯示與標記

**關鍵檔案**:
- `service/PlacesApiService.kt`
- `service/DirectionsApiService.kt`
- `service/DistanceMatrixApiService.kt`
- `service/GeolocationApiService.kt`

## 資料模型層級

```
domain/model/
├── UserRole.kt              # DRIVER / PASSENGER
├── DriverAvailability.kt    # OFFLINE / REST / AVAILABLE / ON_TRIP
├── OrderStatus.kt           # PENDING / ACCEPTED / PICKED_UP / COMPLETED / CANCELLED
├── Order.kt                 # 訂單完整資料
├── Driver.kt                # 司機資料
├── Location.kt              # 經緯度 + 地址
├── PaymentType.kt           # CASH / IPASS (愛心卡)
└── Fare.kt                  # 車資結構
```

## 重要常數與配置

**Server 連線**:
- Base URL: `http://15.164.245.47/api/`
- WebSocket URL: `http://15.164.245.47`
- 定義位置: `app/build.gradle.kts` (BuildConfig) → `utils/Constants.kt`

**花蓮預設座標**:
- 緯度: 23.9871
- 經度: 121.6015
- 預設縮放: 14f

**定位參數**:
- 更新間隔: 10 秒（GPS）
- 最小位移: 10 公尺
- GPS 精度閾值: 50 公尺
- GPS 超時: 8 秒

**WebSocket**:
- 重連延遲: 5 秒
- 最大重連次數: 10 次

## 無障礙友善設計

專案包含中老年人友善模式：
- 超大字體（1.3-1.5 倍）
- 加大按鈕（72-80dp 高度）
- 高對比度顏色
- 簡化操作流程
- 語音提示

**關鍵檔案**:
- `ui/theme/SeniorFriendlyTypography.kt`
- `ui/screens/SimplifiedDriverScreen.kt`
- `ui/screens/AccessibilitySettingsScreen.kt`
- `utils/AccessibilityManager.kt`

## 開發注意事項

### API 呼叫流程
1. 所有 API 定義在 `data/remote/ApiService.kt` 或 `data/remote/PassengerApiService.kt`
2. Repository 層封裝 API 呼叫（`data/repository/`）
3. ViewModel 呼叫 Repository 並管理 UI 狀態
4. Token 由 `AuthInterceptor` 自動攜帶

### WebSocket 使用流程
1. ViewModel 中取得 `WebSocketManager.getInstance()`
2. 司機端: 呼叫 `connect(driverId)`
3. 乘客端: 呼叫 `connectAsPassenger(passengerId)`
4. 監聽對應的 StateFlow (`orderOffer`, `passengerOrderUpdate` 等)
5. 離線或登出時必須呼叫 `disconnect()`

### 狀態管理最佳實踐
- 使用 `StateFlow` 管理 UI 狀態
- ViewModel 中使用 `MutableStateFlow` 內部修改
- UI 層使用 `collectAsState()` 觀察
- 避免在 Composable 中直接呼叫 suspend 函數

### 定位服務使用
1. 在 ViewModel 中初始化 `HybridLocationService`
2. 呼叫 `startLocationUpdates()` 開始定位
3. 觀察 `locationState` StateFlow 取得位置更新
4. 不需要時呼叫 `stopLocationUpdates()` 釋放資源

## 常見問題排查

### WebSocket 無法連接
1. 檢查 `Constants.WS_URL` 是否正確
2. 確認後端伺服器已啟動 (`~/Desktop/HualienTaxiServer`)
3. 查看 Logcat 中的 `WebSocketManager` tag
4. 確認沒有重複連接（檢查 `isConnecting` 和 `currentMode`）

### Token 未攜帶
1. 確認 `RetrofitClient.init(context)` 在 `MainActivity.onCreate()` 中已呼叫
2. 檢查 `DataStoreManager.initializeTokenCache()` 是否執行
3. 確認 token 已保存到 DataStore

### 定位不準確
1. 檢查是否授予定位權限（`ACCESS_FINE_LOCATION`）
2. 在實體裝置上測試（模擬器 GPS 不穩定）
3. 觀察 `LocationState.source` 判斷使用的定位方式
4. GPS 失敗時會自動切換到 Geolocation API

### Firebase 簡訊驗證失敗
1. 確認 Firebase Console 已啟用 Phone Authentication
2. 確認已升級到 Blaze (Pay as you go) 方案
3. 確認 SHA-256 憑證指紋已添加到 Firebase
4. 檢查手機號碼格式 (+886 9xxxxxxxx)

## 測試指南

### 司機端測試流程
1. 選擇「司機」角色
2. 使用手機號碼進行簡訊驗證
3. 切換司機狀態（離線 → 可接單）
4. 觀察 WebSocket 連接狀態
5. 等待訂單推送（需後端配合發送測試訂單）

### 乘客端測試流程
1. 選擇「乘客」角色
2. 使用手機號碼進行簡訊驗證
3. 在地圖上選擇上車點和目的地
4. 建立訂單並等待司機接單
5. 觀察司機實時位置更新

## 相關文檔

- `README.md`: 完整專案文檔（所有更新都寫入此檔案）
- `GOOGLE_MAPS_SETUP.md`: Google Maps API 設定指南
- `COMPLETION_SUMMARY.md`: 功能完成度總結

## 後端協作

後端專案位於 `~/Desktop/HualienTaxiServer` (Node.js + Prisma)：
- API 端點: `/api/auth/`, `/api/drivers/`, `/api/orders/`, `/api/passengers/`
- WebSocket 命名空間: `/`
- 資料庫: SQLite (開發) / PostgreSQL (生產)

前後端溝通時注意：
- 訂單 ID 格式統一
- 經緯度精度統一使用 6 位小數
- 時間戳統一使用 ISO 8601 格式
- 錯誤訊息統一使用中文
