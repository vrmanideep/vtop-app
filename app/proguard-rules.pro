# --- ATTRIBUTES & GLOBAL SAFETY ---
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile
-keepnames class com.vtop.** { *; }

# --- COMPOSE & UI ---
# OkHttp, Compose, and Room include their own consumer rules — no manual keep needed
-dontwarn androidx.compose.**
-keep public class com.vtop.utils.** { *; }
-keep public class com.vtop.ui.** { *; }
-keep public class com.vtop.logic.** { *; }
-keep public class com.vtop.assets.** { *; }
# GSON TypeToken fix — preserves generic signatures used by Vault
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keepattributes Signature
-keepattributes *Annotation*


# --- MODELS (GSON SAFETY) ---
-keep public class com.vtop.models.** {
    <fields>;
    <init>(...);
}

# --- NETWORK ---
-keep public class com.vtop.network.** { *; }
-keepclassmembers class com.vtop.network.VtopClient {
    private <fields>;
    private *** doLogin(...);
    private *** autoLogin(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**

# --- WORKMANAGER / STARTUP / GLANCE ---
-dontwarn androidx.work.**
-dontwarn androidx.room.**
-dontwarn androidx.startup.**
-dontwarn androidx.glance.**

-keep class androidx.work.impl.WorkDatabase_Impl { public <init>(...); }
-keep class androidx.work.WorkManagerInitializer { *; }
-keep public class * extends androidx.work.Worker
-keep public class * extends androidx.work.ListenableWorker

-keep class androidx.startup.InitializationProvider { public <init>(); }
-keep class * implements androidx.startup.Initializer { public <init>(); }

# --- WIDGET ---
-keep class com.vtop.widget.** { *; }
-keep class androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class com.vtop.widget.WidgetUpdater {
    void updateWidgetNow(android.content.Context);
}

# --- KOTLIN COROUTINES ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.**

# --- SUPPRESSION ---
-dontnote **
-ignorewarnings