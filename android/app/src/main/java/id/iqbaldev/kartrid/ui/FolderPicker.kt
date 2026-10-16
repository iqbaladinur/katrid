package id.iqbaldev.kartrid.ui

import android.os.Environment
import android.view.KeyEvent as AKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.iqbaldev.kartrid.core.Sys
import java.io.File

/**
 * Pemilih folder ROM. Pakai path filesystem asli (bukan content://) karena
 * RetroArch butuh path asli buat extra ROM. Butuh MANAGE_EXTERNAL_STORAGE.
 */
@Composable
fun FolderPicker(
    sys: Sys,
    initial: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    sfx: Sfx,
) {
    val root = Environment.getExternalStorageDirectory().absolutePath
    var dir by remember {
        mutableStateOf(
            run {
                val f = initial?.let { File(it) }
                when {
                    f != null && f.isDirectory -> f
                    f?.parentFile?.isDirectory == true -> f.parentFile!!
                    else -> File(root)
                }
            },
        )
    }
    var sel by remember { mutableStateOf(0) }
    val fr = remember { FocusRequester() }

    val dirs = remember(dir) {
        (dir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && it.canRead() && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
    }
    val hits = remember(dir) {
        (dir.listFiles() ?: emptyArray()).count {
            it.isFile && it.extension.equals(sys.ext, ignoreCase = true)
        }
    }
    // baris 0 = ".." (naik), sisanya folder
    val total = dirs.size + 1

    fun up() {
        val p = dir.parentFile
        if (p != null && p.canRead() && dir.absolutePath.length > root.length) {
            dir = p
            sel = 0
            sfx.tick()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.nativeKeyEvent.keyCode) {
                    AKey.KEYCODE_DPAD_UP -> { sel = (sel - 1).coerceAtLeast(0); sfx.tick(); true }
                    AKey.KEYCODE_DPAD_DOWN -> { sel = (sel + 1).coerceAtMost(total - 1); sfx.tick(); true }
                    AKey.KEYCODE_BUTTON_A, AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> {
                        if (sel == 0) up() else { dir = dirs[sel - 1]; sel = 0 }
                        sfx.tick(); true
                    }
                    AKey.KEYCODE_BUTTON_X, AKey.KEYCODE_DPAD_RIGHT -> { onPick(dir.absolutePath); true }
                    AKey.KEYCODE_BUTTON_B, AKey.KEYCODE_BACK -> {
                        if (dir.absolutePath.length > root.length) up() else { onClose(); sfx.tick() }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Box(Modifier.size(960.dp, 640.dp).background(T.bg.copy(alpha = 0.93f)))
        Column(
            Modifier
                .offset(x = 110.dp, y = 70.dp)
                .size(740.dp, 500.dp)
                .background(T.surface)
                .border(1.5.dp, T.hairline)
                .padding(22.dp),
        ) {
            Text(
                text = "PILIH FOLDER ${sys.longLabel}",
                color = T.lime,
                fontFamily = MONO,
                fontSize = 13.sp,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = dir.absolutePath,
                color = T.mutedFg,
                fontFamily = MONO,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(Modifier.height(12.dp))

            // daftar (scroll: jangan dorong tombol keluar panel kalau folder banyak)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                PickerRow(".. (naik satu folder)", sel == 0) { sel = 0; up() }
                dirs.forEachIndexed { i, d ->
                    PickerRow("${d.name}/", sel == i + 1) {
                        sel = i + 1
                        dir = d
                        sel = 0
                    }
                }
            }

            Box(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$hits file .${sys.ext} di folder ini",
                    color = if (hits > 0) T.lime else T.mutedFg,
                    fontFamily = MONO,
                    fontSize = 11.sp,
                )
                Box(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(T.lime)
                        .clickable { onPick(dir.absolutePath) }
                        .padding(horizontal = 18f.tdp, vertical = 12f.tdp),
                ) {
                    Text("PAKAI FOLDER INI", color = T.bg, fontFamily = DISPLAY, fontSize = 13.sp)
                }
                Box(Modifier.width(10.dp))
                Box(
                    Modifier
                        .border(1.5.dp, T.hairline)
                        .clickable { onClose() }
                        .padding(horizontal = 16f.tdp, vertical = 12f.tdp),
                ) {
                    Text("BATAL", color = T.mutedFg, fontFamily = MONO, fontSize = 12.sp)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                HintItem("▲ ▼", "pilih")
                Box(Modifier.width(22.dp))
                HintItem("A", "masuk")
                Box(Modifier.width(22.dp))
                HintItem("X", "pakai folder")
                Box(Modifier.width(22.dp))
                HintItem("B", "naik / batal")
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { fr.requestFocus() }
    }
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(45f.tdp)
            .drawBehind {
                if (selected) {
                    drawRect(T.muted)
                    drawRect(T.lime, topLeft = Offset(0f, 0f), size = Size(4f, size.height))
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) T.fg else T.mutedFg,
            fontFamily = MONO,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
