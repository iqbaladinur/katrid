package id.iqbaldev.kartrid.ui

import android.view.KeyEvent as AKey
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Sys
import id.iqbaldev.kartrid.core.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** jarak pusat item (di jarak d dari fokus) ke pusat item fokus — kontinu */
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
    /** seed sinkron (dipakai preview/test) — app normal pakai [artOf] */
    preloadArt: ((Game) -> ImageBitmap?)? = null,
) {
    val scope = rememberCoroutineScope()
    val current = games.getOrNull(index)
    var cartMenu by remember { mutableStateOf(false) }
    var cartMenuSel by remember { mutableIntStateOf(0) }

    // posisi carousel dianimasikan sebagai indeks pecahan
    val f by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "carousel",
    )

    val yOff = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val spin = remember { Animatable(REST_SPIN) }   // derajat, pivot = pusat kartrid
    val tilt = remember { Animatable(0f) }
    var loading by remember { mutableStateOf<Game?>(null) }

    fun move(delta: Int) {
        if (games.isEmpty()) return
        onIndex((index + delta).coerceIn(0, games.size - 1))
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
                    AKey.KEYCODE_BUTTON_L1, AKey.KEYCODE_BUTTON_L2 -> {
                        val i = Tab.list.indexOf(tab); onTab(Tab.list[(i + Tab.list.size - 1) % Tab.list.size]); sfx.tick(); true
                    }
                    AKey.KEYCODE_BUTTON_R1, AKey.KEYCODE_BUTTON_R2 -> {
                        val i = Tab.list.indexOf(tab); onTab(Tab.list[(i + 1) % Tab.list.size]); sfx.tick(); true
                    }
                    AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> { play(); true }
                    AKey.KEYCODE_BUTTON_X -> { current?.let { onFav(it); sfx.tick() }; true }
                    AKey.KEYCODE_BUTTON_Y -> { onSearch(); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_SELECT -> { cartMenu = true; cartMenuSel = 0; sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { onSettings(); sfx.tick(); true }
                    else -> false
                }
            },
    ) {

        // ---- rak ----
        val from = (index - 3).coerceAtLeast(0)
        val to = (index + 3).coerceAtMost(games.size - 1)
        if (games.isNotEmpty()) {
            val items = (from..to).map { it to abs(it - f) }.sortedByDescending { it.second }
            for ((i, d) in items) {
                val g = games[i]
                val focused = d < 0.5f
                val w = widthAt(d)
                val h = w / CART_ASPECT
                val lift = 10f * (1f - d.coerceAtMost(1f))
                val cy = CART_BOTTOM - lift - h / 2f
                val cx = CENTER_X + (if (i >= index) 1f else -1f) * centerOffset(d)
                val isFocusedCart = i == index

                val s = w / CartFrames.UNIT_W
                val boxLeft = cx - CartFrames.CENTER_X * s
                val boxTop = cy - CartFrames.CENTER_Y * s

                key(g.id) {
                    var art by remember(g.id) { mutableStateOf(preloadArt?.invoke(g)) }
                    LaunchedEffect(g.id, artVersion) {
                        art = withContext(Dispatchers.IO) { artOf(g) }
                    }

                    Box(
                        Modifier
                            .offset(
                                x = (boxLeft).dp,
                                y = (boxTop + if (isFocusedCart) yOff.value else 0f).dp,
                            )
                            .graphicsLayer {
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
                            meta = metaOf(g),
                            w = w,
                            focused = focused,
                            spin = if (isFocusedCart) spin.value else 0f,
                            tilt = if (isFocusedCart) tilt.value else 0f,
                            loading = isFocusedCart && loading != null,
                        )
                    }
                }
            }
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
            HintItem("< >", "move")
            ListSpacer()
            HintItem("DRAG", "puter")
            ListSpacer()
            HintItem("A", "insert")
            ListSpacer()
            HintItem("X", "fav")
            ListSpacer()
            HintItem("Y", "search")
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

internal fun metaOf(g: Game): String {
    val parts = ArrayList<String>()
    g.region?.let { parts.add(it) }
    parts.add(sizeText(g.sizeBytes))
    if (g.plays > 0) parts.add("main ${g.plays}×") else parts.add("never played")
    return parts.joinToString("  ·  ")
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

@Composable
internal fun HintItem(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(46.dp, 22.dp)
                .drawBehind {
                    drawRect(T.shadow, topLeft = Offset(2f, 2f), size = Size(size.width, size.height))
                }
                .background(T.surface)
                .border(1.2.dp, T.hairline),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, color = T.fg, fontFamily = MONO, fontSize = 11.sp)
        }
        Box(Modifier.width(6.dp))
        Text(label, color = T.mutedFg, fontFamily = MONO, fontSize = 11.sp)
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
