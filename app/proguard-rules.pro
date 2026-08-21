# === ZYPlayer ProGuard/R8 规则 ===

# --- 数据模型（Gson/ Room 需要用反射） ---
-keep class com.zyplayer.app.data.model.** { *; }
-keep class com.zyplayer.app.data.local.** { *; }

# --- Room 数据库 ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# --- Gson 序列化 ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- OkHttp / Retrofit ---
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }

# --- OkHttp 平台相关（防止 R8 移除平台代码） ---
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class org.conscrypt.** { *; }

# --- ExoPlayer / Media3 ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Coil 图片加载 ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- Kotlin 协程 ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- 保留 WebView 相关 ---
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, int);
}

# --- 保留 Application 和 Activity 入口 ---
-keep class com.zyplayer.app.App { *; }
-keep class com.zyplayer.app.MainActivity { *; }

# --- 保留枚举 ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- 保留日志（debug 可加，release 可去） ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}