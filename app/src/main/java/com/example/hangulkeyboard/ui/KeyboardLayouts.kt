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

    // 기호 자판 — 골격(줄 수·높이·액션줄 위치)은 글자 자판과 같되, 내용은 전용.
    // 세 글자줄을 '균일 폭 10칸'으로 맞춰 계산기식 numpad(오른쪽 cols 6~8:
    // 789 / 456 / 123)가 세로로 곧게 정렬되게 한다. ⇧·⌫ 도 폭 1.
    // numpad 오른쪽 위(나눗셈) 자리는 @(골뱅이) — 슬래시는 아래 액션줄/왼쪽 열에.
    private fun symbolsMain(bottomArrows: Boolean): List<List<Key>> = listOf(
        charKeys("< > { } [ ] 7 8 9 @"),
        charKeys("\" ' : ; ! ? 4 5 6 *"),
        symBottomRow("/ - _ | \\", "1 2 3"),
        // 액션줄은 동일 배치. ? 자리만 keypad 0 (? 는 2번째 줄에 직통).
        actionRow(bottomArrows).map { if (it == Key.Char("?")) Key.Char("0") else it }
    )

    // ⇧ + 기호 5칸 + 숫자 3칸(cols 6~8) + ⌫ = 10칸, 모두 폭 1 로 numpad 를 정렬.
    private fun symBottomRow(syms: String, digits: String): List<Key> =
        listOf<Key>(Key.Action(ActionType.SHIFT, "⇧", weight = 1f)) +
            charKeys(syms) + charKeys(digits) +
            Key.Action(ActionType.BACKSPACE, "⌫", weight = 1f)

    fun symbols(aux: AuxRows, bottomArrows: Boolean): List<List<Key>> =
        symbolsAuxFor(aux) + symbolsMain(bottomArrows)

    // 기호 자판의 보조줄: 글자 자판과 줄 수는 같게, 내용은 중복 없이.
    // 터미널 기능줄(+-= 대신 esc) / 희소 기호 / 확장 기호(통화·수식) 순.
    private fun symbolsAuxFor(aux: AuxRows): List<List<Key>> = when (aux) {
        AuxRows.ALL -> listOf(symTerminalRow(), symRareRow(), symExtraRow())
        AuxRows.TERMINAL_NUMBER -> listOf(symTerminalRow(), symRareRow())
        AuxRows.TERMINAL -> listOf(symTerminalRow())
        AuxRows.NONE -> emptyList()
    }

    private fun symTerminalRow(): List<Key> = listOf(
        Key.KeyCode("tab", KeyEvent.KEYCODE_TAB),
        Key.Action(ActionType.ALT, "alt"),
        Key.KeyCode("del", KeyEvent.KEYCODE_FORWARD_DEL),
        Key.KeyCode("home", KeyEvent.KEYCODE_MOVE_HOME),
        Key.KeyCode("end", KeyEvent.KEYCODE_MOVE_END),
        Key.Action(ActionType.CLIPBOARD, "📋"),
        Key.Action(ActionType.SNIPPETS, "✂"),
        Key.KeyCode("pgup", KeyEvent.KEYCODE_PAGE_UP),
        Key.KeyCode("pgdn", KeyEvent.KEYCODE_PAGE_DOWN),
        Key.KeyCode("esc", KeyEvent.KEYCODE_ESCAPE)
    )

    // 글자줄·액션줄에 없는 나머지 기호(ASCII 전부 커버).
    private fun symRareRow(): List<Key> = charKeys("~ ` ^ + = # $ % &")

    private fun symExtraRow(): List<Key> = charKeys("₩ € £ · ° ± × ÷ § …")

    // US 자판 기호 쌍(Shift) + 대괄호류 보조 매핑. 숫자는 NUMBER_SHIFT 로 처리.
    private val SYMBOL_SHIFT = mapOf(
        "`" to "~", "-" to "_", "=" to "+", "[" to "{", "]" to "}",
        ";" to ":", "'" to "\"", "\\" to "|", "/" to "?",
        // 보조줄이 숨은(접힘) 프로파일에서도 대괄호류에 닿도록.
        "<" to "[", ">" to "]", "(" to "{", ")" to "}"
    )

    /** 기호 자판 시프트: 숫자 → 특수문자, US 기호 쌍 치환. weight 유지. */
    fun shiftSymbols(rows: List<List<Key>>): List<List<Key>> =
        rows.map { line ->
            line.map { key ->
                if (key is Key.Char)
                    (NUMBER_SHIFT[key.label] ?: SYMBOL_SHIFT[key.label])
                        ?.let { s -> key.copy(label = s, output = s) } ?: key
                else key
            }
        }

    /** 영문 대문자 변환 + 시프트한 숫자 → 특수문자. weight 는 유지(레이아웃 불변). */
    fun shiftEnglish(rows: List<List<Key>>): List<List<Key>> =
        rows.map { line ->
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

    /** 한글 시프트(쌍자음/이중모음) + 시프트한 숫자 → 특수문자. weight 유지. */
    fun shiftKorean(rows: List<List<Key>>): List<List<Key>> =
        rows.map { line ->
            line.map { key ->
                if (key is Key.Char)
                    (KOREAN_SHIFT[key.label] ?: NUMBER_SHIFT[key.label])
                        ?.let { s -> key.copy(label = s, output = s) } ?: key
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

    // 맨 윗줄: 코딩/터미널에서 자주 쓰는 키. 📋 = 클립보드, ✂ = 스니펫
    // (각각 탭 = 토글, 길게 = 서로 교차 전환).
    private fun terminalRow(): List<Key> = listOf(
        Key.KeyCode("tab", KeyEvent.KEYCODE_TAB),
        Key.Action(ActionType.ALT, "alt"),
        Key.KeyCode("del", KeyEvent.KEYCODE_FORWARD_DEL),
        Key.KeyCode("home", KeyEvent.KEYCODE_MOVE_HOME),
        Key.KeyCode("end", KeyEvent.KEYCODE_MOVE_END),
        Key.Action(ActionType.CLIPBOARD, "📋"),
        Key.Action(ActionType.SNIPPETS, "✂"),
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
