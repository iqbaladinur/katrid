package id.iqbaldev.kartrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.iqbaldev.kartrid.R

/** Design tokens — sama persis dengan mockup landscape yang di-approve (riso-volt dark). */
object T {
    val bg = Color(0xFF16121F)
    val surface = Color(0xFF201A2C)
    val fg = Color(0xFFEAE6F2)
    val muted = Color(0xFF2A2338)
    val mutedFg = Color(0xFFB0A8C2)
    val hairline = Color(0xFF362E47)
    val ink = Color(0xFF5B5270)
    val shadow = Color(0xFF050309)
    val violet = Color(0xFFA98BFF)
    val lime = Color(0xFFD4F24E)
    val black = Color.Black
    val labelBg = Color(0xFF111111)
}

val SANS = FontFamily(
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

val DISPLAY = FontFamily(Font(R.font.archivo_black, FontWeight.Normal))

val MONO = FontFamily(
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

const val BASE_W = 960f
const val BASE_H = 640f

/**
 * Layar KPA ngerepot density 240 (mdpi) → 1 dp = 1,5 px.
 * Kanvas desain digambar 1:1 px, jadi elemen interaktif yang di-ukur pakai dp
 * konsekuennya 1,5× lebih kecil dari normal. Pengali ini narik balik ke ukuran
 * sentuh wajar (minimal ~43-48dp).
 */
const val TOUCH = 1.5f

/** nilai desain (px mockup) → dp sentuh yang dibesarkan biar ≥ 43-48dp */
val Float.tdp: Dp get() = (this * TOUCH).dp

/**
 * sp yang digambar 1:1 px sama kayak body text yang "terlalu kecil" secara
 * persepsi di layar 240dpi. Naikkan global supaya teks kebaca seperti app
 * normal (17sp terasa 17sp, bukan 17px).
 */
const val TEXT_SCALE = 1.25f

/**
 * Kanvas desain 960x640 px (layar KONKR Pocket Advance).
 * Density di-override supaya 1.dp == 1 px mockup — jadi semua angka di kode
 * sama persis dengan mockup. Di layar lain seluruh UI di-scale proporsional
 * (letterbox kalau aspeknya beda).
 */
@Composable
fun MockCanvas(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(T.bg),
        contentAlignment = Alignment.Center,
    ) {
        val wPx = with(d) { maxWidth.toPx() }
        val hPx = with(d) { maxHeight.toPx() }
        val sw = if (wPx > 0f) wPx / BASE_W else 1f
        val sh = if (hPx > 0f) hPx / BASE_H else sw
        val scale = minOf(sw, sh).coerceAtLeast(0.05f)
        CompositionLocalProvider(LocalDensity provides Density(scale, TEXT_SCALE)) {
            Box(modifier = Modifier.size(BASE_W.dp, BASE_H.dp)) { content() }
        }
    }
}

/** saturate + brightness — buat kartrid non-fokus (mockup: .6 / .62) */
fun dimColor(argb: Long, sat: Float = 0.6f, bright: Float = 0.62f): Color {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val nr = ((l + (r - l) * sat) * bright).coerceIn(0f, 1f)
    val ng = ((l + (g - l) * sat) * bright).coerceIn(0f, 1f)
    val nb = ((l + (b - l) * sat) * bright).coerceIn(0f, 1f)
    return Color(nr, ng, nb, 1f)
}

/** warna shell untuk state fokus / nggak */
fun shellColor(argb: Long, focused: Boolean): Color =
    if (focused) Color(argb) else dimColor(argb)
