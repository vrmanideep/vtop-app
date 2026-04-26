# --- 1. THE GOLDEN ANDROID RULES ---
# Prevents R8 from deleting background OS components used by Glance and WorkManager
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# --- 2. WIDGET & GLANCE LIBRARIES ---
-keep class com.vtop.widget.** { *; }
-keep class androidx.glance.** { *; }
-keep class androidx.glance.appwidget.** { *; }

# --- 3. WORKMANAGER & STARTUP ---
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }

# --- 4. MODELS & GSON SERIALIZATION ---
# Forces R8 to leave your data alone so Vault can read/write the JSON
-keep class com.vtop.models.** { *; }
-keep class com.vtop.ui.core.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class sun.misc.Unsafe { *; }

# --- 5. NETWORK & UTILS ---
-keep class com.vtop.network.** { *; }
-keep class com.vtop.logic.** { *; }
-keep class com.vtop.utils.** { *; }

# --- 6. ATTRIBUTES ---
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# --- 7. SUPPRESSION (Ignore false alarms) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-dontwarn androidx.compose.**
-dontwarn androidx.work.**
-dontwarn androidx.room.**
-dontwarn kotlinx.coroutines.**
-dontnote **