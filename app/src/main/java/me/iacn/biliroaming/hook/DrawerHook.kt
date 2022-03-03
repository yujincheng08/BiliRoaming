package me.iacn.biliroaming.hook

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class DrawerHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private var drawerLayout: Any? = null
    private var navView: View? = null

    override fun startHook() {
        if (!sPrefs.getBoolean("drawer", false)) return

        Log.d("startHook: DrawerHook")

        instance.kanbanCallback?.new(null)?.callMethod(instance.kanbanCallback(), null)

        instance.mainActivityClass?.hookAfterMethod("onCreate", Bundle::class.java) { param ->
            val self = param.thisObject as Activity
            val view = self.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            (view.parent as ViewGroup).removeViewInLayout(view)
            drawerLayout = instance.drawerLayoutClass?.new(self)
            drawerLayout?.callMethod("addView", view, 0, view.layoutParams)

            val homeFragment = instance.homeUserCenterClass?.new()
            val fragmentManager = self.callMethod("getSupportFragmentManager")
            fragmentManager?.callMethod("beginTransaction")?.callMethod("add", homeFragment, "home")
                ?.callMethod("commit")
            fragmentManager?.callMethod("executePendingTransactions")

            self.setContentView(drawerLayout as View)
        }

        val createHooker: Hooker = { param ->
            val self = param.thisObject as Activity
            val fragmentManager = self.callMethod("getSupportFragmentManager")
            navView = fragmentManager?.callMethod("findFragmentByTag", "home")
                ?.callMethodAs<View>("getView")

            val layoutParams = instance.drawerLayoutParamsClass?.new(
                ViewGroup.MarginLayoutParams(
                    ViewGroup.MarginLayoutParams.MATCH_PARENT,
                    ViewGroup.MarginLayoutParams.MATCH_PARENT
                )
            )
            layoutParams?.javaClass?.fields?.get(0)?.set(layoutParams, Gravity.START)
            navView?.parent ?: drawerLayout?.callMethod("addView", navView, 1, layoutParams)
        }

        instance.mainActivityClass?.runCatching {
            getDeclaredMethod(
                "onPostCreate",
                Bundle::class.java
            )
        }?.onSuccess { it.hookAfterMethod(createHooker) }

        instance.mainActivityClass?.runCatching { getDeclaredMethod("onStart") }
            ?.onSuccess { it.hookAfterMethod(createHooker) }

        instance.mainActivityClass?.replaceMethod("onBackPressed") { param ->
            try {
                if (drawerLayout?.callMethodAs<Boolean>(instance.isDrawerOpen(), navView) == true) {
                    drawerLayout?.callMethod(instance.closeDrawer(), navView, true)
                } else {
                    param.invokeOriginalMethod()
                }
            } catch (e: Throwable) {
                param.invokeOriginalMethod()
            }
        }

        "tv.danmaku.bili.ui.main2.basic.BaseMainFrameFragment".hookAfterMethod(
            mClassLoader,
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        ) { param ->
            val id = getId("avatar_layout")
            (param.args[0] as View).findViewById<View>(id)?.setOnClickListener {
                try {
                    drawerLayout?.callMethod(instance.openDrawer(), navView, true)
                } catch (e: Throwable) {
                    Log.e(e)
                }
            }
        }

    }
}
