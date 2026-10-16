package id.iqbaldev.kartrid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.core.Sys

/**
 * Kartrid GBA/GB: shell + notch + label boxart + band nama sistem.
 * `clipFrac` 0..1 = bagian bawah yang ketutup (animasi masuk slot, PLAN §3b).
 *
 * Tiga style (probing 17 Okt 2026):
 * - A: kotak bersih, boxart FULL (Fit, nggak ke-crop)
 * - B: siluet GBA (atas rounded, bawah bevel) + kontak emas + shade
 * - C "RetroShell": shell abu gradasi rounded-top, label beige, boxart KECIL
 *   (~46% lebar, nggak penuh) + pin kontak gold — simplifikasi dari referensi
 *   kartrid retro (index.html/user, 17 Okt 2026). Semua style pakai Fit.
 */
enum class CartStyle { A, B, C }

@Composable
fun Cartridge(
    sys: Sys,
    art: ImageBitmap?,
    w: Float,
    h: Float,
    focused: Boolean,
    modifier: Modifier = Modifier,
    clipFrac: Float = 0f,
    showNotch: Boolean = focused,
    showBand: Boolean = focused,
    style: CartStyle = CartStyle.C,
) {
    val outlineRoom = if (focused) 12f else 0f
    val shape: Shape = when (style) {
        CartStyle.A -> if (sys.rounded) RoundedCornerShape(8.dp) else RectangleShape
        CartStyle.B ->
            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 3.dp, bottomStart = 3.dp)
        CartStyle.C ->
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
    }
    val shell: Color = shellColor(sys.shell, focused)
    val shadow = if (focused) 8f else 5f

    Box(modifier = modifier.size((w + outlineRoom).dp, (h + outlineRoom).dp)) {

        // ring seleksi: rounded + glow lapis (bukan kotak polos di sekeliling)
        if (focused) {
            Box(
                Modifier
                    .size((w + 14).dp, (h + 14).dp)
                    .align(Alignment.Center)
                    .border(2.dp, T.lime.copy(alpha = 0.28f), RoundedCornerShape(22.dp)),
            )
            Box(
                Modifier
                    .size((w + 7).dp, (h + 7).dp)
                    .align(Alignment.Center)
                    .border(2.5.dp, T.lime, RoundedCornerShape(15.dp)),
            )
        }

        // hard shadow
        Box(
            Modifier
                .size(w.dp, h.dp)
                .align(Alignment.Center)
                .offset(x = shadow.dp, y = shadow.dp)
                .background(T.shadow),
        )

        // shell
        Box(
            Modifier
                .size(w.dp, h.dp)
                .align(Alignment.Center)
                .drawWithContent {
                    val bottom = size.height * (1f - clipFrac.coerceIn(0f, 1f))
                    clipRect(right = size.width, bottom = bottom) {
                        this@drawWithContent.drawContent()
                    }
                }
                .clip(shape)
                .then(
                    if (style == CartStyle.C) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFD0CDBC), Color(0xFFB4B0A0), Color(0xFF9B978B)),
                            ),
                        )
                    } else {
                        Modifier.background(shell)
                    },
                )
                .border(2.dp, T.black, shape),
        ) {
            // label + boxart
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .border(2.dp, if (style == CartStyle.C) Color(0xB3171711) else Color(0x99000000))
                    .background(if (style == CartStyle.C) Color(0xFFDED9C8) else T.labelBg),
            ) {
                // inner hairline label (style C) — framing dobel biar gak flat
                if (style == CartStyle.C) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .border(1.dp, Color(0x26000000)),
                    )
                }
                when {
                    art == null -> PlaceholderView(sys, focused)
                    style == CartStyle.C -> CartLabelC(sys, art, w, h, focused)
                    else -> {
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = if (focused) null else ColorFilter.colorMatrix(dimMatrix()),
                        )
                        if (style == CartStyle.B) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .width((w * 0.22f).dp)
                                    .height(9.dp)
                                    .background(Color(0xFFD9AE4E))
                                    .border(1.dp, Color(0x66000000)),
                            )
                        }
                        if (style == CartStyle.B) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x33000000), Color(0x00000000)),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }

            // band nama sistem di atas
            if (showBand) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(Color(0xBD000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sys.longLabel,
                        color = Color.White,
                        fontFamily = MONO,
                        fontSize = 7.5.sp,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }

            if (style == CartStyle.C) {
                // side rail: alur vertikal di sisi shell
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 4.dp, y = 24.dp)
                        .width(2.dp)
                        .height((h * 0.68f).dp)
                        .background(Color(0x26171711)),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = -6.dp, y = 24.dp)
                        .width(2.dp)
                        .height((h * 0.68f).dp)
                        .background(Color(0x26171711)),
                )
            }

            // pin kontak gold ala PCB (style C, di bawah shell)
            if (style == CartStyle.C) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(9.dp)
                        .background(Color(0xFF191912)),
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(6) {
                            Box(Modifier.width(12.dp).height(5.dp).background(Color(0xFFC9A94B)))
                        }
                    }
                }
            }

            // notch / grip atas-kiri (C: grip ganda ala kartrid asli)
            if (showNotch) {
                Box(
                    Modifier
                        .offset(x = 12.dp, y = 0.dp)
                        .size(30.dp, 9.dp)
                        .background(T.bg)
                        .border(2.dp, T.black),
                )
                if (style == CartStyle.C) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = -12.dp, y = 0.dp)
                            .size(16.dp, 6.dp)
                            .background(T.bg)
                            .border(2.dp, T.black),
                    )
                }
            }
        }
    }
}

/** label beige + boxart kecil (nggak penuh) + teks mikro — style C */
@Composable
private fun CartLabelC(sys: Sys, art: ImageBitmap, w: Float, h: Float, focused: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = sys.longLabel,
            color = Color(0xFF4A4230),
            fontFamily = MONO,
            fontSize = 5.4.sp,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(vertical = 5.dp)
                .width((w * 0.5f).dp)
                .height((h * 0.55f).dp)
                .border(1.5.dp, Color(0x99000000))
                .background(Color(0xFF141414)),
        ) {
            Image(
                bitmap = art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (focused) null else ColorFilter.colorMatrix(dimMatrix()),
            )
        }
        Text(
            text = "8-BIT · ${sys.ext.uppercase()}",
            color = Color(0xFF4A4230),
            fontFamily = MONO,
            fontSize = 4.4.sp,
            letterSpacing = 1.1.sp,
            maxLines = 1,
        )
        Text(
            text = "KRT-${sys.label}-01",
            color = Color(0x664A4230),
            fontFamily = MONO,
            fontSize = 3.4.sp,
            letterSpacing = 0.9.sp,
            maxLines = 1,
        )
    }
}

/** placeholder: monogram sistem + caption (belum ada boxart) */
@Composable
private fun PlaceholderView(sys: Sys, focused: Boolean) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = sys.label,
                color = T.mutedFg.copy(alpha = if (focused) 0.85f else 0.5f),
                fontFamily = DISPLAY,
                fontSize = if (sys.rounded) 22.sp else 26.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = "NO ART",
                color = T.mutedFg.copy(alpha = if (focused) 0.55f else 0.35f),
                fontFamily = MONO,
                fontSize = 7.5.sp,
                letterSpacing = 2.2.sp,
            )
        }
    }
}

/** saturate .6 + brightness .62 buat boxart non-fokus */
private fun dimMatrix(): ColorMatrix = ColorMatrix().apply {
    setToSaturation(0.6f)
    for (r in 0..2) for (c in 0..4) this[r, c] = this[r, c] * 0.62f
}