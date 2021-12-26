-repackageclasses "biliroaming"

-keep class me.iacn.biliroaming.XposedInit {
    <init>();
}

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
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

-allowaccessmodification
-overloadaggressively
