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
 * JSON 형태 — 펼침/접힘 프로파일별로 한글/영문/기호 배열을 담는다:
 * {
 *   "unfolded": { "korean":[[row],...], "english":[...], "symbols":[...] },
 *   "folded":   { "korean":[...], ... }
 * }
 * (예전처럼 최상단에 바로 korean/english/symbols 만 있으면 두 프로파일 공용으로 본다.)
 * 각 row 는 키 항목의 배열. 키 항목은:
 *   "ㅂ"                             일반 문자(라벨=출력, 폭 1)
 *   {"k":"ㅂ","out":"ㅂ","w":1.25}   문자(출력·폭 지정)
 *   {"act":"SHIFT","l":"⇧","w":1.5}  기능 키(ActionType 이름)
 *   {"code":"TAB","l":"tab"}         하드웨어 키(아래 CODE_NAMES)
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
     * 기본 배열(현재 코드)을 프로파일별 JSON 으로 — 편집기 시작점.
     * 펼침=보조줄 전체, 접힘=터미널 기능줄만(앱 프로파일 기본값과 동일). 분할 전 기준.
     */
    fun defaultJson(): String {
        fun modes(aux: AuxRows) = mapOf(
            KeyboardMode.KOREAN to KeyboardLayouts.korean(aux, bottomArrows = true),
            KeyboardMode.ENGLISH to KeyboardLayouts.english(aux, bottomArrows = true),
            KeyboardMode.SYMBOLS to KeyboardLayouts.symbols(aux, bottomArrows = true),
        )
        return JSONObject()
            .put("unfolded", modesToJson(modes(AuxRows.ALL)))
            .put("folded", modesToJson(modes(AuxRows.TERMINAL)))
            .toString(2)
    }
}
