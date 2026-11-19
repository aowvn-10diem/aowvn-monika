package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.aow.monika.config.ConfigEndpoints

/**
 * V206: [ConfigEndpoints.signedConfigUrl] gọi TRỰC TIẾP (không qua hằng URL dựng sẵn) để chứng minh
 * 3 nhánh chặn và nhánh giữ query — đây là chỗ dễ tải nhầm URL thường rồi verify hỏng im lặng.
 */
class ConfigEndpointsTest {
    @Test fun urlRongTraNull() {
        assertNull(ConfigEndpoints.signedConfigUrl(""))
        assertNull(ConfigEndpoints.signedConfigUrl("   "))
    }

    @Test fun urlKhongPhaiHttpTraNull() {
        assertNull(ConfigEndpoints.signedConfigUrl("monika.aow.vn/config.json"))
    }

    @Test fun urlKhongKetThucBangConfigJsonThiKhongNhan() {
        assertNull(ConfigEndpoints.signedConfigUrl("https://monika.aow.vn/cau-hinh.json"))
        assertNull(ConfigEndpoints.signedConfigUrl("https://monika.aow.vn/config.json.bak"))
        assertNull(ConfigEndpoints.signedConfigUrl("https://monika.aow.vn/config.json/"))
    }

    @Test fun urlCoQueryThiGiuNguyenQuery() {
        assertEquals(
            "https://monika.aow.vn/config.signed.json?v=9",
            ConfigEndpoints.signedConfigUrl("https://monika.aow.vn/config.json?v=9"),
        )
        assertEquals(
            "https://worker.example.workers.dev/abc/config.signed.json?build=12&x=1",
            ConfigEndpoints.signedConfigUrl("https://worker.example.workers.dev/abc/config.json?build=12&x=1"),
        )
    }

    @Test fun urlDungMauThiDoiDuoiThanhBanKy() {
        assertEquals(
            "https://monika.aow.vn/config.signed.json",
            ConfigEndpoints.signedConfigUrl(ConfigEndpoints.PAGES_CONFIG_URL),
        )
        assertEquals(
            ConfigEndpoints.signedConfigUrl(ConfigEndpoints.PAGES_CONFIG_URL),
            ConfigEndpoints.PAGES_SIGNED_CONFIG_URL,
        )
    }
}
