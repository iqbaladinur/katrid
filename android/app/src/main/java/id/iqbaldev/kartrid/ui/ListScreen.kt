package id.iqbaldev.kartrid.ui

import android.view.KeyEvent as AKey
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// geometri daftar (basis kanvas 960x640) — 6 baris biar hint bar nggak nutupin isi
private const val ROWS = 6
private const val ROW_H = 60f
private const val ROW_GAP = 6f
private const val ROW_W = 790f
private const val PAD_X = 22f
private const val LIST_TOP = 164f
private const val RAIL_X = 838f
private const val FAV_GOLD = 0xFFFFC94D
private const val SHADOW_PX = 3f

/**
 * S3 Cari & Daftar — D-pad first, daftar berjendela + rail A–Z.
 *
 * Revamp 17 Okt 2026 (aksen design system iqbaldev "riso/print", bukan web):
 * baris = kartu flat dengan ink border + hard offset shadow (0 blur),
 * baris terpilih = keadaan "ketekan" (shadow hilang + geser 2px + border lime),
 * chip tab + badge + hint key semuanya pakai pola ds-btn/ds-badge.
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
    onFav: (Game) -> Unit,
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

    // keyboard naik (field cari auto-fokus) → layout kompak: header disembunyikan,
    // field cari naik ke atas, tinggi baris dipadatkan biar baris nggak ketutup IME.
    val den = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(den)
    val imeOpen = imePx > 0
    val rowH = if (imeOpen) 48f else ROW_H
    val rowPitch = rowH + ROW_GAP
    val listTop = if (imeOpen) 62f else LIST_TOP
    val availBottom = if (imeOpen) (BASE_H - imePx) - 8f else 560f
    val rowsVisible = (((availBottom - listTop) / rowPitch).toInt()).coerceIn(1, ROWS)

    val window = remember(selSafe, list.size, rowsVisible) {
        val half = rowsVisible / 2
        val start = (selSafe - half).coerceIn(0, (list.size - rowsVisible).coerceAtLeast(0))
        start until (start + rowsVisible).coerceAtMost(list.size)
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
                    AKey.KEYCODE_BUTTON_L1 -> { sel = (selSafe - rowsVisible).coerceAtLeast(0); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_R1 -> { sel = (selSafe + rowsVisible).coerceAtMost((list.size - 1).coerceAtLeast(0)); sfx.tick(); true }
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
                    AKey.KEYCODE_BUTTON_Y -> {
                        list.getOrNull(selSafe)?.let { onFav(it) }
                        sfx.tick(); true
                    }
                    AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { kb?.hide(); onClose(); sfx.tick(); true }
                    else -> false
                }
            },
    ) {
        Box(Modifier.fillMaxSize().background(T.bg))

        // ---------- header ----------
        if (!imeOpen) Row(
            Modifier
                .fillMaxWidth()
                .height(92f.dp)
                .padding(start = PAD_X.dp, end = PAD_X.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // penanda judul: blok lime (aksen sistem, bukan underline)
            Box(Modifier.size(9.dp, 22.dp).background(T.lime))
            Box(Modifier.width(6.dp))
            Text("LIBRARY", color = T.fg, fontFamily = MONO, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Box(Modifier.width(20.dp))
            for (s in Tab.list) {
                TabChip(label = s.label, active = s == tab) {
                    onTab(s); sel = 0; sfx.tick()
                }
            }
            Box(Modifier.weight(1f))
            CountBadge(list.size)
        }

        // ---------- field pencarian ----------
        Box(
            Modifier
                .offset(x = PAD_X.dp, y = (if (imeOpen) 8f else 100f).dp)
                .width((ROW_W + 48f).dp)
                .height(46f.dp)
                .drawBehind {
                    drawRect(T.shadow, topLeft = Offset(SHADOW_PX, SHADOW_PX), size = Size(size.width, size.height))
                }
                .background(T.surface)
                .border(1.5.dp, T.hairline)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(">", color = T.lime, fontFamily = MONO, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .width(560.dp)
                        .focusRequester(searchFr),
                    textStyle = TextStyle(color = T.fg, fontFamily = MONO, fontSize = 14.sp),
                    cursorBrush = SolidColor(T.lime),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (query.isEmpty()) {
                                Text("SEARCH ${games.size} TITLES", color = T.mutedFg.copy(alpha = 0.7f), fontFamily = MONO, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
                Box(Modifier.weight(1f))
                if (query.isNotEmpty()) {
                    Text("${list.size} HIT", color = T.lime, fontFamily = MONO, fontSize = 11.sp, letterSpacing = 1.sp)
                }
            }
        }

        // ---------- daftar ----------
        for ((slot, i) in window.withIndex()) {
            val g = list[i]
            val on = i == selSafe
            key(g.id) {
                var art by remember(g.id) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(g.id, artVersion) {
                    art = withContext(Dispatchers.IO) { artOf(g) }
                }
                RowItem(
                    index = i,
                    game = g,
                    art = art,
                    selected = on,
                    y = listTop + slot * rowPitch,
                    rowH = rowH,
                    onClick = { sel = i; sfx.tick(); onLaunch(g) },
                )
            }
        }

        // ---------- rail A–Z ----------
        if (!imeOpen) Column(
            Modifier.offset(x = RAIL_X.dp, y = (LIST_TOP + 2f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cur = list.getOrNull(selSafe)?.title?.firstOrNull()?.uppercaseChar()
            for ((c, _) in initials) {
                val here = c == cur
                Box(
                    Modifier
                        .size(22.dp, 14.dp)
                        .background(if (here) T.lime else Color.Transparent)
                        .clickable { jump(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = c.toString(),
                        color = if (here) T.bg else T.mutedFg,
                        fontFamily = MONO,
                        fontSize = 10.sp,
                        fontWeight = if (here) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // ---------- empty state ----------
        if (list.isEmpty()) {
            Box(
                Modifier
                    .offset(x = (PAD_X + 200f).dp, y = (if (imeOpen) 100f else 300f).dp)
                    .width(360.dp)
                    .height(120.dp)
                    .drawBehind {
                        drawRect(T.shadow, topLeft = Offset(5f, 5f), size = Size(size.width, size.height))
                    }
                    .background(T.surface)
                    .border(1.5.dp, T.hairline),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (query.isBlank()) "NO GAMES YET" else "NO MATCH",
                    color = T.mutedFg,
                    fontFamily = MONO,
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp,
                )
            }
        }

        // ---------- hint bar ----------
        if (!imeOpen) Row(
            Modifier
                .fillMaxWidth()
                .offset(y = 586f.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .drawBehind {
                        drawRect(T.shadow, topLeft = Offset(4f, 4f), size = Size(size.width, size.height))
                    }
                    .background(T.surface)
                    .border(1.5.dp, T.hairline)
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HintItem("A", "play")
                    HintItem("X", "system")
                    HintItem("Y", "favorite")
                    HintItem("B", "back")
                }
            }
        }
    }

    LaunchedEffect(query) {
        sel = 0
    }

    LaunchedEffect(Unit) {
        runCatching { fr.requestFocus() }
        // tunggu input connection siap, baru fokus field + munculin keyboard
        delay(300)
        runCatching { searchFr.requestFocus() }
        kb?.show()
    }
}

/** satu baris daftar: nomor + thumb boxart + judul/meta + badge */
@Composable
private fun RowItem(
    index: Int,
    game: Game,
    art: ImageBitmap?,
    selected: Boolean,
    y: Float,
    rowH: Float,
    onClick: () -> Unit,
) {
    val press = if (selected) 2f else 0f
    val thumb = rowH - 18f
    Row(
        Modifier
            .offset(x = (PAD_X + press).dp, y = (y + press).dp)
            .width(ROW_W.dp)
            .height(rowH.dp)
            .then(
                if (selected) Modifier
                else Modifier.drawBehind {
                    // hard offset shadow 0 blur — DNA design system
                    drawRect(T.shadow, topLeft = Offset(SHADOW_PX, SHADOW_PX), size = Size(size.width, size.height))
                },
            )
            .background(if (selected) T.muted else T.surface)
            .border(
                width = if (selected) 2.dp else 1.5.dp,
                color = if (selected) T.lime else T.hairline,
            )
            .clickable { onClick() }
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // nomor urut ala console list
        Text(
            text = (index + 1).toString().padStart(3, '0'),
            color = T.mutedFg.copy(alpha = if (selected) 0.9f else 0.55f),
            fontFamily = MONO,
            fontSize = 11.sp,
        )
        Box(Modifier.width(12.dp))

        // thumb boxart: frame ink + hard shadow mini
        Box(
            Modifier
                .size(thumb.dp)
                .drawBehind {
                    drawRect(T.shadow, topLeft = Offset(2f, 2f), size = Size(size.width, size.height))
                }
                .clip(RoundedCornerShape(6.dp))
                .background(shellColor(game.sys.shell, selected))
                .border(1.5.dp, T.black, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                    alpha = if (selected) 1f else 0.78f,
                )
            } else {
                Text(
                    text = game.sys.label,
                    color = T.fg.copy(alpha = if (selected) 0.7f else 0.35f),
                    fontFamily = MONO,
                    fontSize = 8.sp,
                )
            }
        }
        Box(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = game.title,
                color = if (selected) T.fg else T.fg.copy(alpha = 0.9f),
                fontFamily = MONO,
                fontSize = 14.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(game.sys.longLabel)
                    game.region?.let { append(" · ").append(it) }
                    append(" · ").append(fmtSize(game.sizeBytes))
                },
                color = T.mutedFg.copy(alpha = 0.85f),
                fontFamily = MONO,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(12.dp))

        if (game.plays > 0) MetaBadge("${game.plays}×", T.mutedFg)
        if (game.fav) {
            Box(Modifier.width(6.dp))
            MetaBadge("★", Color(FAV_GOLD), borderColor = Color(FAV_GOLD))
        }
    }
}

/** chip tab ala ds-btn: kotak, ink border, hard shadow; aktif = lime solid */
@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .drawBehind {
                if (!active) drawRect(T.shadow, topLeft = Offset(2f, 2f), size = Size(size.width, size.height))
            }
            .background(if (active) T.lime else T.surface)
            .border(1.5.dp, if (active) T.lime else T.hairline, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (active) T.bg else T.mutedFg,
            fontFamily = MONO,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

/** badge angka ala ds-badge (pill mono kecil) */
@Composable
private fun CountBadge(n: Int) {
    Box(
        Modifier
            .drawBehind {
                drawRect(T.shadow, topLeft = Offset(2f, 2f), size = Size(size.width, size.height))
            }
            .background(T.surface)
            .border(1.5.dp, T.hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text("$n GAMES", color = T.mutedFg, fontFamily = MONO, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun MetaBadge(text: String, color: Color, borderColor: Color = T.hairline) {
    Box(
        Modifier
            .background(T.bg.copy(alpha = 0.55f))
            .border(1.2.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontFamily = MONO, fontSize = 10.sp, letterSpacing = 0.5.sp)
    }
}

