package vn.aow.monika.security

/** Bọc/mở khóa bytes khi lưu đĩa (trên máy thật dùng Android Keystore; test dùng bản không mã hóa). */
interface KeyWrap {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(cipher: ByteArray): ByteArray
    object Plain : KeyWrap {
        override fun wrap(plain: ByteArray) = plain
        override fun unwrap(cipher: ByteArray) = cipher
    }
}
