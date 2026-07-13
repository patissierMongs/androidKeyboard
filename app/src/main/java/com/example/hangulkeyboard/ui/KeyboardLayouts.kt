package com.example.hangulkeyboard.ui

import android.view.KeyEvent

/** 키 하나의 정의. */
sealed interface Key {
    /** 일반 문자 키. [label] 은 화면 표시, [output] 은 실제 입력값(없으면 label). [weight] 는 가로 폭. */
    data class Char(val label: String, val output: String = label, val weight: Float = 1f) : Key

    /** 특수 기능 키. [weight] 가 0 보다 크면 타입별 기본 폭 대신 쓴다. */
    data class Action(val type: ActionType, val label: String = "", val weight: Float = 0f) : Key

    /** 하드웨어 keyCode 를 그대로 보내는 키(Del/Home/End/PgUp/PgDn 등). */
    data class KeyCode(val label: String, val code: Int) : Key

    /** 빈 공간(가중치만 차지). 줄을 가운데로 들여써서 열을 맞추는 데 쓴다. */
    data class Gap(val weight: Float) : Key
}

enum class ActionType {
    SHIFT, BACKSPACE, LANGUAGE, SYMBOLS, SPACE, ENTER, COMMA, PERIOD, PIN,
    LEFT, RIGHT, UP, DOWN,
    CTRL, ALT, CLIPBOARD, SNIPPETS,
    SELECT_ALL, COPY, PASTE, CUT, UNDO
}

/** 자판 모드. */
enum class KeyboardMode { KOREAN, ENGLISH, SYMBOLS }

/** 시프트/컨트롤 3단계: 해제 / 단일입력(한 번 쓰면 해제) / 지속(고정). */
enum class ShiftState { OFF, SINGLE, LOCKED }

/** 분할 가운데 칸(접힘에선 상단 스트립)의 모드. 📋 키로 순환한다. */
enum class CenterMode { CURSOR, CLIPBOARD, SNIPPETS }

/**
 * 상단 보조줄(터미널/특수문자/숫자) 표시 범위.
 * 접었을 때(커버 화면)는 세로 공간이 좁아 줄을 줄이고 글자키를 키우는 용도.
 */
enum class AuxRows { ALL, TERMINAL_NUMBER, TERMINAL, NONE }

object KeyboardLayouts {

    // ── 공유 프레임(물리 키보드처럼 한 격자) ─────────────────────────────
    // 하나의 키 격자를 정의하고, 각 글자 슬롯이 한글/영문/기호 값을 함께 갖는다.
    // 모드를 바꿔도(한/영 · ?123) 키 위치·폭은 그대로고 '내는 값'만 바뀐다.
    // 기능 키(⇧ · space · ↵ · 방향키 등)는 모든 레이어에서 동일하다.

    private sealed interface Slot {
        /** 모든 레이어 공통 기능 키. */
        data class Fn(val key: Key) : Slot
        /** 글자 슬롯. 레이어별 값(kor/eng/sym)과 고정 폭. */
        data class Ch(val kor: String, val eng: String, val sym: String, val w: Float = 1f) : Slot
    }

    private fun ch(kor: String, eng: String, sym: String, w: Float = 1f) = Slot.Ch(kor, eng, sym, w)
    private fun same(s: String, w: Float = 1f) = Slot.Ch(s, s, s, w)   // 레이어와 무관한 고정 문자
    private fun fn(key: Key) = Slot.Fn(key)

    private fun slotKey(slot: Slot, mode: KeyboardMode): Key = when (slot) {
        is Slot.Fn -> slot.key
        is Slot.Ch -> Key.Char(
            when (mode) {
                KeyboardMode.KOREAN -> slot.kor
                KeyboardMode.ENGLISH -> slot.eng
                KeyboardMode.SYMBOLS -> slot.sym
            },
            weight = slot.w
        )
    }

    // 글자 3줄 — 두벌식/QWERTY 위치에 기호 레이어를 얹었다. 폭은 프레임에 고정이라
    // 한/영/기호 어느 레이어에서도 키가 움직이지 않는다.
    private val LETTER_ROWS: List<List<Slot>> = listOf(
        listOf(
            ch("ㅂ", "q", "!", 1.25f), ch("ㅈ", "w", "@"), ch("ㄷ", "e", "#"), ch("ㄱ", "r", "$"),
            ch("ㅅ", "t", "%"), ch("ㅛ", "y", "^"), ch("ㅕ", "u", "&"), ch("ㅑ", "i", "*"),
            ch("ㅐ", "o", "("), ch("ㅔ", "p", ")", 1.25f)
        ),
        listOf(
            ch("ㅁ", "a", "-", 1.5f), ch("ㄴ", "s", "_"), ch("ㅇ", "d", "="), ch("ㄹ", "f", "+"),
            ch("ㅎ", "g", "["), ch("ㅗ", "h", "]"), ch("ㅓ", "j", "{"), ch("ㅏ", "k", "}"),
            ch("ㅣ", "l", "\\", 1.5f)
        ),
        listOf(
            fn(Key.Action(ActionType.SHIFT, "⇧", 1.5f)),
            ch("ㅋ", "z", ";"), ch("ㅌ", "x", ":"), ch("ㅊ", "c", "'"), ch("ㅍ", "v", "\""),
            ch("ㅠ", "b", "<"), ch("ㅜ", "n", ">"), ch("ㅡ", "m", "?"),
            fn(Key.Action(ActionType.BACKSPACE, "⌫", 1.5f))
        )
    )

    // 상단 보조줄 — 기능/기호/숫자. 세 레이어 공통(위치·값 고정).
    private fun termRow(): List<Slot> = listOf(
        fn(Key.KeyCode("tab", KeyEvent.KEYCODE_TAB)),
        fn(Key.Action(ActionType.ALT, "alt")),
        fn(Key.KeyCode("del", KeyEvent.KEYCODE_FORWARD_DEL)),
        fn(Key.KeyCode("home", KeyEvent.KEYCODE_MOVE_HOME)),
        fn(Key.KeyCode("end", KeyEvent.KEYCODE_MOVE_END)),
        fn(Key.Action(ActionType.CLIPBOARD, "📋")),
        fn(Key.Action(ActionType.SNIPPETS, "✂")),
        fn(Key.KeyCode("pgup", KeyEvent.KEYCODE_PAGE_UP)),
        fn(Key.KeyCode("pgdn", KeyEvent.KEYCODE_PAGE_DOWN)),
        same("+"), same("-"), same("=")
    )
    private fun progRow(): List<Slot> = listOf(
        same("'"), same("\""), same("|"), same("/"), same("\\"),
        same("{"), same("}"), same("["), same("]"), same("_")
    )
    private fun numRow(): List<Slot> = "1 2 3 4 5 6 7 8 9 0".split(" ").map { same(it) }

    private fun auxRows(aux: AuxRows): List<List<Slot>> = when (aux) {
        AuxRows.ALL -> listOf(termRow(), progRow(), numRow())
        AuxRows.TERMINAL_NUMBER -> listOf(termRow(), numRow())
        AuxRows.TERMINAL -> listOf(termRow())
        AuxRows.NONE -> emptyList()
    }

    // 맨 아래 기능줄 — 모든 레이어 공통. 분할(bottomArrows=false) 시 가운데 미니
    // 방향키가 있으므로 방향키 자리에 자주 쓰는 특수문자를 둔다.
    private fun actionRow(bottomArrows: Boolean): List<Slot> = listOf(
        fn(Key.Action(ActionType.CTRL, "ctrl")),
        fn(Key.Action(ActionType.SYMBOLS, "?123")),
        fn(Key.Action(ActionType.LANGUAGE, "한/A")),
        if (bottomArrows) fn(Key.Action(ActionType.LEFT, "◀")) else same("!"),
        fn(Key.Action(ActionType.SPACE, "", 1.56f)),
        if (bottomArrows) fn(Key.Action(ActionType.UP, "▲", 0.94f)) else same("(", 0.94f),
        if (bottomArrows) fn(Key.Action(ActionType.DOWN, "▼")) else same(")"),
        same("?"),
        if (bottomArrows) fn(Key.Action(ActionType.COMMA, ",", 0.94f)) else same("/", 0.94f),
        fn(Key.Action(ActionType.SPACE, "", 1.56f)),
        if (bottomArrows) fn(Key.Action(ActionType.RIGHT, "▶")) else fn(Key.Action(ActionType.COMMA, ",")),
        fn(Key.Action(ActionType.PERIOD, ".")),
        fn(Key.Action(ActionType.ENTER, "↵"))
    )

    private fun frame(aux: AuxRows, bottomArrows: Boolean): List<List<Slot>> =
        auxRows(aux) + LETTER_ROWS + listOf(actionRow(bottomArrows))

    private fun render(mode: KeyboardMode, aux: AuxRows, bottomArrows: Boolean): List<List<Key>> =
        frame(aux, bottomArrows).map { row -> row.map { slotKey(it, mode) } }

    /** 세 자판 모두 같은 프레임에서 나온다 — 전환해도 키가 움직이지 않는다. */
    fun korean(aux: AuxRows, bottomArrows: Boolean) = render(KeyboardMode.KOREAN, aux, bottomArrows)
    fun english(aux: AuxRows, bottomArrows: Boolean) = render(KeyboardMode.ENGLISH, aux, bottomArrows)
    fun symbols(aux: AuxRows, bottomArrows: Boolean) = render(KeyboardMode.SYMBOLS, aux, bottomArrows)

    // ── 시프트 변환(표시된 라벨 기준, 커스텀 배열에도 적용됨) ────────────────
    private val KOREAN_SHIFT = mapOf(
        "ㅂ" to "ㅃ", "ㅈ" to "ㅉ", "ㄷ" to "ㄸ", "ㄱ" to "ㄲ", "ㅅ" to "ㅆ",
        "ㅐ" to "ㅒ", "ㅔ" to "ㅖ"
    )
    private val NUMBER_SHIFT = mapOf(
        "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
        "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")"
    )
    // 무시프트 기호 레이어에 없는 것(~ `)을 시프트로 보충.
    private val SYMBOL_SHIFT = mapOf("!" to "~", "@" to "`")

    fun shiftKorean(rows: List<List<Key>>): List<List<Key>> = rows.map { line ->
        line.map { key ->
            if (key is Key.Char)
                (KOREAN_SHIFT[key.label] ?: NUMBER_SHIFT[key.label])
                    ?.let { s -> key.copy(label = s, output = s) } ?: key
            else key
        }
    }

    fun shiftEnglish(rows: List<List<Key>>): List<List<Key>> = rows.map { line ->
        line.map { key ->
            when {
                key is Key.Char && key.label.length == 1 && key.label[0].isLetter() ->
                    key.copy(label = key.label.uppercase(), output = key.output.uppercase())
                key is Key.Char && NUMBER_SHIFT.containsKey(key.label) ->
                    NUMBER_SHIFT.getValue(key.label).let { key.copy(label = it, output = it) }
                else -> key
            }
        }
    }

    fun shiftSymbols(rows: List<List<Key>>): List<List<Key>> = rows.map { line ->
        line.map { key ->
            if (key is Key.Char)
                (NUMBER_SHIFT[key.label] ?: SYMBOL_SHIFT[key.label])
                    ?.let { s -> key.copy(label = s, output = s) } ?: key
            else key
        }
    }
}
