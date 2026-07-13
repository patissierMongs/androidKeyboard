package com.example.hangulkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutConfigTest {

    @Test fun `기본 프레임 JSON 이 다시 파싱된다`() {
        val json = LayoutConfig.defaultJson()
        val custom = LayoutConfig.parse(json)
        assertTrue(!custom.isEmpty)
        val un = custom.forFolded(false)
        // 세 모드가 모두 있고, 프레임에서 나왔으니 구조가 동일해야 한다.
        assertEquals(un[KeyboardMode.KOREAN]!!.size, un[KeyboardMode.ENGLISH]!!.size)
        assertEquals(un[KeyboardMode.KOREAN]!!.size, un[KeyboardMode.SYMBOLS]!!.size)
    }

    @Test fun `프레임 슬롯이 레이어별 값으로 투영된다`() {
        val json = """
          { "unfolded": { "frame": [
              [ {"kor":"ㅂ","eng":"q","sym":"!"}, {"k":"+"}, {"act":"SPACE"} ]
          ] } }
        """.trimIndent()
        val un = LayoutConfig.parse(json).forFolded(false)
        val kor = un[KeyboardMode.KOREAN]!![0]
        val eng = un[KeyboardMode.ENGLISH]!![0]
        val sym = un[KeyboardMode.SYMBOLS]!![0]
        assertEquals("ㅂ", (kor[0] as Key.Char).output)
        assertEquals("q", (eng[0] as Key.Char).output)
        assertEquals("!", (sym[0] as Key.Char).output)
        // {"k":"+"} 는 세 레이어 동일
        assertEquals("+", (kor[1] as Key.Char).output)
        assertEquals("+", (sym[1] as Key.Char).output)
        // 기능 키는 그대로
        assertEquals(ActionType.SPACE, (kor[2] as Key.Action).type)
    }

    @Test fun `구형 모드별 형식도 호환된다`() {
        val json = """{ "korean":[["ㅂ","ㅈ"]], "english":[["q","w"]], "symbols":[["!","@"]] }"""
        val un = LayoutConfig.parse(json).forFolded(false)
        assertEquals("ㅂ", (un[KeyboardMode.KOREAN]!![0][0] as Key.Char).output)
        assertEquals("@", (un[KeyboardMode.SYMBOLS]!![0][1] as Key.Char).output)
    }
}
