// Nguyên bản từ Azahar Emulator Project (GPLv2 hoặc mới hơn) — giữ nguyên vì mã native gọi theo tên.
// Copyright 2023 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.cheats.model

import androidx.annotation.Keep

@Keep
object CheatEngine {
    external fun loadCheatFile(titleId: Long)
    external fun saveCheatFile(titleId: Long)

    external fun getCheats(): Array<Cheat>

    external fun addCheat(cheat: Cheat?)
    external fun removeCheat(index: Int)
    external fun updateCheat(index: Int, newCheat: Cheat?)
}
