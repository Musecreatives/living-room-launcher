package com.livingroom.launcher

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// ShaddaiTV tokens (shaddai-enterprises/brand/shaddaitv/tokens.css).
val Ink0 = Color(0xFF0A1622) // --stv-ink
val Ink2 = Color(0xFF152738)
val Fg = Color(0xFFF7F9FC) // --stv-paper
val Fg2 = Color(0xFFC9D4DF)
val BlueLight = Color(0xFF8FC1E8) // --stv-blue-light: blue text on ink
val Blue = Color(0xFF2E75C4) // --stv-blue: fills only

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(
    res: Int,
    vararg weights: FontWeight,
) = FontFamily(
    weights.map { w -> Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
)

// ShaddaiTV brand type: Manrope for everything (display and body share one family).
val Manrope = variableFamily(R.font.manrope_variable, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
val Sora = Manrope
val Figtree = Manrope

/** True when the system "Remove animations" setting is on. */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
