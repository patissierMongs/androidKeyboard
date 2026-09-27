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
 * JSON 형태 — 펼침/접힘 프로파일별. 권장은 '레이어 분리' 형식: 격자 구조(grid)와
 * 레이어별 값(layers.kor/eng/sym)을 나눠 둔다. 구조가 하나라 레이어를 바꿔도
 * 키가 안 움직이고, 각 레이어의 값은 자기 배열에서 한눈에 읽힌다:
 * {
 *   "unfolded": {
 *     "grid":   [ [ {"w":1.25}, {}, {"rep":true}, {"gap":0.5},
 *                   {"act":"SHIFT","l":"⇧","w":1.5}, {"code":"TAB","l":"tab"} ], ... ],
 *     "layers": { "kor": [ ["ㅂ", {"v":"ㅅ","alt":"ㅆ"}, ...], ... ],
 *                 "eng": [...], "sym": [...] },
 *     "center": [ [{"act":"SELECT","l":"선택"}], [{"act":"LEFT","l":"◀"}, ...], ... ]
 *   },
 *   "folded": { ... }
 * }
 *  - grid 슬롯: {}=글자 슬롯(w=폭, rep=꾹 누르면 반복), {"gap":w}=빈 공간,
 *    {"act":...}=기능 키, {"code":...}=하드웨어 키 (기능/하드웨어 키는 레이어 공통)
 *  - layers 값: 글자 슬롯 순서대로 "ㅂ" 또는 {"v":"ㅅ","alt":"ㅆ"}(길게 = 대체키)
 *  - center: 분할 가운데 커서 패드의 버튼(아래줄부터), 줄당 배열
 *
 * (호환) 이전 '프레임' 형식(슬롯당 kor/eng/sym 값)과 모드별 배열도 받는다:
 *   { "unfolded": { "frame": [ [ {"kor":"ㅂ","eng":"q","sym":"!"}, ... ] ] }, ... }
 *   { "unfolded": { "korean":[...], "english":[...], "symbols":[...] }, ... }
 *   또는 최상단에 바로 korean/english/symbols (두 프로파일 공용).
 */
object LayoutConfig {

    /** 프로파일별 커스텀 배열 + 분할 센터 패드 버튼. 비어 있는 부분은 기본값 폴백. */
    data class Custom(
        val unfolded: Map<KeyboardMode, List<List<Key>>>,
        val folded: Map<KeyboardMode, List<List<Key>>>,
        val centerUnfolded: List<List<Key>> = emptyList(),
        val centerFolded: List<List<Key>> = emptyList(),
    ) {
        fun forFolded(isFolded: Boolean): Map<KeyboardMode, List<List<Key>>> =
            if (isFolded) folded else unfolded

        /** 분할 센터 패드의 커스텀 버튼(아래줄부터). 비면 기본 커서 패드. */
        fun centerForFolded(isFolded: Boolean): List<List<Key>> =
            if (isFolded) centerFolded else centerUnfolded

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
            val un = root.optJSONObject("unfolded")
            val fo = root.optJSONObject("folded")
            Custom(
                unfolded = un?.let(::parseModes) ?: emptyMap(),
                folded = fo?.let(::parseModes) ?: emptyMap(),
                centerUnfolded = un?.let(::parseCenter) ?: emptyList(),
                centerFolded = fo?.let(::parseCenter) ?: emptyList(),
            )
        } else {
            val flat = parseModes(root)
            val center = parseCenter(root)
            Custom(flat, flat, center, center)
        }
    }

    /** 분할 센터 패드 버튼("center", 아래줄부터). 잘못된 JSON 이면 기본 패드로. */
    private fun parseCenter(obj: JSONObject): List<List<Key>> {
        val arr = obj.optJSONArray("center") ?: return emptyList()
        return runCatching {
            List(arr.length()) { r ->
                val row = arr.getJSONArray(r)
                List(row.length()) { c -> parseKey(row.get(c)) }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseModes(obj: JSONObject): Map<KeyboardMode, List<List<Key>>> {
        // 레이어 분리 형식(grid+layers) 우선, 다음 프레임 형식, 다음 모드별 배열(호환).
        parseGridLayers(obj)?.let { return it }
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

    /**
     * 레이어 분리 형식: grid(구조 — 폭·반복·빈 공간·기능 키) + layers.kor/eng/sym
     * (글자 슬롯 순서대로 값). 형식이 아니면 null(다음 형식으로 폴백).
     */
    private fun parseGridLayers(obj: JSONObject): Map<KeyboardMode, List<List<Key>>>? {
        val grid = obj.optJSONArray("grid") ?: return null
        val layers = obj.optJSONObject("layers") ?: return null
        val layerArr = mapOf(
            KeyboardMode.KOREAN to layers.optJSONArray("kor"),
            KeyboardMode.ENGLISH to layers.optJSONArray("eng"),
            KeyboardMode.SYMBOLS to layers.optJSONArray("sym"),
        )
        return runCatching {
            val out = KeyboardMode.entries.associateWith { ArrayList<List<Key>>() }
            for (r in 0 until grid.length()) {
                val row = grid.getJSONArray(r)
                val outRow = KeyboardMode.entries.associateWith { ArrayList<Key>() }
                var chIdx = 0   // 이 줄에서 몇 번째 글자 슬롯인지(레이어 값 인덱스)
                for (c in 0 until row.length()) {
                    val slot = row.optJSONObject(c)
                    when {
                        slot != null && (slot.has("act") || slot.has("code")) -> {
                            val k = parseKey(slot)   // 기능 키: 모든 레이어 공통
                            KeyboardMode.entries.forEach { outRow.getValue(it).add(k) }
                        }
                        slot != null && slot.has("gap") -> {
                            val g = Key.Gap(slot.getDouble("gap").toFloat())
                            KeyboardMode.entries.forEach { outRow.getValue(it).add(g) }
                        }
                        else -> {
                            val w = slot?.optDouble("w", 1.0)?.toFloat() ?: 1f
                            val rep = slot?.optBoolean("rep", false) ?: false
                            for (m in KeyboardMode.entries) {
                                val v = layerArr[m]?.optJSONArray(r)?.opt(chIdx)
                                outRow.getValue(m).add(layerValue(v, w, rep))
                            }
                            chIdx++
                        }
                    }
                }
                KeyboardMode.entries.forEach { out.getValue(it).add(outRow.getValue(it)) }
            }
            out as Map<KeyboardMode, List<List<Key>>>
        }.getOrNull()
    }

    /** layers 배열의 값 하나 → 글자 키. "ㅂ" 또는 {"v":"ㅅ","alt":"ㅆ"}. */
    private fun layerValue(v: Any?, w: Float, rep: Boolean): Key = when {
        v is JSONObject -> {
            val label = v.optString("v", "")
            Key.Char(label, v.optString("out", label), w, v.optString("alt", ""), rep)
        }
        v == null || v == JSONObject.NULL -> Key.Char("", "", w, "", rep)
        else -> Key.Char(v.toString(), v.toString(), w, "", rep)
    }

    /** 프레임(슬롯당 한/영/기호 값) → 모드별 행으로 투영한다. */
    private fun parseFrame(frame: JSONArray): Map<KeyboardMode, List<List<Key>>> {
        val kor = ArrayList<List<Key>>(); val eng = ArrayList<List<Key>>(); val sym = ArrayList<List<Key>>()
        for (r in 0 until frame.length()) {
            val row = frame.getJSONArray(r)
            val kr = ArrayList<Key>(); val er = ArrayList<Key>(); val sr = ArrayList<Key>()
            for (c in 0 until row.length()) {
                val item = row.get(c)
                if (item is JSONObject && (item.has("act") || item.has("code") || item.has("gap"))) {
                    val k = parseKey(item)   // 기능 키·빈 공간: 모든 레이어 공통
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
            item.has("gap") -> Key.Gap(item.getDouble("gap").toFloat())
            else -> {
                val label = item.getString("k")
                Key.Char(
                    label, item.optString("out", label), item.optDouble("w", 1.0).toFloat(),
                    item.optString("alt", ""), item.optBoolean("rep", false)
                )
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
            if (key.label == key.output && key.weight == 1f && key.alt.isEmpty() && !key.repeat) key.label
            else JSONObject().put("k", key.label).apply {
                if (key.output != key.label) put("out", key.output)
                if (key.weight != 1f) put("w", key.weight.toDouble())
                if (key.alt.isNotEmpty()) put("alt", key.alt)
                if (key.repeat) put("rep", true)
            }
        is Key.Action -> JSONObject().put("act", key.type.name).apply {
            if (key.label.isNotEmpty()) put("l", key.label)
            if (key.weight > 0f) put("w", key.weight.toDouble())
        }
        is Key.KeyCode -> JSONObject()
            .put("code", CODE_NAMES_REV[key.code] ?: "TAB")
            .put("l", key.label)
        is Key.Gap -> JSONObject().put("gap", key.weight.toDouble())
    }

    /**
     * 기본 배열(현재 코드)을 레이어 분리 형식 JSON 으로 — 편집기 시작점.
     * 세 모드가 같은 격자라, 구조(grid)는 한 번만 쓰고 값은 레이어별 배열로 나눈다.
     * 펼침=보조줄 전체, 접힘=터미널 기능줄만. 분할 전(bottomArrows=true) 기준.
     */
    fun defaultJson(): String {
        fun profileObj(aux: AuxRows): JSONObject {
            val kor = KeyboardLayouts.korean(aux, bottomArrows = true)
            val eng = KeyboardLayouts.english(aux, bottomArrows = true)
            val sym = KeyboardLayouts.symbols(aux, bottomArrows = true)
            val grid = JSONArray()
            val lk = JSONArray(); val le = JSONArray(); val ls = JSONArray()
            for (r in kor.indices) {
                val gRow = JSONArray()
                val kRow = JSONArray(); val eRow = JSONArray(); val sRow = JSONArray()
                for (c in kor[r].indices) {
                    val k = kor[r][c]
                    if (k is Key.Char) {
                        val slot = JSONObject()
                        if (k.weight != 1f) slot.put("w", k.weight.toDouble())
                        if (k.repeat) slot.put("rep", true)
                        gRow.put(slot)
                        kRow.put(k.output)
                        eRow.put((eng[r][c] as? Key.Char)?.output ?: k.output)
                        sRow.put((sym[r][c] as? Key.Char)?.output ?: k.output)
                    } else gRow.put(keyToJson(k))
                }
                grid.put(gRow)
                lk.put(kRow); le.put(eRow); ls.put(sRow)
            }
            return JSONObject()
                .put("grid", grid)
                .put("layers", JSONObject().put("kor", lk).put("eng", le).put("sym", ls))
                .put("center", defaultCenterJson())
        }
        return JSONObject()
            .put("unfolded", profileObj(AuxRows.ALL))
            .put("folded", profileObj(AuxRows.TERMINAL))
            .toString(2)
    }

    /** 기본 커서 패드(아래줄부터)를 "center" JSON 으로 — 편집기에서 바꾸는 시작점. */
    private fun defaultCenterJson(): JSONArray {
        fun act(type: String, l: String) = JSONObject().put("act", type).put("l", l)
        fun code(name: String, l: String) = JSONObject().put("code", name).put("l", l)
        return JSONArray()
            .put(JSONArray().put(act("SELECT", "선택")))
            .put(JSONArray().put(act("LEFT", "◀")).put(act("RIGHT", "▶")))
            .put(JSONArray().put(act("UP", "▲")).put(act("DOWN", "▼")))
            .put(JSONArray().put(code("ESC", "esc")).put(act("SELECT_ALL", "전체")))
            .put(JSONArray().put(act("COPY", "복사")).put(act("PASTE", "붙여")))
            .put(JSONArray().put(act("CUT", "잘라")).put(act("UNDO", "되돌")))
    }
}
