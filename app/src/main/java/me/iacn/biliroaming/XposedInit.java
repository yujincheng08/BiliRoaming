package me.iacn.biliroaming;

import net.androidwing.hotxposed.IHookerDispatcher;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
public class XposedInit implements IHookerDispatcher {
    @Override
    public void dispatch(LoadPackageParam loadPackageParam) {
        System.out.println("125551");
        System.out.println("158827ddd75");
    }
}