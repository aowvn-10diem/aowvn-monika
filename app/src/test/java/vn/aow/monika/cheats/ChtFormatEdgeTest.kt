package vn.aow.monika.cheats

import org.junit.Assert.*
import org.junit.Test

class ChtFormatEdgeTest {

    @Test fun parseAcceptsUnquotedValuesAndMissingSpaces() {
        // regex: cheat(\d+)_(desc|code|enable)\s*=\s*"?(.*?)"?\s*$ -> dấu " tùy chọn, \s* quanh "=" tùy chọn
        val txt = "cheat0_desc=Không dấu nháy\ncheat0_code=ABCD-1234\n"
        assertEquals(listOf(CheatEntry("Không dấu nháy", "ABCD-1234", false, "user")), ChtFormat.parse(txt))
    }

    @Test fun enableIsTrueForAnyCasingOfTrue() {
        // on[i] = m.groupValues[3].equals("true", ignoreCase = true)
        val txt = buildString {
            appendLine("cheat0_code = \"1111\""); appendLine("cheat0_enable = TRUE")
            appendLine("cheat1_code = \"2222\""); appendLine("cheat1_enable = True")
            appendLine("cheat2_code = \"3333\""); appendLine("cheat2_enable = tRuE")
        }
        assertEquals(listOf(true, true, true), ChtFormat.parse(txt).map { it.enabled })
    }

    @Test fun enableUnexpectedValuesStayFalse() {
        // chỉ chuỗi "true" (không phân biệt hoa thường) mới bật; "1"/"yes"/"on"/rỗng đều false
        val txt = buildString {
            listOf("1", "yes", "on", "").forEachIndexed { i, v ->
                appendLine("cheat${i}_code = \"${i}${i}\"")
                appendLine("cheat${i}_enable = $v")
            }
        }
        assertEquals(listOf(false, false, false, false), ChtFormat.parse(txt).map { it.enabled })
    }

    @Test fun duplicatedIndexKeepsLastLineForDescAndCode() {
        // desc[i] = ... và code[i] = ... ghi đè theo thứ tự dòng xuất hiện
        val txt = """
            cheat0_desc = "Cũ"
            cheat0_code = "1111"
            cheat0_desc = "Mới"
            cheat0_code = "9999"
            cheat0_enable = false
        """.trimIndent()
        assertEquals(listOf(CheatEntry("Mới", "9999", false, "user")), ChtFormat.parse(txt))
    }

    @Test fun sparseIndexesSortedAndDefaultNameUsesOriginalIndex() {
        // (desc.keys + code.keys).toSortedSet() sắp tăng dần; tên mặc định "Mã ${i + 1}" theo chỉ số gốc
        val txt = """
            cheat5_code = "5555"
            cheat0_code = "0000"
            cheat2_code = "2222"
        """.trimIndent()
        assertEquals(
            listOf(
                CheatEntry("Mã 1", "0000", false, "user"),
                CheatEntry("Mã 3", "2222", false, "user"),
                CheatEntry("Mã 6", "5555", false, "user"),
            ),
            ChtFormat.parse(txt),
        )
    }

    @Test fun crlfLinesParseAndValuesHaveNoCarriageReturn() {
        // text.lineSequence() tách cả \r\n và regex kết thúc \s*$ nuốt ký tự \r còn lại
        val txt = "cheat0_desc = \"Tên\"\r\ncheat0_code = \"1111\"\r\ncheat1_code = \"2222\"\r\n"
        val list = ChtFormat.parse(txt)
        assertEquals(listOf("Tên", "Mã 2"), list.map { it.name })
        assertEquals(listOf("1111", "2222"), list.map { it.code })
    }

    @Test fun leadingSpacesAndTabsAreIgnored() {
        // ^\s* ở đầu regex cho phép thụt lề trước "cheatN_..."
        val txt = "   cheat0_desc = \"A\"\n\t\tcheat0_code = \"1111\"\n"
        assertEquals(listOf(CheatEntry("A", "1111", false, "user")), ChtFormat.parse(txt))
    }

    @Test fun sourceParameterIsPassedToEveryEntry() {
        // CheatEntry(..., source) dùng tham số source của parse (mặc định "user")
        val txt = "cheat0_code = \"1\"\ncheat1_code = \"2\"\n"
        assertTrue(ChtFormat.parse(txt, "auto").all { it.source == "auto" })
        assertTrue(ChtFormat.parse(txt).all { it.source == "user" })
    }

    @Test fun blankDescFallsBackToDefaultName() {
        // desc[i]?.trim().orEmpty().ifBlank { "Mã ${i + 1}" } -> chuỗi rỗng hoặc toàn khoảng trắng đều thành tên mặc định
        val txt = "cheat0_desc = \"\"\ncheat0_code = \"1111\"\ncheat1_desc = \"   \"\ncheat1_code = \"2222\"\n"
        assertEquals(listOf("Mã 1", "Mã 2"), ChtFormat.parse(txt).map { it.name })
    }

    @Test fun serializeWritesCheatsCountAndBlocksInOrder() {
        // appendLine("cheats = ${list.size}") rồi mỗi mục = 1 dòng trống + desc + code + enable
        val txt = ChtFormat.serialize(listOf(CheatEntry("B", "2222", true), CheatEntry("A", "1111", false)))
        assertEquals(
            "cheats = 2\n\ncheat0_desc = \"B\"\ncheat0_code = \"2222\"\ncheat0_enable = true\n\n" +
                "cheat1_desc = \"A\"\ncheat1_code = \"1111\"\ncheat1_enable = false\n",
            txt,
        )
    }

    @Test fun serializeReplacesQuotesInNameButNotInCode() {
        // e.name.replace('"', '\'') chỉ áp cho desc; dòng code ghi thẳng e.code
        val txt = ChtFormat.serialize(listOf(CheatEntry("Say \"hi\"", "02 \"AA\" 03", true)))
        assertTrue(txt, txt.contains("cheat0_desc = \"Say 'hi'\""))
        assertTrue(txt, txt.contains("cheat0_code = \"02 \"AA\" 03\""))
    }

    @Test fun roundTripKeepsOrderAndMixedEnabledFlags() {
        // serialize ghi đúng desc/code/enable mà parse đọc lại được
        val list = listOf(
            CheatEntry("A", "1111", true),
            CheatEntry("B", "2222", false),
            CheatEntry("C", "3333 4444", true),
        )
        assertEquals(list, ChtFormat.parse(ChtFormat.serialize(list)))
    }

    @Test fun quotedCodeIsTrimmedBeforeUse() {
        // code[i]?.trim() nên khoảng trắng nằm trong cặp nháy bị cắt bỏ
        val txt = "cheat0_code = \"  1234  \"\n"
        assertEquals("1234", ChtFormat.parse(txt).single().code)
    }

    @Test fun enableWithoutDescOrCodeProducesNoEntry() {
        // indexes = (desc.keys + code.keys) nên chỉ có cheatN_enable thì danh sách rỗng
        val txt = "cheats = 1\ncheat0_enable = true\n"
        assertTrue(ChtFormat.parse(txt).isEmpty())
    }
}
