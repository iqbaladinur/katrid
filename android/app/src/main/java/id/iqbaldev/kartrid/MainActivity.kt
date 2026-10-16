package id.iqbaldev.kartrid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings as ASettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.iqbaldev.kartrid.core.ART_FAILED
import id.iqbaldev.kartrid.core.ART_OK
import id.iqbaldev.kartrid.core.ArtCache
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Library
import id.iqbaldev.kartrid.core.RetroArch
import id.iqbaldev.kartrid.core.RomScanner
import id.iqbaldev.kartrid.core.Store
import id.iqbaldev.kartrid.core.Sys
import id.iqbaldev.kartrid.ui.DISPLAY
import id.iqbaldev.kartrid.ui.FolderPicker
import id.iqbaldev.kartrid.ui.ListScreen
import id.iqbaldev.kartrid.ui.MONO
import id.iqbaldev.kartrid.ui.MockCanvas
import id.iqbaldev.kartrid.ui.SANS
import id.iqbaldev.kartrid.ui.SettingsScreen
import id.iqbaldev.kartrid.ui.Sfx
import id.iqbaldev.kartrid.ui.ShelfScreen
import id.iqbaldev.kartrid.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var artCache: ArtCache
    private lateinit var sfx: Sfx
    private var justLaunched = false
    private var detectedCores: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        artCache = ArtCache(this)
        sfx = Sfx(this)
        immersive()

        setContent {
            MockCanvas {
                KartridRoot(
                    ctx = this,
                    store = store,
                    artCache = artCache,
                    sfx = sfx,
                    hasStorage = { hasStorage() },
                    requestStorage = { requestStoragePermission() },
                    launchGame = { g -> launchGame(g) },
                    onCores = { detectedCores = it },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (justLaunched) {
            justLaunched = false
            sfx.eject()
        }
    }

    override fun onDestroy() {
        sfx.release()
        super.onDestroy()
    }

    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun hasStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = Intent(ASettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:$packageName"))
            runCatching { startActivity(i) }.onFailure {
                runCatching { startActivity(Intent(ASettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 11)
        }
    }

    private fun launchGame(g: Game): String? {
        val pkg = RetroArch.pickPackage(this, store.settings.retroPkg)
            ?: return "RetroArch belum terdeteksi di device ini"
        val core = store.settings.cores[g.system]?.takeIf { it.isNotBlank() }
            ?: detectedCores.firstOrNull { it in g.sys.cores }
            ?: g.sys.cores.first()
        val cfg = store.settings.cfgPath?.takeIf { it.isNotBlank() } ?: RetroArch.defaultCfg(pkg)
        val i = RetroArch.launchIntent(
            pkg = pkg,
            rom = g.path,
            core = RetroArch.corePath(pkg, core),
            cfg = cfg,
            quitFocus = store.settings.quitFocus,
            force60 = store.settings.force60,
        )
        return runCatching {
            startActivity(i)
            justLaunched = true
            null
        }.getOrElse { "Gagal buka RetroArch: ${it.message}" }
    }
}

@Composable
private fun KartridRoot(
    ctx: Context,
    store: Store,
    artCache: ArtCache,
    sfx: Sfx,
    hasStorage: () -> Boolean,
    requestStorage: () -> Unit,
    launchGame: (Game) -> String?,
    onCores: (List<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var settings by remember { mutableStateOf(store.settings) }
    var games by remember { mutableStateOf(store.library.games) }
    var tab by remember { mutableStateOf(Sys.GBA) }
    var index by remember { mutableStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var artVersion by remember { mutableStateOf(0) }
    var permOk by remember { mutableStateOf(hasStorage()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var cores by remember { mutableStateOf<List<String>>(emptyList()) }
    var retroPkg by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(0) }
    var pickSys by remember { mutableStateOf<Sys?>(null) }
    var cacheBytes by remember { mutableStateOf(0L) }

    val tried = remember { HashSet<String>() }

    suspend fun runScan() {
        if (scanning) return
        scanning = true
        try {
            note = "mencari folder…"
            val folders = settings.folders.ifEmpty {
                withContext(Dispatchers.IO) { RomScanner.discover(ctx) }
            }
            note = "memindai…"
            val found = withContext(Dispatchers.IO) {
                RomScanner.scan(folders) { n, _ -> note = "memindai… $n" }
            }
            val merged = store.mergeScan(found, settings)
            store.saveLibrary(Library(merged, System.currentTimeMillis()))
            if (settings.folders.isEmpty() && folders.isNotEmpty()) {
                settings = settings.copy(folders = folders)
                store.saveSettings(settings)
            }
            games = merged
            note = ""

            // unduh boxart di background
            if (settings.autoArt) {
                withContext(Dispatchers.IO) {
                    var n = 0
                    for (g in merged) {
                        if (g.id in tried) continue
                        tried.add(g.id)
                        if (g.artState == ART_OK || g.artState == ART_FAILED) continue
                        val ok = artCache.fetch(g)
                        n++
                        if (n % 6 == 0) withContext(Dispatchers.Main) { artVersion++ }
                    }
                }
                withContext(Dispatchers.Main) { artVersion++ }
            }
        } finally {
            scanning = false
            note = ""
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pkg = RetroArch.pickPackage(ctx, store.settings.retroPkg)
            val cs = if (pkg != null) RetroArch.queryCores(ctx) else emptyList()
            withContext(Dispatchers.Main) { retroPkg = pkg; cores = cs; onCores(cs) }
        }
        if (permOk) runScan()
    }

    LaunchedEffect(tab) {
        val first = games.indexOfFirst { it.system == tab.key && !it.hidden }
        index = if (first >= 0) first else 0
    }

    val counts = remember(games) {
        games.filter { !it.hidden }.groupingBy { it.system }.eachCount()
    }
    val visible = remember(tab, games) {
        games.filter { it.system == tab.key && !it.hidden }
    }

    Box(Modifier.fillMaxWidth()) {
        if (screen == 0) {
            ShelfScreen(
                tab = tab,
                counts = counts,
                games = visible,
                index = index.coerceIn(0, (visible.size - 1).coerceAtLeast(0)),
                focusRequester = focusRequester,
                artOf = { g ->
                    if (tried.isEmpty() || g.id in tried) artCache.get(g)?.asImageBitmap() else null
                },
                artVersion = artVersion,
                scanning = scanning,
                scanNote = note,
                onTab = { tab = it },
                onIndex = { index = it },
                onLaunch = { g ->
                    val err = launchGame(g)
                    if (err != null) toast = err
                },
                onFav = { g ->
                    val updated = games.map { if (it.id == g.id) it.copy(fav = !it.fav) else it }
                    games = updated
                    store.saveLibrary(Library(updated, store.library.scannedAt))
                },
                onSettings = {
                    screen = 1
                    scope.launch {
                        cacheBytes = withContext(Dispatchers.IO) { artCache.cacheSize() }
                    }
                },
                onSearch = { screen = 2 },
                sfx = sfx,
            )
        } else if (screen == 1) {
            SettingsScreen(
                settings = settings,
                counts = counts,
                scannedFolders = settings.folders,
                retroPkg = retroPkg,
                cores = cores,
                cacheBytes = cacheBytes,
                onUpdate = { s -> settings = s; store.saveSettings(s) },
                onRescan = { scope.launch { runScan() } },
                onClearCache = {
                    scope.launch {
                        withContext(Dispatchers.IO) { artCache.clear() }
                        cacheBytes = 0L
                        artVersion++
                        toast = "cache boxart dihapus"
                    }
                },
                onResetStats = {
                    val updated = games.map { it.copy(plays = 0, totalMs = 0L, lastPlayedAt = null) }
                    games = updated
                    store.saveLibrary(Library(updated, store.library.scannedAt))
                    toast = "statistik main direset"
                },
                onFolderPick = { s -> pickSys = s },
                onClose = {
                    screen = 0
                    scope.launch {
                        delay(120)
                        runCatching { focusRequester.requestFocus() }
                    }
                },
                sfx = sfx,
            )
        } else {
            ListScreen(
                tab = tab,
                games = games.filter { !it.hidden },
                artOf = { g -> artCache.get(g)?.asImageBitmap() },
                artVersion = artVersion,
                onLaunch = { g ->
                    val err = launchGame(g)
                    if (err != null) toast = err
                },
                onClose = {
                    screen = 0
                    scope.launch {
                        delay(120)
                        runCatching { focusRequester.requestFocus() }
                    }
                },
                onTab = { tab = it },
                sfx = sfx,
            )
        }

        if (!permOk) {
            PermPanel(
                onOpen = {
                    requestStorage()
                },
                onSkip = { permOk = true },
            )
        }

        pickSys?.let { s ->
            FolderPicker(
                sys = s,
                initial = settings.folders[s.key]?.firstOrNull(),
                onPick = { path ->
                    val ns = settings.copy(folders = settings.folders + (s.key to listOf(path)))
                    settings = ns
                    store.saveSettings(ns)
                    pickSys = null
                    toast = "folder ${s.label}: $path"
                    scope.launch { runScan() }
                },
                onClose = { pickSys = null },
                sfx = sfx,
            )
        }
    }

    LaunchedEffect(permOk) {
        if (permOk && games.isEmpty() && !scanning) runScan()
    }

    toast?.let { m ->
        LaunchedEffect(m) {
            delay(2600)
            toast = null
        }
        Box(
            Modifier
                .offset(x = 300.dp, y = 560.dp)
                .size(360.dp, 40.dp)
                .background(T.surface)
                .border(1.5.dp, T.lime),
            contentAlignment = Alignment.Center,
        ) {
            Text(m, color = T.fg, fontFamily = MONO, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }

    // focus buat nerima key D-pad
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun PermPanel(onOpen: () -> Unit, onSkip: () -> Unit) {
    Box(Modifier.fillMaxWidth().offset(y = 240.dp).padding(horizontal = 210.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(T.black, topLeft = Offset(8f, 8f), size = Size(size.width, size.height)) }
                .background(T.surface)
                .border(1.5.dp, T.hairline)
                .padding(24.dp),
        ) {
            Text("AKSES FILE", color = T.violet, fontFamily = MONO, fontSize = 12.sp, letterSpacing = 1.4.sp)
            Text(
                "Kartrid butuh akses folder biar bisa baca ROM dan ngasih path asli ke RetroArch.",
                color = T.fg,
                fontFamily = SANS,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.padding(top = 16.dp)) {
                Box(
                    Modifier
                        .background(T.lime)
                        .clickable { onOpen() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text("BUKA PENGATURAN", color = T.bg, fontFamily = DISPLAY, fontSize = 12.sp)
                }
                Box(Modifier.width(12.dp))
                Box(
                    Modifier
                        .border(1.5.dp, T.hairline)
                        .clickable { onSkip() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text("LEWATI", color = T.mutedFg, fontFamily = MONO, fontSize = 11.5.sp)
                }
            }
        }
    }
}
