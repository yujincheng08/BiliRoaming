package me.iacn.biliroaming.reshook

import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam

abstract class BaseHook(val params: InitPackageResourcesParam) {
    abstract fun startHook()
}