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

// Living Room tokens (jellyfin-tv/prototype): near-black ink scale, light foreground.
val Ink0 = Color(0xFF06080C)
val Ink2 = Color(0xFF141A24)
val Fg = Color(0xFFEEF2F7)
val Fg2 = Color(0xFFB3BDCB)

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(
    res: Int,
    vararg weights: FontWeight,
) = FontFamily(
    weights.map { w -> Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
)

val Sora = variableFamily(R.font.sora_variable, FontWeight.SemiBold, FontWeight.Bold)
val Figtree = variableFamily(R.font.figtree_variable, FontWeight.Medium, FontWeight.SemiBold)

/** True when the system "Remove animations" setting is on. */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
