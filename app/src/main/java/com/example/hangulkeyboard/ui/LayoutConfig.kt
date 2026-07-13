package com.example.hangulkeyboard.ui

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 커스텀 키보드 레이아웃(JSON) 입출력.
 *
 * 앱은 이 JSON 이 있으면 그 배열로 키보드를 그리고, 없으면 [KeyboardLayouts]
 * 기본 배열을 쓴다. 시프트·분할·조합 같은 동작은 그대로 엔진(코드)에 남는다.
 *
 * JSON 형태 — 펼침/접힘 프로파일별. 권장은 '프레임' 형식(물리 키보드처럼 하나의
 * 격자에 슬롯마다 한글/영문/기호 값을 함께 둔다. 레이어를 바꿔도 키가 안 움직임):
 * {
 *   "unfolded": { "frame": [ [slot, slot, ...], ... ] },
 *   "folded":   { "frame": [ ... ] }
 * }
 * 프레임의 슬롯은:
 *   {"kor":"ㅂ","eng":"q","sym":"!","w":1.25}   레이어별 값 + 폭(글자 슬롯)
 *   {"k":"+"}                                   세 레이어 모두 같은 문자
 *   "+"                                         위와 동일(문자열 축약)
 *   {"act":"SHIFT","l":"⇧","w":1.5}             기능 키(모든 레이어 공통)
 *   {"code":"TAB","l":"tab"}                    하드웨어 키
 *
 * (호환) 프레임 대신 모드별 배열도 받는다:
 *   { "unfolded": { "korean":[...], "english":[...], "symbols":[...] }, ... }
 *   또는 최상단에 바로 korean/english/symbols (두 프로파일 공용).
 */
object LayoutConfig {

    /** 프로파일별 커스텀 배열. 비어 있는 모드는 기본 배열로 폴백된다. */
    data class Custom(
        val unfolded: Map<KeyboardMode, List<List<Key>>>,
        val folded: Map<KeyboardMode, List<List<Key>>>,
    ) {
        fun forFolded(isFolded: Boolean): Map<KeyboardMode, List<List<Key>>> =
            if (isFolded) folded else unfolded

        val isEmpty: Boolean get() = unfolded.isEmpty() && folded.isEmpty()

        companion object { val EMPTY = Custom(emptyMap(), emptyMap()) }
    }

    // JSON 이름 ↔ 하드웨어 keyCode. 편집기에서 쓰는 축약 이름.
    private val CODE_NAMES = mapOf(
        "TAB" to KeyEvent.KEYCODE_TAB,
        "ESC" to KeyEvent.KEYCODE_ESCAPE,
        "DEL" to KeyEvent.KEYCODE_FORWARD_DEL,
        "HOME" to KeyEvent.KEYCODE_MOVE_HOME,
        "END" to KeyEvent.KEYCODE_MOVE_END,
        "PGUP" to KeyEvent.KEYCODE_PAGE_UP,
        "PGDN" to KeyEvent.KEYCODE_PAGE_DOWN,
    )
    private val CODE_NAMES_REV = CODE_NAMES.entries.associate { (k, v) -> v to k }

    private val MODE_KEYS = mapOf(
        KeyboardMode.KOREAN to "korean",
        KeyboardMode.ENGLISH to "english",
        KeyboardMode.SYMBOLS to "symbols",
    )

    /** JSON 문자열 → 프로파일별 커스텀 배열. 실패한 부분은 빠진다(기본값 폴백). */
    fun parse(json: String?): Custom {
        if (json.isNullOrBlank()) return Custom.EMPTY
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return Custom.EMPTY
        // 프로파일 구조가 있으면 각각, 없으면(구형 평면형) 두 프로파일 공용으로.
        return if (root.has("unfolded") || root.has("folded")) {
            Custom(
                unfolded = root.optJSONObject("unfolded")?.let(::parseModes) ?: emptyMap(),
                folded = root.optJSONObject("folded")?.let(::parseModes) ?: emptyMap(),
            )
        } else {
            val flat = parseModes(root)
            Custom(flat, flat)
        }
    }

    private fun parseModes(obj: JSONObject): Map<KeyboardMode, List<List<Key>>> {
        // 프레임 형식 우선(슬롯당 레이어별 값). 없으면 모드별 배열(호환).
        obj.optJSONArray("frame")?.let { return parseFrame(it) }
        val out = mutableMapOf<KeyboardMode, List<List<Key>>>()
        for ((mode, key) in MODE_KEYS) {
            val rowsJson = obj.optJSONArray(key) ?: continue
            runCatching { parseRows(rowsJson) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { out[mode] = it }
        }
        return out
    }

    /** 프레임(슬롯당 한/영/기호 값) → 모드별 행으로 투영한다. */
    private fun parseFrame(frame: JSONArray): Map<KeyboardMode, List<List<Key>>> {
        val kor = ArrayList<List<Key>>(); val eng = ArrayList<List<Key>>(); val sym = ArrayList<List<Key>>()
        for (r in 0 until frame.length()) {
            val row = frame.getJSONArray(r)
            val kr = ArrayList<Key>(); val er = ArrayList<Key>(); val sr = ArrayList<Key>()
            for (c in 0 until row.length()) {
                val item = row.get(c)
                if (item is JSONObject && (item.has("act") || item.has("code"))) {
                    val k = parseKey(item)   // 기능 키: 모든 레이어 공통
                    kr.add(k); er.add(k); sr.add(k)
                } else {
                    val w: Float
                    val vk: String; val ve: String; val vs: String
                    if (item is JSONObject && (item.has("kor") || item.has("eng") || item.has("sym"))) {
                        vk = item.optString("kor", ""); ve = item.optString("eng", ""); vs = item.optString("sym", "")
                        w = item.optDouble("w", 1.0).toFloat()
                    } else {
                        // {"k":"+"} 또는 "+" — 세 레이어 동일
                        val v = if (item is JSONObject) item.optString("k", "") else item.toString()
                        vk = v; ve = v; vs = v
                        w = if (item is JSONObject) item.optDouble("w", 1.0).toFloat() else 1f
                    }
                    kr.add(Key.Char(vk, vk, w)); er.add(Key.Char(ve, ve, w)); sr.add(Key.Char(vs, vs, w))
                }
            }
            kor.add(kr); eng.add(er); sym.add(sr)
        }
        return mapOf(KeyboardMode.KOREAN to kor, KeyboardMode.ENGLISH to eng, KeyboardMode.SYMBOLS to sym)
    }

    private fun parseRows(rows: JSONArray): List<List<Key>> =
        List(rows.length()) { r ->
            val row = rows.getJSONArray(r)
            List(row.length()) { c -> parseKey(row.get(c)) }
        }

    private fun parseKey(item: Any): Key = when (item) {
        is String -> Key.Char(item)
        is JSONObject -> when {
            item.has("act") -> Key.Action(
                type = ActionType.valueOf(item.getString("act")),
                label = item.optString("l", ""),
                weight = item.optDouble("w", 0.0).toFloat(),
            )
            item.has("code") -> Key.KeyCode(
                label = item.optString("l", item.getString("code").lowercase()),
                code = CODE_NAMES[item.getString("code")] ?: KeyEvent.KEYCODE_UNKNOWN,
            )
            else -> {
                val label = item.getString("k")
                Key.Char(label, item.optString("out", label), item.optDouble("w", 1.0).toFloat())
            }
        }
        else -> Key.Char(item.toString())
    }

    private fun modesToJson(layouts: Map<KeyboardMode, List<List<Key>>>): JSONObject {
        val obj = JSONObject()
        for ((mode, key) in MODE_KEYS) {
            val rows = layouts[mode] ?: continue
            val rowsJson = JSONArray()
            rows.forEach { row ->
                val rowJson = JSONArray()
                row.forEach { rowJson.put(keyToJson(it)) }
                rowsJson.put(rowJson)
            }
            obj.put(key, rowsJson)
        }
        return obj
    }

    private fun keyToJson(key: Key): Any = when (key) {
        is Key.Char ->
            if (key.label == key.output && key.weight == 1f) key.label
            else JSONObject().put("k", key.label).apply {
                if (key.output != key.label) put("out", key.output)
                if (key.weight != 1f) put("w", key.weight.toDouble())
            }
        is Key.Action -> JSONObject().put("act", key.type.name).apply {
            if (key.label.isNotEmpty()) put("l", key.label)
            if (key.weight > 0f) put("w", key.weight.toDouble())
        }
        is Key.KeyCode -> JSONObject()
            .put("code", CODE_NAMES_REV[key.code] ?: "TAB")
            .put("l", key.label)
        is Key.Gap -> JSONObject().put("k", " ")   // 편집기엔 갭이 없지만 방어적으로.
    }

    /**
     * 기본 배열(현재 코드)을 프레임 형식 JSON 으로 — 편집기 시작점.
     * 세 모드가 같은 격자라, 각 자리를 겹쳐 슬롯(한/영/기호 값)으로 되돌린다.
     * 펼침=보조줄 전체, 접힘=터미널 기능줄만. 분할 전(bottomArrows=true) 기준.
     */
    fun defaultJson(): String {
        fun frameObj(aux: AuxRows): JSONObject {
            val kor = KeyboardLayouts.korean(aux, bottomArrows = true)
            val eng = KeyboardLayouts.english(aux, bottomArrows = true)
            val sym = KeyboardLayouts.symbols(aux, bottomArrows = true)
            val frame = JSONArray()
            for (r in kor.indices) {
                val row = JSONArray()
                for (c in kor[r].indices) {
                    val k = kor[r][c]
                    if (k is Key.Char) {
                        val e = (eng[r][c] as? Key.Char)?.output ?: k.output
                        val s = (sym[r][c] as? Key.Char)?.output ?: k.output
                        val slot = JSONObject().put("kor", k.output).put("eng", e).put("sym", s)
                        if (k.weight != 1f) slot.put("w", k.weight.toDouble())
                        row.put(slot)
                    } else row.put(keyToJson(k))
                }
                frame.put(row)
            }
            return JSONObject().put("frame", frame)
        }
        return JSONObject()
            .put("unfolded", frameObj(AuxRows.ALL))
            .put("folded", frameObj(AuxRows.TERMINAL))
            .toString(2)
    }
}
