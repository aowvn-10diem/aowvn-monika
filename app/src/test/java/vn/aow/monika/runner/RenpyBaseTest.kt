package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RenpyBaseTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tree(): File = tmp.newFolder("Game").also { File(it, "game").mkdirs(); File(it, "renpy").mkdirs() }

    @Test fun thuMucGameChuaGame() = tree().let { assertEquals(it, RenpyBase.resolve(it)) }

    @Test fun chonThangThuMucGame() = tree().let { assertEquals(it, RenpyBase.resolve(File(it, "game"))) }

    @Test fun fileTrongCay() {
        val root = tree()
        val sh = File(root, "Game.sh").apply { writeText("#!/bin/sh") }
        assertEquals(root, RenpyBase.resolve(sh))
        val deep = File(root, "game/scripts").apply { mkdirs() }
        assertEquals(root, RenpyBase.resolve(File(deep, "x.rpyc").apply { writeText("") }))
    }

    @Test fun khongPhaiRenpy() {
        val d = tmp.newFolder("Other")
        assertNull(RenpyBase.resolve(d))
        assertNull(RenpyBase.resolve(File(d, "a.exe").apply { writeText("") }))
        assertNull(RenpyBase.resolve(null))
    }
}
