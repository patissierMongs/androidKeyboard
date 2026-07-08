package com.example.hangulkeyboard

import org.json.JSONObject

/**
 * 오타 측정기(기기 내 저장 전용).
 *
 * 두 가지를 기록한다:
 *  1. 터치 편향 — 글자 키를 누를 때마다 키 셀 안에서의 정규화 좌표(0..1)를
 *     누적한다. 키별 평균이 중심(0.5)에서 벗어난 정도가 곧 그 키를 누르는
 *     손버릇(예: 항상 오른쪽 아래를 누름)이다.
 *  2. 혼동 쌍 — "글자 입력 → 곧바로 백스페이스 → 다른 글자"를 오타 수정으로
 *     보고 (지운 글자 → 새 글자) 쌍을 센다. 커서 이동·스페이스 등 다른 입력이
 *     끼면 수정으로 치지 않는다.
 *
 * 시간은 파라미터로 받아 단위 테스트가 가능하다.
 */
class TypoTracker {

    var taps = 0L
        private set
    var corrections = 0L
        private set

    /** "지운글자>새글자" → 횟수 */
    val confusion = mutableMapOf<String, Int>()

    /** 글자 → [fx-0.5 누적, fy-0.5 누적, 표본 수] */
    val offsets = mutableMapOf<String, FloatArray>()

    private data class Press(val label: String, val time: Long)

    private var lastPress: Press? = null
    private var pending: Press? = null   // 백스페이스로 지워진 직전 입력

    /** 글자 키 입력. [fx],[fy] 는 키 셀 안 정규화 좌표(0..1). */
    fun onCharPress(label: String, fx: Float, fy: Float, now: Long) {
        taps++
        val o = offsets.getOrPut(label) { FloatArray(3) }
        o[0] += fx - 0.5f
        o[1] += fy - 0.5f
        o[2] += 1f
        pending?.let { p ->
            if (now - p.time <= CORRECTION_WINDOW_MS && label != p.label) {
                corrections++
                confusion.merge("${p.label}>$label", 1, Int::plus)
            }
        }
        pending = null
        lastPress = Press(label, now)
    }

    /** 백스페이스. 직전 글자 입력이 최근이면 수정 후보로 올린다. */
    fun onBackspace(now: Long) {
        val lp = lastPress
        if (lp != null && now - lp.time <= BACKSPACE_WINDOW_MS) {
            pending = lp
        }
        lastPress = null
    }

    /** 글자·백스페이스 외 입력(스페이스/엔터/커서 이동 등) — 수정 추적을 끊는다. */
    fun onOtherInput() {
        lastPress = null
        pending = null
    }

    fun confusionCount(from: String, to: String): Int = confusion["$from>$to"] ?: 0

    fun reset() {
        taps = 0
        corrections = 0
        confusion.clear()
        offsets.clear()
        lastPress = null
        pending = null
    }

    fun toJson(): String {
        val conf = JSONObject()
        confusion.forEach { (k, v) -> conf.put(k, v) }
        val offs = JSONObject()
        offsets.forEach { (k, v) ->
            offs.put(k, org.json.JSONArray(listOf(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())))
        }
        return JSONObject()
            .put("taps", taps)
            .put("corrections", corrections)
            .put("confusion", conf)
            .put("offsets", offs)
            .toString()
    }

    fun loadJson(json: String?) {
        reset()
        if (json.isNullOrEmpty()) return
        runCatching {
            val root = JSONObject(json)
            taps = root.optLong("taps")
            corrections = root.optLong("corrections")
            val conf = root.optJSONObject("confusion")
            conf?.keys()?.forEach { k -> confusion[k] = conf.getInt(k) }
            val offs = root.optJSONObject("offsets")
            offs?.keys()?.forEach { k ->
                val arr = offs.getJSONArray(k)
                offsets[k] = floatArrayOf(
                    arr.getDouble(0).toFloat(),
                    arr.getDouble(1).toFloat(),
                    arr.getDouble(2).toFloat()
                )
            }
        }
    }

    companion object {
        // 백스페이스가 '직전 입력의 취소'로 인정되는 시간.
        const val BACKSPACE_WINDOW_MS = 3_000L
        // 지운 뒤 새 글자가 '수정'으로 인정되는 시간.
        const val CORRECTION_WINDOW_MS = 5_000L
    }
}
