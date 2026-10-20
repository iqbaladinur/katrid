package id.iqbaldev.kartrid.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as ACanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import id.iqbaldev.kartrid.R
import id.iqbaldev.kartrid.core.Sys

/**
 * Kartrid Switch 3D ala rak beneran.
 *
 * Pipeline:
 *  1. Shell plastik dirender 27 frame (−26°…+26°, step 2°) dengan lubang label transparan
 *     — lihat [CartFrames]. PNG ada di drawable-nodpi/cf_00..cf_26.png.
 *  2. Label (boxart 1:1 + panel warna sistem) digambar runtime per game ke bitmap 560×360,
 *     lalu dipetakan ke 4 titik pojok label frame aktif pakai `setPolyToPoly` → perspektif
 *     beneran, boxart tetap dinamis (custom art jalan).
 *  3. Frame digambar SETELAH label: lubang transparan bikin bibir recess, lip highlight, dan
 *     bayangan jatuh nempel otomatis ke label.
 */
object CartLabel {
    const val W = 560
    const val H = 360
    const val ART_W = 360                    // art 1:1 persis, nol crop kalau sumbernya persegi

    /**
     * Cache label (bitmap 560×360 ARGB_8888 = ~806 KB tiap label).
     *
     * Versi lama: LruCache 12 entry. Kalau rak render 20 item, tiap scroll
     * evict 8+ label → label dibangun ULANG di main thread (build() = Canvas +
     * drawText + drawBitmap) = jank. Cache lama juga salah karena ukurannya
     * count, bukan byte.
     *
     * Sekarang: budget byte (12 MB ≈ 15 label penuh) + eviction LRU by byte.
     */
    private const val CACHE_BYTES = 20L * 1024 * 1024
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES.toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    fun get(
        key: String,
        sys: Sys,
        art: ImageBitmap?,
        title: String,
        meta: String,
        tfDisplay: Typeface?,
        tfMonoBold: Typeface?,
        tfMono: Typeface?,
    ): ImageBitmap {
        cache.get(key)?.let { return it }
        val bmp = build(sys, art, title, meta, tfDisplay, tfMonoBold, tfMono)
        cache.put(key, bmp)
        return bmp
    }

    /**
     * Bangun label lebih awal (dipanggil dari background thread) supaya
     * scroll pertama kali nggak perlu build bitmap di main thread.
     *
     * PENTING: `build()` bikin Android Bitmap → harus jalan di thread yang
     * punya akses resource. Canvas + Paint aman di thread apa pun (bukan UI
     * thread), jadi ini dipanggil dari Dispatchers.IO.
     */
    fun warm(
        g: id.iqbaldev.kartrid.core.Game,
        art: ImageBitmap?,
        ctx: android.content.Context,
    ) {
        val sys = id.iqbaldev.kartrid.core.Sys.of(g.system)
        val key = "${g.title}|${sys.key}|${art?.hashCode()}"
        if (cache.get(key) != null) return
        val meta = metaOf(g)
        val bmp = build(
            sys, art, g.title, meta,
            CartFonts.display(ctx), CartFonts.monoBold(ctx), CartFonts.mono(ctx),
        )
        cache.put(key, bmp)
    }

    fun clear() = cache.evictAll()

    private fun build(
        sys: Sys,
        art: ImageBitmap?,
        title: String,
        meta: String,
        tfDisplay: Typeface?,
        tfMonoBold: Typeface?,
        tfMono: Typeface?,
    ): ImageBitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val panel = panelColor(sys)
        val fg = panelFg(sys)
        val sub = panelSub(sys)

        c.drawColor(panel)

        // --- art 1:1 di kiri (square, full tinggi label), nol crop untuk sumber persegi ---
        if (art != null) {
            val src = centerCrop(art.width, art.height, ART_W, H)
            c.drawBitmap(art.asAndroidBitmap(), src, Rect(0, 0, ART_W, H), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawColor(0xFF2A2338.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tfDisplay; color = 0xFFB0A8C2.toInt(); textSize = 64f; textAlign = Paint.Align.CENTER
            }
            c.drawText(sys.label, ART_W / 2f, H / 2f + 6f, p)
            p.typeface = tfMono
            p.textSize = 13f
            p.color = 0x88B0A8C2.toInt()
            p.letterSpacing = 0.35f
            c.drawText("NO ART", ART_W / 2f, H / 2f + 46f, p)
        }

        // --- panel kanan ---
        val x = ART_W + 24f
        val pw = W - ART_W - 44f

        val pCode = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfDisplay
            textSize = 58f
            color = fg
            letterSpacing = -0.02f
        }
        c.drawText(sys.label, x, 74f, pCode)

        val pName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfMonoBold
            textSize = 10.5f
            color = sub
            letterSpacing = 0.16f
        }
        c.drawText(sys.longLabel.uppercase(), x, 96f, pName)

        val rule = Paint().apply { color = fg; alpha = 60 }
        c.drawRect(x, 110f, x + pw, 111.4f, rule)

        val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfMonoBold
            textSize = 16.5f
            color = fg
        }
        var y = 140f
        for (line in wrap(title, pTitle, pw, 4)) {
            c.drawText(line, x, y, pTitle)
            y += 20f
        }

        val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfMono
            textSize = 10f
            color = sub
            letterSpacing = 0.04f
        }
        c.drawText(meta.uppercase(), x, H - 18f, pMeta)

        return bmp.asImageBitmap()
    }

    private fun centerCrop(sw: Int, sh: Int, dw: Int, dh: Int): Rect {
        val scale = maxOf(dw.toFloat() / sw, dh.toFloat() / sh)
        val w = (dw / scale).toInt().coerceAtMost(sw)
        val h = (dh / scale).toInt().coerceAtMost(sh)
        val left = (sw - w) / 2
        val top = (sh - h) / 2
        return Rect(left, top, left + w, top + h)
    }

    private fun wrap(text: String, p: Paint, maxW: Float, maxLines: Int): List<String> {
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val cand = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(cand) <= maxW) {
                line = StringBuilder(cand)
            } else {
                if (line.isNotEmpty()) out.add(line.toString())
                if (out.size >= maxLines) break
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty() && out.size < maxLines) out.add(line.toString())
        if (out.size == maxLines) {
            var last = out.last()
            while (p.measureText("$last…") > maxW && last.length > 1) last = last.dropLast(1)
            out[out.size - 1] = "$last…"
        }
        return out
    }

    fun panelColor(sys: Sys): Int = when (sys) {
        Sys.GB -> 0xFFCFC9DD.toInt()
        Sys.GBC -> 0xFF4F86AE.toInt()
        Sys.GBA -> 0xFF5A4C93.toInt()
    }

    private fun panelFg(sys: Sys): Int = when (sys) {
        Sys.GB -> 0xFF16121F.toInt()
        else -> 0xFFF6F3FF.toInt()
    }

    private fun panelSub(sys: Sys): Int = when (sys) {
        Sys.GB -> 0xFF4F4862.toInt()
        Sys.GBC -> 0xFFD6E6F0.toInt()
        else -> 0xFFD8D0F2.toInt()
    }
}

private val frameCache = object : LruCache<Int, ImageBitmap>(27) {}

/**
 * Font dipakai untuk label kartrid. Dipisah supaya bisa dipakai di luar
 * composable (mis. prewarm label di background thread → nggak jank).
 */
object CartFonts {
    @Volatile private var display: Typeface? = null
    @Volatile private var monoBold: Typeface? = null
    @Volatile private var mono: Typeface? = null

    fun display(ctx: android.content.Context): Typeface? =
        display ?: runCatching { ResourcesCompat.getFont(ctx, R.font.archivo_black) }
            .getOrNull().also { display = it }

    fun monoBold(ctx: android.content.Context): Typeface? =
        monoBold ?: runCatching { ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_700) }
            .getOrNull().also { monoBold = it }

    fun mono(ctx: android.content.Context): Typeface? =
        mono ?: runCatching { ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_500) }
            .getOrNull().also { mono = it }
}

private fun frameBitmap(idx: Int, ctx: android.content.Context): ImageBitmap {
    frameCache.get(idx)?.let { return it }
    val bmp = BitmapFactory.decodeResource(ctx.resources, CartFrames.RES[idx]).asImageBitmap()
    frameCache.put(idx, bmp)
    return bmp
}

/** rasio kartrid fisik: 31,5 / 21,5 mm */
const val CART_ASPECT = 31.5f / 21.5f

@Composable
fun Cartridge(
    sys: Sys,
    art: ImageBitmap?,
    title: String,
    meta: String,
    w: Float,
    focused: Boolean,
    spin: Float = 0f,
    tilt: Float = 0f,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current

    val tfDisplay = remember { runCatching { ResourcesCompat.getFont(ctx, R.font.archivo_black) }.getOrNull() }
    val tfMonoBold = remember { runCatching { ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_700) }.getOrNull() }
    val tfMono = remember { runCatching { ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_500) }.getOrNull() }

    val idx = CartFrames.indexFor(spin)
    val frame = remember(idx, ctx) { frameBitmap(idx, ctx) }
    val label = remember(sys, title, meta, art) {
        CartLabel.get("$title|${sys.key}|${art?.hashCode()}", sys, art, title, meta, tfDisplay, tfMonoBold, tfMono)
    }

    val scale = w / CartFrames.UNIT_W
    val imgW = CartFrames.IMG_W * scale
    val imgH = CartFrames.IMG_H * scale

    val dimFilter = remember(focused) {
        if (focused) null else {
            // saturasi 0.92 + brightness 0.58 (kartrid non-fokus)
            val base = ColorMatrix().apply { setSaturation(0.92f) }
            val a = base.array.copyOf()
            for (i in a.indices) if (i % 5 != 4) a[i] *= 0.58f
            val out = ColorMatrix().apply { set(a) }
            ColorMatrixColorFilter(out)
        }
    }

    val labelPaint = remember(dimFilter) {
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = dimFilter }
    }
    val framePaint = remember(dimFilter) {
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = dimFilter }
    }
    val matrix = remember { Matrix() }
    val srcPts = remember {
        floatArrayOf(
            0f, 0f,
            CartLabel.W.toFloat(), 0f,
            CartLabel.W.toFloat(), CartLabel.H.toFloat(),
            0f, CartLabel.H.toFloat(),
        )
    }
    val dstPts = remember { FloatArray(8) }

    val q = CartFrames.QUADS[idx]

    Box(
        modifier = modifier
            .size(imgW.dp, imgH.dp)
            .graphicsLayer {
                rotationX = tilt
                cameraDistance = 16f * density.density
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas

                // 1. label ke quad (di belakang frame)
                for (i in 0 until 4) {
                    dstPts[i * 2] = q[i * 2] * scale
                    dstPts[i * 2 + 1] = q[i * 2 + 1] * scale
                }
                matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
                nc.drawBitmap(label.asAndroidBitmap(), matrix, labelPaint)

                // 2. shell plastik — lubang label transparan bikin recess nempel ke art
                nc.drawBitmap(frame.asAndroidBitmap(), null, RectF(0f, 0f, imgW, imgH), framePaint)
            }
        }

        if (loading) {
            val cx = (0 until 4).sumOf { (q[it * 2] * scale).toDouble() }.toFloat() / 4f
            val cy = (0 until 4).sumOf { (q[it * 2 + 1] * scale).toDouble() }.toFloat() / 4f
            val inspection = LocalInspectionMode.current
            val angle = if (inspection) {
                90f
            } else {
                val tr = rememberInfiniteTransition(label = "cart-spin")
                val a by tr.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                    label = "cart-angle",
                )
                a
            }
            val r = w * 0.13f
            val stroke = (r * 0.18f).coerceAtLeast(2f)
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(T.bg.copy(alpha = 0.55f), radius = w * 0.20f, center = Offset(cx, cy))
                drawArc(
                    color = T.lime.copy(alpha = 0.22f), startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = T.lime, startAngle = angle, sweepAngle = 100f,
                    useCenter = false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
