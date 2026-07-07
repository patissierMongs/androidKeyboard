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
    CTRL, ALT, CLIPBOARD,
    SELECT_ALL, COPY, PASTE, CUT, UNDO
}

/** 자판 모드. */
enum class KeyboardMode { KOREAN, ENGLISH, SYMBOLS }

/** 시프트 3단계: 해제 / 단일입력(한 글자 후 해제) / 지속(고정). */
enum class ShiftState { OFF, SINGLE, LOCKED }

/**
 * 상단 보조줄(터미널/특수문자/숫자) 표시 범위.
 * 접었을 때(커버 화면)는 세로 공간이 좁아 줄을 줄이고 글자키를 키우는 용도.
 */
enum class AuxRows { ALL, TERMINAL_NUMBER, TERMINAL, NONE }

object KeyboardLayouts {

    // 글자 자판 본체(글자 3줄 + 액션줄). 보조줄은 auxRowsFor 로 앞에 붙는다.
    // bottomArrows=false(분할 시)면 액션줄 방향키 자리가 ! ( ) / 로 바뀐다.
    private fun englishMain(bottomArrows: Boolean): List<List<Key>> = listOf(
        row("q w e r t y u i o p"),
        indentedRow("a s d f g h j k l"),
        bottomLetterRow("z x c v b n m"),
        actionRow(bottomArrows)
    )

    private fun koreanMain(bottomArrows: Boolean): List<List<Key>> = listOf(
        row("ㅂ ㅈ ㄷ ㄱ ㅅ ㅛ ㅕ ㅑ ㅐ ㅔ"),
        indentedRow("ㅁ ㄴ ㅇ ㄹ ㅎ ㅗ ㅓ ㅏ ㅣ"),
        bottomLetterRow("ㅋ ㅌ ㅊ ㅍ ㅠ ㅜ ㅡ"),
        actionRow(bottomArrows)
    )

    /** 영문 QWERTY — 프로파일이 고른 보조줄 + 글자 자판. */
    fun english(aux: AuxRows, bottomArrows: Boolean): List<List<Key>> =
        auxRowsFor(aux) + englishMain(bottomArrows)

    /** 한글 두벌식 — 프로파일이 고른 보조줄 + 글자 자판. */
    fun korean(aux: AuxRows, bottomArrows: Boolean): List<List<Key>> =
        auxRowsFor(aux) + koreanMain(bottomArrows)

    private fun auxRowsFor(aux: AuxRows): List<List<Key>> = when (aux) {
        AuxRows.ALL -> listOf(terminalRow(), progRow(), numberRow())
        AuxRows.TERMINAL_NUMBER -> listOf(terminalRow(), numberRow())
        AuxRows.TERMINAL -> listOf(terminalRow())
        AuxRows.NONE -> emptyList()
    }

    // 기호 자판 — 글자 자판과 완전히 같은 골격(10키 / 9키 / 7키+⇧⌫ / 액션줄).
    // 전환해도 줄 수·키 위치·가운데 칸이 그대로라 손 위치가 안 흔들린다.
    // 2번째 줄은 US 자판의 기호 키 배열, Shift 로 짝 기호(~ _ + { } : " | ?)와
    // 숫자줄 특수문자(! @ # …)가 나온다. 3번째 줄은 자주 쓰는 Shift 짝 직통.
    private fun symbolsMain(bottomArrows: Boolean): List<List<Key>> = listOf(
        row("1 2 3 4 5 6 7 8 9 0"),
        indentedRow("` - = [ ] ; ' \\ /"),
        bottomLetterRow("_ : \" < > * |"),
        actionRow(bottomArrows)
    )

    fun symbols(aux: AuxRows, bottomArrows: Boolean): List<List<Key>> =
        auxRowsFor(aux) + symbolsMain(bottomArrows)

    // US 자판 기호 쌍(Shift). 숫자는 NUMBER_SHIFT 로 처리한다.
    private val SYMBOL_SHIFT = mapOf(
        "`" to "~", "-" to "_", "=" to "+", "[" to "{", "]" to "}",
        ";" to ":", "'" to "\"", "\\" to "|", "/" to "?"
    )

    /** 기호 자판 시프트: 숫자 → 특수문자, US 기호 쌍 치환. */
    fun shiftSymbols(rows: List<List<Key>>): List<List<Key>> =
        rows.map { line ->
            line.map { key ->
                if (key is Key.Char)
                    (NUMBER_SHIFT[key.label] ?: SYMBOL_SHIFT[key.label])
                        ?.let { Key.Char(it) } ?: key
                else key
            }
        }

    /** 영문 대문자 변환 + 시프트한 숫자 → 특수문자. */
    fun shiftEnglish(rows: List<List<Key>>): List<List<Key>> =
        rows.map { line ->
            line.map { key ->
                when {
                    key is Key.Char && key.label.length == 1 && key.label[0].isLetter() ->
                        Key.Char(key.label.uppercase(), key.output.uppercase())
                    key is Key.Char && NUMBER_SHIFT.containsKey(key.label) ->
                        Key.Char(NUMBER_SHIFT.getValue(key.label))
                    else -> key
                }
            }
        }

    // 한글 시프트(쌍자음/이중모음). 매핑이 있는 키만 치환.
    private val KOREAN_SHIFT = mapOf(
        "ㅂ" to "ㅃ", "ㅈ" to "ㅉ", "ㄷ" to "ㄸ", "ㄱ" to "ㄲ", "ㅅ" to "ㅆ",
        "ㅐ" to "ㅒ", "ㅔ" to "ㅖ"
    )

    // 시프트한 숫자 → 특수문자 (US 자판 기준). 한/영 모드 공통으로 쓴다.
    private val NUMBER_SHIFT = mapOf(
        "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
        "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")"
    )

    /** 한글 시프트(쌍자음/이중모음) + 시프트한 숫자 → 특수문자. */
    fun shiftKorean(rows: List<List<Key>>): List<List<Key>> =
        rows.map { line ->
            line.map { key ->
                if (key is Key.Char)
                    (KOREAN_SHIFT[key.label] ?: NUMBER_SHIFT[key.label])
                        ?.let { Key.Char(it) } ?: key
                else key
            }
        }

    // 10칸짜리 윗줄. 양 끝 키(ㅂ/ㅔ, q/p, 1/0)는 화면 가장자리라 오타가 잦아
    // 살짝 넓힌다(홈row 의 1.5 보다는 완만하게 1.25).
    private fun row(spaceSeparated: String): List<Key> {
        val parts = spaceSeparated.split(" ")
        return parts.mapIndexed { i, s ->
            Key.Char(s, weight = if (i == 0 || i == parts.lastIndex) 1.25f else 1f)
        }
    }

    // 9칸짜리 홈row. 좌우 빈칸 없이 양끝 키(ㅁ/ㅣ, a/l)만 넓혀 폭을 채운다.
    private fun indentedRow(spaceSeparated: String): List<Key> {
        val parts = spaceSeparated.split(" ")
        return parts.mapIndexed { i, s ->
            Key.Char(s, weight = if (i == 0 || i == parts.lastIndex) 1.5f else 1f)
        }
    }

    // 상단 숫자줄. 시프트하면 NUMBER_SHIFT 매핑으로 특수문자가 된다.
    private fun numberRow(): List<Key> = charKeys("1 2 3 4 5 6 7 8 9 0")

    // 맨 윗줄: 코딩/터미널에서 자주 쓰는 키. 맨 앞은 Tab(기존 Ctrl 자리).
    private fun terminalRow(): List<Key> = listOf(
        Key.KeyCode("tab", KeyEvent.KEYCODE_TAB),
        Key.Action(ActionType.ALT, "alt"),
        Key.KeyCode("del", KeyEvent.KEYCODE_FORWARD_DEL),
        Key.KeyCode("home", KeyEvent.KEYCODE_MOVE_HOME),
        Key.KeyCode("end", KeyEvent.KEYCODE_MOVE_END),
        Key.Action(ActionType.CLIPBOARD, "📋"),
        Key.KeyCode("pgup", KeyEvent.KEYCODE_PAGE_UP),
        Key.KeyCode("pgdn", KeyEvent.KEYCODE_PAGE_DOWN),
        Key.Char("+"),
        Key.Char("-"),
        Key.Char("=")
    )

    // 프로그래밍/쉘에서 자주 쓰는 특수문자줄
    private fun progRow(): List<Key> = charKeys("~ ` | / \\ { } [ ] _")

    private fun charKeys(spaceSeparated: String): List<Key> =
        spaceSeparated.split(" ").map { Key.Char(it) }

    // 글자줄 + 좌측 Shift / 우측 Backspace
    private fun bottomLetterRow(letters: String): List<Key> =
        listOf<Key>(Key.Action(ActionType.SHIFT, "⇧"))
            .plus(charKeys(letters))
            .plus(Key.Action(ActionType.BACKSPACE, "⌫"))

    // 맨 아래 기능키 줄 (맨 앞 Ctrl 추가, 문장부호는 ,→? .→, ?→. 로 순환):
    //  Ctrl · ?123 · 한/A · ◀ · [space] · ▲ · ▼ · ? · , · [space] · ▶ · . · ↵
    // 분할(bottomArrows=false)이면 가운데 미니 방향키가 있으므로 방향키 자리에
    // 자주 쓰는 특수문자(! ( ) /)를 둔다.
    private fun actionRow(bottomArrows: Boolean = true): List<Key> = listOf(
        Key.Action(ActionType.CTRL, "ctrl"),
        Key.Action(ActionType.SYMBOLS, "?123"),
        Key.Action(ActionType.LANGUAGE, "한/A"),
        if (bottomArrows) Key.Action(ActionType.LEFT, "◀") else Key.Char("!"),
        // 스페이스는 가운데 쪽으로 살짝 넓히고(+0.06) 그만큼 가운데 쪽 이웃을
        // 줄여, 둘 사이 경계만 움직이고 다른 키 경계는 그대로 둔다.
        Key.Action(ActionType.SPACE, "", weight = 1.56f),
        if (bottomArrows) Key.Action(ActionType.UP, "▲", weight = 0.94f)
        else Key.Char("(", weight = 0.94f),
        if (bottomArrows) Key.Action(ActionType.DOWN, "▼") else Key.Char(")"),
        Key.Char("?"),
        // 분할이면 , 를 오른쪽 . 옆으로 보내고 이 자리는 / 가 차지한다.
        if (bottomArrows) Key.Action(ActionType.COMMA, ",", weight = 0.94f)
        else Key.Char("/", weight = 0.94f),
        Key.Action(ActionType.SPACE, "", weight = 1.56f),
        if (bottomArrows) Key.Action(ActionType.RIGHT, "▶") else Key.Action(ActionType.COMMA, ","),
        Key.Action(ActionType.PERIOD, "."),
        Key.Action(ActionType.ENTER, "↵")
    )
}
