-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage {
  public void *(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam);
}

-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources {
  public void *(de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam);
}

-keepclassmembers class me.iacn.biliroaming.MainActivity.Companion{
    boolean isModuleActive();
}