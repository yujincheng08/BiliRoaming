package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class StoryPlayerAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {


        val purifyTags = sPrefs.getStringSet("purify_story_video_ad_tags", emptySet()) ?: emptySet()
        if (purifyTags.isEmpty()) return

        Log.d("startHook: StoryPlayerAd, purifyTags: $purifyTags")

        instance.storyPagerPlayerClass?.hookBeforeMethod(
            instance.addVideo(), List::class.java) { params ->
                val storyDetailList = params.args[0] as? MutableList<*> ?: return@hookBeforeMethod
                val toRemove = mutableListOf<Any>()

                storyDetailList.forEach {
                    val storyDetail = it!!::class.java
                    val getCartIconInfoMethod = storyDetail.getDeclaredMethod("getCartIconInfo")
                    val isAdMethod = storyDetail.getDeclaredMethod("isAd")
                    getCartIconInfoMethod.isAccessible = true
                    isAdMethod.isAccessible = true

                    val isAd = isAdMethod.invoke(it) as? Boolean

                    var cartInfoText: String? = null
                    val cartIconInfo = getCartIconInfoMethod.invoke(it)
                    if (cartIconInfo != null) {
                        val cartClass = cartIconInfo.javaClass
                        val getEntryTextMethod = cartClass.getDeclaredMethod("getEntryText")
                        getEntryTextMethod.isAccessible = true
                        cartInfoText = getEntryTextMethod.invoke(cartIconInfo) as? String
                    }

                    val shouldRemove = when {
                        "ad" in purifyTags && isAd == true -> true
                        "short" in purifyTags && cartInfoText == "短剧" -> true
                        "shopping" in purifyTags && cartInfoText == "购物" -> true
                        "tv" in purifyTags && cartInfoText == "电视剧" -> true
                        "doc" in purifyTags && cartInfoText == "纪录片" -> true
                        "ent" in purifyTags && cartInfoText == "娱乐" -> true
                        "movie" in purifyTags && cartInfoText == "电影" -> true
                        "music" in purifyTags && cartInfoText == "音乐" -> true
                        "topic" in purifyTags && cartInfoText == "话题" -> true
                        else -> false
                    }
                    if (shouldRemove) {
                        toRemove.add(it)
                    }
                }

                storyDetailList.removeAll(toRemove.toSet())
                val blockedCount = sPrefs.getInt("purify_story_video_ad_blocked_count", 0)
                sPrefs.edit().putInt("purify_story_video_ad_blocked_count", blockedCount + toRemove.size).apply()
            }

    }


}
