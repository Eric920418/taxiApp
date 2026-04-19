# 花蓮計程車 - 雙模式 Android App

> **HualienTaxiDriver** - 司機端 + 乘客端統一應用程式
> 版本：v1.10.0-MVP（beta15）| 更新日期：2026-04-19

## 📝 最新更新（2026-04-19）- 地標動態同步

### 問題
App 端 `HualienLocalAddressDB.kt`（97 筆 hardcoded）每次要加新地標（例如 commit 73cad31「好樂迪、三角形餐酒館」）都要改 Kotlin 重新發 APK，運營沒辦法自己維護。

### 解法
Admin Panel 新增「地標管理」頁面（Server 端改動），App 啟動時拉最新地標合併進本地索引：

- **新增**：`data/repository/LandmarkSyncRepository.kt` — 啟動時呼叫 `GET /api/landmarks/sync`，fire-and-forget 不阻斷
- **新增**：`data/remote/dto/LandmarkSyncDto.kt` — 回應 DTO
- **修改**：`service/HualienLocalAddressDB.kt` — 加「靜態 + 動態」分層索引，新增 `applyRemoteLandmarks()` 原子替換；lookup 介面不變，呼叫方零改動
- **修改**：`data/remote/PassengerApiService.kt` — 加 `syncLandmarks(since)` endpoint
- **修改**：`MainActivity.kt` — `onCreate` 最後啟動背景同步

### 關鍵設計
- **hardcoded 永遠作為離線 fallback** — 斷網/首次啟動仍可用原 97 筆地標
- **同名時 Server 版本 override** — Admin 修正的座標/別名會覆蓋 hardcoded（例如座標打錯修正）
- **不刪 hardcoded** — Server 軟刪除不影響 hardcoded 那份
- **零 UI 改動** — `lookup/getCoords/resolveAliases` 介面不變，舊程式直接受惠

### 驗證
```bash
# 新增一個測試地標
# Admin Panel → 地標管理 → 新增「測試館 ABC」

# App 重啟
adb logcat -s LandmarkSync   # 看到「同步完成：收到 N 筆」

# App 斷網重啟仍可用 hardcoded
```

詳細 Server 端設計見 `~/Desktop/HualienTaxiServer/README.md` 最新修改章節。

---

## 目錄

- [快速開始](#快速開始)
- [專案概述](#專案概述)
- [技術架構](#技術架構)
- [專案結構](#專案結構)
- [開發指南](#開發指南)
- [Firebase 設定](#firebase-設定)
- [測試與除錯](#測試與除錯)
- [版本歷史](#版本歷史)

---

## 快速開始

### 1. 環境需求
- Android Studio Ladybug 2024.2.1+
- JDK 17+
- Android SDK 34（targetSdk 36）
- 實體裝置（推薦）或模擬器

### 2. Clone 並建置
```bash
cd /Users/eric/AndroidStudioProjects/HualienTaxiDriver
./gradlew build
```

### 3. 設定 Server 連線
`local.properties` 已設定：
```properties
server.url=http://15.164.245.47
MAPS_API_KEY=YOUR_MAPS_API_KEY
```

### 4. 執行 App
```bash
./gradlew installDebug
```

### 5. 啟動後端 Server（開發時）
```bash
cd ~/Desktop/HualienTaxiServer
pnpm dev
```

---

## 專案概述

### 核心功能

| 功能 | 司機端 | 乘客端 | 狀態 |
|------|--------|--------|------|
| Firebase 簡訊驗證 | ✅ | ✅ | 完成 |
| Google Maps 整合 | ✅ | ✅ | 完成 |
| WebSocket 即時通訊 | ✅ | ✅ | 完成 |
| 訂單管理 | ✅ | ✅ | 完成 |
| 定位服務（混合定位） | ✅ | ✅ | 完成 |
| 車資計算 + 拍照 | ✅ | - | 完成 |
| 評價系統 | ✅ | ✅ | 完成 |
| 收入統計 | ✅ | - | 完成 |
| FCM 推播通知 | ✅ | ✅ | 完成 |
| 中老年友善模式 | ✅ | - | 完成 |
| 智能一鍵操作 | ✅ | - | 完成 |
| 智能派單 V2 | ✅ | ✅ | 完成 |
| Whisper 語音助理 | ✅ | ✅ | 完成 |
| 語音叫車自動化流程 | - | ✅ | 完成 |
| 語音優先介面 | - | ✅ | v1.5.0 新增 |
| AI 自動接單 | ✅ | - | v1.6.0 新增 |
| 熱區配額系統 | ✅ | ✅ | v1.6.0 新增 |
| 語音對講機 | ✅ | ✅ | v1.7.0 新增 |
| **司機到達通知** | - | ✅ | **v1.8.1 新增** |
| **電話叫車整合** | ✅ | - | **v1.9.0 新增** |

### 技術棧

```
語言：Kotlin
UI：Jetpack Compose + Material3
架構：MVVM + Clean Architecture
網路：Retrofit + OkHttp + Socket.io-client
地圖：Google Maps SDK + Places API + Directions API + Distance Matrix API + Geolocation API
定位：Hybrid Location (GPS + WiFi/基站)
背景服務：Foreground Service
本地儲存：DataStore (Preferences)
相機：CameraX
動畫：Lottie Compose（2D 角色動畫）
推播：Firebase Cloud Messaging
認證：Firebase Phone Authentication
語音：OpenAI Whisper API + GPT-4o-mini（意圖解析）
```

---

## 技術架構

### 分層架構

```
┌─────────────────────────────────────┐
│  Presentation (UI)                  │
│  - Compose Screens                  │
│  - ViewModels                       │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  Domain (業務邏輯)                   │
│  - Use Cases                        │
│  - Models                           │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  Data (資料層)                       │
│  - Repository                       │
│  - API Service (Retrofit)           │
│  - WebSocket Manager                │
│  - DataStore                        │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  外部服務                            │
│  - HualienTaxiServer (Node.js)      │
│  - Google Maps API                  │
│  - Firebase                         │
└─────────────────────────────────────┘
```

### 雙角色系統

```
MainActivity
    ├── RoleManager (角色管理)
    │       ├── DRIVER → 司機端 NavGraph
    │       └── PASSENGER → 乘客端 NavGraph
    │
    ├── 司機端
    │       ├── HomeScreen / SimplifiedDriverScreen / SeniorFriendlyHomeScreen
    │       ├── OrdersScreen
    │       ├── EarningsScreen
    │       └── ProfileScreen
    │
    └── 乘客端
            ├── VoiceFirstPassengerScreen  ← 語音優先介面（預設首頁）
            ├── PassengerHomeScreen         ← 傳統地圖模式
            ├── PassengerOrdersScreen
            └── PassengerProfileScreen
```

---

## 專案結構

```
app/src/main/java/com/hualien/taxidriver/
├── MainActivity.kt                    # 主入口（角色判斷）
├── navigation/
│   ├── Screen.kt                     # 路由定義
│   ├── NavGraph.kt                   # 司機端導航
│   └── PassengerNavGraph.kt          # 乘客端導航
│
├── ui/
│   ├── theme/                        # Material3 主題
│   │   ├── Theme.kt                  # 預設主題
│   │   └── WarmCompanionTheme.kt     # 暖心陪伴風主題（語音優先介面）
│   ├── screens/                      # 畫面
│   │   ├── HomeScreen.kt             # 司機主頁
│   │   ├── SimplifiedDriverScreen.kt # 智能一鍵模式
│   │   ├── SeniorFriendlyHomeScreen.kt # 中老年友善
│   │   ├── OrdersScreen.kt
│   │   ├── EarningsScreen.kt
│   │   ├── ProfileScreen.kt
│   │   └── passenger/                # 乘客端畫面
│   │       ├── VoiceFirstPassengerScreen.kt  # 語音優先介面
│   │       ├── PassengerHomeScreen.kt        # 傳統地圖模式
│   │       └── ...
│   └── components/
│       ├── XiaoJuCharacter.kt        # 語音波形指示器
│       ├── PickupMapSelector.kt      # Uber 風格上車點選擇器
│       ├── OrderTags.kt              # 訂單標籤（來源/補貼/寵物）
│       └── ...
│
├── viewmodel/                        # ViewModels
│   ├── HomeViewModel.kt
│   ├── PassengerViewModel.kt
│   └── ...
│
├── domain/model/                     # 資料模型
│   ├── Order.kt
│   ├── Driver.kt
│   ├── OrderStatus.kt
│   └── ...
│
├── data/
│   ├── repository/                   # Repository
│   └── remote/
│       ├── ApiService.kt             # 司機端 API
│       ├── PassengerApiService.kt    # 乘客端 API
│       ├── RetrofitClient.kt
│       ├── AuthInterceptor.kt        # Token 攔截器
│       ├── TokenRefreshAuthenticator.kt # Token 自動刷新
│       ├── WebSocketManager.kt       # Socket.io
│       └── dto/                      # DTO
│
├── service/
│   ├── LocationService.kt            # 前景定位服務
│   ├── HybridLocationService.kt      # 混合定位
│   ├── GeolocationApiService.kt      # WiFi/基站定位
│   ├── PlacesApiService.kt           # 地址搜尋
│   ├── DirectionsApiService.kt       # 路線計算
│   ├── DistanceMatrixApiService.kt   # 距離矩陣
│   ├── TaxiFirebaseMessagingService.kt # FCM
│   └── VoiceRecorderService.kt       # 語音錄製 + VAD
│
├── manager/
│   └── SmartOrderManager.kt          # GPS 自動偵測
│
└── utils/
    ├── Constants.kt                  # 常數設定
    ├── DataStoreManager.kt           # 持久化存儲
    ├── RoleManager.kt                # 角色管理
    ├── FareCalculator.kt             # 車資計算
    ├── VoiceAssistant.kt             # TTS 語音播報
    ├── VoiceCommandHandler.kt        # 語音指令處理
    └── ...
```

---

## 開發指南

### Server 連線設定

```kotlin
// app/build.gradle.kts
buildConfigField("String", "SERVER_URL", "\"http://15.164.245.47/api/\"")
buildConfigField("String", "WS_URL", "\"http://15.164.245.47\"")
```

Server 使用 nginx 反向代理，port 80 自動轉發到 Node.js port 3000。

### API 架構

**司機端 API**（`/api/drivers/`、`/api/orders/`）
- 登入/註冊
- 狀態切換
- 訂單接受/拒絕/完成
- 車資提交
- 收入統計

**乘客端 API**（`/api/passengers/`）
- 登入（自動註冊）
- 叫車
- 取消訂單
- 訂單歷史
- 評價

### WebSocket 事件

**司機端**
```
發送: driver:online, driver:location, order:accept, order:reject, order:destination-confirm
接收: order:offer, order:cancelled, order:taken, order:urge
```

**乘客端**
```
發送: passenger:online
接收: nearby:drivers, order:update, driver:location
```

### Token 自動管理

- `AuthInterceptor`：API 請求自動添加 Bearer Token
- `TokenRefreshAuthenticator`：401 錯誤時自動刷新 Firebase ID Token
- `DataStoreManager`：持久化存儲 Token 和用戶資訊

### 混合定位策略

```
1. 啟動時 → Geolocation API 快速定位（2-3秒）
2. 同時啟動 GPS 定位
3. GPS 成功且精度好 → 使用 GPS
4. GPS 失敗/精度差 → 使用 Geolocation API
5. 室內自動切換到 WiFi/基站定位
```

### 智能派單系統 V2（v1.3.0 新增）

**分層派單架構**
```
訂單創建 → SmartDispatcherV2
    │
    ├── 第 1 批（3 位最適合司機）→ 20 秒等待
    │       ↓ 無人接單
    ├── 第 2 批（接下來 3 位）→ 20 秒等待
    │       ↓ 無人接單
    ├── ... 最多 5 批
    │       ↓ 5 分鐘總超時
    └── 訂單自動取消
```

**六維度評分系統**
| 維度 | 權重 | 說明 |
|------|------|------|
| 距離 | 20% | 司機到上車點的直線距離 |
| ETA | 20% | 真實預計到達時間（Google API） |
| 收入均衡 | 20% | 優先派給今日收入較低的司機 |
| 接單預測 | 20% | ML 模型預測司機接單機率 |
| 效率匹配 | 10% | 司機類型與訂單類型匹配 |
| 熱區加成 | 10% | 目的地是否為熱門區域 |

**混合 ETA 策略**
- 距離 < 3km：估算公式（距離 × 1.3 / 25 × 60 秒）
- 距離 ≥ 3km：Google Distance Matrix API
- 快取機制：資料庫 + 記憶體雙層快取

**拒單原因追蹤**
司機拒單時必須選擇原因：
- `TOO_FAR`：距離太遠
- `LOW_FARE`：車資太低
- `UNWANTED_AREA`：不想去該區域
- `OFF_DUTY`：準備下班
- `OTHER`：其他原因

**監控 API（Server 端）**
```
GET  /api/dispatch/v2/stats              # 派單統計
GET  /api/dispatch/v2/driver-patterns/:id # 司機行為模式
GET  /api/dispatch/v2/rejection-analysis  # 拒單分析
GET  /api/dispatch/v2/active-orders       # 活動訂單
POST /api/dispatch/v2/retrain-model       # 重訓練 ML 模型
```

**1+1 疊單（Android 端相容）**
- 模式定義：司機同時最多持有 `1` 張當前單 + `1` 張下一單，不支援更長隊列。
- 新增訂單狀態：`QUEUED`，代表「已預掛給司機，但尚未輪到執行」。
- 新增 payload 欄位：
  - `queuePosition`: `1` 表示當前單，`2` 表示下一單
  - `queuedAfterOrderId`: 下一單依附的前單 ID
  - `predictedHandoverAt`: 預估交接時間（毫秒）
  - `assignmentMode`: `SINGLE` / `STACKED_1P1`
- Android 端行為：
  - `HomeViewModel` 同時維護 `currentOrder` 與 `queuedOrder`
  - 司機首頁分開顯示「當前訂單」與「下一單」
  - 下一單若仍為 `OFFERED`，司機可直接接受或拒絕
  - 主單完成或取消時，若下一單仍有效，App 會先在本地升為新的當前單
- 目前邊界：
  - 本 repo 已完成 Android 端資料模型、狀態流與 UI 相容
  - 真正的 `1+1` 派單判斷、ETA 重算、保留/釋放重派仍需後端派單器配合

### Whisper 語音助理系統（v1.4.0 新增）

**架構概覽**
```
Android (錄音)         Server (轉錄+解析)      OpenAI
    │                       │                    │
    ├── VAD 偵測說話結束 ───►│                    │
    │                       │                    │
    ├── 上傳音檔（m4a）─────►├── Whisper API ────►│
    │                       │◄── 文字轉錄 ───────┤
    │                       │                    │
    │                       ├── GPT-4o-mini ────►│
    │                       │◄── 結構化指令 ─────┤
    │                       │                    │
    │◄── VoiceCommand JSON ─┤                    │
    │                       │                    │
    ├── 執行指令 + TTS 回饋  │                    │
```

**預估延遲：~1-1.5 秒**

**支援的語音指令（司機端）**
| 指令類型 | 觸發語句範例 | 動作 |
|----------|-------------|------|
| ACCEPT_ORDER | 「好」「接」「可以」「我來」 | 接受當前訂單 |
| REJECT_ORDER | 「不要」「拒絕」「不接」 | 拒絕當前訂單 |
| MARK_ARRIVED | 「到了」「我到了」 | 標記已到達上車點 |
| START_TRIP | 「上車了」「出發」 | 開始載客行程 |
| END_TRIP | 「到了」「結束」 | 結束行程 |
| UPDATE_STATUS | 「上線」「下線」「休息」 | 切換司機狀態 |
| QUERY_EARNINGS | 「今天賺多少」「收入」 | 查詢收入統計 |
| NAVIGATE | 「導航到xxx」 | 開啟導航 |
| EMERGENCY | 「救命」「報警」 | 緊急求助 |

**支援的語音指令（乘客端 v1.4.1 新增）**
| 指令類型 | 觸發語句範例 | 動作 |
|----------|-------------|------|
| BOOK_RIDE | 「去火車站」「去太魯閣」「載我去xxx」 | 語音叫車（自動搜尋目的地） |
| SET_DESTINATION | 「目的地是xxx」「終點xxx」 | 設置目的地 |
| SET_PICKUP | 「在xxx上車」「來xxx接我」 | 設置上車點 |
| CANCEL_ORDER | 「取消」「不要了」「取消訂單」 | 取消當前訂單 |
| CALL_DRIVER | 「打給司機」「聯絡司機」 | 撥打司機電話 |
| CHECK_STATUS | 「司機在哪」「多久到」 | 查詢訂單狀態 |

**花蓮在地景點識別**
GPT 意圖解析支援花蓮常見地點：
- 交通：花蓮火車站、新城火車站
- 景點：太魯閣、七星潭、鯉魚潭、東大門夜市、松園別館
- 醫院：慈濟醫院、花蓮醫院
- 學校：東華大學、慈濟大學
- 商圈：中山路、中正路、遠百

**VAD（Voice Activity Detection）參數**
```kotlin
SILENCE_THRESHOLD = 500        // 靜音振幅閾值
SILENCE_DURATION_MS = 1500L    // 靜音持續時間（判定說話結束）
MAX_RECORDING_MS = 15000L      // 最長錄音時間
```

**API 端點（Server）**
```
POST /api/whisper/transcribe           # 司機端上傳音檔
POST /api/whisper/transcribe-passenger # 乘客端上傳音檔
GET  /api/whisper/usage                # 查詢用量統計
GET  /api/whisper/health               # 服務健康檢查
POST /api/whisper/test                 # 司機端文字測試（開發用）
POST /api/whisper/test-passenger       # 乘客端文字測試（開發用）
```

**成本估算**
| 項目 | 單價 | 每日預估用量 | 月成本 |
|------|------|------------|--------|
| Whisper API | $0.006/分鐘 | 30 分鐘 | ~$5.4 |
| GPT-4o-mini | $0.15/1M tokens | 10K tokens | ~$0.05 |
| **總計** | | | **~$6/月** |

**Android 端關鍵檔案**
- `service/VoiceRecorderService.kt`：錄音 + VAD 偵測
- `utils/VoiceCommandHandler.kt`：司機端指令處理
- `utils/PassengerVoiceCommandHandler.kt`：乘客端指令處理（v1.4.1）
- `ui/components/VoiceCommandButton.kt`：語音按鈕 UI（含乘客專用組件）
- `data/remote/dto/VoiceCommandDto.kt`：指令資料結構（司機+乘客）
- `viewmodel/PassengerViewModel.kt`：乘客語音功能整合

**Server 端關鍵檔案**
- `src/services/WhisperService.ts`：Whisper + GPT-4o-mini（支援司機+乘客）
- `src/api/whisper.ts`：API 路由（含乘客端端點）

---

## Firebase 設定

### Phone Authentication 設定

1. **Firebase Console** → Authentication → Sign-in method → 啟用 Phone

2. **取得 SHA-256 憑證指紋**
```bash
./gradlew signingReport
```

3. **新增到 Firebase**
   - Settings → Your apps → Android App → Add fingerprint
   - 貼上 SHA-256 值

### 計費注意事項

Firebase Phone Auth **必須使用 Blaze 方案**：
- 免費額度：每月 10,000 次驗證
- 超額費用：每次約 $0.01 USD

**建議**：設定預算警報（Firebase Console → Usage and billing → Budget alerts）

### FCM 推播設定

App 已整合三種通知頻道：
- `order_notifications`：訂單相關（高優先級）
- `status_notifications`：狀態更新
- `general_notifications`：一般訊息

---

## 測試與除錯

### 本地測試流程

**司機端**
1. 選擇「司機」角色
2. 簡訊驗證登入
3. 切換狀態：離線 → 可接單
4. 等待訂單推送

**乘客端**
1. 選擇「乘客」角色
2. 簡訊驗證登入
3. 選擇上車點和目的地
4. 點擊「立即叫車」

### Logcat 標籤

```
HualienTaxi, WebSocketManager, LocationService, GeolocationApi
```

### 模擬器 GPS

Android Studio → Extended Controls → Location
- 緯度：23.9871
- 經度：121.6015

### 常見問題

| 問題 | 排查方向 |
|------|----------|
| WebSocket 無法連接 | 檢查 Server 是否啟動、WS_URL 是否正確 |
| Token 未攜帶 | 確認 `RetrofitClient.init()` 在 MainActivity 中已呼叫 |
| 定位不準確 | 使用實體裝置、檢查權限 |
| 簡訊驗證失敗 | 確認 Blaze 方案、SHA-256 指紋 |

---

## 版本歷史

### v1.9.3 (2026-03-25) - 當前版本
**修復：語音叫車地址亂匹配問題**

修復語音輸入街道門牌地址（如「信豐街805」）被 Google Places API 模糊匹配為完全不同地址（如「中和街163號」）的嚴重問題。

- **根因**：Places Autocomplete 專為地標/商家搜尋設計，對街道門牌地址的模糊匹配會返回不相關結果，且系統盲目 auto-select 第一個結果
- **修復**：`searchAndAutoSelectDestination()` 重構為雙路徑分流：
  - **街道地址**（含 路/街/巷/弄/號）→ Android Geocoder API（精確門牌解析），自動加花蓮前綴
  - **地標名稱**（火車站、慈濟等）→ Google Places API（原有邏輯不變）
- **驗證機制**：Geocoder 結果需包含原始街名，否則 fallback 到 Places API
- **透明確認**：當解析結果與用戶原話不同時，語音提示差異：「您說的是 XX，找到的是 YY，對嗎？」
- **影響檔案**：僅 `PassengerViewModel.kt`（新增 `isStreetAddress()`、`extractStreetName()`、`resolveStreetAddress()`、`searchWithPlacesApi()`）

### v1.9.2 (2026-03-09)
**修復：電話叫車地址辨識準確性**

修復 `CallFieldExtractor.ts` 和 `PhoneCallProcessor.ts` 中四個地址辨識 bug：

- **Bug1 修復**：`pickup_address` 現在也套用 `normalizeAddress()`（原本只有 `destination_address` 有套用，導致上車點「火車站」→「花蓮火車站」轉換失效）
- **Bug2 修復**：Geocoding 改用正確的 Google Geocoding API 處理街道門牌地址，而非 Places Text Search（後者專門搜商家/景點，無法精確定位「中正路38號」類地址）
- **Bug3 修復**：依地址類型決定前綴策略：街道地址自動補 `花蓮縣花蓮市`（含鄉鎮名則只補 `花蓮縣`）；地標/景點走 Places Search 並加 location bias（花蓮市中心 + 80km radius 限定花蓮縣範圍）
- **Bug4 修復**：`HUALIEN_LANDMARKS` 擴充 13 個鄉鎮地名（吉安、壽豐、光復、豐濱、瑞穗、富里、秀林、萬榮、卓溪、玉里、鳳林等）
- **GPT prompt 改善**：新增花蓮地址格式規則區塊，指導 GPT 正確處理鄉鎮地址與純路名格式
- **診斷 log**：`handleNewOrder` 新增四條關鍵 log（轉錄原文、GPT提取結果、上車/目的地 Geocoding 結果）

### v1.9.1 (2026-03-08)
**修復：電話叫車端到端鏈路修復**

解決電話叫車系統三個關鍵問題：錄音靜音、WebSocket 秒斷、電話訂單被直接取消。

#### Android App 修復
- **WebSocket 秒斷修復**：移除三個畫面 (HomeScreen, SimplifiedDriverScreen, SeniorFriendlyHomeScreen) 的 `DisposableEffect` 中 `disconnectWebSocket()` 調用，避免畫面切換/Compose 重組時誤斷線
- **禁用 Socket.io 雙重重連**：關閉 Socket.io 內建重連 (`reconnection = false`)，僅使用自定義指數退避重連機制，避免兩套機制衝突
- **斷線原因診斷**：`WebSocketManager` 新增 `classifyDisconnectReason()` 分類斷線原因（server disconnect / client disconnect / ping timeout / transport close）
- **重連 mode 保持**：新增 `lastConnectionMode` 欄位，修復 `cleanupSocket()` 清除 `currentMode` 後無法重連的問題
- **ViewModel 連接狀態修正**：斷線時不再清除 `connectedDriverId`，讓自動重連機制正常工作；手動斷開時才清除

#### 伺服器端修復（需在 EC2 執行）
- **Asterisk 錄音品質**：`extensions_taxi.conf` 加 `VOLUME(RX)=3` 增強來電者音量 + `WaitForSilence` 調整
- **HT813 FXO 增益**：Rx Gain 0dB → +6dB
- **Whisper 幻覺偵測**：靜音錄音 → Whisper 產生幻覺文字 → 過濾已知幻覺模式
- **NO_DRIVERS 訂單保留**：電話訂單派單失敗時保持 PENDING 而非 CANCELLED
- **司機上線推送 PENDING 訂單**：`driver:online` 時檢查 30 分鐘內的 PENDING 電話訂單並重新派單
- **Socket.io heartbeat**：`pingInterval: 25000, pingTimeout: 20000`

### v1.9.0 (2026-02-23)
**新功能：電話叫車系統整合 (PHONE Entry)**

透過 HT813 VoIP 閘道器接市話電話，經 3CX PBX 錄音後自動語音轉文字、GPT 提取訂單欄位，進入後台統一派單系統。系統從單一 APP 來源擴展為多來源（PHONE/APP/LINE）統一訂單處理。

#### 系統架構
```
市話來電 → HT813 (FXO→SIP) → 3CX PBX (錄音+Webhook)
    → 後端 PhoneCallProcessor
        → Whisper STT（語音轉文字）
        → GPT-4o-mini（欄位提取 + 事件分類）
        → Google Places（地址→座標）
        → SmartDispatcherV2（派單）
    → 司機 App 收到帶標籤的訂單
```

#### 電話處理管線
```
RECEIVED → DOWNLOADING → TRANSCRIBING → PARSING → [事件判定] → DISPATCHING → COMPLETED
```

**事件分類**：
- `NEW_ORDER`：新訂單 → 建單並派單
- `URGE`：催單（同號碼 30 分鐘內有活動訂單）→ 通知司機
- `CANCEL`：取消 → 更新訂單狀態
- `CHANGE`：修改 → 更新訂單欄位

#### 訂單新增欄位
| 欄位 | 說明 | 預設值 |
|------|------|--------|
| `source` | 來源（PHONE/APP/LINE） | APP |
| `subsidyType` | 補貼類型（SENIOR_CARD/LOVE_CARD/PENDING/NONE） | NONE |
| `subsidyConfirmed` | 司機是否已確認實體卡片 | false |
| `subsidyAmount` | 實際補貼金額（元） | 0 |
| `petPresent` | 是否有寵物（YES/NO/UNKNOWN） | UNKNOWN |
| `petCarrier` | 是否有寵物籠（YES/NO/UNKNOWN） | UNKNOWN |
| `customerPhone` | 來電號碼 | null |
| `destinationConfirmed` | 司機已確認目的地 | false |

#### 司機能力過濾
| 能力 | 欄位 | 說明 |
|------|------|------|
| 敬老卡 | `can_senior_card` | 過濾無法刷敬老卡的司機 |
| 愛心卡 | `can_love_card` | 過濾無法刷愛心卡的司機 |
| 寵物 | `can_pet` | 過濾不接受寵物的司機 |

#### 愛心卡補貼流程

三個入口（App/電話/LINE）都支援愛心卡：

1. **乘客叫車時聲明**：App 端勾選「我有愛心卡」/ 電話端語音識別 / LINE bot
2. **派單過濾**：SmartDispatcherV2 只派給 `can_love_card = TRUE` 的司機
3. **司機到達確認**：`ArrivedAtPickup` 狀態顯示確認 UI，司機目視確認實體卡片
   - 「已確認卡片」→ `PATCH /orders/:orderId/subsidy { action: "CONFIRM" }`
   - 「乘客無卡」→ `PATCH /orders/:orderId/subsidy { action: "CANCEL" }` → 改一般計費
4. **結帳拆帳**：FareDialog 顯示跳表金額、愛心卡補助扣減、乘客實付金額
5. **補貼金額**：從 Server `FareConfigService` 動態載入（env: `LOVE_CARD_SUBSIDY_AMOUNT`，預設 NT$73）
6. **補貼上限**：不超過跳表金額（`min(subsidyAmount, meterAmount)`）

#### 訂單標籤 UI
- **來源標籤**：電話(橙色)/LINE(綠色)，APP 不顯示
- **補貼標籤**：敬老卡(紫色)/愛心卡(紅色)/待確認(黃色)
- **寵物標籤**：有籠(綠色)/無籠(橙色)/有寵物(黃色)/待確認(灰色)

標籤在 HomeScreen（普通版）和 SimplifiedDriverScreen（大字版）中均有顯示。APP 來源的愛心卡訂單也會顯示標籤。

#### 目的地確認流程
電話訂單的目的地由 GPT 從通話中提取，可能不準確。司機收到訂單後：
1. 看到「目的地待確認」提示
2. 點擊「確認目的地」按鈕
3. 透過 WebSocket `order:destination-confirm` 事件確認

#### 花蓮地標映射（GPT 提取用）
內建約 30 個花蓮常見地標的縮寫映射（火車站→花蓮火車站、東大門→東大門夜市、遠百→遠東百貨花蓮店、慈濟→慈濟醫院 等），提升地址辨識準確度。

#### 新增後端檔案
```
src/db/migrations/004-phone-order-tables.sql  # DB 遷移（orders/drivers 新欄位 + phone_calls 表）
src/api/phone-calls.ts                        # 電話 Webhook + 管理 API
src/services/PhoneCallProcessor.ts            # 核心處理管線
src/services/CallFieldExtractor.ts            # GPT 欄位提取 + 事件分類
```

#### 新增/修改 Android 檔案
```
ui/components/OrderTags.kt                    # 訂單標籤組件（新增）
domain/model/Order.kt                         # 新增 source/subsidy/pet 等欄位
data/remote/dto/PassengerDto.kt               # OrderDto 新增對應欄位
data/remote/WebSocketManager.kt               # 新增 order:urge 事件
viewmodel/HomeViewModel.kt                    # 催單監聽 + 目的地確認 + 語音播報增強
ui/screens/HomeScreen.kt                      # 標籤 + 電話號碼 + 目的地確認 UI
ui/screens/SimplifiedDriverScreen.kt          # 大字標籤 + 電話號碼顯示
```

#### 後端 API
```
POST /api/phone-calls/webhook        # 3CX CallCompleted Webhook
GET  /api/phone-calls                # 列出電話記錄（?status=FAILED）
GET  /api/phone-calls/:callId        # 查看處理狀態
POST /api/phone-calls/:callId/retry  # 手動重試失敗處理
```

---

### v1.8.5 (2026-01-26)
**修復：乘客端叫車後無「正在找司機」狀態提示**

#### 問題描述
乘客在語音優先介面（VoiceFirstPassengerScreen）選完上車地點、按下「在這裡叫車」按鈕後，畫面沒有任何狀態提示，看起來像沒有反應。用戶甚至還可以再次使用語音功能。

#### 根本原因
1. **`REQUESTING` 狀態未處理**：`statusMessage` 的 when 表達式中沒有處理 `OrderStatus.REQUESTING` 狀態
2. **`BOOKING` 狀態未處理**：`voiceAutoflowState == VoiceAutoflowState.BOOKING` 時也沒有對應的狀態文字
3. **底部操作區域缺少載入指示**：在叫車過程中，底部仍顯示語音按鈕，沒有顯示載入狀態

#### 解決方案
1. **添加 `REQUESTING` 和 `BOOKING` 狀態的文字提示**：顯示「正在為您叫車...」
2. **添加對應的狀態圖示**：顯示 `HourglassTop` 圖示
3. **底部操作區域顯示載入指示器**：在 `BOOKING` 狀態時顯示 CircularProgressIndicator

#### 修改檔案
```
ui/screens/passenger/VoiceFirstPassengerScreen.kt
  - statusMessage 添加 BOOKING/REQUESTING 分支
  - statusIcon 添加 BOOKING/REQUESTING 分支
  - BottomActionArea 添加 BOOKING 狀態的載入指示器
```

---

### v1.8.4 (2026-01-26)
**修復：司機端「結束行程」後 UI 卡頓問題**

#### 問題描述
司機在抵達目的地後點擊「結束行程」按鈕，UI 會出現卡頓現象。用戶必須等待一段時間或多次點擊才能進入車資結算頁面。

#### 根本原因
1. **狀態更新時序問題**：`SmartOrderManager.executeNextAction()` 會立即改變內部狀態為 `WaitingForPayment`，但不等待後端 API 響應結果
2. **按鈕鎖定時間不足**：`isProcessing` 只等待固定 1 秒就解除，但 API 可能需要更長時間
3. **狀態不同步**：`isProcessing` 與 `uiState.isLoading` 不同步，導致用戶可以在 API 未完成時再次點擊
4. **缺乏錯誤反饋**：API 失敗時沒有顯示錯誤提示

#### 解決方案
1. **分離動作判斷與狀態更新**：新增 `getNextAction()` 方法只返回需執行的動作，不改變狀態
2. **等待 API 完成**：按鈕點擊後等待 `uiState.isLoading` 變為 false（最長 10 秒）
3. **雙重鎖定機制**：結合 `isProcessing` 和 `uiState.isLoading` 防止重複點擊
4. **新增錯誤提示**：API 失敗時顯示 Toast 並語音播報

#### 技術實現
```kotlin
// SmartOrderManager.kt - 分離動作判斷與狀態更新
fun getNextAction(): NextAction {
    // 只返回動作類型，不改變內部狀態
    return when (val state = _orderState.value) {
        is SmartOrderState.OnTrip ->
            if (state.nearDestination) NextAction.TripEnded else NextAction.ShowDestination
        // ...
    }
}

fun confirmAction(action: NextAction) {
    // 在 API 成功後調用此方法更新狀態
    when (action) {
        NextAction.TripEnded -> endTrip()
        // ...
    }
}

// SimplifiedDriverScreen.kt - 等待 API 完成
SmartActionButton(
    isProcessing = isProcessing || uiState.isLoading, // 結合兩種載入狀態
    onClick = {
        if (isProcessing || uiState.isLoading) return@SmartActionButton

        scope.launch {
            isProcessing = true
            val nextAction = smartOrderManager.getNextAction()
            handleNextAction(...)

            // 等待 API 完成（最長 10 秒）
            while (uiState.isLoading && waitTime < 10000) {
                delay(100)
                waitTime += 100
            }
            isProcessing = false
        }
    }
)
```

#### 修改檔案
```
manager/SmartOrderManager.kt      # 新增 getNextAction() + confirmAction()
ui/screens/SimplifiedDriverScreen.kt  # 按鈕鎖定邏輯 + 錯誤提示
```

#### 狀態同步機制
狀態更新由 ViewModel 的 API 結果驅動：
1. 用戶點擊按鈕 → `getNextAction()` 返回動作類型
2. `handleNextAction()` 調用 ViewModel 方法（如 `endTrip()`）
3. API 成功 → `uiState.currentOrder` 更新（status = SETTLING）
4. `LaunchedEffect(uiState.currentOrder)` 觸發
5. `smartOrderManager.setOrder(order)` 被調用
6. `updateOrderState()` 根據 order.status 設置正確狀態

---

### v1.8.3 (2026-01-26)
**修復：系統大字體設定導致 UI 爆版問題**

#### 問題描述
老年人使用的手機通常會在系統設定中開啟「大字體」模式（fontScale = 1.3~1.5），
而本 App 本身已針對老年人設計大字體（WarmTypography 最大達 40sp），
兩者疊加後導致字體過大，造成所有介面嚴重爆版（文字溢出、按鈕重疊、佈局崩壞）。

#### 解決方案
在 Theme 層使用 `CompositionLocalProvider` 限制 `fontScale` 最大值為 1.0，
確保 App 內建的大字體設計不受系統設定影響。

#### 技術實現
```kotlin
// Theme.kt / WarmCompanionTheme.kt
private const val MAX_FONT_SCALE = 1.0f

val currentDensity = LocalDensity.current
val fontScaleLimited = Density(
    density = currentDensity.density,
    fontScale = minOf(currentDensity.fontScale, MAX_FONT_SCALE)
)

CompositionLocalProvider(LocalDensity provides fontScaleLimited) {
    MaterialTheme(...)
}
```

#### 修改檔案
```
ui/theme/Theme.kt                # HualienTaxiDriverTheme fontScale 限制
ui/theme/WarmCompanionTheme.kt   # WarmCompanionTheme fontScale 限制
```

#### 設計考量
- App 已針對老年人設計大字體，不需要系統再額外放大
- 限制 fontScale = 1.0 而非完全禁用（保留未來調整空間）
- 使用 Compose 原生方式（LocalDensity），避免 deprecated API

---

### v1.8.2 (2026-01-22)
**修復：登出功能不完整導致狀態殘留**

#### 問題描述
司機端和乘客端的登出功能不完整，登出後存在以下問題：
- WebSocket 連接未斷開，導致仍收到訊息
- Firebase Auth 未登出，重開 App 仍是登入狀態
- 角色選擇狀態未清除，無法回到角色選擇畫面

#### 修復內容

**司機端 (ProfileScreen.kt)**
```kotlin
// 修復前：只清除 FCM Token 和 DataStore
FcmTokenManager.clearTokenOnLogout(context, driverId)
dataStoreManager.clearLoginData()
onLogout()

// 修復後：完整清除所有狀態
1. WebSocketManager.cancelReconnect()  // 取消重連
2. WebSocketManager.disconnect()       // 斷開連接
3. FcmTokenManager.clearTokenOnLogout() // 清除 FCM Token
4. FirebaseAuth.signOut()              // 登出 Firebase
5. dataStoreManager.clearLoginData()   // 清除登入資料
6. roleManager.logout()                // 清除角色（回到選擇畫面）
```

**乘客端 (PassengerProfileScreen.kt)**
```kotlin
// 修復前：只清除 RoleManager
roleManager.logout()

// 修復後：完整清除所有狀態
1. WebSocketManager.cancelReconnect()  // 取消重連
2. WebSocketManager.disconnect()       // 斷開連接
3. FirebaseAuth.signOut()              // 登出 Firebase
4. roleManager.logout()                // 清除角色
```

#### 修改檔案
```
ui/screens/ProfileScreen.kt           # 司機端登出邏輯
ui/screens/passenger/PassengerProfileScreen.kt  # 乘客端登出邏輯
navigation/NavGraph.kt                # 新增 roleManager 參數
MainActivity.kt                       # 傳遞 roleManager 到 MainNavigation
```

---

### v1.8.1 (2026-01-20)
**新功能：司機到達上車點通知**

當司機到達上車點時，乘客會收到系統通知提醒，提升乘客體驗：

#### 功能特點
- **系統通知**：即使 App 在背景也能收到推播通知
- **震動提醒**：連續三次震動（500ms-200ms-500ms），確保乘客注意到
- **語音播報**：TTS 播報「XXX 已到達上車點，請準備上車」
- **重複防護**：30 秒內不會重複發送通知

#### 通知內容
```
標題：司機已到達！
內容：王小明已到達上車點，請準備上車
```

#### 技術實現
1. **新增通知頻道**
   - `passenger_arrival_notifications`：高優先級，震動 + 聲音 + 燈光
   - `passenger_order_status`：默認優先級，一般狀態更新

2. **觸發時機**
   - 司機端調用 `markArrived()` API
   - 後端更新訂單狀態為 `ARRIVED`
   - WebSocket 推送 `order:update` 事件到乘客端
   - `PassengerViewModel.updateOrderStatus()` 檢測到狀態變為 `ARRIVED`
   - 觸發系統通知 + TTS 語音播報

#### 新增檔案
```
utils/PassengerNotificationHelper.kt  # 乘客通知輔助類
```

#### 修改檔案
```
viewmodel/PassengerViewModel.kt  # 新增到達通知觸發邏輯
MainActivity.kt                  # 初始化乘客通知頻道
```

---

### v1.8.0 (2026-01-20)
**修復：乘客端取消叫車功能失效 + 新增歡迎語音**

#### 新功能：啟動歡迎語音

乘客端開啟 App 時，會自動播放歡迎語音並開始錄音：

```
「大豐你好，哪裡搭車呢？」
```

播放完成後自動開始錄音，等待用戶說出目的地。

**實作**：
- `PassengerViewModel.playWelcomeGreeting()` - 播放歡迎語
- 使用 `hasPlayedWelcome` 標記確保只播放一次
- 播放完成後自動呼叫 `startVoiceRecording()`

**修改檔案**：
- `viewmodel/PassengerViewModel.kt` - 新增 `playWelcomeGreeting()`
- `ui/screens/passenger/VoiceFirstPassengerScreen.kt` - 初始化時呼叫

#### 修復：乘客端取消叫車功能失效

**問題**：乘客按下「取消叫車」按鈕沒有反應。

**根因**：`cancelOrder()` 使用函數開頭捕獲的舊 state，導致狀態更新被覆蓋。

**修復**：將 `state.copy(...)` 改為 `_uiState.value.copy(...)`

**修改檔案**：`viewmodel/PassengerViewModel.kt`

---

### v1.7.9 (2026-01-20)
**修復：跳錶車資尾數 + 確認目的地增加派車時間 + 火車站特殊流程**

#### 修復 1：跳錶車資尾數只能是 0 或 5

**問題**：車資計算結果會出現 119、143 這種不合理的尾數（實際跳錶只會出現 0 或 5）。

**修復**：改用跳錶次數計算法
```kotlin
跳錶次數 = ceil((距離公尺 - 1250) / 200)
里程費 = 跳錶次數 × 5 元
夜間加成 = roundToNearest5(日間車資 × 20%)
```

**修改檔案**：`utils/FareCalculator.kt`

#### 新功能：費率可從 Server 動態調整

費率現在可以透過 Server 的 `.env` 環境變數統一管理，Android 端會在啟動時自動載入。

**Server 端配置**（`/var/www/taxiServer/.env`）：
```bash
FARE_BASE_PRICE=100           # 起跳價
FARE_BASE_DISTANCE_METERS=1250  # 起跳距離
FARE_JUMP_DISTANCE_METERS=200   # 每跳距離
FARE_JUMP_PRICE=5             # 每跳價格
FARE_NIGHT_SURCHARGE_RATE=0.2   # 夜間加成比例
FARE_NIGHT_START_HOUR=23      # 夜間開始時間
FARE_NIGHT_END_HOUR=6         # 夜間結束時間
```

**API 端點**：
- `GET /api/config/fare` - 取得費率配置
- `POST /api/config/fare/calculate` - 測試車資計算

**新增檔案**：
- Server: `src/services/FareConfigService.ts`, `src/api/config.ts`
- Android: `data/remote/dto/FareConfigDto.kt`

**修改檔案**：
- `utils/FareCalculator.kt` - 新增 `loadConfigFromServer()`
- `MainActivity.kt` - 啟動時載入費率配置
- `data/remote/ApiService.kt` - 新增 `getFareConfig()` API

#### 修復 2：確認目的地語音增加派車預估時間

**問題**：確認目的地時沒有告知乘客大概要等多久。

**修改前**：「去火車站，約 145 元，對嗎？」

**修改後**：「去火車站，約 145 元，派車約 3 分鐘，可以等嗎？」

**實作**：
- 新增 `getEstimatedPickupTime()` 函數
- 從 `nearbyDrivers` 找最近司機，計算距離
- 假設平均車速 30 km/h (500m/分鐘) 估算 ETA
- 無司機資訊時預設「派車約 3 分鐘」

#### 新功能：火車站趕車特殊流程

**業務需求**：乘客去火車站時，如果是趕火車且 30 分鐘內要到車站，需要確認附近有司機才能派車。

**流程**：
```
用戶說「去火車站」
    ↓
系統問「趕火車是幾點的？」
    ↓
用戶語音回答時間（如「十點半」）
    ↓
系統解析時間，計算距離發車還有多久
    ↓
如果 ≤30 分鐘且附近沒車 → 「抱歉，目前附近沒有司機，改天再為您服務」
否則 → 繼續正常叫車流程
```

**實作**：
- 新增 `VoiceAutoflowState.ASKING_TRAIN_TIME` 狀態
- `setPendingDestination()` 檢測目的地是否為火車站
- `askTrainTime()` 詢問火車時間
- `handleTrainTimeResponse()` 處理用戶回覆
- `parseTrainTime()` 解析中文時間（支援「十點半」「下午三點」等格式）
- UI 顯示火車圖示 🚂

**修改檔案**：
- `viewmodel/PassengerViewModel.kt` - 火車站流程邏輯
- `ui/screens/passenger/VoiceFirstPassengerScreen.kt` - UI 狀態顯示

---

### v1.7.8 (2026-01-20)
**優化：乘客端地點搜尋 + 語音播報簡化**

#### 修復 1：地點搜尋限制花蓮地區
**問題**：搜尋地點時需要加「花蓮」前綴才能找到想去的地方。

**修復**：
```
service/PlacesApiService.kt:73-77
  - 新增 setCountries(listOf("TW")) 限制在台灣
  - 改用 setLocationRestriction(HUALIEN_BOUNDS) 嚴格限制搜尋範圍
```

#### 修復 2：確認目的地語音播報簡化
**問題**：確認目的地時語音播報太冗長。

**修改前**：「您是要去「火車站」嗎？距離約 3.2 公里，預估車資 145 元。請說「對」確認，或說「不對」重選」

**修改後**：「去火車站，約 145 元，對嗎？」

**修復**：
```
viewmodel/PassengerViewModel.kt:1479-1486, 1514-1516
  - 簡化語音播報文字，更簡潔自然
```

---

### v1.7.7 (2026-01-02)
**修復：訂單完成後重複出現 + 訂單被接走事件處理**

#### 修復 1：訂單完成後重複出現
**問題**：司機完成訂單（提交車資）後，重開 App 還會再次出現同一筆訂單。

**根因**：`WebSocketManager.cleanupSocket()` 沒有清除 StateFlow 中的舊訂單數據，導致重連時舊訂單仍然殘留。

**修復**：
1. **清除 StateFlow 數據**（核心修復）
   ```
   WebSocketManager.kt:421-428
   - cleanupSocket() 現在會清除所有 StateFlow：
     _orderOffer, _orderStatusUpdate, _batchTimeout,
     _passengerOrderUpdate, _driverLocation, _voiceChatMessage
   ```

2. **連接前清除舊訂單**
   ```
   HomeViewModel.kt:263-265
   - connectWebSocket() 在連接前先清除 currentOrder
   ```

3. **防止乘客重複叫車**（後端）
   ```
   passengers.ts:117-135
   - 檢查乘客是否已有進行中訂單，若有返回 409 錯誤
   ```

4. **忽略已完成/取消訂單推送**
   ```
   HomeViewModel.kt:153-167
   - 收到訂單推送時，忽略 DONE/CANCELLED 狀態的訂單
   - 如果已有進行中訂單，忽略新推送
   ```

#### 修復 2：訂單被其他司機接走事件
**問題**：當訂單被其他司機接走時，當前司機界面沒有正確清除訂單。

**修復**：
```
WebSocketManager.kt:226-249
- 新增 on("order:taken") 事件監聽
- 收到事件時清除對應的 orderOffer
```

---

### v1.7.6 (2026-01-02)
**修復：司機端與乘客端預估車資不一致**

**問題**：乘客端顯示的預估車資與司機端收到的車資不同（例如花蓮火車站→七星潭差距達 100 元）。

**根因**：
1. **距離計算方式不同**：
   - 乘客端：Google Directions API（**道路距離**）
   - 後端：Haversine 公式（**直線距離**）
2. **計算公式參數不同**（已在 v1.7.5 統一）

**修復**：乘客端建單時傳遞道路距離和車資給後端
```
data/remote/dto/PassengerDto.kt (RideRequest)
  + tripDistanceMeters: Int?  // Google Directions API 計算的道路距離
  + estimatedFare: Int?       // 乘客端計算的車資

data/repository/PassengerRepository.kt
  - requestRide() 新增 tripDistanceMeters, estimatedFare 參數

viewmodel/PassengerViewModel.kt:540-541
  - 建單時傳遞 state.routeInfo?.distanceMeters 和 state.fareEstimate?.totalFare

後端 passengers.ts:147-160
  - 優先使用乘客端傳入的車資（clientEstimatedFare）
  - 若無，才 fallback 到直線距離計算
```

**結果**：司機端和乘客端現在顯示完全一致的車資（基於實際道路距離）

---

### v1.7.5 (2026-01-02)
**修復：乘客端電話按鈕不顯示 + 對話泡泡爆版 + 里程統計為 0**

#### 修復 1：電話按鈕不顯示

**問題**：乘客端在司機接單後，從語音模式切換到地圖模式再切回來，電話按鈕才會出現。

**根因**：後端 `orders.ts` 的 accept 端點在發送 `order:update` WebSocket 事件時，沒有包含 `driverPhone` 欄位。SQL 查詢只 JOIN 了 passengers 表，沒有 JOIN drivers 表。

**修復**：
```
後端：
  src/api/orders.ts:313-332 - SQL 查詢增加 JOIN drivers 表
  - 新增 driverPhone 到 orderUpdate 物件
```

#### 修復 2：對話泡泡爆版

**問題**：乘客端語音優先介面的狀態文字使用 `displayMedium` 字體，當文字過長時會超出邊界。

**修復**：
```
ui/screens/passenger/VoiceFirstPassengerScreen.kt:284
  - 字體從 WarmTypography.displayMedium 改為 WarmTypography.headlineLarge
  - 新增 maxLines = 4 限制最大行數
```

#### 修復 3：今日統計里程一直是 0.0km

**問題**：司機端「今日統計」的里程累計一直顯示 0.0 km。

**根因**：`submitFare` 使用 `currentOrder?.tripDistance` 計算距離，但 `tripDistance` 是預估距離，只有乘客指定目的地時才有值。若乘客沒指定目的地，距離就是 null。

**修復**：用跳表金額反推實際距離
```
viewmodel/HomeViewModel.kt:512-522
  - 若有 tripDistance 則使用預估距離
  - 若無，則用跳表金額反推：
    - 花蓮費率：起步100元/1.25km，續程5元/200m
    - 距離(km) = 1.25 + max(0, (meterAmount - 100) / 5) * 0.2
```

---

### v1.7.4 (2026-01-02)
**修復：司機端訂單缺少預估車資 + 今日統計顯示**

#### 修復 1：訂單缺少預估車資

**問題**：司機端收到訂單時，UI 和語音播報都沒有顯示預估車資。

**根因**：後端 `passengers.ts` 回退到舊派單器時，沒有傳遞 `estimatedFare` 參數。

**修復**：
```
後端：
  src/api/passengers.ts:238 - 回退邏輯添加 estimatedFare 參數
  src/services/OrderDispatcher.ts:197 - 日誌輸出增加車資顯示

Android：
  data/remote/WebSocketManager.kt:198-200 - 增加 estimatedFare 調試日誌
```

#### 修復 2：今日統計一直顯示 0 0 0

**問題**：司機主畫面的「今日統計」區塊固定顯示 0 訂單、NT$ 0 收入、0 km 里程。

**根因**：
1. `TodayStatsCard` 組件中的數值是硬編碼的 "0"
2. `submitFare` 沒有傳遞 `distance` 和 `duration` 給後端，導致 `actual_distance_km` 為 null

**修復**：
```
viewmodel/HomeViewModel.kt
  - HomeUiState 新增：todayOrderCount, todayEarnings, todayDistance
  - 新增 loadTodayStats(driverId) 方法
  - submitFare() 現在會傳遞 tripDistance 和計算後的 duration
  - 車資提交成功後自動刷新今日統計

ui/screens/HomeScreen.kt
  - TodayStatsCard 改為接收參數並顯示實際數據
  - 初始化時調用 loadTodayStats()
```

#### 部署步驟
1. 重啟後端服務：`cd ~/HualienTaxiServer && pnpm dev`
2. 重新編譯 Android App

---

### v1.7.3 (2026-01-01)
**司機端預估車資顯示 + Bug 修復**

#### 新增功能：司機端顯示預估車資

當司機收到新訂單時：
1. **UI 顯示**：訂單卡片新增「💰 車資」欄位，顯示預估車資
2. **語音播報**：增強播報內容，包含到客人距離、行程距離、預估車資

語音播報示例：
```
「您有新訂單，5分鐘車程，從花蓮火車站到遠百，
 行程約 3.2 公里，預估車資 145 元。
 說「接」接單，說「不要」拒絕」
```

修改檔案：
```
ui/screens/HomeScreen.kt            # 訂單卡片新增車資欄位
ui/screens/SimplifiedDriverScreen.kt # 簡化版新增車資欄位
ui/screens/SeniorFriendlyHomeScreen.kt # 長輩模式新增車資欄位（大字體）
viewmodel/HomeViewModel.kt          # announceNewOrder() 增強播報內容
```

#### Bug 修復：收入統計頁面崩潰問題

問題描述：司機端點擊「收入」選單時 App 會直接崩潰

根因分析：
- `EarningsOrder.completedAt` 是 ISO 8601 格式字串（如 `2026-01-01T23:56:00.000Z`）
- 原代碼使用已棄用的 `Date(String)` 構造函數，無法解析此格式

修復方案：使用 `SimpleDateFormat` 正確解析 ISO 8601 格式

修改檔案：
```
ui/screens/EarningsScreen.kt
  - TodayOrderCard(): 修正日期解析邏輯，加入 try-catch 防護
```

---

### v1.7.2 (2026-01-01)
**完善 ETA 廣播 + 後端實時位置轉發 + 確認目的地顯示車資**

#### 新增功能：確認目的地時顯示預估車資

在語音模式中，當用戶說出目的地後，系統會：
1. 自動計算從當前位置到目的地的路線和車資
2. 語音播報：「您是要去 XX 嗎？距離約 X 公里，預估車資 XX 元」
3. 同時在畫面顯示車資資訊

示例：
```
系統：「您是要去花蓮火車站嗎？
       距離約 3.2 公里
       預估車資 145 元」
```

#### 修改檔案
```
viewmodel/PassengerViewModel.kt
  - setPendingDestination(): 新增路線計算和車資估算
  - speakSimpleConfirmation(): 備援方案（無法計算車資時）

ui/screens/passenger/VoiceFirstPassengerScreen.kt
  - statusMessage: 確認狀態時顯示車資資訊
```

#### 後端更新（司機位置轉發）

完善 ETA 廣播系統，後端現在會將司機位置實時轉發給訂單乘客：

- 新增 `calculateDistance()` 函數（Haversine 公式計算兩點距離）
- `driver:location` 事件處理：查詢活躍訂單，轉發位置給對應乘客
- 發送資料包含：`orderId`, `distanceToPickup`, `etaMinutes`

#### 其他修改檔案
```
後端：
  HualienTaxiServer/src/index.ts  # 新增 calculateDistance + 位置轉發邏輯

Android：
  data/remote/WebSocketManager.kt     # DriverLocationInfo 新增欄位
  viewmodel/PassengerViewModel.kt     # 優先使用後端 ETA
```

---

### v1.7.1 (2026-01-01)
**ETA 自動廣播**

新增司機到達時間自動語音播報，讓看不到地圖的用戶也能掌握司機動態：

#### 三次廣播時機
1. **司機接單時** - 「王司機已接單，預計 5 分鐘後到達」
2. **距離減半時** - 「王司機還有大約 3 分鐘到達」
3. **剩餘 1 公里** - 「王司機快到了，還有不到一公里」

#### 技術實現
- 使用 Haversine 公式計算司機到上車點的距離
- 監聽 WebSocket 的 `driver:location` 事件實時更新
- 優先使用後端提供的 ETA（`etaToPickup`）
- 備援：根據距離估算（假設 30 km/h）

#### 修改檔案
```
viewmodel/PassengerViewModel.kt  # 新增 ETA 追蹤和廣播邏輯
domain/model/Order.kt            # 使用 etaToPickup、distanceToPickup 欄位
```

---

### v1.7.0 (2026-01-01)
**語音對講機功能**

新增司機與乘客之間的語音訊息功能，類似對講機的異步語音通訊：

#### 功能特點
- **按住說話**：按住按鈕錄音，放開後自動發送
- **語音轉文字**：使用 Whisper API 將語音轉為文字
- **TTS 播報**：接收方透過 TTS 自動播報訊息
- **聊天記錄**：顯示聊天氣泡，保留對話歷史
- **未讀提示**：面板關閉時顯示未讀訊息數量

#### 新增檔案
```
data/remote/dto/VoiceChatDto.kt      # 語音對講訊息資料模型
utils/VoiceChatManager.kt            # 對講核心邏輯
ui/components/VoiceChatBubble.kt     # 聊天氣泡 UI 組件
```

#### 修改檔案
```
data/remote/WebSocketManager.kt      # 新增 voice:message 事件
viewmodel/PassengerViewModel.kt      # 整合乘客端對講功能
viewmodel/HomeViewModel.kt           # 整合司機端對講功能
ui/screens/passenger/PassengerHomeScreen.kt  # 乘客端對講 UI
ui/screens/passenger/VoiceFirstPassengerScreen.kt  # 語音模式對講 UI
ui/screens/HomeScreen.kt             # 司機端對講 UI
ui/screens/SimplifiedDriverScreen.kt # 簡化版司機端對講 UI
```

#### 使用流程
```
發送方：按住說話 → 錄音 → 上傳 Whisper API 轉文字 → WebSocket 推送文字
接收方：收到文字 → TTS 播報 → 顯示聊天氣泡
```

#### 後端需求
- 新增 `voice:message` WebSocket 事件轉發邏輯

---

### v1.6.0 (2025-12-31)
**AI 自動接單 + 熱區配額系統**

#### 司機端 - AI 自動接單
- 新增 `AutoAcceptSettingsScreen.kt` - AI 自動接單設定介面
- 新增 `AutoAcceptViewModel.kt` - 自動接單狀態管理
- 新增 `AutoAcceptDto.kt` - 自動接單 API 資料模型
- 設定項目：
  - 啟用/停用自動接單
  - 智慧模式（基於 ML 預測）
  - 自動接單分數閾值（50-100）
  - 最大接送距離、最低車資、最短行程
  - 每日上限、冷卻時間、連續接單上限
  - 啟用時段設定

#### 乘客端 - 熱區排隊
- 熱區配額滿時自動進入排隊
- 顯示排隊位置和預估等待時間
- 動態加價提示（配額使用 80% 以上）

#### 導航更新
- `ProfileScreen` 新增「AI 自動接單」和「無障礙設定」入口
- `NavGraph` 新增 `AutoAcceptSettings` 和 `AccessibilitySettings` 路由

#### 新增 API
```kotlin
// 自動接單設定
GET  /api/drivers/:driverId/auto-accept-settings
PUT  /api/drivers/:driverId/auto-accept-settings
GET  /api/drivers/:driverId/auto-accept-stats
```

---

### v1.5.0 (2025-12-31)
**語音優先乘客介面**

全新設計的乘客端介面，以語音互動為核心：

**設計理念**：
- 語音為主要操作方式，觸控為備選（不方便說話時可用）
- 簡潔清晰的狀態顯示
- 適合老年人使用：高對比度、大字體、超大按鈕

**UI 架構**：
```
┌─────────────────────────────────────┐
│  [地圖] 附近 3 位司機在線  [設置]    │  ← 頂部工具列
│                                     │
│              🎙️                     │  ← 狀態圖示
│                                     │
│    ╭─────────────────────────╮      │
│    │  說出您想去的地方吧     │      │  ← 狀態文字
│    ╰─────────────────────────╯      │
│                                     │
│           [ 🎤 按住說話 ]           │  ← 超大語音按鈕
│                                     │
│         [ 在這裡叫車 ]              │  ← 快速叫車（觸控備選）
└─────────────────────────────────────┘
```

**關鍵檔案**：
- `ui/screens/passenger/VoiceFirstPassengerScreen.kt` - 主介面
- `ui/components/XiaoJuCharacter.kt` - 語音波形指示器
- `ui/theme/WarmCompanionTheme.kt` - 暖心陪伴風主題
- `navigation/PassengerNavGraph.kt` - 導航整合

**模式切換**：
- 預設進入語音優先介面
- 點擊左上角「地圖」按鈕 → 切換到傳統地圖模式
- 兩種模式分工明確：語音操作用語音模式，地圖操作用傳統模式

---

### v1.4.2 (2025-12-31)
**語音叫車自動化流程**

完整的語音自動叫車流程，從說出目的地到自動派單，一氣呵成：

**完整流程**：
1. 用戶說「去中原大學」→ 系統自動搜尋
2. 語音詢問「您是要去中原大學嗎？」→ 用戶說「對」確認
3. 顯示 Uber 風格地圖選點 UI → 用戶拖曳選擇上車點
4. 點擊「在這裡上車」→ 語音播報「正在為您叫車」→ 自動派單

**技術細節**：
- Server 端新增 CONFIRM/REJECT 意圖識別（WhisperService.ts）
- Android 端新增 `VoiceAutoflowState` 狀態機
- 新增 `PickupMapSelector.kt` Uber 風格上車點選擇器
  - 全螢幕地圖 + 中心固定標記
  - 拖曳時自動反向地理編碼
  - 目的地卡片顯示
- `PassengerViewModel` 新增語音自動流程方法：
  - `searchAndAutoSelectDestination()` - 街道地址走 Geocoder、地標走 Places API
  - `confirmDestination()` / `rejectDestination()` - 確認/拒絕目的地
  - `confirmPickupAndBook()` - 確認上車點並自動叫車
- 視覺反饋：搜尋中 / 等待確認 卡片提示

### v1.4.1 (2025-12-12)
**乘客端 Whisper 語音叫車功能**
- 乘客可用語音叫車：「去火車站」「去太魯閣」
- 支援 6 種乘客語音指令（叫車、設目的地、設上車點、取消、聯絡司機、查狀態）
- GPT-4o-mini 花蓮在地景點識別
- Server 端新增 `/api/whisper/transcribe-passenger` 端點
- Android 端新增：
  - `PassengerVoiceCommandHandler.kt`
  - `PassengerVoiceCommandCard` / `PassengerFloatingVoiceButton` 組件
  - `PassengerVoiceAction` / `PassengerVoiceCommand` DTO
  - `PassengerViewModel` 語音功能整合

### v1.4.0 (2025-12-12)
**Whisper 語音助理系統（司機端）**
- OpenAI Whisper API 語音轉文字
- GPT-4o-mini 自然語言意圖解析
- VAD（Voice Activity Detection）智能截斷
- 支援 9 種語音指令（接單、拒單、到達、出發、結束等）
- 低延遲設計（~1-1.5 秒端到端）
- 月成本約 $6 USD
- Server 端：WhisperService.ts、whisper.ts API
- Android 端：VoiceRecorderService、VoiceCommandHandler、VoiceCommandButton

### v1.3.0 (2025-12-12)
**智能派單系統 V2 大更新**
- 分層派單引擎（SmartDispatcherV2）：每批 3 位司機，20 秒超時
- TensorFlow.js ML 拒單預測模型
- 混合 ETA 策略（< 3km 估算，≥ 3km Google API）
- 六維度司機評分系統
- 強制拒單原因選擇（TOO_FAR/LOW_FARE/UNWANTED_AREA/OFF_DUTY/OTHER）
- 派單監控 API（統計/行為模式/拒單分析）
- Android 端新增：RejectOrderDialog、batch-timeout 事件處理
- Order 資料模型新增：batchNumber、estimatedFare、googleEtaSeconds、responseDeadline

### v1.3.1 (2026-03-11)
**1+1 疊單 Android 端承接**
- 新增 `QUEUED` 訂單狀態，支援「當前單 + 下一單」模型
- Order / OrderDto 新增：`queuePosition`、`queuedAfterOrderId`、`predictedHandoverAt`、`assignmentMode`
- `HomeViewModel` 改為同時維護 `currentOrder` 與 `queuedOrder`
- 司機首頁新增「下一單」卡片，清楚區分主單與預掛單
- 主單完成或取消時，若下一單仍有效，App 會本地升單並維持載客狀態
- 後端 `1+1` 派單規則、重算與重派仍需另外實作

### v1.2.9 (2025-12-08)
- 乘客端評價司機功能
- 雙向評價系統完成

### v1.2.8 (2025-12-08)
- 編譯錯誤修復（DataStoreManager、WebSocketManager）
- Material Icons Extended 依賴

### v1.2.7 (2025-11-25)
- 訂單距離和預估時間顯示
- 三個司機界面同步更新

### v1.2.6 (2025-11-25)
- OrderDispatcher 智能訂單派發系統
- 自動重新派單、超時機制

### v1.2.5 (2025-11-25)
- 評分系統整合
- FCM Token 完整同步
- 乘客設定和評價頁面

### v1.2.4 (2025-11-25)
- EarningsScreen 收入統計
- 車資拍照功能 (CameraX)
- 評分系統 Server API

### v1.2.3 (2025-11-25)
- FCM 推播通知系統
- WebSocket 智能重連（指數退避）
- 電池優化（動態定位頻率）

### v1.2.2 (2025-11-25)
- WebSocket 連接優化
- Token 自動刷新機制
- Geolocation API 用量監控

### v1.2.0 (2025-11-25)
- Firebase Phone Authentication（全面改用簡訊驗證）
- 移除密碼登入

### v1.1.0 (2025-11-10)
- Google Maps API 完整整合
- Places API 地址搜尋
- Directions API 路線計算
- Distance Matrix API 司機匹配
- Geolocation API 混合定位

### v1.0.0 (2025-10-21)
- 雙模式架構（司機+乘客）
- 乘客端叫車功能
- WebSocket 即時訂單
- 基礎 UI 完成

---

## 相關資源

- **後端專案**：`~/Desktop/HualienTaxiServer`
- **詳細開發指南**：`CLAUDE.md`
- **Google Maps 設定**：`GOOGLE_MAPS_SETUP.md`

---

## 技術支援

- **Android 最低版本**：8.0 (API 26)
- **目標版本**：Android 15 (API 36)
- **建議裝置**：4GB+ RAM、支援 Google Play 服務
- **網路需求**：4G/5G 或 Wi-Fi
