package id.iqbaldev.kartrid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
 * Kartrid ala GBA/GB: shell + notch + label boxart + band nama sistem.
 * `clipFrac` 0..1 = bagian bawah yang ketutup (animasi masuk slot, PLAN §3b).
 */
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
) {
    val outlineRoom = if (focused) 12f else 0f
    val shape: Shape = if (sys.rounded) RoundedCornerShape(8.dp) else RectangleShape
    val shell = shellColor(sys.shell, focused)
    val shadow = if (focused) 8f else 5f

    Box(modifier = modifier.size((w + outlineRoom).dp, (h + outlineRoom).dp)) {

        // outline lime 3px, jarak 3px dari shell (cuma yang fokus)
        if (focused) {
            Box(
                Modifier
                    .size((w + 6).dp, (h + 6).dp)
                    .align(Alignment.Center)
                    .border(3.dp, T.lime),
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
                .background(shell)
                .border(2.dp, T.black, shape),
        ) {
            // label + boxart
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .border(2.dp, Color(0x99000000))
                    .background(T.labelBg),
            ) {
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = if (focused) null else ColorFilter.colorMatrix(dimMatrix()),
                    )
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

            // notch / grip atas-kiri
            if (showNotch) {
                Box(
                    Modifier
                        .offset(x = 14.dp, y = 0.dp)
                        .size(24.dp, 8.dp)
                        .background(T.bg)
                        .border(2.dp, T.black),
                )
            }
        }
    }
}

/** saturate .6 + brightness .62 buat boxart non-fokus */
private fun dimMatrix(): ColorMatrix = ColorMatrix().apply {
    setToSaturation(0.6f)
    for (r in 0..2) for (c in 0..4) this[r, c] = this[r, c] * 0.62f
}
