package id.iqbaldev.kartrid.ui

import android.view.KeyEvent as AKey
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Sys
import id.iqbaldev.kartrid.core.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// geometri rak (px, basis kanvas 960x640) — kartrid LANDSCAPE rasio asli 31,5:21,5
private const val TOP_H = 84f
private const val FAV_SHELL = 0xFFFFC94D
private const val CENTER_X = 480f
private const val CART_BOTTOM = 430f        // garis dasar rak
private const val FOCUS_W = 305f            // lebar kartrid fokus
private const val ITEM_W = 200f             // lebar tetangga
private const val GAP = 46f
private const val TITLE_Y = 470f
/** sudut istirahat kartrid fokus: serong mentok, sisi kanan menjauh ke belakang */
private const val REST_SPIN = CartFrames.MAX_ANGLE

private fun widthAt(d: Float): Float {
    val t = d.coerceIn(0f, 1f)
    return FOCUS_W + (ITEM_W - FOCUS_W) * t
}

/**
 * Jarak pusat item (di jarak d dari fokus) ke pusat item fokus — kontinu.
 *
 * CATATAN PERF: ini dipertahankan persis seperti aslinya (integrasi 32 langkah)
 * supaya hasil pikselnya identik. Biayanya 32 float ops per item per frame —
 * untuk 20 item = 640 ops/frame, nggak signifikan dibanding draw bitmap.
 * (Sempat dicoba diganti lookup table, tapi error numerik integral asli
 * bergantung pada d → posisi item jauh geser ~1.6px.)
 */
private fun centerOffset(d: Float): Float {
    if (d <= 0f) return 0f
    val steps = 32
    val dt = d / steps
    var s = 0f
    var t = 0f
    repeat(steps) {
        s += (GAP + widthAt(t)) * dt
        t += dt
    }
    return s
}

@Composable
fun ShelfScreen(
    tab: Tab,
    counts: Map<String, Int>,
    games: List<Game>,
    index: Int,
    focusRequester: FocusRequester,
    artOf: suspend (Game) -> ImageBitmap?,
    artVersion: Int,
    scanning: Boolean,
    artRunning: Boolean,
    scanNote: String,
    onTab: (Tab) -> Unit,
    onIndex: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onFav: (Game) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onSetArt: (Game) -> Unit,
    onClearArt: (Game) -> Unit,
    sfx: Sfx,
    /** jumlah item yang dirender di rak (fokus + tetangga kiri/kanan) */
    visibleCount: Int = 7,
    /** berapa game yang dilompati tiap tekan UP/DOWN — default 20 */
    skipStep: Int = 20,
    /** seed sinkron (dipakai preview/test) — app normal pakai [artOf] */
    preloadArt: ((Game) -> ImageBitmap?)? = null,
    /** art dari MEMORI saja (tanpa IO) — biar label nggak dibangun 2x */
    peekArt: ((Game) -> ImageBitmap?)? = null,
    /** custom wallpaper from settings (null = built-in dark canvas) */
    bg: ImageBitmap? = null,
    /** dim overlay over [bg], percent 0-90 */
    bgDim: Int = 55,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val current = games.getOrNull(index)
    var cartMenu by remember { mutableStateOf(false) }
    var cartMenuSel by remember { mutableIntStateOf(0) }

    // posisi carousel: indeks pecahan yang dianimasikan.
    //
    // PERF: `pos` dibaca sebagai `.value` di dalam ShelfRack → re-komposisi
    // tiap frame cuma menyentuh rak.
    //
    // SKIP (UP/DOWN) = SNAP, bukan tween. Tween 20 langkah bikin satu frame
    // harus ngekompos puluhan kartrid (window melebar ke posisi lama + baru)
    // = lag. Snap = instan; boxart target disiapkan dulu (prewarm, max 60ms)
    // supaya nggak sempat tampil tanpa art.
    val pos = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) {
        val target = index.toFloat()
        if (abs(target - pos.value) > 1.5f) pos.snapTo(target)
        else pos.animateTo(target, tween(200, easing = FastOutSlowInEasing))
    }

    // Prewarm label tujuan SKIP (UP/DOWN) saat idle — supaya pas tombol ditekan
    // boxart+label sudah siap, tanpa nambah latency di keypress.
    // Dilakukan bertahap (satu per satu, jeda 40ms) biar nggak spike memory/CPU.
    LaunchedEffect(index, skipStep, artVersion) {
        delay(450)                     // tunggu animasi & input reda
        withContext(Dispatchers.IO) {
            val target = (index + skipStep).coerceIn(0, (games.size - 1).coerceAtLeast(0))
            val jobs = ((target - 3)..(target + 3)).mapNotNull { j ->
                val gg = games.getOrNull(j) ?: return@mapNotNull null
                async { runCatching { artOf(gg)?.let { CartLabel.warm(gg, it, ctx, artVersion) } } }
            }
            jobs.awaitAll()
        }
    }

    val yOff = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val spin = remember { Animatable(REST_SPIN) }   // derajat, pivot = pusat kartrid
    val tilt = remember { Animatable(0f) }
    var loading by remember { mutableStateOf<Game?>(null) }

    fun move(delta: Int) {
        if (games.isEmpty()) return
        onIndex((index + delta).coerceIn(0, games.size - 1))
    }

    /**
     * Skip (UP/DOWN): lompat [delta] game + preview.
     *
     * Lompatan besar TIDAK dianimasikan (lihat `pos.snapTo`) — tween 20 langkah
     * bikin frame pertama berat. Sebagai gantinya, boxart+label tujuan disiapkan
     * dulu di background (best-effort, maksimal 60ms) lalu lompat. Batas waktu
     * penting: kalau storage lambat, jangan sampai tombol terasa nge-lag.
     */
    fun skip(delta: Int) {
        if (games.isEmpty()) return
        val target = (index + delta).coerceIn(0, games.size - 1)
        if (target == index) return
        scope.launch {
            withTimeoutOrNull(110) {
                withContext(Dispatchers.IO) {
                    // PARALEL: 7 label × ~15ms kalau serial = 105ms+ (keburu
                    // timeout, sisa item tetap bangun label di main thread).
                    val jobs = ((target - 3)..(target + 3)).mapNotNull { j ->
                        val gg = games.getOrNull(j) ?: return@mapNotNull null
                        async { runCatching { artOf(gg)?.let { CartLabel.warm(gg, it, ctx, artVersion) } } }
                    }
                    jobs.awaitAll()
                }
            }
            onIndex(target)
        }
    }

    suspend fun settle() {
        coroutineScope {
            launch {
                spin.animateTo(REST_SPIN, spring(dampingRatio = 0.6f, stiffness = 380f))
            }
            launch {
                tilt.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 420f))
            }
        }
    }

    suspend fun play(g: Game) = coroutineScope {
        launch { yOff.animateTo(34f, tween(180)) }
        launch { scale.animateTo(0.97f, tween(180)) }
        sfx.insert()
        sfx.haptic(18)
        delay(190)

        loading = g
        delay(400)
        onLaunch(g)
        delay(120)

        loading = null
        yOff.snapTo(0f)
        scale.snapTo(1f)
    }

    fun play() {
        val g = current ?: return
        if (loading != null) return
        scope.launch { play(g) }
    }

    // kartrid baru → puteran balik ke tengah
    LaunchedEffect(index) { settle() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (cartMenu) {
                    when (ev.nativeKeyEvent.keyCode) {
                        AKey.KEYCODE_DPAD_UP -> { cartMenuSel = (cartMenuSel - 1).coerceAtLeast(0); sfx.tick(); true }
                        AKey.KEYCODE_DPAD_DOWN -> { cartMenuSel = (cartMenuSel + 1).coerceAtMost(2); sfx.tick(); true }
                        AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> {
                            val g = current
                            if (g != null) {
                                when (cartMenuSel) {
                                    0 -> onSetArt(g)
                                    1 -> onClearArt(g)
                                    else -> onFav(g)
                                }
                            }
                            cartMenu = false
                            sfx.tick(); true
                        }
                        AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { cartMenu = false; sfx.tick(); true }
                        else -> true
                    }
                } else when (ev.nativeKeyEvent.keyCode) {
                    AKey.KEYCODE_DPAD_LEFT -> { move(-1); sfx.tick(); true }
                    AKey.KEYCODE_DPAD_RIGHT -> { move(1); sfx.tick(); true }
                    // UP/DOWN = lompat ke game sebelumnya/berikutnya (skip) + preview.
                    // Preview = posisi fokus langsung bergeser, art & judul ikut
                    // (bukan cuma menyorot daftar) — jadi user bisa lihat game
                    // target sebelum tekan A.
                    AKey.KEYCODE_DPAD_UP -> { skip(-skipStep); sfx.tick(); true }
                    AKey.KEYCODE_DPAD_DOWN -> { skip(skipStep); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_L1, AKey.KEYCODE_BUTTON_L2 -> {
                        val i = Tab.list.indexOf(tab); onTab(Tab.list[(i + Tab.list.size - 1) % Tab.list.size]); sfx.tick(); true
                    }
                    AKey.KEYCODE_BUTTON_R1, AKey.KEYCODE_BUTTON_R2 -> {
                        val i = Tab.list.indexOf(tab); onTab(Tab.list[(i + 1) % Tab.list.size]); sfx.tick(); true
                    }
                    AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> { play(); true }
                    AKey.KEYCODE_BUTTON_X -> { onSearch(); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_Y -> { current?.let { onFav(it); sfx.tick() }; true }
                    AKey.KEYCODE_BUTTON_SELECT -> { cartMenu = true; cartMenuSel = 0; sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { onSettings(); sfx.tick(); true }
                    else -> false
                }
            },
    ) {

        // ---- custom background (settings > Display) ----
        // Full-bleed layer behind everything: Crop so the image covers the 960x640
        // canvas, then dim + top/bottom scrim so the title and hint bar stay readable.
        // No image = the plain dark canvas painted by MockCanvas (previous behaviour).
        val bgImg = bg
        if (bgImg != null) {
            Image(
                bitmap = bgImg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val a = bgDim.coerceIn(0, 90) / 100f
            Box(Modifier.fillMaxSize().background(T.bg.copy(alpha = a)))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to T.bg.copy(alpha = 0.86f),
                        0.26f to T.bg.copy(alpha = a * 0.45f),
                        0.74f to T.bg.copy(alpha = a * 0.45f),
                        1f to T.bg.copy(alpha = 0.9f),
                    )
                )
            )
        }

        // ---- rak ----
        //
        // PERF (20 Okt 2026): rak dipisah jadi SUBTREE sendiri (ShelfRack) yang
        // membaca `fAnim.value`. Akibatnya re-komposisi tiap frame animasi cuma
        // menyentuh rak — top bar, judul, dan hint bar nggak ikut dihitung ulang.
        //
        // Window render: `visibleCount` item di sekitar fokus (default 7, sama
        // seperti versi lama). Dijaga KECIL: makin banyak item dikompos, makin
        // berat satu frame saat window bergeser.
        val half = (visibleCount - 1) / 2
        val from = (index - half).coerceAtLeast(0)
        val to = (index + (visibleCount - 1 - half)).coerceAtMost(games.size - 1)

        if (games.isNotEmpty()) {
            ShelfRack(
                games = games,
                index = index,
                from = from,
                to = to,
                fAnim = { pos.value },
                yOff = yOff,
                scale = scale,
                spin = spin,
                tilt = tilt,
                loading = loading,
                artOf = artOf,
                artVersion = artVersion,
                preloadArt = preloadArt,
                peekArt = peekArt,
                sfx = sfx,
                settle = suspend { settle() },
            )
        }

        // ---- top bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(TOP_H.dp)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            for (s in Tab.list) {
                val on = s == tab
                Row(
                    Modifier
                        .clickable { onTab(s); sfx.tick() }
                        .drawBehind { if (on) drawRect(T.muted) }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp, 10.dp)
                            .background(shellColor(s.sys?.shell ?: FAV_SHELL, on))
                    )
                    Box(Modifier.width(8.dp))
                    Text(
                        text = s.label,
                        color = if (on) T.lime else T.fg.copy(alpha = 0.75f),
                        fontFamily = MONO,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(Modifier.width(8.dp))
                    Text(
                        text = (counts[s.key] ?: 0).toString().padStart(2, '0'),
                        color = if (on) T.lime else T.mutedFg,
                        fontFamily = MONO,
                        fontSize = 11.sp,
                    )
                }
            }
            Box(Modifier.weight(1f))

            if (scanning || artRunning) {
                Text(
                    text = scanNote.ifBlank { if (artRunning) "fetching boxart…" else "scanning…" },
                    color = T.mutedFg,
                    fontFamily = MONO,
                    fontSize = 11.sp,
                )
            }
            Box(
                Modifier
                    .size(32f.tdp)
                    .clickable { onSettings(); sfx.tick() },
                contentAlignment = Alignment.Center,
            ) {
                CogIcon()
            }
        }

        // ---- judul + meta ----
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = TITLE_Y.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (current != null) {
                Text(
                    text = current.title,
                    color = T.fg,
                    fontFamily = DISPLAY,
                    fontSize = 34.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = metaOf(current),
                    color = T.mutedFg,
                    fontFamily = MONO,
                    fontSize = 13.5.sp,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(text = "EMPTY SHELF", color = T.mutedFg, fontFamily = DISPLAY, fontSize = 34.sp)
            }
        }

        // ---- hint bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = 598.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            HintItem("A", "insert")
            ListSpacer()
            HintItem("X", "search")
            ListSpacer()
            HintItem("Y", "fav")
            ListSpacer()
            HintItem("B", "settings")
            ListSpacer()
            HintItem("SEL", "menu")
        }

        // ---- menu aksi kartrid (SELECT) ----
        if (cartMenu) {
            val g = current
            val opts = listOf("Set custom boxart", "Remove custom boxart", if (g?.fav == true) "Unfavorite" else "Favorite")
            Box(
                Modifier
                    .offset(x = (CENTER_X - 230f).dp, y = 322f.dp)
                    .size(460.dp, 190.dp)
                    .background(T.bg.copy(alpha = 0.92f))
                    .border(1.5.dp, T.hairline),
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        (g?.title ?: "").uppercase(),
                        color = T.mutedFg, fontFamily = MONO, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.height(8.dp))
                    for ((i, o) in opts.withIndex()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .drawBehind { if (i == cartMenuSel) drawRect(T.muted) }
                                .clickable { cartMenuSel = i; sfx.tick() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (i == cartMenuSel) "»" else " ", color = T.lime, fontFamily = MONO, fontSize = 12.sp)
                            Box(Modifier.width(8.dp))
                            Text(o, color = if (i == cartMenuSel) T.lime else T.fg, fontFamily = MONO, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ---- empty state ----
        if (games.isEmpty() && !scanning) {
            EmptyPanel()
        }
    }

    LaunchedEffect(games.size) {
        if (index > games.size - 1) onIndex((games.size - 1).coerceAtLeast(0))
    }
}

/**
 * Isi rak kartrid.
 *
 * Dipisah dari [ShelfScreen] supaya pembacaan posisi animasi (`fAnim.value`)
 * cuma me-rekomposisi subtree ini — top bar, judul, dan hint bar di luar
 * nggak ikut dihitung ulang tiap frame.
 *
 * Kenapa ini bikin mulus (tanpa ubah visual sama sekali):
 *  - geometri (lebar/posisi/skew) tetap dihitung dari `f` yang beranimasi,
 *    jadi transisi kontinu PERSIS seperti sebelumnya;
 *  - item yang lebarnya tidak berubah punya parameter sama → Compose men-skip
 *    re-komposisi & re-draw-nya. Per transisi cuma ~2 item (d < 1) yang
 *    benar-benar dihitung ulang;
 *  - `metaOf` di-cache per game (dulu bikin String baru tiap frame);
 *  - item yang jauh di luar layar di-cull (nggak dikompos sama sekali).
 */
@Composable
private fun ShelfRack(
    games: List<Game>,
    index: Int,
    from: Int,
    to: Int,
    fAnim: () -> Float,
    yOff: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>,
    spin: Animatable<Float, AnimationVector1D>,
    tilt: Animatable<Float, AnimationVector1D>,
    loading: Game?,
    artOf: suspend (Game) -> ImageBitmap?,
    artVersion: Int,
    preloadArt: ((Game) -> ImageBitmap?)?,
    peekArt: ((Game) -> ImageBitmap?)?,
    sfx: Sfx,
    settle: suspend () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val metaCache = remember { HashMap<String, String>() }
    val f = fAnim()

    for (i in from..to) {
        val g = games[i]
        val isFocusedCart = i == index
        val d = abs(i - f)
        val w = widthAt(d)
        val h = w / CART_ASPECT
        val lift = 10f * (1f - d.coerceAtMost(1f))
        val cy = CART_BOTTOM - lift - h / 2f
        val s = w / CartFrames.UNIT_W
        val cx = CENTER_X + (if (i >= index) 1f else -1f) * centerOffset(d)

        // JANGAN cull di sini (jangan `continue`): kalau item di-dispose saat
        // geser, state `art` hilang → boxart reload dari disk → BLINK.
        // Cull dilakukan di fase gambar (alpha=0 di graphicsLayer) supaya
        // komposisi + state tetap hidup.
        val halfW = CartFrames.IMG_W * s / 2f
        val onScreen = !(cx + halfW < -80f || cx - halfW > 1040f)

        val boxLeft = cx - CartFrames.CENTER_X * s
        val boxTop = cy - CartFrames.CENTER_Y * s
        if (metaCache.size > 256) metaCache.clear()
        val meta = metaCache.getOrPut(g.id.ifEmpty { g.path }) { metaOf(g) }

        key(g.id) {
            var art by remember(g.id) {
                mutableStateOf(preloadArt?.invoke(g) ?: peekArt?.invoke(g))
            }
            LaunchedEffect(g.id, artVersion) {
                art = withContext(Dispatchers.IO) { artOf(g) }
            }
            // Prewarm label di background: build() bikin bitmap 560×360
            // (Canvas + drawText + drawBitmap). Tanpa ini, build pertama kali
            // terjadi di MAIN THREAD → jank saat item baru masuk layar.
            LaunchedEffect(g.id, art) {
                val a = art
                if (a != null) withContext(Dispatchers.IO) { CartLabel.warm(g, a, ctx, artVersion) }
            }

            Box(
                Modifier
                    .offset(
                        x = boxLeft.dp,
                        y = (boxTop + if (isFocusedCart) yOff.value else 0f).dp,
                    )
                    // pengganti urutan "sortedByDescending d" versi lama:
                    // kartrid fokus digambar paling akhir = paling atas.
                    .zIndex(if (isFocusedCart) 1f else 0f)
                    .graphicsLayer {
                        // cull di fase gambar: item di luar layar nggak digambar,
                        // TAPI komposisi & state art-nya tetap hidup (anti-blink).
                        alpha = if (onScreen) 1f else 0f

                        val sc = if (isFocusedCart) scale.value else 1f
                        scaleX = sc
                        scaleY = sc
                        rotationZ = if (isFocusedCart) 0f else {
                            val side = if (i < index) -1f else 1f
                            side * (1.4f + 0.7f * d.coerceAtMost(1f)).coerceAtMost(2.6f)
                        }
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .then(
                        if (isFocusedCart) {
                            Modifier.pointerInput(g.id) {
                                detectDragGestures(
                                    onDragStart = { sfx.tick() },
                                    onDragEnd = { scope.launch { settle() } },
                                    onDragCancel = { scope.launch { settle() } },
                                ) { change, drag ->
                                    change.consume()
                                    scope.launch {
                                        spin.snapTo(
                                            (spin.value + drag.x * 0.55f)
                                                .coerceIn(CartFrames.MIN_ANGLE, CartFrames.MAX_ANGLE)
                                        )
                                    }
                                    scope.launch {
                                        tilt.snapTo(
                                            (tilt.value + drag.y * 0.22f).coerceIn(-10f, 10f)
                                        )
                                    }
                                }
                            }
                        } else Modifier
                    ),
            ) {
                Cartridge(
                    sys = g.sys,
                    art = art,
                    title = g.title,
                    meta = meta,
                    w = w,
                    focused = d < 0.5f,
                    spin = if (isFocusedCart) spin.value else 0f,
                    tilt = if (isFocusedCart) tilt.value else 0f,
                    loading = isFocusedCart && loading != null,
                    labelKey = CartLabel.labelKey(g.id, art, artVersion),
                )
            }
        }
    }
}

internal fun metaOf(g: Game): String {
    val parts = ArrayList<String>()
    g.region?.let { parts.add(it) }
    parts.add(sizeText(g.sizeBytes))
    if (g.plays > 0) parts.add("played ${g.plays}×") else parts.add("never played")
    return parts.joinToString("  ·  ")
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

@Composable
internal fun HintItem(key: String, label: String) {
    // retro keycap: single letter = circle (like the A/B buttons), longer label (SEL) = pill.
    val single = key.length == 1
    val shape = if (single) CircleShape else RoundedCornerShape(50)
    val keyW = if (single) 24.dp else 38.dp
    val density = LocalDensity.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(keyW, 24.dp)
                .drawBehind {
                    // hard offset shadow, same as the cards & tabs
                    val r = if (single) size.minDimension / 2f else size.height / 2f
                    drawRoundRect(
                        T.shadow,
                        topLeft = Offset(with(density) { 2.dp.toPx() }, with(density) { 2.dp.toPx() }),
                        size = size,
                        cornerRadius = CornerRadius(r, r),
                    )
                }
                .clip(shape)
                .background(T.lime)
                .border(1.5.dp, T.black, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = key,
                color = T.black,
                fontFamily = DISPLAY,
                fontSize = if (single) 12.sp else 9.sp,
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.width(8.dp))
        Text(
            text = label.uppercase(),
            color = T.fg,
            fontFamily = MONO,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CogIcon(color: Color = T.mutedFg) {
    Box(
        Modifier
            .size(30f.tdp)
            .drawBehind {
                val c = center
                val minR = size.minDimension / 2f
                val body = minR * 0.34f
                val toothLen = minR * 0.47f
                val toothW = minR * 0.15f
                val toothH = minR * 0.17f
                for (k in 0 until 8) {
                    rotate(k * 45f, pivot = c) {
                        drawRect(
                            color,
                            topLeft = Offset(c.x - toothW / 2f, c.y - toothLen),
                            size = Size(toothW, toothH),
                        )
                    }
                }
                drawCircle(color, radius = body, center = c)
                drawCircle(T.surface, radius = body * 0.42f, center = c)
            },
    )
}

@Composable
private fun ListSpacer() {
    Box(Modifier.width(24.dp))
}

@Composable
private fun EmptyPanel() {
    Box(
        Modifier
            // center vertikal di layar: (640 - 190) / 2 = 225
            .offset(x = (CENTER_X - 220f).dp, y = ((640f - 190f) / 2f).dp)
            .size(440.dp, 190.dp)
            .drawBehind {
                drawRect(T.black, topLeft = Offset(7f, 7f), size = Size(size.width, size.height))
            }
            .background(T.surface)
            .border(1.5.dp, T.hairline),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No ROMs yet.\nPress B (or the gear) to set your ROM folders.",
            color = T.mutedFg,
            fontFamily = MONO,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
