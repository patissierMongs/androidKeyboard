package com.example.hangulkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 공유 프레임 불변식: 한글/영문/기호 자판은 '같은 격자'에서 나와야 한다.
 * 즉 줄 수·각 줄의 키 개수·각 자리의 종류(문자/기능/하드웨어)·폭이 모두 같고,
 * 글자 키의 '값'만 달라야 한다. (모드 전환 시 키가 움직이지 않음을 보장.)
 */
class FrameConsistencyTest {

    private fun modes(aux: AuxRows, bottomArrows: Boolean) = listOf(
        KeyboardLayouts.korean(aux, bottomArrows),
        KeyboardLayouts.english(aux, bottomArrows),
        KeyboardLayouts.symbols(aux, bottomArrows),
    )

    /** 자리의 '형태'(종류+폭). 글자 키의 실제 문자는 제외 — 그건 레이어마다 달라도 된다. */
    private fun shape(key: Key): String = when (key) {
        is Key.Char -> "char:${key.weight}"
        is Key.Action -> "act:${key.type}:${key.weight}"
        is Key.KeyCode -> "code:${key.code}"
        is Key.Gap -> "gap:${key.weight}"
    }

    private fun assertSameFrame(aux: AuxRows, bottomArrows: Boolean) {
        val (kor, eng, sym) = modes(aux, bottomArrows)
        assertEquals("줄 수가 같아야 한다", kor.size, eng.size)
        assertEquals("줄 수가 같아야 한다", kor.size, sym.size)
        for (r in kor.indices) {
            assertEquals("$r 번째 줄 키 개수", kor[r].size, eng[r].size)
            assertEquals("$r 번째 줄 키 개수", kor[r].size, sym[r].size)
            for (c in kor[r].indices) {
                val s = shape(kor[r][c])
                assertEquals("[$r,$c] 한↔영 형태", s, shape(eng[r][c]))
                assertEquals("[$r,$c] 한↔기호 형태", s, shape(sym[r][c]))
            }
        }
    }

    @Test fun `펼침 프레임이 세 모드에서 동일`() = assertSameFrame(AuxRows.ALL, bottomArrows = false)
    @Test fun `접힘 프레임이 세 모드에서 동일`() = assertSameFrame(AuxRows.TERMINAL, bottomArrows = true)
    @Test fun `비분할 프레임이 세 모드에서 동일`() = assertSameFrame(AuxRows.ALL, bottomArrows = true)

    @Test fun `모드별로 글자 값은 실제로 다르다`() {
        val (kor, eng, sym) = modes(AuxRows.ALL, bottomArrows = true)
        // 글자 3줄 중 첫 글자줄의 첫 키: ㅂ / q / ! 이어야 한다.
        val row = kor.size - 4   // 보조 3줄 다음이 글자 첫 줄
        assertEquals("ㅂ", (kor[row][0] as Key.Char).output)
        assertEquals("q", (eng[row][0] as Key.Char).output)
        assertEquals("!", (sym[row][0] as Key.Char).output)
    }
}
