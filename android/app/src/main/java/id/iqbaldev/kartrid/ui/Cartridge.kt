package id.iqbaldev.kartrid.ui

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.R
import id.iqbaldev.kartrid.core.Sys
import androidx.compose.ui.graphics.asImageBitmap

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
 * - D "3D": shell photoreal hasil render (drawable-nodpi/cart_gba.png) + boxart
 *   di-overlay pas label rect — 17 Okt 2026, user: "cari file 3d cartridge gba".
 * - E "Switch": kartrid ala Nintendo Switch — card rounded tipis, top cap WARNA
 *   per-sistem (sys.shell), area art rounded di bawah + micro label — 17 Okt.
 */
enum class CartStyle { A, B, C, D, E }

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
    style: CartStyle = CartStyle.E,
) {
    if (style == CartStyle.D) {
        Cartridge3D(sys, art, w, h, focused, modifier, clipFrac)
        return
    }
    if (style == CartStyle.E) {
        CartridgeSwitch(sys, art, w, h, focused, modifier, clipFrac)
        return
    }
    val outlineRoom = if (focused) 12f else 0f
    val shape: Shape = when (style) {
        CartStyle.A -> if (sys.rounded) RoundedCornerShape(8.dp) else RectangleShape
        CartStyle.B ->
            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 3.dp, bottomStart = 3.dp)
        CartStyle.C ->
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
        CartStyle.D -> RoundedCornerShape(8.dp) // unreachable (early return), biar exhaustive
        CartStyle.E -> RoundedCornerShape(8.dp) // unreachable (early return), biar exhaustive
    }
    val shell: Color = shellColor(sys.shell, focused)
    val shadow = if (focused) 8f else 5f

    Box(modifier = modifier.size((w + outlineRoom).dp, (h + outlineRoom).dp)) {

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
        // art 1:1 square + rounded (user: "covernya itu 1:1 aja, rounded gambar nya")
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .size((h * 0.52f).dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141414))
                .border(1.5.dp, Color(0x99000000), RoundedCornerShape(10.dp)),
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

/** Style D: shell render 3D (cart_gba.png) + boxart di label rect */
@Composable
private fun Cartridge3D(
    sys: Sys,
    art: ImageBitmap?,
    w: Float,
    h: Float,
    focused: Boolean,
    modifier: Modifier = Modifier,
    clipFrac: Float = 0f,
) {
    val ctx = LocalContext.current
    val shell = remember {
        runCatching {
            BitmapFactory.decodeResource(ctx.resources, R.drawable.cart_gba)?.asImageBitmap()
        }.getOrNull()
    }
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 6.dp)

    Box(
        modifier
            .size(w.dp, h.dp)
            .drawWithContent {
                val bottom = size.height * (1f - clipFrac.coerceIn(0f, 1f))
                clipRect(right = size.width, bottom = bottom) { this@drawWithContent.drawContent() }
            }
            .clip(shape),
    ) {
        if (shell != null) {
            Image(
                bitmap = shell,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (focused) null else ColorFilter.colorMatrix(dimMatrix()),
            )
        }
        // label rect (fraksi dari asset cart_gba.png) — overlay boxart biar "nempel"
        val lx = w * 0.115f
        val ly = h * 0.205f
        val lw = w * 0.770f
        val lh = h * 0.650f
        Box(
            Modifier
                .offset(x = lx.dp, y = ly.dp)
                .size(lw.dp, lh.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF101014)),
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = sys.label,
                        color = T.mutedFg.copy(alpha = 0.8f),
                        fontFamily = DISPLAY,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
    }
}

/** Style E: kartrid ala Nintendo Switch — card rounded, top cap warna sistem, art bawah */
@Composable
private fun CartridgeSwitch(
    sys: Sys,
    art: ImageBitmap?,
    w: Float,
    h: Float,
    focused: Boolean,
    modifier: Modifier = Modifier,
    clipFrac: Float = 0f,
) {
    val shape = RoundedCornerShape(20.dp)
    val capH = h * 0.17f
    val artY = capH + 12f
    val artH = h - artY - 30f

    Box(
        modifier
            .size(w.dp, h.dp)
            .shadow(10.dp, shape, clip = false, ambientColor = Color(0x80151515), spotColor = Color(0x55151515))
            .drawWithContent {
                val bottom = size.height * (1f - clipFrac.coerceIn(0f, 1f))
                clipRect(right = size.width, bottom = bottom) { this@drawWithContent.drawContent() }
            }
            .clip(shape)
            .border(1.5.dp, Color(0x88171711), shape),
    ) {
        // body plastik off-white
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFF7F5EF), Color(0xFFDCDAD1)))),
        )
        // top cap = warna sistem
        Box(
            Modifier
                .fillMaxWidth()
                .height(capH.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(sys.shell).copy(alpha = 0.55f), Color(sys.shell)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = sys.longLabel,
                color = Color(0xEEFFFFFF),
                fontFamily = MONO,
                fontSize = 5.6.sp,
                letterSpacing = 1.4.sp,
                maxLines = 1,
            )
        }
        // area art rounded — margin gede (user: "box art margin nya digedein lagi")
        Box(
            Modifier
                .offset(y = artY.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(artH.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF101014)),
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (focused) null else ColorFilter.colorMatrix(dimMatrix()),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = sys.label,
                        color = T.mutedFg.copy(alpha = 0.8f),
                        fontFamily = DISPLAY,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
        // micro label bawah
        Box(
            Modifier
                .fillMaxWidth()
                .offset(y = (h - 21f).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "KARTRID · ${sys.ext.uppercase()}",
                color = Color(0x8C171711),
                fontFamily = MONO,
                fontSize = 3.8.sp,
                letterSpacing = 1.1.sp,
                maxLines = 1,
            )
        }
    }
}

/** saturate .6 + brightness .62 buat boxart non-fokus */
private fun dimMatrix(): ColorMatrix = ColorMatrix().apply {
    setToSaturation(0.6f)
    for (r in 0..2) for (c in 0..4) this[r, c] = this[r, c] * 0.62f
}