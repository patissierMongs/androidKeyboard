package com.example.hangulkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TypoTrackerTest {

    @Test fun `입력-백스페이스-다른키 = 수정으로 집계`() {
        val t = TypoTracker()
        t.onCharPress("ㅅ", 0.9f, 0.5f, 1_000)
        t.onBackspace(1_500)
        t.onCharPress("ㅛ", 0.2f, 0.5f, 2_000)
        assertEquals(1, t.confusionCount("ㅅ", "ㅛ"))
        assertEquals(1L, t.corrections)
        assertEquals(2L, t.taps)
    }

    @Test fun `같은 키 재입력은 수정이 아니다`() {
        val t = TypoTracker()
        t.onCharPress("ㅅ", 0.5f, 0.5f, 1_000)
        t.onBackspace(1_500)
        t.onCharPress("ㅅ", 0.5f, 0.5f, 2_000)
        assertEquals(0L, t.corrections)
    }

    @Test fun `시간 창을 넘기면 수정이 아니다`() {
        val t = TypoTracker()
        t.onCharPress("ㅅ", 0.5f, 0.5f, 1_000)
        t.onBackspace(1_500)
        t.onCharPress("ㅛ", 0.5f, 0.5f, 1_500 + TypoTracker.CORRECTION_WINDOW_MS + 1)
        assertEquals(0L, t.corrections)

        val t2 = TypoTracker()
        t2.onCharPress("ㅅ", 0.5f, 0.5f, 1_000)
        t2.onBackspace(1_000 + TypoTracker.BACKSPACE_WINDOW_MS + 1)  // 한참 뒤 삭제
        t2.onCharPress("ㅛ", 0.5f, 0.5f, 5_000)
        assertEquals(0L, t2.corrections)
    }

    @Test fun `다른 입력이 끼면 수정 추적이 끊긴다`() {
        val t = TypoTracker()
        t.onCharPress("ㅅ", 0.5f, 0.5f, 1_000)
        t.onOtherInput()          // 스페이스 등
        t.onBackspace(1_500)      // 스페이스를 지운 것
        t.onCharPress("ㅛ", 0.5f, 0.5f, 2_000)
        assertEquals(0L, t.corrections)
    }

    @Test fun `백스페이스 연타는 한 번만 후보를 만든다`() {
        val t = TypoTracker()
        t.onCharPress("ㅅ", 0.5f, 0.5f, 1_000)
        t.onBackspace(1_100)
        t.onBackspace(1_150)
        t.onBackspace(1_200)
        t.onCharPress("ㅛ", 0.5f, 0.5f, 2_000)
        assertEquals(1L, t.corrections)
    }

    @Test fun `터치 편향 누적`() {
        val t = TypoTracker()
        t.onCharPress("ㅅ", 0.9f, 0.7f, 1_000)
        t.onCharPress("ㅅ", 0.7f, 0.7f, 2_000)
        val o = t.offsets.getValue("ㅅ")
        assertEquals(0.6f, o[0], 1e-4f)   // (0.4 + 0.2)
        assertEquals(0.4f, o[1], 1e-4f)   // (0.2 + 0.2)
        assertEquals(2f, o[2], 1e-4f)
    }

    @Test fun `JSON 저장-복원 왕복`() {
        val t = TypoTracker()
        t.onCharPress("ㅅ", 0.9f, 0.5f, 1_000)
        t.onBackspace(1_500)
        t.onCharPress("ㅛ", 0.2f, 0.5f, 2_000)
        val restored = TypoTracker().apply { loadJson(t.toJson()) }
        assertEquals(t.taps, restored.taps)
        assertEquals(t.corrections, restored.corrections)
        assertEquals(1, restored.confusionCount("ㅅ", "ㅛ"))
        assertEquals(
            t.offsets.getValue("ㅅ")[0],
            restored.offsets.getValue("ㅅ")[0],
            1e-4f
        )
    }
}
