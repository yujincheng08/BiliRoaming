-repackageclasses "biliroaming"

-keep class me.iacn.biliroaming.XposedInit {
    <init>();
}

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}

-keepclasseswithmembers class me.iacn.biliroaming.utils.DexHelper {
 native <methods>;
 long token;
 java.lang.ClassLoader classLoader;
}

-keepattributes RuntimeVisible*Annotations

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class me.iacn.biliroaming.MainActivity$Companion {
    boolean isModuleActive();
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

-allowaccessmodification
-overloadaggressively
