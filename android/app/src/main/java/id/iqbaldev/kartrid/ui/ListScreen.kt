package id.iqbaldev.kartrid.ui

import android.view.KeyEvent as AKey
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Sys
import id.iqbaldev.kartrid.core.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ROWS = 8
private const val ROW_H = 72f

/**
 * S3 Cari & Daftar — versi D-pad: daftar berjendela + rail A–Z.
 * (Text input nyusul; di handheld tanpa keyboard, rail alfabet lebih enak.)
 */
@Composable
fun ListScreen(
    tab: Tab,
    games: List<Game>,
    artOf: suspend (Game) -> ImageBitmap?,
    artVersion: Int,
    onLaunch: (Game) -> Unit,
    onClose: () -> Unit,
    onTab: (Tab) -> Unit,
    sfx: Sfx,
) {
    var sel by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val searchFr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val list = remember(games, query) {
        if (query.isBlank()) games
        else games.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    val selSafe = sel.coerceIn(0, (list.size - 1).coerceAtLeast(0))

    val window = remember(selSafe, list.size) {
        val half = ROWS / 2
        val start = (selSafe - half).coerceIn(0, (list.size - ROWS).coerceAtLeast(0))
        start until (start + ROWS).coerceAtMost(list.size)
    }

    // rail A–Z: initial -> index pertama
    val initials = remember(list) {
        list.withIndex()
            .map { (i, g) -> (g.title.firstOrNull()?.uppercaseChar() ?: '#') to i }
            .groupBy { it.first }
            .mapValues { (_, v) -> v.first().second }
            .toSortedMap()
    }

    fun jump(c: Char) {
        val idx = initials[c] ?: return
        sel = idx
        sfx.tick()
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.nativeKeyEvent.keyCode) {
                    AKey.KEYCODE_DPAD_UP -> { sel = (selSafe - 1).coerceAtLeast(0); sfx.tick(); true }
                    AKey.KEYCODE_DPAD_DOWN -> { sel = (selSafe + 1).coerceAtMost((list.size - 1).coerceAtLeast(0)); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_L1 -> { sel = (selSafe - 10).coerceAtLeast(0); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_R1 -> { sel = (selSafe + 10).coerceAtMost((list.size - 1).coerceAtLeast(0)); sfx.tick(); true }
                    AKey.KEYCODE_DPAD_LEFT -> {
                        val c = initials.keys.filter { it < (list.getOrNull(selSafe)?.title?.firstOrNull()?.uppercaseChar() ?: 'A') }
                        c.lastOrNull()?.let { jump(it) } ?: run { sfx.tick() }
                        true
                    }
                    AKey.KEYCODE_DPAD_RIGHT -> {
                        val cur = list.getOrNull(selSafe)?.title?.firstOrNull()?.uppercaseChar() ?: 'A'
                        initials.keys.firstOrNull { it > cur }?.let { jump(it) } ?: run { sfx.tick() }
                        true
                    }
                    AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> {
                        list.getOrNull(selSafe)?.let { onLaunch(it) }
                        true
                    }
                    AKey.KEYCODE_BUTTON_X -> {
                        onTab(Tab.list[(Tab.list.indexOf(tab) + 1) % Tab.list.size])
                        sel = 0
                        sfx.tick()
                        true
                    }
                    AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { onClose(); sfx.tick(); true }
                    else -> false
                }
            },
    ) {
        Box(Modifier.fillMaxSize().background(T.bg))

        // header
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 22.dp)
                .drawBehind {
                    drawRect(T.hairline, topLeft = Offset(0f, size.height - 1.5f), size = Size(size.width, 1.5f))
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("SEARCH & LIST", color = T.lime, fontFamily = MONO, fontSize = 15.sp, letterSpacing = 1.4.sp)
            for (s in Tab.list) {
                val on = s == tab
                Text(
                    text = s.label,
                    color = if (on) T.lime else T.mutedFg,
                    fontFamily = MONO,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onTab(s); sel = 0; sfx.tick() },
                )
            }
            Box(Modifier.width(1.dp))
            Text("${list.size} games", color = T.mutedFg, fontFamily = MONO, fontSize = 13.sp)
        }

        // field pencarian — fokus otomatis, keyboard langsung muncul
        Row(
            Modifier
                .offset(y = 48.dp)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .height(42.dp)
                .background(T.surface.copy(alpha = 0.95f))
                .border(1.5.dp, T.hairline)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(">", color = T.lime, fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFr),
                textStyle = TextStyle(color = T.fg, fontFamily = MONO, fontSize = 13.sp),
                cursorBrush = SolidColor(T.lime),
                singleLine = true,
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text("SEARCH…", color = T.mutedFg, fontFamily = MONO, fontSize = 13.sp)
                        }
                        inner()
                    }
                },
            )
        }

        // daftar
        Column(Modifier.offset(y = 104.dp).padding(start = 22.dp)) {
            for (i in window) {
                val g = list[i]
                val on = i == selSafe
                key(g.id) {
                    var art by remember(g.id) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(g.id, artVersion) {
                        art = withContext(Dispatchers.IO) { artOf(g) }
                    }
                    Row(
                        Modifier
                            .width(760.dp)
                            .height(ROW_H.dp)
                            .drawBehind {
                                if (on) {
                                    drawRect(T.muted)
                                    drawRect(T.lime, topLeft = Offset(0f, 0f), size = Size(4f, size.height))
                                }
                                drawRect(T.hairline, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f))
                            }
                            .clickable { sel = i; sfx.tick(); onLaunch(g) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(start = 8.dp))
                        Box(
                            Modifier
                                .size(46f.tdp)
                                .border(1.5.dp, T.black)
                                .background(shellColor(g.sys.shell, on)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val bmp = art
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                    contentScale = ContentScale.Fit,
                                    alpha = if (on) 1f else 0.72f,
                                )
                            } else {
                                Text(
                                    text = g.sys.label,
                                    color = T.fg.copy(alpha = if (on) 0.55f else 0.3f),
                                    fontFamily = MONO,
                                    fontSize = 8.sp,
                                )
                            }
                        }
                        Box(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = g.title,
                                color = if (on) T.fg else T.fg.copy(alpha = 0.88f),
                                fontFamily = MONO,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = buildString {
                                g.region?.let { append(it).append(" · ") }
                                append(fmtSize(g.sizeBytes))
                                if (g.plays > 0) append(" · ${g.plays}×")
                            },
                            color = T.mutedFg,
                            fontFamily = MONO,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        Box(Modifier.width(10.dp))
                    }
                }
            }
        }

        // rail A–Z
        Column(
            Modifier
                .offset(x = 800.dp, y = 108.dp)
                .padding(start = 8.dp, end = 8.dp),
        ) {
            for ((c, _) in initials) {
                val cur = list.getOrNull(selSafe)?.title?.firstOrNull()?.uppercaseChar()
                Text(
                    text = c.toString(),
                    color = if (c == cur) T.lime else T.mutedFg,
                    fontFamily = MONO,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(vertical = 3.dp)
                        .clickable { jump(c) },
                )
            }
        }

        // hint: satu box solid biar kebaca di atas daftar
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = 588.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .background(T.surface)
                    .border(1.5.dp, T.hairline)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HintItem("A", "play")
                    HintItem("X", "system")
                    HintItem("B", "back")
                }
            }
        }
    }

    LaunchedEffect(query) {
        sel = 0
    }

    LaunchedEffect(Unit) {
        runCatching { searchFr.requestFocus() }
        kb?.show()
        runCatching { fr.requestFocus() }
    }
}
