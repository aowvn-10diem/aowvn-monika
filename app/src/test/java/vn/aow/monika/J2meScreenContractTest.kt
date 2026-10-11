package vn.aow.monika

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import vn.aow.monika.runner.J2ME_MENU_FALLBACK_ICON
import vn.aow.monika.runner.j2meMenuIcon
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * V77: hợp đồng màn chơi Java (J2ME). Robolectric không nạp được lớp `javax.*` của J2ME nên không mở được
 * `MicroActivity`; test đọc thẳng tài nguyên XML của module `j2me/`:
 *  - không còn thanh công cụ trên đầu, chỉ một nút menu nổi có mô tả;
 *  - mọi mục của menu J2ME gốc (kể cả chụp màn hình) đều có icon riêng trong menu Monika.
 */
class J2meScreenContractTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val j2meRes = File("../j2me/src/main/res")

    private fun parse(path: String): Element {
        val f = File(j2meRes, path)
        assertTrue("Thiếu ${f.path}", f.isFile)
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return dbf.newDocumentBuilder().parse(f).documentElement
    }

    private fun Element.elements(tag: String): List<Element> =
        (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    @Test fun playScreenHasNoTopToolbarAndOneMenuButton() {
        val root = parse("layout/activity_micro.xml")
        val toolbar = root.elements("androidx.appcompat.widget.Toolbar").single()
        assertEquals("gone", toolbar.attr("visibility"))

        val buttons = root.elements("ImageButton")
        assertEquals(1, buttons.size)
        val menu = buttons.single()
        assertEquals("@+id/monika_menu_button", menu.attr("id"))
        assertTrue(menu.attr("contentDescription").isNotBlank())
        val gravity = menu.attr("layout_gravity")
        assertTrue(gravity.contains("top") && gravity.contains("end"))
        // Nút nằm sau lớp phủ của game để nhận chạm trước.
        val kids = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
        assertTrue(kids.indexOf(menu) > kids.indexOfFirst { it.attr("id") == "@+id/overlay" })

        val strings = parse("values/strings.xml").elements("string")
        assertTrue(strings.any { it.getAttribute("name") == "monika_menu_button" && it.textContent.isNotBlank() })
    }

    @Test fun everyJ2meMenuItemHasMonikaIcon() {
        val leaves = parse("menu/midlet_displayable.xml").elements("item")
            .filter { it.elements("menu").isEmpty() }
            .map { it.attr("id").removePrefix("@+id/") }
        assertTrue(leaves.contains("action_take_screenshot"))
        assertTrue(leaves.contains("action_exit_midlet"))
        val missing = leaves.filter { j2meMenuIcon(it) == J2ME_MENU_FALLBACK_ICON }
        assertEquals("Mục menu J2ME chưa có icon trong menu Monika: $missing", emptyList<String>(), missing)
    }

    /**
     * V85c / A33: khi dùng bàn phím Monika (mặt phím luôn đục) thanh "Độ mờ" và "Ép độ mờ" phải bị khóa kèm ghi chú
     * "Bàn phím Monika luôn đục để dễ đọc", không để cài đặt chết im lặng; kiểu bàn phím khác giữ nguyên.
     * Robolectric không mở được `ConfigActivity`, nên đọc thẳng layout, chuỗi và mã nguồn.
     */
    @Test fun opacityIsLockedWithNoteWhenMonikaKeyboardIsUsed() {
        val config = parse("layout/activity_config.xml")
        val note = config.elements("TextView").single { it.attr("id") == "@+id/tvOpacityNote" }
        assertEquals("@string/monika_keyboard_opaque", note.attr("text"))
        assertEquals("gone", note.attr("visibility")) // chỉ hiện khi dùng bàn phím Monika
        val force = config.elements("com.google.android.material.switchmaterial.SwitchMaterial").single { it.attr("id") == "@+id/cxVKForceOpacity" }
        assertEquals("@id/tvOpacityNote", force.attr("layout_constraintTop_toBottomOf").ifEmpty {
            force.getAttributeNS("http://schemas.android.com/apk/res-auto", "layout_constraintTop_toBottomOf")
        })
        val text = parse("values/strings.xml").elements("string").single { it.getAttribute("name") == "monika_keyboard_opaque" }.textContent
        assertEquals("Bàn phím Monika luôn đục để dễ đọc", text)
        // Khóa gắn với đúng kiểu bàn phím Monika, gọi lúc nạp cấu hình và mỗi khi bật/tắt nhóm phím ảo; kiểu khác không bị khóa.
        val src = File("../j2me/src/main/java/ru/playsoftware/j2meloader/config/ConfigActivity.java").readText()
        val body = src.substringAfter("private void applyMonikaOpacityLock()").substringBefore("\n\t}\n")
        assertTrue(body.contains("params.vkType == VirtualKeyboard.TYPE_MONIKA"))
        assertTrue(body.contains("sbVKAlpha.setEnabled(!monika)") && body.contains("cxVKForceOpacity.setEnabled(!monika)"))
        assertTrue(body.contains("tvOpacityNote.setVisibility(monika &&"))
        assertTrue("phải gọi khóa ở ít nhất 2 chỗ (nạp cấu hình + bật/tắt bàn phím ảo)",
            Regex("applyMonikaOpacityLock\\(\\);").findAll(src).count() >= 2)
    }
}
