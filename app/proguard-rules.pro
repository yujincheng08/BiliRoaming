-repackageclasses "biliroaming"

-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void *(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}

-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources {
    public void *(de.robv.android.xposed.callbacks.XC_InitPackageResources$InitPackageResourcesParam);
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

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-allowaccessmodification
-overloadaggressively

-keep class me.iacn.biliroaming.VideoExportDialog$VideoEntry {
    <fields>;
}

-keep class me.iacn.biliroaming.VideoExportDialog$VideoEntry$PageData {
    <fields>;
}

-keep class me.iacn.biliroaming.VideoExportDialog$VideoEntry$Ep {
    <fields>;
}