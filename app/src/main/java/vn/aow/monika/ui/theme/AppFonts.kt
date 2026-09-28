package vn.aow.monika.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import vn.aow.monika.R

/** Manrope (Google Fonts, SIL OFL) — 1 file variable font cho mọi độ đậm. */
@OptIn(ExperimentalTextApi::class)
object AppFonts {
    private fun w(weight: FontWeight) =
        Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

    val family = FontFamily(w(FontWeight.Normal), w(FontWeight.Medium), w(FontWeight.SemiBold), w(FontWeight.Bold))
}
