package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field
import java.util.*
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
            ) { methodHookParam ->
                methodHookParam.thisObject.callMethod(
                    "dmSegMobile", methodHookParam.args[0]
                )
                    ?.let { dmSegMobileReply ->
                        methodHookParam.args[1].callMethod("onNext", dmSegMobileReply)
                    }
                methodHookParam.result = null
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
                val ts = Date().time
                Log.d("pakku :before " + danmuList.size)
                danmuList = pakku!!.doFilter(danmuList)
                val dis = Date().time - ts
                Log.d("pakku:after " + danmuList.size + " for " + dis)
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
 * Modified by DeltaFlyer, 2023: Convert to Kotlin.
 */

class PakkuPeer {
    companion object {
        private fun toSubscript(n: Int): String {
            val subscripts = charArrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')
            return n.toString().map { subscripts[it.toString().toInt()] }.joinToString("")
        }

        private fun makeMark(txt: String, cnt: Int): String {
            val cntString = if (PakkuCore.Setting.DANMU_SUBSCRIPT)
                "₍${toSubscript(cnt)}₎"
            else
                "[x$cnt]"
            return when (PakkuCore.Setting.DANMU_MARK) {
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
        baseElem.callMethod("setContent", danmakuList.groupBy { it.content }.maxByOrNull {
            it.value.size
        }?.key)
        baseElem.callMethod("setWeight", danmakuList.map { it.weight }.average().toInt())
        baseElem.callMethod("setColor", danmakuList.groupBy { it.color }.maxByOrNull {
            it.value.size
        }?.key)

        var mostMode = danmakuList.groupBy { it.mode }.maxByOrNull {
            it.value.size
        }?.key
        if (PakkuCore.Setting.MODE_ELEVATION) {
            if (mostMode == 1) {
                mostMode = 5
            }
        }
        baseElem.callMethod("setMode", mostMode)
        if (danmakuList.size > PakkuCore.Setting.MARK_THRESHOLD) {
            baseElem.callMethod("setContent", makeMark(baseDanmaku.content, danmakuList.size))
        }

        return baseElem
    }
}

class PakkuDanmaku(danmakuElem: Any) {
    var progress: Int
    var content: String
    var gram: IntArray
    var elem: Any
    var repeated: Boolean
    var pool: Int
    var mode: Int
    var weight: Int
    var color: Int


    init {
        this.progress = danmakuElem.callMethodAs("getProgress")
        this.content = PakkuCore.instance.detaolu(danmakuElem.callMethodAs("getContent"))
        this.gram = PakkuEditDistance.instance.gen2gramArray(this.content)
        this.elem = danmakuElem
        this.repeated = false
        this.pool = danmakuElem.callMethodAs("getPool")
        this.mode = danmakuElem.callMethodAs("getMode")
        this.weight = danmakuElem.callMethodAs("getWeight")
        this.color = danmakuElem.callMethodAs("getColor")
    }
}

class PakkuCore(settingJson: String?) {

    init {
        if (settingJson != null && settingJson.isNotEmpty() && settingJson[0] == '{') {
            Setting.setStaticValuesFromJson(JSONObject(settingJson))
        }
        initWidthTable()
        generateCtx()
        instance = this
    }


    class Setting {
        companion object {
            var MARK_THRESHOLD = 1
            var DANMU_MARK = "prefix"
            var TRIM_SPACE = true
            var MAX_DIST = 5
            var TRIM_ENDING = true
            var MAX_COSINE = 60
            var THRESHOLD = 20
            var FORCELIST =
                "[[\"^23{2,}\$\",\"233...\"],[\"^6{3,}\$\",\"666...\"],[\"^[fF]+\$\",\"FFF...\"],[\"^[hH]+\$\",\"hhh...\"]]"
            var WHITELIST = "[]"
            var PROC_TYPE7 = false
            var TRIM_WIDTH = true
            var PROC_POOL1 = false
            var HIDE_THRESHOLD = 0
            var DANMU_SUBSCRIPT = true
            var PROC_TYPE4 = false
            var MODE_ELEVATION = true
            var REPRESENTATIVE_PERCENT = 20

            fun setStaticValuesFromJson(json: JSONObject) {
                val fields: Array<Field> = Companion::class.java.declaredFields
                for (field in fields) {
                    val fieldName: String = field.name
                    val value: String = json.optString(fieldName)
                    if (value.isEmpty()) continue
                    val convertedValue = when {
                        value == "on" || value == "off" -> true
                        value.toIntOrNull() != null -> value.toInt()
                        else -> value
                    }
                    field.isAccessible = true
                    field.set(null, convertedValue)
                }
            }
        }
    }

    companion object {
        lateinit var instance: PakkuCore
    }

    private lateinit var WIDTH_TABLE: MutableMap<Char, Char>
    private lateinit var FORCELIST_ctx: Array<Pair<Regex, String>>
    private lateinit var WHITELIST_ctx: Array<Regex>

    private val ENDING_CHARS = ".。,，/?？!！…~～@^、+=-_♂♀ "
    private val trim_space_re = Regex("[ 　]+")


    private fun initWidthTable() {
        WIDTH_TABLE = mutableMapOf()
        val before =
            "　１２３４５６７８９０！＠＃＄％＾＆＊（）－＝＿＋［］｛｝;＇:＂,．／＜＞？＼｜｀～ｑｗｅｒｔｙｕｉｏｐａｓｄｆｇｈｊｋｌｚｘｃｖｂｎｍＱＷＥＲＴＹＵＩＯＰＡＳＤＦＧＨＪＫＬＺＸＣＶＢＮＭ"
        val after =
            " 1234567890！@#\$%^&*()-=_+[]{}；'：\"，./<>？\\|`~qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM"
        assert(before.length == after.length)
        for (i in before.indices) {
            WIDTH_TABLE[before[i]] = after[i]
        }
    }

    private fun generateCtx() {
        Setting.FORCELIST.let {
            val jsonArray = JSONArray(it)
            val size = jsonArray.length()
            val forceList = mutableListOf<Pair<Regex, String>>()
            for (i in 0 until size) {
                val pairArray = jsonArray.getJSONArray(i)
                val regex = pairArray.getString(0).toRegex()
                val replacement = pairArray.getString(1)
                forceList.add(regex to replacement)
            }
            FORCELIST_ctx = forceList.toTypedArray()
        }

        Setting.WHITELIST.let {
            val jsonArray = JSONArray(it)
            val size = jsonArray.length()
            val whiteList = mutableListOf<Regex>()
            for (i in 0 until size) {
                val reg = jsonArray.getString(i)
                whiteList.add(Regex(reg))
            }
            WHITELIST_ctx = whiteList.toTypedArray()
        }
    }

    private fun whitelisted(content: String): Boolean {
        for (it in WHITELIST_ctx) {
            if (it.containsMatchIn(content)) {
                return true
            }
        }
        return false
    }

    fun detaolu(inp: String): String {

        var len = inp.length
        var text = ""
        if (Setting.TRIM_ENDING) {
            for (i in len - 1 downTo 1) {
                if (!ENDING_CHARS.contains(inp[i])) {
                    len = i + 1
                    break
                }
            }
        }

        for (i in 0 until len) {
            val to = if (Setting.TRIM_WIDTH) WIDTH_TABLE[inp[i]] else null
            text += to ?: inp[i]
        }
        if (Setting.TRIM_SPACE) {
            text = text.replace(trim_space_re, " ")
        }
        FORCELIST_ctx.forEach {
            if (it.first.containsMatchIn(text)) {
                return@detaolu text.replace(it.first, it.second)
            }
        }
        return text
    }

    private fun splitIgnore(pakkuDanmakuList: List<PakkuDanmaku>): Pair<MutableList<Any>, MutableList<PakkuDanmaku>> {
        val resultDanmakuList = mutableListOf<Any>()
        val needFilterDanmakuList = mutableListOf<PakkuDanmaku>()
        pakkuDanmakuList.forEach { elem ->
            val mode = elem.mode
            when {
                !Setting.PROC_POOL1 && elem.pool == 1 -> {
                    resultDanmakuList.add(elem.elem)
                }
                !Setting.PROC_TYPE7 && mode == 7 -> {
                    resultDanmakuList.add(elem.elem)
                }
                !Setting.PROC_TYPE4 && mode == 4 -> {
                    resultDanmakuList.add(elem.elem)
                }
                whitelisted(elem.content) -> {
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
                Setting.THRESHOLD * 1000
            ) {
                comparingDanmaku = pakkuDanmakuList[index + danmakuOffset]
                if (!comparingDanmaku.repeated) {
                    if (pakkuEditDistance.similarMemorized(
                            currentDanmaku.content, comparingDanmaku.content,
                            currentDanmaku.gram, comparingDanmaku.gram
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
            if (Setting.HIDE_THRESHOLD == 0 || it.size() < Setting.HIDE_THRESHOLD) {
                val danmakuElem = it.buildRepresent(Setting.REPRESENTATIVE_PERCENT)
                resultDanmakuList.add(danmakuElem)
            }
        }
        return resultDanmakuList
    }

    fun doFilter(danmuList: List<Any?>): List<Any?> {
        var pakkuDanmakuList = mutableListOf<PakkuDanmaku>()
        var i = 0
        danmuList.forEach {
            i += 1
            pakkuDanmakuList.add(PakkuDanmaku(it!!))
        }

        val splitResult = splitIgnore(pakkuDanmakuList)
        val resultDanmakuList = splitResult.first
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
 * Modified by DeltaFlyer, 2023: Convert to Kotlin.
 */

class PakkuEditDistance {

    private val ed_a = IntArray(0x10ffff)
    private val ed_b = IntArray(0x10ffff)
    private val ed_counts = ed_a
    private val MIN_DANMU_SIZE = 10

    companion object {

        fun thash(a: Int, b: Int): Int {
            return ((a shl 10) xor b) and 1048575
        }

        val instance = PakkuEditDistance()
    }

    private fun editDistance(p: String, q: String): Int {
        val edCounts = ed_counts

        for (i in p.indices) {
            edCounts[p[i].code]++
        }

        for (i in q.indices) {
            edCounts[q[i].code]--
        }

        var ans = 0

        for (i in p.indices) {
            ans += abs(edCounts[p[i].code])
            edCounts[p[i].code] = 0
        }

        for (i in q.indices) {
            ans += abs(edCounts[q[i].code])
            edCounts[q[i].code] = 0
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
        if (PakkuCore.Setting.MAX_COSINE > 100) {
            return 0
        }
        val edA = ed_a
        val edB = ed_b
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

    fun similarMemorized(P: String, Q: String, Pgram: IntArray, Qgram: IntArray): String {
        if (P == Q) {
            return "=="
        }

        val dis = editDistance(P, Q)
        if (P.length + Q.length < MIN_DANMU_SIZE) {
            if (dis < (P.length + Q.length) / MIN_DANMU_SIZE * PakkuCore.Setting.MAX_DIST - 1) {
                return "≤$dis"
            }
        } else {
            if (dis <= PakkuCore.Setting.MAX_DIST) {
                return "≤$dis"
            }
        }
        if (dis >= P.length + Q.length) {
            return "false"
        }

        val cos = (cosineDistanceMemorized(Pgram, Qgram, P.length, Q.length) * 100).inv()
        if (cos >= PakkuCore.Setting.MAX_COSINE) {
            return "$cos%"
        }

        return "false"
    }

    fun compare(P: String, Q: String): String {
        return similarMemorized(P, Q, gen2gramArray(P), gen2gramArray(Q))
    }
}