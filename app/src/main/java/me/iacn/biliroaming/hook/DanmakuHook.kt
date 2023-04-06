package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import kotlin.math.abs
import kotlin.math.floor

class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {


    private var pakku: PakkuCore? = null

    override fun startHook() {
        if (!sPrefs.getBoolean("danmaku_filter", false)) {
            return
        }
        Log.d("StartHook: DanmakuHook")
        if (sPrefs.getBoolean("danmaku_filter_pakku_switch", false)) {
            val setting = sPrefs.getString("danmaku_filter_pakku_setting", "")
            pakku = PakkuCore(setting)
        }
        hookDanmaku()
    }

    private fun hookDanmaku() {
        "com.bapis.bilibili.community.service.dm.v1.DMMoss".findClass(mClassLoader).run {
            hookBeforeMethod(
                "dmSegMobile",
                "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq",
                "com.bilibili.lib.moss.api.MossResponseHandler"
            ) { param ->
                val handler = param.args[1]
                param.args[1] = Proxy.newProxyInstance(
                    handler.javaClass.classLoader,
                    arrayOf(instance.mossResponseHandlerClass)
                ) { _, method, args ->
                    if (method.name == "onNext") {
                        filterDanmaku(args[0])
                        method(handler, *args)
                    } else if (args == null) {
                        method(handler)
                    } else {
                        method(handler, *args)
                    }
                }
            }
            hookAfterMethod(
                "dmSegMobile", "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq"
            ) { methodHookParam ->
                filterDanmaku(methodHookParam.result)
            }
        }
    }

    private fun filterDanmaku(dmSegmentMobileReply: Any) {
        var danmuList: List<Any?>
        dmSegmentMobileReply.getObjectFieldOrNullAs<List<*>>("elems_").orEmpty().let {
            danmuList = it
            if (sPrefs.getBoolean("danmaku_filter_weight_switch", false)) {
                danmuList = filterByWeight(danmuList)
            }
            if (pakku != null) {
                danmuList = pakku!!.doFilter(danmuList)
            }
        }
        dmSegmentMobileReply.callMethod("clearElems")
        dmSegmentMobileReply.callMethod("addAllElems", danmuList)
    }

    private fun filterByWeight(danmuList: List<Any?>): List<Any?> {
        val resultDanmakuList = mutableListOf<Any>()
        val weightThreshold = sPrefs.getInt("danmaku_filter_weight_value", 0)
        for (danmakuElem in danmuList) {
            if (danmakuElem == null) {
                continue
            }
            val weight = danmakuElem.callMethodAs<Int>("getWeight")
            if (weight < weightThreshold) continue
            resultDanmakuList.add(danmakuElem)
        }
        return resultDanmakuList
    }
}


/**
 * Copyright (C)  2017-2023 @xmcp.
 *
 * This code is licensed under the GPL VERSION 3.
 * See LICENSE.txt for details.
 *
 * Modified by DeltaFlyer, 2023: Implement with Kotlin.
 */

class PakkuPeer {
    companion object {
        private fun toSubscript(n: Int): String {
            val subscripts = charArrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')
            return n.toString().map { subscripts[it.toString().toInt()] }.joinToString("")
        }

        private fun makeMark(txt: String, cnt: Int): String {
            val cntString = if (PakkuSetting.DANMU_SUBSCRIPT)
                "₍${toSubscript(cnt)}₎"
            else
                "[x$cnt]"
            return when (PakkuSetting.DANMU_MARK) {
                "suffix" -> "$txt${cntString}"
                "prefix" -> "${cntString}$txt"
                else -> txt
            }
        }


    }

    private val danmakuList: MutableList<PakkuDanmaku> = mutableListOf()

    fun addDanmaku(danmaku: PakkuDanmaku) {
        this.danmakuList.add(danmaku)
    }

    fun size(): Int {
        return danmakuList.size
    }

    fun buildRepresent(REPRESENTATIVE_PERCENT: Int?): Any {
        val baseDanmaku =
            if (REPRESENTATIVE_PERCENT != null) {
                danmakuList[floor((danmakuList.size * REPRESENTATIVE_PERCENT / 100).toDouble()).toInt()
                    .coerceAtMost(danmakuList.size - 1)]
            } else {
                danmakuList[0]
            }
        val baseElem = baseDanmaku.elem
        baseElem.callMethod("setContent", danmakuList.groupingBy { it.getContent() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key)
        baseElem.callMethod("setWeight", danmakuList.map { it.getWeight() }.average().toInt())
        baseElem.callMethod("setColor", danmakuList.groupingBy { it.getColor() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key)

        var mostMode = danmakuList.groupBy { it.mode }.maxByOrNull {
            it.value.size
        }?.key
        if (PakkuSetting.MODE_ELEVATION) {
            if (mostMode == 1) {
                mostMode = 5
            }
        }
        baseElem.callMethod("setMode", mostMode)
        if (danmakuList.size > PakkuSetting.MARK_THRESHOLD) {
            baseElem.callMethod("setContent", makeMark(baseDanmaku.getContent(), danmakuList.size))
        }

        return baseElem
    }
}

class PakkuDanmaku(danmakuElem: Any) {
    var progress: Int
    var elem: Any
    var repeated: Boolean
    var pool: Int
    var mode: Int
    private var _gram: IntArray? = null
    private var _content: String? = null

    fun getGram(): IntArray {
        return this._gram ?: (PakkuEditDistance.instance.gen2gramArray(this.getContent())).also {
            this._gram = it
        }
    }

    fun getContent(): String {
        return this._content ?: PakkuCore.instance.detaolu(this.elem.callMethodAs("getContent"))
            .also {
                this._content = it
            }
    }

    fun getWeight(): Int {
        return this.elem.callMethodAs("getWeight")
    }

    fun getColor(): Int {
        return this.elem.callMethodAs("getColor")
    }

    init {
        this.elem = danmakuElem
        this.repeated = false

        this.progress = danmakuElem.callMethodAs("getProgress")
        this.pool = danmakuElem.callMethodAs("getPool")
        this.mode = danmakuElem.callMethodAs("getMode")
    }
}

class TrimText {
    companion object {
        private const val fullWidthChars =
            "　１２３４５６７８９０！＠＃＄％＾＆＊（）－＝＿＋［］｛｝;＇:＂,．／＜＞？＼｜｀～ｑｗｅｒｔｙｕｉｏｐａｓｄｆｇｈｊｋｌｚｘｃｖｂｎｍＱＷＥＲＴＹＵＩＯＰＡＳＤＦＧＨＪＫＬＺＸＣＶＢＮＭ"
        private const val halfWidthChars =
            " 1234567890！@#\$%^&*()-=_+[]{}；'：\"，./<>？\\|`~qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM"

        private val width_re = Regex("[" + fullWidthChars.replace("/", "\\/") + "]")

        private val ending_chars_re =
            Regex("[" + ".。,，/?？!！…~～@^、+=-_♂♀ ".replace("/", "\\/") + "]+$")

        private val trim_space_re = Regex("[ 　]+")


        fun replaceWidth(input: StringBuilder): StringBuilder {
            input.replace(width_re) { matchResult ->
                halfWidthChars[fullWidthChars.indexOf(matchResult.value)].toString()
            }
            return input
        }

        fun replaceEndingChar(input: StringBuilder): StringBuilder {
            input.replace(ending_chars_re, "")
            return input
        }

        fun replaceSpace(input: StringBuilder): StringBuilder {
            input.replace(trim_space_re, " ")
            return input
        }
    }
}

class PakkuSetting {
    companion object {
        var MAX_DIST = 5
        var MAX_COSINE = 60
        var THRESHOLD = 20

        var FORCELIST =
            "[[\"^23{2,}\$\",\"233...\"],[\"^6{3,}\$\",\"666...\"],[\"^[fF]+\$\",\"FFF...\"],[\"^[hH]+\$\",\"hhh...\"]]"
        var WHITELIST = "[]"

        var PROC_POOL1 = false
        var PROC_TYPE4 = false
        var PROC_TYPE7 = false

        var TRIM_WIDTH = true
        var TRIM_SPACE = true
        var TRIM_ENDING = true

        var MODE_ELEVATION = false
        var REPRESENTATIVE_PERCENT = 20

        var MARK_THRESHOLD = 1
        var DANMU_MARK = "prefix"
        var HIDE_THRESHOLD = 0
        var DANMU_SUBSCRIPT = true

        fun parseStaticValuesFromJson(json: JSONObject) {
            val fields: Array<Field> = PakkuSetting::class.java.declaredFields
            for (field in fields) {
                val fieldName: String = field.name
                val value: String = json.optString(fieldName)
                if (value.isEmpty()) continue
                val convertedValue = when {
                    value == "on" -> true
                    value == "off" -> false
                    value.toIntOrNull() != null -> value.toInt()
                    else -> value
                }
                field.isAccessible = true
                field.set(PakkuSetting, convertedValue)
            }
        }
    }
}

class PakkuCore(settingJson: String?) {

    init {
        if (settingJson != null && settingJson.isNotEmpty() && settingJson[0] == '{') {
            PakkuSetting.parseStaticValuesFromJson(JSONObject(settingJson))
        }
        generateCtx()
        instance = this
    }

    companion object {
        lateinit var instance: PakkuCore
    }

    private lateinit var customForceList: Array<Pair<Regex, String>>
    private lateinit var customWhiteList: Array<Regex>

    private fun generateCtx() {
        PakkuSetting.FORCELIST.let {
            val jsonArray = JSONArray(it)
            val size = jsonArray.length()
            val forceList = mutableListOf<Pair<Regex, String>>()
            for (i in 0 until size) {
                val pairArray = jsonArray.getJSONArray(i)
                val regex = pairArray.getString(0).toRegex()
                val replacement = pairArray.getString(1)
                forceList.add(regex to replacement)
            }
            customForceList = forceList.toTypedArray()
        }

        PakkuSetting.WHITELIST.let {
            val jsonArray = JSONArray(it)
            val size = jsonArray.length()
            val whiteList = mutableListOf<Regex>()
            for (i in 0 until size) {
                val reg = jsonArray.getString(i)
                whiteList.add(Regex(reg))
            }
            customWhiteList = whiteList.toTypedArray()
        }
    }

    private fun whitelisted(content: String): Boolean {
        if (customWhiteList.isNotEmpty()) {
            for (it in customWhiteList) {
                if (it.containsMatchIn(content)) {
                    return true
                }
            }
        }
        return false
    }

    fun detaolu(inp: String): String {
        val builder = StringBuilder(inp)
        if (PakkuSetting.TRIM_ENDING) {
            TrimText.replaceWidth(builder)
        }
        if (PakkuSetting.TRIM_WIDTH) {
            TrimText.replaceEndingChar(builder)
        }
        if (PakkuSetting.TRIM_SPACE) {
            TrimText.replaceSpace(builder)
        }
        for ((regex, replacement) in customForceList) {
            if (regex.containsMatchIn(builder)) {
                return builder.replace(regex, replacement)
            }
        }
        return builder.toString()
    }

    private fun splitIgnore(pakkuDanmakuList: List<PakkuDanmaku>): Pair<MutableList<Any>, MutableList<PakkuDanmaku>> {
        val resultDanmakuList = mutableListOf<Any>()
        val needFilterDanmakuList = mutableListOf<PakkuDanmaku>()
        pakkuDanmakuList.forEach { elem ->
            val mode = elem.mode
            when {
                !PakkuSetting.PROC_POOL1 && elem.pool == 1 -> {
                    resultDanmakuList.add(elem.elem)
                }
                !PakkuSetting.PROC_TYPE7 && mode == 7 -> {
                    resultDanmakuList.add(elem.elem)
                }
                !PakkuSetting.PROC_TYPE4 && mode == 4 -> {
                    resultDanmakuList.add(elem.elem)
                }
                whitelisted(elem.getContent()) -> {
                    resultDanmakuList.add(elem.elem)
                }
                else -> {
                    needFilterDanmakuList.add(elem)
                }
            }
        }
        return resultDanmakuList to needFilterDanmakuList
    }

    private fun findPeer(pakkuDanmakuList: MutableList<PakkuDanmaku>): MutableList<Any> {
        val resultDanmakuList = mutableListOf<Any>()

        val pakkuEditDistance = PakkuEditDistance.instance
        pakkuDanmakuList.sortBy { it.progress }
        val peerList = mutableListOf<PakkuPeer>()
        for (index: Int in 0 until pakkuDanmakuList.size - 1) {
            val currentDanmaku = pakkuDanmakuList[index]
            if (currentDanmaku.repeated) continue
            var danmakuOffset = 1
            var comparingDanmaku = pakkuDanmakuList[index + danmakuOffset]
            var peer: PakkuPeer? = null
            while (index + danmakuOffset < pakkuDanmakuList.size
                && comparingDanmaku.progress - currentDanmaku.progress <
                PakkuSetting.THRESHOLD * 1000
            ) {
                comparingDanmaku = pakkuDanmakuList[index + danmakuOffset]
                if (!comparingDanmaku.repeated) {
                    if (pakkuEditDistance.similarMemorized(
                            currentDanmaku, comparingDanmaku,
                        ) != "false"
                    ) {
                        if (peer == null) {
                            peer = PakkuPeer()
                        }
                        if (!currentDanmaku.repeated) {
                            currentDanmaku.repeated = true
                            peer.addDanmaku(currentDanmaku)
                        }
                        comparingDanmaku.repeated = true
                        peer.addDanmaku(comparingDanmaku)
                    }
                }
                danmakuOffset += 1
            }
            if (peer != null) {
                peerList.add(peer)
            }
        }

        pakkuDanmakuList.forEach {
            if (!it.repeated) resultDanmakuList.add(it.elem)
        }
        peerList.forEach {
            if (PakkuSetting.HIDE_THRESHOLD == 0 || it.size() < PakkuSetting.HIDE_THRESHOLD) {
                val danmakuElem = it.buildRepresent(PakkuSetting.REPRESENTATIVE_PERCENT)
                resultDanmakuList.add(danmakuElem)
            }
        }
        return resultDanmakuList
    }

    fun doFilter(danmuList: List<Any?>): List<Any?> {
        val resultDanmakuList = mutableListOf<Any>()
        var pakkuDanmakuList = mutableListOf<PakkuDanmaku>()
        var i = 0
        danmuList.forEach {
            i += 1
            pakkuDanmakuList.add(PakkuDanmaku(it!!))
        }

        val splitResult = splitIgnore(pakkuDanmakuList)
        resultDanmakuList.addAll(splitResult.first)
        pakkuDanmakuList = splitResult.second

        resultDanmakuList.addAll(findPeer(pakkuDanmakuList))
        return resultDanmakuList
    }

}

/**
 * Copyright (C)  2017-2023 @dramforever, @xmcp, @fanthos.
 *
 * This code is licensed under the GPL VERSION 3.
 * See LICENSE.txt for details.
 *
 * Modified by DeltaFlyer, 2023: Implement with Kotlin.
 */

class PakkuEditDistance {

    private val edA = IntArray(0x10ffff)
    private val edB = IntArray(0x10ffff)
    private val edCounts = edA
    private val MIN_DANMU_SIZE = 10

    companion object {

        fun thash(a: Int, b: Int): Int {
            return ((a shl 10) xor b) and 1048575
        }

        val instance = PakkuEditDistance()
    }

    private fun editDistance(p: String, q: String): Int {
        val edCounts = edCounts

        p.forEach { edCounts[it.code]++ }

        q.forEach { edCounts[it.code]-- }

        var ans = 0

        p.forEach {
            ans += abs(edCounts[it.code])
            edCounts[it.code] = 0
        }

        q.forEach {
            ans += abs(edCounts[it.code])
            edCounts[it.code] = 0
        }

        return ans
    }

    fun gen2gramArray(p: String): IntArray {
        val pWithFirstChar = p + p[0]
        val res = IntArray(p.length)
        for (i in p.indices) {
            res[i] = (thash(pWithFirstChar[i].code, pWithFirstChar[i + 1].code))
        }
        return res
    }

    private fun cosineDistanceMemorized(
        pgram: IntArray,
        qgram: IntArray,
        plen: Int,
        qlen: Int
    ): Int {
        if (PakkuSetting.MAX_COSINE > 100) {
            return 0
        }
        val edA = edA
        val edB = edB
        for (i in 0 until plen) {
            edA[pgram[i]]++
        }

        for (i in 0 until qlen) {
            edB[qgram[i]]++
        }

        var x = 0
        var y = 0
        var z = 0

        for (i in 0 until plen) {
            val h1 = pgram[i]
            if (edA[h1] != 0) {
                y += edA[h1] * edA[h1]
                if (edB[h1] != 0) {
                    x += edA[h1] * edB[h1]
                    z += edB[h1] * edB[h1]
                    edB[h1] = 0
                }

                edA[h1] = 0
            }
        }

        for (i in 0 until qlen) {
            val h1 = qgram[i]
            if (edB[h1] != 0) {
                z += edB[h1] * edB[h1]
                edB[h1] = 0
            }
        }
        return x * x / y / z
    }

    fun similarMemorized(danmakuP: PakkuDanmaku, danmakuQ: PakkuDanmaku): String {
        val contentP = danmakuP.getContent()
        val contentQ = danmakuQ.getContent()
        if (contentP == contentQ) {
            return "=="
        }

        val dis = editDistance(contentP, contentQ)
        if (contentP.length + contentQ.length < MIN_DANMU_SIZE) {
            if (dis < (contentP.length + contentQ.length) / MIN_DANMU_SIZE * PakkuSetting.MAX_DIST - 1) {
                return "≤$dis"
            }
        } else {
            if (dis <= PakkuSetting.MAX_DIST) {
                return "≤$dis"
            }
        }
        if (dis >= contentP.length + contentQ.length) {
            return "false"
        }

        val cos = (cosineDistanceMemorized(
            danmakuP.getGram(),
            danmakuQ.getGram(),
            contentP.length,
            contentQ.length
        ) * 100).inv()
        if (cos >= PakkuSetting.MAX_COSINE) {
            return "$cos%"
        }

        return "false"
    }
}