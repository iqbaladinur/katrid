package id.iqbaldev.kartrid.ui

import android.view.KeyEvent as AKey
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Sys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// geometri (px, basis kanvas 960x640 — PLAN §3b)
private const val TOP_H = 52f
private const val CENTER_X = 480f
private const val CART_BOTTOM = 380f
private const val FOCUS_W = 288f
private const val FOCUS_H = 206f
private const val ITEM_W = 196f
private const val GAP = 34f
private const val SLOT_W = 204f
private const val SLOT_H = 18f
private const val SLOT_TOP = 380f

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

private fun aspect() = FOCUS_H / FOCUS_W

@Composable
fun ShelfScreen(
    tab: Sys,
    counts: Map<String, Int>,
    games: List<Game>,
    index: Int,
    focusRequester: FocusRequester,
    artOf: suspend (Game) -> ImageBitmap?,
    artVersion: Int,
    scanning: Boolean,
    scanNote: String,
    onTab: (Sys) -> Unit,
    onIndex: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onFav: (Game) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    sfx: Sfx,
) {
    val scope = rememberCoroutineScope()
    val current = games.getOrNull(index)

    // posisi carousel dianimasikan sebagai indeks pecahan
    val f by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "carousel",
    )

    val yOff = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    var clipFrac by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf<Game?>(null) }

    fun move(delta: Int) {
        if (games.isEmpty()) return
        onIndex((index + delta).coerceIn(0, games.size - 1))
    }

    suspend fun play(g: Game) = coroutineScope {
        tip = "MENUJU SLOT…"
        launch { yOff.animateTo(58f, tween(220)) }
        launch { scale.animateTo(0.96f, tween(220)) }
        delay(230)

        sfx.insert()
        sfx.haptic(18)
        tip = "KLEK!"
        clipFrac = 0.46f
        flash = true
        launch { yOff.animateTo(44f, tween(90)) }
        launch { scale.animateTo(0.98f, tween(90)) }
        delay(200)

        loading = g
        delay(360)
        onLaunch(g)
        delay(150)

        loading = null
        flash = false
        clipFrac = 0f
        tip = null
        yOff.snapTo(0f)
        scale.snapTo(1f)
    }

    fun play() {
        val g = current ?: return
        if (loading != null) return
        scope.launch { play(g) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.nativeKeyEvent.keyCode) {
                    AKey.KEYCODE_DPAD_LEFT -> { move(-1); sfx.tick(); true }
                    AKey.KEYCODE_DPAD_RIGHT -> { move(1); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_L1 -> { move(-5); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_R1 -> { move(5); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> { play(); true }
                    AKey.KEYCODE_BUTTON_X -> { current?.let { onFav(it); sfx.tick() }; true }
                    AKey.KEYCODE_BUTTON_Y -> { onSearch(); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { onSettings(); sfx.tick(); true }
                    else -> false
                }
            },
    ) {

        // ---- slot strip (di belakang kartrid) ----
        Box(
            Modifier
                .offset(x = (CENTER_X - SLOT_W / 2f).dp, y = SLOT_TOP.dp)
                .size(SLOT_W.dp, SLOT_H.dp)
                .background(Color(0xFF0A0810))
                .border(2.dp, T.black)
                .drawBehind { drawRect(T.lime, topLeft = Offset.Zero, size = Size(size.width, 2f)) },
        )

        // ---- rak ----
        val from = (index - 3).coerceAtLeast(0)
        val to = (index + 3).coerceAtMost(games.size - 1)
        if (games.isNotEmpty()) {
            val items = (from..to).map { it to abs(it - f) }.sortedByDescending { it.second }
            for ((i, d) in items) {
                val g = games[i]
                val focused = d < 0.5f
                val w = widthAt(d)
                val h = w * aspect()
                val lift = 8f * (1f - d.coerceAtMost(1f))
                val bottom = CART_BOTTOM - lift
                val xLeft = CENTER_X + (if (i >= index) 1f else -1f) * centerOffset(d) - w / 2f
                val yTop = bottom - h
                val room = if (focused) 6f else 0f
                val isFocusedCart = i == index

                key(g.id) {
                    var art by remember(g.id) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(g.id, artVersion) {
                        art = withContext(Dispatchers.IO) { artOf(g) }
                    }

                    Cartridge(
                        sys = g.sys,
                        art = art,
                        w = w,
                        h = h,
                        focused = focused,
                        clipFrac = if (isFocusedCart) clipFrac else 0f,
                        showBand = focused,
                        showNotch = focused,
                        modifier = Modifier
                            .offset(
                                x = (xLeft - room).dp,
                                y = (yTop - room + if (isFocusedCart) yOff.value else 0f).dp,
                            )
                            .graphicsLayer {
                                val s = if (isFocusedCart) scale.value else 1f
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
            }
        }

        // ---- flash + ring pas nyentuh mulut slot ----
        if (flash) {
            Box(
                Modifier
                    .offset(x = (CENTER_X - 130f).dp, y = (SLOT_TOP - 2f).dp)
                    .size(260.dp, 9.dp)
                    .background(T.lime.copy(alpha = 0.85f)),
            )
            Box(
                Modifier
                    .offset(x = (CENTER_X - 150f).dp, y = (SLOT_TOP - 16f).dp)
                    .size(300.dp, 74.dp)
                    .border(2.dp, T.lime.copy(alpha = 0.5f)),
            )
            Box(
                Modifier
                    .offset(x = (CENTER_X - 150f).dp, y = (SLOT_TOP - 16f).dp)
                    .size(300.dp, 74.dp)
                    .border(6.dp, T.lime.copy(alpha = 0.10f)),
            )
        }

        // ---- top bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(TOP_H.dp)
                .padding(horizontal = 22.dp)
                .drawBehind { drawRect(T.hairline, topLeft = Offset(0f, size.height - 1.5f), size = Size(size.width, 1.5f)) },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            for (s in Sys.order) {
                val on = s == tab
                Box(
                    Modifier
                        .clickable { onTab(s); sfx.tick() }
                        .drawBehind {
                            if (on) drawRect(T.lime, topLeft = Offset(0f, size.height - 3f), size = Size(size.width, 3f))
                        }
                        .padding(top = 10.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = "${s.label} ${counts[s.key] ?: 0}",
                        color = if (on) T.lime else T.mutedFg,
                        fontFamily = MONO,
                        fontSize = 16.sp,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
            Box(Modifier.weight(1f))

            if (scanning) {
                Text(
                    text = scanNote.ifBlank { "scanning…" },
                    color = T.mutedFg,
                    fontFamily = MONO,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Box(
                Modifier
                    .size(30f.tdp)
                    .padding(bottom = 6.dp)
                    .border(1.5.dp, T.hairline)
                    .clickable { onSettings(); sfx.tick() }
                    .drawBehind {
                        val x0 = size.width * 0.27f
                        val x1 = size.width * 0.73f
                        for (k in 0..2) {
                            val y = size.height * (0.36f + 0.14f * k)
                            drawRect(T.mutedFg, topLeft = Offset(x0, y), size = Size(x1 - x0, 1.8f))
                        }
                    },
            )
        }

        // ---- judul + meta ----
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = 470.dp),
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
                Text(
                    text = "EMPTY SHELF",
                    color = T.mutedFg,
                    fontFamily = DISPLAY,
                    fontSize = 34.sp,
                )
            }
        }

        // ---- tip animasi ----
        tip?.let {
            Text(
                text = it,
                color = T.lime,
                fontFamily = MONO,
                fontSize = 14.sp,
                letterSpacing = 1.4.sp,
                modifier = Modifier.offset(y = 478.dp).fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        // ---- hint bar ----
        if (tip == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset(y = 598.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                HintItem("< >", "move")
                ListSpacer()
                HintItem("A", "insert")
                ListSpacer()
                HintItem("X", "favorite")
                ListSpacer()
                HintItem("Y", "search")
                ListSpacer()
                HintItem("B", "settings")
            }
        }

        // ---- empty state ----
        if (games.isEmpty() && !scanning) {
            EmptyPanel()
        }

        // ---- overlay loading setelah kartrid masuk ----
        loading?.let { LoadOverlay(it) }
    }

    LaunchedEffect(games.size) {
        if (index > games.size - 1) onIndex((games.size - 1).coerceAtLeast(0))
    }
}

private fun metaOf(g: Game): String {
    val parts = ArrayList<String>()
    g.region?.let { parts.add(it) }
    parts.add(sizeText(g.sizeBytes))
    if (g.plays > 0) parts.add("played ${g.plays}×")
    else parts.add("never played")
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
                .border(1.2.dp, T.hairline)
                .background(T.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, color = T.fg, fontFamily = MONO, fontSize = 11.sp)
        }
        Box(Modifier.width(6.dp))
        Text(label, color = T.mutedFg, fontFamily = MONO, fontSize = 11.sp)
    }
}

@Composable
private fun ListSpacer() {
    Box(Modifier.width(24.dp))
}

@Composable
private fun EmptyPanel() {
    Box(
        Modifier
            .offset(x = (CENTER_X - 220f).dp, y = 300.dp)
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

@Composable
private fun LoadOverlay(g: Game) {
    Box(Modifier.fillMaxSize().background(T.bg.copy(alpha = 0.86f)))
    Column(
        Modifier.fillMaxWidth().offset(y = 250.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "LOADING",
            color = T.fg,
            fontFamily = DISPLAY,
            fontSize = 24.sp,
        )
        Text(
            text = g.title,
            color = T.mutedFg,
            fontFamily = MONO,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(
            Modifier
                .padding(top = 14.dp)
                .size(430.dp, 8.dp)
                .border(1.5.dp, T.hairline)
                .background(T.muted),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(T.lime, size = Size(size.width * 0.64f, size.height)) },
            )
        }
    }
}


