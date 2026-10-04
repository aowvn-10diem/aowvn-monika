package vn.aow.monika

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.runner.MkxpConfigWriter
import java.io.File

class MkxpConfigWriterTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject
    private fun File.touch(vararg p: String) { p.forEach { File(this, it).apply { parentFile.mkdirs(); writeText("x") } } }

    @Test fun nhanRaPhienBanTheoTep() {
        assertEquals(1, MkxpConfigWriter.detectRgss(tmp.newFolder().apply { touch("Game.rgssad") }))
        assertEquals(1, MkxpConfigWriter.detectRgss(tmp.newFolder().apply { touch("Data/Scripts.rxdata") }))
        assertEquals(2, MkxpConfigWriter.detectRgss(tmp.newFolder().apply { touch("game.RGSS2A") }))
        assertEquals(2, MkxpConfigWriter.detectRgss(tmp.newFolder().apply { touch("Data/Scripts.rvdata") }))
        assertEquals(3, MkxpConfigWriter.detectRgss(tmp.newFolder().apply { touch("Game.rgss3a") }))
        assertEquals(3, MkxpConfigWriter.detectRgss(tmp.newFolder().apply { touch("Data/Scripts.rvdata2") }))
        assertEquals(0, MkxpConfigWriter.detectRgss(tmp.newFolder()))
    }

    @Test fun taoMoiKhiChuaCoTep() {
        val o = obj(MkxpConfigWriter.build(null, 3, listOf("/rtp/ace")))
        assertEquals(3, o["rgssVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf("/rtp/ace"), (o["RTP"] as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test fun preloadGopVaGiuMucCuaGame() {
        val o = obj(MkxpConfigWriter.build("""{"preloadScript": ["/game/patch.rb"]}""", 2, emptyList(), preload = listOf("/files/rgss-compat/monika-win32api.rb")))
        assertEquals(listOf("/game/patch.rb", "/files/rgss-compat/monika-win32api.rb"), (o["preloadScript"] as JsonArray).map { it.jsonPrimitive.content })
        val again = obj(MkxpConfigWriter.build(MkxpConfigWriter.build(null, 2, emptyList(), preload = listOf("/a.rb")), 2, emptyList(), preload = listOf("/a.rb")))
        assertEquals(listOf("/a.rb"), (again["preloadScript"] as JsonArray).map { it.jsonPrimitive.content }) // không trùng khi ghi lại
        assertEquals(null, obj(MkxpConfigWriter.build(null, 2, emptyList()))["preloadScript"]) // không có preload → không thêm khóa
    }

    @Test fun giuKhoaLaVaChuThichCuaGame() {
        val old = """
            // chú thích nguyên dòng
            {
              "windowTitle": "Game Việt Hóa",
              "JITEnable": true,
              "RTP": ["/cu"],
            }
        """.trimIndent()
        val o = obj(MkxpConfigWriter.build(old, 1, listOf("/cu", "/moi"), listOf("MS Gothic>Noto Sans")))
        assertEquals("Game Việt Hóa", o["windowTitle"]!!.jsonPrimitive.content)
        assertEquals("true", o["JITEnable"]!!.jsonPrimitive.content)
        assertEquals(listOf("/cu", "/moi"), (o["RTP"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals(listOf("MS Gothic>Noto Sans"), (o["fontSub"] as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test fun tonTrongRgssVersionCuaGame() {
        assertEquals("2", obj(MkxpConfigWriter.build("""{"rgssVersion": 2}""", 3, emptyList()))["rgssVersion"]!!.jsonPrimitive.content)
        assertEquals("3", obj(MkxpConfigWriter.build("""{"rgssVersion": 0}""", 3, emptyList()))["rgssVersion"]!!.jsonPrimitive.content)
    }

    @Test fun tepHongKhongLamSapVaGhiDuocRaFile() {
        val d = tmp.newFolder().apply { touch("Game.rgss3a") }
        File(d, "mkxp.json").writeText("{ khong phai json")
        val f = MkxpConfigWriter.write(d, listOf("/rtp"))
        assertTrue(f.readText().contains("\"rgssVersion\": 3"))
    }
}
