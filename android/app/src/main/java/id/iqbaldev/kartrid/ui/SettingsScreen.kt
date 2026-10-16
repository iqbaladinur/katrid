package id.iqbaldev.kartrid.ui

import android.view.KeyEvent as AKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.core.RetroArch
import id.iqbaldev.kartrid.core.Settings
import id.iqbaldev.kartrid.core.Sys

private const val NAV_W = 250f
private const val ROW_H = 96f   // baris pengaturan: jempol-friendly

/** satu baris pengaturan */
private sealed interface Row {
    val name: String
    val sub: String

    data class Choices(
        override val name: String,
        override val sub: String,
        val options: List<String>,
        val current: String?,
        val pick: (String) -> Unit,
    ) : Row

    data class Toggle(
        override val name: String,
        override val sub: String,
        val value: Boolean,
        val set: (Boolean) -> Unit,
    ) : Row

    data class Action(
        override val name: String,
        override val sub: String,
        val label: String,
        val run: () -> Unit,
    ) : Row

    data class Info(
        override val name: String,
        override val sub: String,
        val value: String = "",
    ) : Row
}

@Composable
fun SettingsScreen(
    settings: Settings,
    counts: Map<String, Int>,
    scannedFolders: Map<String, List<String>>,
    retroPkg: String?,
    installed: List<String>,
    cores: List<String>,
    cacheBytes: Long,
    onUpdate: (Settings) -> Unit,
    onRescan: () -> Unit,
    onClearCache: () -> Unit,
    onResetStats: () -> Unit,
    onFolderPick: (Sys) -> Unit,
    onClose: () -> Unit,
    sfx: Sfx,
    initialGroup: Int = 0,
    initialPick: String? = null,
) {
    val groups = listOf("Perpustakaan", "Emulator", "Tampilan", "Gambar & cache", "Data & tentang")
    var group by remember { mutableStateOf(initialGroup.coerceIn(0, groups.size - 1)) }
    var sel by remember { mutableStateOf(0) }
    var pickRow by remember { mutableStateOf<Row.Choices?>(null) }
    var pickSel by remember { mutableStateOf(0) }
    val fr = remember { FocusRequester() }
    val scroll = rememberScrollState()

    fun rows(g: Int): List<Row> = when (g) {
        0 -> buildList {
            for (s in Sys.order) {
                val paths = settings.folders[s.key].orEmpty()
                add(
                    Row.Action(
                        name = "Folder ${s.longLabel}",
                        sub = paths.firstOrNull() ?: "belum diatur — tekan A untuk pilih",
                        label = if (paths.isEmpty()) "PILIH" else "GANTI",
                        run = { onFolderPick(s) },
                    ),
                )
            }
            add(
                Row.Action(
                    name = "Pindai ulang",
                    sub = "${counts.values.sum()} file .gb/.gbc/.gba terdeteksi",
                    label = "PINDAI",
                    run = onRescan,
                ),
            )
            add(
                Row.Toggle(
                    name = "Pindai otomatis saat dibuka",
                    sub = "refresh inkremental pakai waktu modifikasi file",
                    value = settings.autoScan,
                    set = { onUpdate(settings.copy(autoScan = it)) },
                ),
            )
        }
        1 -> buildList {
            val engines = installed.ifEmpty { RetroArch.PACKAGES }
            add(
                Row.Choices(
                    name = "Engine",
                    sub = if (installed.isEmpty()) "RetroArch belum terdeteksi — install dulu"
                    else "aplikasi emulator yang dipakai (tap ganti)",
                    options = engines,
                    current = settings.retroPkg ?: retroPkg,
                    pick = { onUpdate(settings.copy(retroPkg = it)) },
                ),
            )
            for (s in Sys.order) {
                val avail = s.cores.filter { it in cores }.ifEmpty { s.cores }
                val cur = settings.cores[s.key]
                add(
                    Row.Choices(
                        name = "Core ${s.label}",
                        sub = if (cores.isEmpty()) "auto-deteksi kosong — pilih manual"
                        else "core untuk ${s.longLabel} (tap ganti)",
                        options = avail,
                        current = cur?.takeIf { it in avail } ?: avail.first(),
                        pick = { onUpdate(settings.copy(cores = settings.cores + (s.key to it))) },
                    ),
                )
            }
            add(
                Row.Info(
                    name = "Config file",
                    sub = settings.cfgPath ?: retroPkg?.let { RetroArch.defaultCfg(it) } ?: "—",
                    value = "otomatis",
                ),
            )
            add(
                Row.Toggle(
                    name = "Keluar saat keluar game",
                    sub = "QUITFOCUS — balik ke rak pas RetroArch kehilangan fokus",
                    value = settings.quitFocus,
                    set = { onUpdate(settings.copy(quitFocus = it)) },
                ),
            )
            add(
                Row.Toggle(
                    name = "Paksa 60 Hz",
                    sub = "REFRESH=60 — lebih mulus, sedikit lebih boros",
                    value = settings.force60,
                    set = { onUpdate(settings.copy(force60 = it)) },
                ),
            )
        }
        2 -> buildList {
            add(
                Row.Toggle(
                    name = "Animasi kartrid masuk slot",
                    sub = "turun → masuk slot → flash + klik",
                    value = settings.animEnabled,
                    set = { onUpdate(settings.copy(animEnabled = it)) },
                ),
            )
            add(Row.Info(name = "Orientasi", sub = "landscape dikunci (3:2)", value = "960×640"))
            add(Row.Info(name = "Kanvas desain", sub = "semua ukuran di-scale proporsional", value = "960×640 px"))
        }
        3 -> buildList {
            add(
                Row.Toggle(
                    name = "Unduh boxart otomatis",
                    sub = "libretro-thumbnails, downsample 256px",
                    value = settings.autoArt,
                    set = { onUpdate(settings.copy(autoArt = it)) },
                ),
            )
            add(Row.Info(name = "Ukuran cache", sub = "boxart tersimpan di storage internal app", value = fmtSize(cacheBytes)))
            add(Row.Action(name = "Bersihkan cache boxart", sub = "hapus semua PNG yang keunduh", label = "HAPUS", run = onClearCache))
        }
        else -> buildList {
            add(Row.Info(name = "Kartrid", sub = "frontend GB/GBC/GBA untuk RetroArch", value = "v0.1.0"))
            add(Row.Info(name = "Total game", sub = "semua sistem", value = counts.values.sum().toString()))
            add(Row.Info(name = "RetroArch", sub = "paket terdeteksi", value = retroPkg ?: "—"))
            add(Row.Info(name = "Core terpasang", sub = cores.joinToString(", ").ifBlank { "—" }, value = cores.size.toString()))
            for (s in Sys.order) {
                val f = scannedFolders[s.key].orEmpty()
                add(Row.Info(name = "Folder ${s.label}", sub = f.joinToString(" · ").ifBlank { "—" }, value = "${counts[s.key] ?: 0}"))
            }
            add(Row.Action(name = "Reset statistik main", sub = "jumlah main & durasi jadi nol", label = "RESET", run = onResetStats))
        }
    }

    val list = rows(group)
    val picker = pickRow

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val r = pickRow
                if (r != null) {
                    when (ev.nativeKeyEvent.keyCode) {
                        AKey.KEYCODE_DPAD_UP -> { pickSel = (pickSel - 1).coerceAtLeast(0); sfx.tick(); true }
                        AKey.KEYCODE_DPAD_DOWN -> { pickSel = (pickSel + 1).coerceAtMost(r.options.size - 1); sfx.tick(); true }
                        AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> {
                            r.pick(r.options[pickSel.coerceIn(0, r.options.size - 1)])
                            pickRow = null
                            sfx.tick()
                            true
                        }
                        AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { pickRow = null; sfx.tick(); true }
                        else -> false
                    }
                } else {
                    when (ev.nativeKeyEvent.keyCode) {
                        AKey.KEYCODE_DPAD_UP -> { sel = (sel - 1).coerceAtLeast(0); sfx.tick(); true }
                        AKey.KEYCODE_DPAD_DOWN -> { sel = (sel + 1).coerceAtMost(list.size - 1); sfx.tick(); true }
                        AKey.KEYCODE_DPAD_LEFT -> { group = (group - 1 + groups.size) % groups.size; sel = 0; sfx.tick(); true }
                        AKey.KEYCODE_DPAD_RIGHT -> { group = (group + 1) % groups.size; sel = 0; sfx.tick(); true }
                        AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> {
                            val row = list.getOrNull(sel)
                            when (row) {
                                is Row.Choices -> { pickRow = row; pickSel = row.options.indexOf(row.current).coerceAtLeast(0); sfx.tick() }
                                is Row.Toggle -> row.set(!row.value)
                                is Row.Action -> row.run()
                                else -> Unit
                            }
                            true
                        }
                        AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> { onClose(); sfx.tick(); true }
                        else -> false
                    }
                }
            },
    ) {
        // top bar
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 22.dp)
                .drawBehind {
                    drawRect(T.hairline, topLeft = Offset(0f, size.height - 1.5f), size = Size(size.width, 1.5f))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("← PENGATURAN", color = T.fg, fontFamily = MONO, fontSize = 13.sp, letterSpacing = 1.4.sp)
            Box(Modifier.weight(1f))
            Text("v0.1.0", color = T.mutedFg, fontFamily = MONO, fontSize = 11.sp)
        }

        // nav kiri
        Column(
            Modifier
                .offset(y = 38.dp)
                .width(NAV_W.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawRect(T.hairline, topLeft = Offset(size.width - 1.5f, 0f), size = Size(1.5f, size.height))
                }
                .padding(top = 12.dp),
        ) {
            groups.forEachIndexed { i, g ->
                val on = i == group
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (on) {
                                drawRect(T.muted)
                                drawRect(T.lime, topLeft = Offset(0f, 0f), size = Size(4f, size.height))
                            }
                        }
                        .clickable { group = i; sel = 0; sfx.tick() }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (on) "› $g" else g,
                        color = if (on) T.lime else T.mutedFg,
                        fontFamily = MONO,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // body (scroll — grup Emulator 8 baris × 52dp nggak muat sekaligus)
        Column(
            Modifier
                .offset(x = NAV_W.dp, y = 38.dp)
                .padding(start = 26.dp, end = 26.dp, top = 12.dp)
                .height(590.dp)
                .verticalScroll(scroll),
        ) {
            Text(
                text = groups[group].uppercase(),
                color = T.mutedFg,
                fontFamily = MONO,
                fontSize = 12.5.sp,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            list.forEachIndexed { i, r ->
                SettingRow(r, i == sel) {
                    sel = i
                    when (r) {
                        is Row.Choices -> { pickRow = r; pickSel = r.options.indexOf(r.current).coerceAtLeast(0); sfx.tick() }
                        is Row.Toggle -> r.set(!r.value)
                        is Row.Action -> r.run()
                        else -> Unit
                    }
                }
            }
        }

        // hint
        Row(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = 588.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            HintItem("▲ ▼", "pilih")
            Box(Modifier.width(26.dp))
            HintItem("◀ ▶", "grup")
            Box(Modifier.width(26.dp))
            HintItem("A", "ubah")
            Box(Modifier.width(26.dp))
            HintItem("B", "kembali")
        }

        // modal pilih engine/core
        if (picker != null) {
            PickerOverlay(
                title = picker.name,
                options = picker.options,
                current = picker.current,
                selected = pickSel.coerceIn(0, picker.options.size - 1),
                label = { engineLabel(it) },
                onSelect = { idx ->
                    picker.pick(picker.options[idx])
                    pickRow = null
                },
                onClose = { pickRow = null },
                sfx = sfx,
            )
        }
    }

    // ikutin seleksi pas scroll (perkiraan posisi baris)
    LaunchedEffect(sel, group) {
        scroll.animateScrollTo(((sel * ROW_H - 150).toInt()).coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        runCatching { fr.requestFocus() }
    }

    // buka modal picker langsung (dipakai preview / deep-link)
    LaunchedEffect(initialPick) {
        if (initialPick != null) {
            val r = rows(group).firstOrNull { it is Row.Choices && it.name == initialPick } as? Row.Choices
            if (r != null) {
                pickRow = r
                pickSel = r.options.indexOf(r.current).coerceAtLeast(0)
            }
        }
    }
}

@Composable
private fun SettingRow(r: Row, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_H.dp)
            .drawBehind {
                if (selected) drawRect(T.muted)
                drawRect(T.hairline, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f))
            }
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = r.name,
                color = if (selected) T.fg else T.fg.copy(alpha = 0.92f),
                fontFamily = SANS,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = r.sub,
                color = T.mutedFg,
                fontFamily = MONO,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (r) {
            is Row.Toggle -> SwitchBox(r.value)
            is Row.Choices -> Box(
                Modifier
                    .border(1.5.dp, if (selected) T.lime else T.hairline)
                    .background(T.muted)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    text = shortCore(r.current.orEmpty()).ifBlank { "▾ pilih" },
                    color = if (selected) T.lime else T.fg,
                    fontFamily = MONO,
                    fontSize = 13.5.sp,
                )
            }
            is Row.Action -> Box(
                Modifier
                    .border(1.5.dp, if (selected) T.lime else T.hairline)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                Text(r.label, color = if (selected) T.lime else T.fg, fontFamily = MONO, fontSize = 13.5.sp)
            }
            is Row.Info -> Text(r.value, color = T.mutedFg, fontFamily = MONO, fontSize = 10.5.sp)
        }
    }
}

/** modal daftar pilihan (pola Daijishou: baris → daftar → pilih) */
@Composable
private fun PickerOverlay(
    title: String,
    options: List<String>,
    current: String?,
    selected: Int,
    label: (String) -> String,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    sfx: Sfx,
) {
    val maxRows = 7
    val start = (selected - 2).coerceIn(0, (options.size - maxRows).coerceAtLeast(0))
    val visible = options.slice(start until (start + maxRows).coerceAtMost(options.size))
    val boxH = (visible.size * 72 + 116).coerceAtMost(560)

    Box(Modifier.size(960.dp, 640.dp).background(T.bg.copy(alpha = 0.93f)))
    Column(
        Modifier
            .offset(x = 250.dp, y = ((640 - boxH) / 2f).dp)
            .size(460.dp, boxH.dp)
            .background(T.surface)
            .border(1.5.dp, T.hairline)
            .padding(16.dp),
    ) {
        Text(title, color = T.lime, fontFamily = MONO, fontSize = 12.sp, letterSpacing = 1.4.sp)
        Box(Modifier.height(10.dp))
        visible.forEachIndexed { i, o ->
            val on = start + i == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .drawBehind {
                        if (on) drawRect(T.muted)
                        drawRect(T.hairline, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f))
                    }
                    .clickable { sfx.tick(); onSelect(start + i) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label(o),
                    color = if (on) T.lime else T.fg,
                    fontFamily = MONO,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (o == current) {
                    Box(Modifier.weight(1f))
                    Text("●", color = T.lime, fontFamily = MONO, fontSize = 12.sp)
                }
            }
        }
        Box(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            HintItem("▲ ▼", "pilih")
            Box(Modifier.width(22.dp))
            HintItem("A", "pilih ini")
            Box(Modifier.width(22.dp))
            HintItem("B", "batal")
        }
    }
}

@Composable
private fun SwitchBox(on: Boolean) {
    Box(
        Modifier
            .size(84.dp, 48.dp)
            .border(1.5.dp, if (on) T.lime else T.ink)
            .background(if (on) T.lime else T.muted),
    ) {
        Box(
            Modifier
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(3.dp)
                .size(32.dp)
                .background(if (on) T.bg else T.fg),
        )
    }
}

fun shortCore(name: String): String =
    name.removeSuffix("_libretro_android.so").removeSuffix("_libretro.so").uppercase()

fun engineLabel(pkg: String): String = when (pkg) {
    "com.retroarch" -> "RetroArch"
    "com.retroarch.aarch64" -> "RetroArch (aarch64)"
    "com.retroarch.ra32" -> "RetroArch (32-bit)"
    "com.retroarch.quest" -> "RetroArch (Quest)"
    else -> pkg
}

fun fmtSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}