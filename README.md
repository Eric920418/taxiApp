# 花蓮計程車 - 雙模式 Android App

> **HualienTaxiDriver** - 司機端 + 乘客端統一應用程式
> 版本：v1.5.0-MVP | 更新日期：2025-12-31

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
| **語音優先介面（小橘）** | - | ✅ | **v1.5.0 新增** |

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
│   │       ├── VoiceFirstPassengerScreen.kt  # 語音優先介面（小橘）
│   │       ├── PassengerHomeScreen.kt        # 傳統地圖模式
│   │       └── ...
│   └── components/
│       ├── XiaoJuCharacter.kt        # 2D 動畫角色「小橘」
│       ├── PickupMapSelector.kt      # Uber 風格上車點選擇器
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
發送: driver:online, driver:location, order:accept, order:reject
接收: order:offer, order:cancelled, order:taken
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

### v1.5.0 (2025-12-31) - 當前版本
**語音優先乘客介面「小橘」**

全新設計的乘客端介面，以語音互動為核心，打破傳統表單式 UI：

**設計理念**：
- 「小橘」2D 動畫角色為中心，與用戶實時互動
- 語音為主要操作方式，觸控為備選（不方便說話時可用）
- 暖心陪伴風設計，適合老年人使用
- 高對比度、大字體、超大按鈕

**角色狀態動畫**：
| 狀態 | 視覺表現 | 觸發時機 |
|------|----------|----------|
| IDLE | 微笑、呼吸動畫 | 待機 |
| LISTENING | 耳朵豎起、眼睛放大 | 錄音中 |
| THINKING | 眨眼、頭部微轉 | 處理中 |
| SPEAKING | 嘴巴開合 | 語音播報 |
| HAPPY | 彈跳、發光 | 叫車成功 |
| WAITING | 緩慢脈動 | 等待司機 |

**UI 架構**：
```
┌─────────────────────────────────────┐
│  [地圖] 附近 3 位司機在線  [設置]    │  ← 頂部工具列
│                                     │
│           ╭──────────╮              │
│           │  (◕‿◕)  │              │  ← 小橘角色
│           │  您好！  │              │
│           ╰──────────╯              │
│                                     │
│    ╭─────────────────────────╮      │
│    │  說出您想去的地方吧     │      │  ← 對話氣泡
│    ╰─────────────────────────╯      │
│                                     │
│           [ 🎤 按住說話 ]           │  ← 超大語音按鈕
│                                     │
│         [ 在這裡叫車 ]              │  ← 快速叫車（觸控備選）
└─────────────────────────────────────┘
```

**關鍵檔案**：
- `ui/screens/passenger/VoiceFirstPassengerScreen.kt` - 主介面
- `ui/components/XiaoJuCharacter.kt` - 2D 動畫角色
- `ui/theme/WarmCompanionTheme.kt` - 暖心陪伴風主題
- `navigation/PassengerNavGraph.kt` - 導航整合

**模式切換**：
- 預設進入語音優先介面（小橘）
- 點擊左上角「地圖」按鈕 → 切換到傳統地圖模式
- 傳統地圖模式已純化：只提供地圖搜尋叫車功能，語音功能統一由小橘介面處理
- 兩種模式分工明確：語音操作找小橘，地圖操作用傳統模式

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
  - `searchAndAutoSelectDestination()` - 自動搜尋並選擇第一個結果
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
