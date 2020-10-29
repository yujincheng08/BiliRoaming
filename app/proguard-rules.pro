-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void *(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}

-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources {
    public void *(de.robv.android.xposed.callbacks.XC_InitPackageResources$InitPackageResourcesParam);
}

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}

# For Xpatch only
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherLoader { <fields>; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class me.iacn.biliroaming.MainActivity$Companion {
    boolean isModuleActive();
}