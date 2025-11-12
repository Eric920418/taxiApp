# Google Maps API Key 設置指南

## 步驟1：前往Google Cloud Console
訪問：https://console.cloud.google.com/

## 步驟2：建立新專案或選擇現有專案
1. 點擊頂部的專案下拉選單
2. 點擊「新增專案」
3. 輸入專案名稱：`HualienTaxi`

## 步驟3：啟用Maps SDK for Android
1. 在左側選單選擇「API和服務」→「資料庫」
2. 搜尋「Maps SDK for Android」
3. 點擊並啟用

## 步驟4：建立API金鑰
1. 左側選單選擇「憑證」
2. 點擊「建立憑證」→「API金鑰」
3. 複製產生的API Key

## 步驟5：（可選）限制API Key
為了安全性，建議限制API Key：

1. 點擊剛建立的API Key
2. 應用程式限制 → 選擇「Android應用程式」
3. 新增套件名稱：`com.hualien.taxidriver`
4. 取得SHA-1憑證指紋：

```bash
cd /Users/eric/AndroidStudioProjects/HualienTaxiDriver
./gradlew signingReport
```

在輸出中找到「SHA-1」開頭的字串，複製並貼到Google Cloud Console中。

## 步驟6：更新AndroidManifest.xml

將取得的API Key替換到：
`app/src/main/AndroidManifest.xml` 第35行

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_REAL_API_KEY_HERE" />
```

## 步驟7：重新編譯並運行App

```bash
./gradlew clean
./gradlew installDebug
```

---

## 費用說明

- Google Maps SDK for Android 提供 **每月$200美元免費額度**
- 地圖載入：每1000次 $7美元
- 司機端App使用量極低，**基本不會超過免費額度**

---

## 測試用API Key（不建議生產使用）

如果只是開發測試，可以先不限制API Key，但記得上線前一定要加上限制！
