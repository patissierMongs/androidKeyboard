package com.example.hangulkeyboard.hangul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 두벌식 오토마타 검증. README 의 검증 표 + 백스페이스/문맥 상태 케이스.
 */
class HangulComposerTest {

    /** 자모 문자열을 차례로 넣고 (확정분 + 남은 조합분) 전체 결과를 돌려준다. */
    private fun compose(jamos: String): String {
        val composer = HangulComposer()
        val out = StringBuilder()
        jamos.forEach { out.append(composer.input(it)) }
        out.append(composer.flush())
        return out.toString()
    }

    @Test fun `기본 음절 조합`() {
        assertEquals("안녕하세요", compose("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
        assertEquals("꽃", compose("ㄲㅗㅊ"))
    }

    @Test fun `겹받침`() {
        assertEquals("닭", compose("ㄷㅏㄹㄱ"))
        assertEquals("값", compose("ㄱㅏㅂㅅ"))
    }

    @Test fun `복합 모음`() {
        assertEquals("왜", compose("ㅇㅗㅐ"))
        assertEquals("의", compose("ㅇㅡㅣ"))
        assertEquals("뭐", compose("ㅁㅜㅓ"))
    }

    @Test fun `받침 이동`() {
        assertEquals("아나", compose("ㅇㅏㄴㅏ"))
    }

    @Test fun `겹받침 분리 이동`() {
        assertEquals("달가", compose("ㄷㅏㄹㄱㅏ"))
        assertEquals("갑사", compose("ㄱㅏㅂㅅㅏ"))
    }

    @Test fun `받침 불가 자음은 새 글자로`() {
        // ㄸ 은 받침이 될 수 없어 앞 글자를 확정하고 새 초성이 된다.
        assertEquals("가따", compose("ㄱㅏㄸㅏ"))
    }

    @Test fun `자음 연속 입력`() {
        // 초성만 있는데 자음이 또 오면 이전 초성을 확정.
        assertEquals("ㄱㄴ", compose("ㄱㄴ"))
    }

    @Test fun `모음 단독 시작`() {
        assertEquals("ㅏ", compose("ㅏ"))
        assertEquals("아", compose("ㅇㅏ"))
    }

    @Test fun `조합 불가 문자는 그대로 통과`() {
        assertEquals("가1", compose("ㄱㅏ1"))
    }

    @Test fun `백스페이스 - 받침부터 자모 단위로 지운다`() {
        val c = HangulComposer()
        "ㄷㅏㄹㄱ".forEach { c.input(it) }
        assertEquals("닭", c.composing)
        assertTrue(c.backspace())   // 겹받침 ㄺ → ㄹ
        assertEquals("달", c.composing)
        assertTrue(c.backspace())   // 받침 제거
        assertEquals("다", c.composing)
        assertTrue(c.backspace())   // 중성 제거
        assertEquals("ㄷ", c.composing)
        assertTrue(c.backspace())   // 초성 제거
        assertEquals("", c.composing)
        assertFalse(c.backspace())  // 더 지울 것 없음
    }

    @Test fun `백스페이스 - 복합 모음은 단계적으로 분해`() {
        val c = HangulComposer()
        "ㅇㅗㅐ".forEach { c.input(it) }
        assertEquals("왜", c.composing)
        assertTrue(c.backspace())   // ㅙ → ㅗ
        assertEquals("오", c.composing)
    }

    @Test fun `expectingVowel - 초성만 있을 때만 참`() {
        val c = HangulComposer()
        assertFalse(c.expectingVowel)
        c.input('ㄱ')
        assertTrue(c.expectingVowel)
        c.input('ㅏ')
        assertFalse(c.expectingVowel)
        c.input('ㄴ')                 // 받침 상태
        assertFalse(c.expectingVowel)
    }
}
