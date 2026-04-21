# ============================
# 花蓮計程車 App - ProGuard Rules
# ============================

# 保留行號（方便 crash 除錯）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================
# Gson + Retrofit
# ============================
-keepattributes Signature
-keepattributes *Annotation*

# Gson：保留所有使用 @SerializedName 的欄位
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留所有 data class（domain model + DTO）用於 Gson 序列化
-keep class com.hualien.taxidriver.domain.model.** { *; }
-keep class com.hualien.taxidriver.data.remote.dto.** { *; }
-keep class com.hualien.taxidriver.service.*Response { *; }
-keep class com.hualien.taxidriver.service.*Request { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ============================
# Socket.io
# ============================
-keep class io.socket.** { *; }
-keep class io.socket.client.** { *; }
-keep class io.socket.engineio.** { *; }
-dontwarn io.socket.**

# ============================
# Google Maps / Places
# ============================
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.libraries.places.** { *; }
-dontwarn com.google.android.gms.**

# Google Maps API response models
-keep class com.hualien.taxidriver.service.GoogleMapsApiModels** { *; }
-keep class com.hualien.taxidriver.service.DirectionsApiService$* { *; }
-keep class com.hualien.taxidriver.service.DistanceMatrixApiService$* { *; }
-keep class com.hualien.taxidriver.service.GeolocationApiService$* { *; }
-keep class com.hualien.taxidriver.service.PlacesApiService$* { *; }

# ============================
# Firebase
# ============================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ============================
# Coil (圖片載入)
# ============================
-dontwarn coil3.**

# ============================
# Lottie (動畫)
# ============================
-dontwarn com.airbnb.android.lottie.**
-keep class com.airbnb.lottie.** { *; }
-keep class com.airbnb.lottie.parser.** { *; }
-keep class com.airbnb.lottie.model.** { *; }

# ============================
# Kotlin / Coroutines
# ============================
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# Kotlin serialization（如果有用到）
-keepattributes RuntimeVisibleAnnotations

# ============================
# Enum 保護
# ============================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
