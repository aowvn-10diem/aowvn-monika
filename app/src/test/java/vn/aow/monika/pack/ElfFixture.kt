package vn.aow.monika.pack

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Dựng byte của một `.so` ELF64/arm64 tối thiểu để test: luôn có header hợp lệ (ABI arm64), và tuỳ chọn một
 * segment `PT_NOTE` chứa note `GNU` build-id — đúng thứ `PackTransaction.validateElf` và `buildId` đọc.
 */
internal object ElfFixture {
    const val BUILD_ID = "9f2c3b4a5d6e7f8091a2b3c4d5e6f708192a3b4c"

    private const val HEADER = 64
    private const val PHDR = 56
    private const val NOTE_AT = HEADER + PHDR

    /** `.so` arm64 không có note build-id (giống lõi buildbot đã strip). */
    fun arm64(): ByteArray = elf(null)

    /** `.so` arm64 có `NT_GNU_BUILD_ID` với mô tả [id] (mặc định [BUILD_ID]). */
    fun arm64WithBuildId(id: ByteArray = hex(BUILD_ID)): ByteArray = elf(id)

    fun hex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun elf(buildId: ByteArray?): ByteArray {
        val noteLen = if (buildId == null) 0 else 12 + 4 + align4(buildId.size)
        val buffer = ByteBuffer.allocate(NOTE_AT + noteLen).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf(127, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        buffer.put(2) // ELFCLASS64
        buffer.put(1) // little-endian
        buffer.put(1) // EI_VERSION
        buffer.put(0)
        buffer.position(16); buffer.putShort(3); buffer.putShort(183); buffer.putInt(1) // ET_DYN, EM_AARCH64, EV_CURRENT
        buffer.position(32); buffer.putLong(HEADER.toLong()) // e_phoff
        buffer.position(52); buffer.putShort(HEADER.toShort()); buffer.putShort(PHDR.toShort())
        buffer.putShort((if (buildId == null) 0 else 1).toShort()) // e_ehsize, e_phentsize, e_phnum
        if (buildId != null) {
            buffer.position(HEADER); buffer.putInt(4); buffer.putInt(4) // PT_NOTE, PF_R
            buffer.putLong(NOTE_AT.toLong()) // p_offset
            buffer.position(HEADER + 32); buffer.putLong(noteLen.toLong()); buffer.putLong(noteLen.toLong())
            buffer.position(NOTE_AT); buffer.putInt(4); buffer.putInt(buildId.size); buffer.putInt(3) // NT_GNU_BUILD_ID
            buffer.put(byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0))
            buffer.put(buildId)
        }
        return buffer.array()
    }

    private fun align4(n: Int) = (n + 3) and -4
}
