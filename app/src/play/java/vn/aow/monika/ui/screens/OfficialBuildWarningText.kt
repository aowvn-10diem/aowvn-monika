package vn.aow.monika.ui.screens

/**
 * [A78] V154 · flavor `play`: bản Play KHÔNG được mời người dùng tải app từ website ngoài Google Play
 * (chính sách Play, A65/A68) → câu cảnh báo **không** nêu tên miền, không link, không lời mời tải.
 * Bản GitHub giữ câu có monika.aow.vn (xem `app/src/github/.../OfficialBuildWarningText.kt`).
 */
const val OFFICIAL_BUILD_WARNING =
    "Bản này không phải bản phát hành trên Google Play. Nếu bạn cài từ nguồn khác, hãy kiểm tra lại nguồn cài."
