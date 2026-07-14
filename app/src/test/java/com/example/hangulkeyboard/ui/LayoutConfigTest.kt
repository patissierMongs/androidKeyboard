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

    @Test fun `레이어 분리 형식(grid+layers)이 파싱된다`() {
        val json = """
          { "unfolded": {
              "grid": [ [ {"w":1.25}, {"rep":true}, {"gap":0.5}, {"act":"SPACE"} ] ],
              "layers": {
                "kor": [ [ "ㅂ", {"v":"ㅅ","alt":"ㅆ"} ] ],
                "eng": [ [ "q", "t" ] ],
                "sym": [ [ "!", "%" ] ]
              }
          } }
        """.trimIndent()
        val un = LayoutConfig.parse(json).forFolded(false)
        val kor = un[KeyboardMode.KOREAN]!![0]
        val eng = un[KeyboardMode.ENGLISH]!![0]
        // 폭·반복은 구조(grid)에서, 값·대체키는 레이어에서 온다.
        assertEquals(1.25f, (kor[0] as Key.Char).weight)
        assertEquals("ㅂ", (kor[0] as Key.Char).output)
        assertEquals("q", (eng[0] as Key.Char).output)
        assertTrue((kor[1] as Key.Char).repeat)
        assertEquals("ㅆ", (kor[1] as Key.Char).alt)
        assertEquals("t", (eng[1] as Key.Char).output)
        // 빈 공간과 기능 키는 모든 레이어 공통.
        assertEquals(0.5f, (kor[2] as Key.Gap).weight)
        assertEquals(ActionType.SPACE, (eng[3] as Key.Action).type)
    }

    @Test fun `센터 패드 버튼(center)이 파싱된다`() {
        val json = """
          { "unfolded": {
              "grid": [ [ {} ] ],
              "layers": { "kor": [["ㅂ"]], "eng": [["q"]], "sym": [["!"]] },
              "center": [ [ {"act":"SELECT","l":"선택"} ],
                          [ {"code":"ESC","l":"esc"}, {"act":"COPY","l":"복사"} ] ]
          } }
        """.trimIndent()
        val center = LayoutConfig.parse(json).centerForFolded(false)
        assertEquals(2, center.size)
        assertEquals(ActionType.SELECT, (center[0][0] as Key.Action).type)
        assertEquals("esc", (center[1][0] as Key.KeyCode).label)
        assertEquals(ActionType.COPY, (center[1][1] as Key.Action).type)
    }

    @Test fun `기본 JSON 은 레이어 분리 형식으로 나오고 센터 패드를 포함한다`() {
        val json = LayoutConfig.defaultJson()
        assertTrue(json.contains("\"grid\""))
        assertTrue(json.contains("\"layers\""))
        val custom = LayoutConfig.parse(json)
        assertTrue(custom.centerForFolded(false).isNotEmpty())
        assertTrue(custom.centerForFolded(true).isNotEmpty())
    }
}
