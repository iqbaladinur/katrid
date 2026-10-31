package id.iqbaldev.kartrid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.provider.Settings as ASettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberUpdatedState
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
import id.iqbaldev.kartrid.core.ART_NEVER
import id.iqbaldev.kartrid.core.ART_OK
import id.iqbaldev.kartrid.core.ArtCache
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Library
import id.iqbaldev.kartrid.core.PlaylistReader
import id.iqbaldev.kartrid.core.RetroArch
import id.iqbaldev.kartrid.core.RomScanner
import id.iqbaldev.kartrid.core.Store
import id.iqbaldev.kartrid.core.Sys
import id.iqbaldev.kartrid.core.Tab
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
import id.iqbaldev.kartrid.ui.tdp
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var artCache: ArtCache
    private lateinit var sfx: Sfx
    private var justLaunched = false
/** game currently being played + its start time (for plays/totalMs stats) */
private var playingId: String? = null
private var playStartMs = 0L
    private var retroWatcher: Job? = null
    private var detectedCores = listOf<String>()

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        android.util.Log.d("Kartrid", "KEYDOWN code=$keyCode name=${KeyEvent.keyCodeToString(keyCode)}")
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        if (justLaunched) {
            justLaunched = false
            retroWatcher?.cancel()
            retroWatcher = null
            sfx.eject()
            // back from RetroArch → record the session (plays +1, totalMs, lastPlayedAt)
            recordPlay()
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
            playingId = g.id
            playStartMs = System.currentTimeMillis()
            startActivity(i)
            justLaunched = true
            watchRetroArchExit(pkg)
            null
        }.getOrElse { "Gagal buka RetroArch: ${it.message}" }
    }

    /** nunggu proses RetroArch mati → auto balik ke rak (console-launcher feel) */
    private fun watchRetroArchExit(pkg: String) {
        retroWatcher?.cancel()
        retroWatcher = lifecycleScope.launch {
            // TUNGGU proses RA beneran spawn (max 10s) — kalau langsung poll, RA belum
            // muncul dan watcher ngira RA mati → narik app ke depan = launch kebatalan.
            var spawned = false
            repeat(10) {
                if (isProcessAlive(pkg)) { spawned = true; return@repeat }
                delay(1000)
            }
            if (!spawned) return@launch
            while (isProcessAlive(pkg)) {
                delay(1000)
            }
            recordPlay()
            justLaunched = false
            sfx.eject()
            runCatching {
                startActivity(
                    Intent(this@MainActivity, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    ),
                )
            }
        }
    }

    /** bump plays, totalMs and lastPlayedAt for the game that just ended */
    private fun recordPlay() {
        val id = playingId ?: return
        playingId = null
        val now = System.currentTimeMillis()
        val dur = (now - playStartMs).coerceAtLeast(0L)
        val updated = store.library.games.map {
            if (it.id == id) it.copy(plays = it.plays + 1, totalMs = it.totalMs + dur, lastPlayedAt = now) else it
        }
        store.saveLibrary(Library(updated, store.library.scannedAt, store.library.folderStamp))
    }

    private fun isProcessAlive(pkg: String): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.any { it.processName == pkg } == true
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var settings by remember { mutableStateOf(store.settings) }
    var games by remember { mutableStateOf(store.library.games) }
    var tab by remember {
        mutableStateOf(Tab.list.firstOrNull { it.key == store.settings.lastTab } ?: Tab.GBA)
    }
    var index by remember { mutableStateOf(store.settings.lastIndex[tab.key] ?: 0) }
    var scanning by remember { mutableStateOf(false) }
    var artRunning by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var artVersion by remember { mutableStateOf(0) }
    var permOk by remember { mutableStateOf(hasStorage()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var cores by remember { mutableStateOf<List<String>>(emptyList()) }
    var retroPkg by remember { mutableStateOf<String?>(null) }
    var installedPkgs by remember { mutableStateOf<List<String>>(emptyList()) }
    var screen by remember { mutableStateOf(0) }
    var pickSys by remember { mutableStateOf<Sys?>(null) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var customArtTarget by remember { mutableStateOf<Game?>(null) }

    /** tulis artState terbaru ke state + disk (dari Main thread) */
    fun flushArt(r: MutableMap<String, String>) {
        if (r.isEmpty()) return
        val upd = games.map { r[it.id]?.let { s -> it.copy(artState = s) } ?: it }
        games = upd
        store.saveLibrary(Library(upd, store.library.scannedAt, store.library.folderStamp))
        r.clear()
    }

    /** ambil boxart yang belum ada (paralel 8); gagal → ART_FAILED biar nggak retry tiap launch */
    suspend fun refreshArt() {
        if (artRunning) return
        artRunning = true
        try {
            val pending = games.filter { it.artState != ART_OK && it.artState != ART_FAILED }
            val total = pending.size
            if (total == 0) return
            // fetch index boxart CDN sekali per sistem → fetch(g) cuma download yang beneran ada
            note = "indexing art…"
            for (s in pending.map { it.sys }.distinct()) artCache.ensureIndex(s)
            val results = ConcurrentHashMap<String, String>()
            val done = AtomicInteger()
            val sem = Semaphore(8)
            note = "art 0/$total"
            coroutineScope {
                for (g in pending) {
                    launch(Dispatchers.IO) {
                        sem.withPermit {
                            // hard timeout per game: fetch yang nge-hang (DNS/socket MTK) nggak
                            // boleh ngunci worker selamanya → dianggap FAILED, lanjut ke game lain
                            val ok = runCatching { withTimeout(45_000) { artCache.fetch(g) } }
                                .getOrDefault(false)
                            results[g.id] = if (ok) ART_OK else ART_FAILED
                            val d = done.incrementAndGet()
                            if (d % 25 == 0) withContext(Dispatchers.Main) {
                                flushArt(results)
                                artVersion++
                                note = "art $d/$total"
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                flushArt(results)
                artVersion++
                note = ""
            }
        } finally {
            artRunning = false
        }
    }

    /** import gambar pilihan user → filesDir/custom, set artFile game itu */
    fun importCustomArt(g: Game, uri: Uri) {
        val mime = ctx.contentResolver.getType(uri)
        val ext = when (mime) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val dir = File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "kartrid_custom").apply { mkdirs() }
        val f = File(dir, "${g.id}.$ext")
        val ok = runCatching {
            val n = ctx.contentResolver.openInputStream(uri)?.use { ins ->
                f.outputStream().use { outs -> ins.copyTo(outs) }
            }
            (n ?: 0L) > 0
        }.getOrDefault(false)
        if (!ok) {
            toast = "gagal import image"
            return
        }
        artCache.invalidate(g.id)
        val updated = games.map { if (it.id == g.id) it.copy(artFile = f.absolutePath, artState = ART_OK) else it }
        games = updated
        store.saveLibrary(Library(updated, store.library.scannedAt, store.library.folderStamp))
        artVersion++
        toast = "custom boxart di-set"
    }

    /** hapus custom art → artState NEVER biar pipeline auto-download nyoba art asli lagi */
    fun clearCustomArt(g: Game) {
        artCache.invalidate(g.id)
        val updated = games.map { if (it.id == g.id) it.copy(artFile = null, artState = ART_NEVER) else it }
        games = updated
        store.saveLibrary(Library(updated, store.library.scannedAt, store.library.folderStamp))
        artVersion++
        toast = "custom boxart dihapus"
        scope.launch { refreshArt() }
    }

    // system image picker (SAF) — pilih gambar custom boxart
    val artPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val g = customArtTarget
        customArtTarget = null
        if (uri != null && g != null) importCustomArt(g, uri)
    }

    /** paksa re-scan untuk sys tertentu (clear stamp), atau full re-scan kalau sys=null */
    suspend fun runScan(clearStampsFor: Set<String>? = null) {
        if (scanning) return
        scanning = true
        try {
            note = "looking for folders…"
            val folders = settings.folders.ifEmpty {
                withContext(Dispatchers.IO) { RomScanner.discover(ctx) }
            }
            note = "scanning…"
            // clear stamp untuk folder yang explicitly di-set user → wajib re-scan
            // stamp key format: "SYS|/path"; filter out semua key yang startsWith salah satu sys
            val clearedStamps = if (clearStampsFor != null) {
                store.library.folderStamp.filterKeys { key -> clearStampsFor.none { key.startsWith("$it|") } }
            } else {
                store.library.folderStamp
            }
            val out = withContext(Dispatchers.IO) {
                RomScanner.scan(folders, store.library.games, clearedStamps) { n, _ -> note = "scanning… $n" }
            }
            val merged = store.mergeScan(out.games, settings)
            store.saveLibrary(Library(merged, System.currentTimeMillis(), out.stamps))
            if (settings.folders.isEmpty() && folders.isNotEmpty()) {
                settings = settings.copy(folders = folders)
                store.saveSettings(settings)
            }
            games = merged
            note = ""
        } finally {
            scanning = false
            note = ""
        }
        // unduh boxart DI LUAR flag scanning → rak langsung kebuka, art jalan di belakang
        if (settings.autoArt) refreshArt()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pkg = RetroArch.pickPackage(ctx, store.settings.retroPkg)
            val installed = RetroArch.installed(ctx)
            val cs = if (pkg != null) {
                RetroArch.queryCores(ctx).ifEmpty { PlaylistReader.cores() }
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) { retroPkg = pkg; installedPkgs = installed; cores = cs; onCores(cs) }
        }
        if (permOk && settings.autoScan && settings.folders.isNotEmpty()) runScan()
    }

    // jumlah game visible di tab tertentu (FAV = campuran semua sistem)
    val countFor: (Tab) -> Int = { t ->
        val s = t.sys
        if (s == null) games.count { it.fav && !it.hidden }
        else games.count { it.system == s.key && !it.hidden }
    }

    // posisi selected per tab: simpan & balikin biar gak reset ke 0 tiap pindah tab
    fun switchTab(new: Tab) {
        if (new == tab) return
        settings = settings.copy(lastIndex = settings.lastIndex + (tab.key to index))
        store.saveSettings(settings)
        tab = new
        index = (settings.lastIndex[new.key] ?: 0).coerceIn(0, (countFor(new) - 1).coerceAtLeast(0))
    }

    // clamp index kalau daftar berubah (unfavorite dst.) — bukan reset
    LaunchedEffect(tab, games) {
        index = index.coerceIn(0, (countFor(tab) - 1).coerceAtLeast(0))
    }

    // persist position (tab + index) on pause/stop so it does not reset when reopened
    val curTab by rememberUpdatedState(tab)
    val curIndex by rememberUpdatedState(index)
    val curSettings by rememberUpdatedState(settings)
    DisposableEffect(lifecycleOwner) {
        val saveObs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE || e == Lifecycle.Event.ON_STOP) {
                val s = curSettings.copy(
                    lastIndex = curSettings.lastIndex + (curTab.key to curIndex),
                    lastTab = curTab.key,
                )
                settings = s
                store.saveSettings(s)
            }
        }
        lifecycleOwner.lifecycle.addObserver(saveObs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(saveObs) }
    }

    val counts = remember(games) { Tab.list.map { it.key to countFor(it) }.toMap() }
    val visible = remember(tab, games) {
        val s = tab.sys
        if (s == null) games.filter { it.fav && !it.hidden }
        else games.filter { it.system == s.key && !it.hidden }
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
                    // custom art dari user: load langsung dari path
                    g.artFile?.let { path ->
                        runCatching { android.graphics.BitmapFactory.decodeFile(path) }
                            .getOrNull()?.asImageBitmap()
                    } ?: artCache.get(g)?.asImageBitmap()
                },
                artVersion = artVersion,
                scanning = scanning,
                artRunning = artRunning,
                peekArt = { g -> artCache.peek(g)?.asImageBitmap() },
                scanNote = note,
                onTab = { switchTab(it) },
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
                onSetArt = { g ->
                    customArtTarget = g
                    artPicker.launch("image/*")
                },
                onClearArt = { clearCustomArt(it) },
                sfx = sfx,
                skipStep = settings.shelfSkip,
            )
        } else if (screen == 1) {
            SettingsScreen(
                settings = settings,
                counts = counts,
                scannedFolders = settings.folders,
                retroPkg = retroPkg,
                installed = installedPkgs,
                cores = cores,
                cacheBytes = cacheBytes,
                onUpdate = { s -> settings = s; store.saveSettings(s) },
                onRescan = { scope.launch { runScan(clearStampsFor = settings.folders.keys) } },
                onClearCache = {
                    scope.launch {
                        withContext(Dispatchers.IO) { artCache.clear() }
                        cacheBytes = 0L
                        // reset failed art → di-retry
                        val reset = games.map { if (it.artState == ART_FAILED) it.copy(artState = ART_NEVER) else it }
                        games = reset
                        store.saveLibrary(Library(reset, store.library.scannedAt, store.library.folderStamp))
                        artVersion++
                        toast = "cache boxart dihapus — retrying missing art…"
                        refreshArt()
                    }
                },
                onResetStats = {
                    val updated = games.map { it.copy(plays = 0, totalMs = 0L, lastPlayedAt = null) }
                    games = updated
                    store.saveLibrary(Library(updated, store.library.scannedAt))
                    toast = "statistik main direset"
                },
                onFolderPick = { s -> pickSys = s },
                games = games,
                onSetArt = { g ->
                    customArtTarget = g
                    artPicker.launch("image/*")
                },
                onClearArt = { clearCustomArt(it) },
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
                games = if (tab.sys == null) games.filter { it.fav && !it.hidden }
                else games.filter { !it.hidden },
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
                onTab = { switchTab(it) },
                onFav = { g ->
                    val updated = games.map { if (it.id == g.id) it.copy(fav = !it.fav) else it }
                    games = updated
                    store.saveLibrary(Library(updated, store.library.scannedAt, store.library.folderStamp))
                },
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
                    toast = "${s.label} folder: $path"
                    scope.launch { runScan(clearStampsFor = setOf(s.key)) }
                },
                onClose = { pickSys = null },
                sfx = sfx,
            )
        }
    }

    // re-check izin tiap kali app balik ke foreground (absen pas keluar dari Settings)
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(resumeTick) {
        games = store.library.games
        permOk = hasStorage()
        if (permOk && games.isEmpty() && !scanning && settings.autoScan && settings.folders.isNotEmpty()) runScan()
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
internal fun PermPanel(onOpen: () -> Unit, onSkip: () -> Unit) {
    Box(Modifier.fillMaxWidth().offset(y = 240.dp).padding(horizontal = 210.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(T.black, topLeft = Offset(8f, 8f), size = Size(size.width, size.height)) }
                .background(T.surface)
                .border(1.5.dp, T.hairline)
                .padding(24.dp),
        ) {
            Text("FILE ACCESS", color = T.violet, fontFamily = MONO, fontSize = 12.sp, letterSpacing = 1.4.sp)
            Text(
                text = "Kartrid needs folder access to read ROMs and hand real paths to RetroArch.",
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
                        .padding(horizontal = 22f.tdp, vertical = 12f.tdp),
                ) {
                    Text("OPEN SETTINGS", color = T.bg, fontFamily = DISPLAY, fontSize = 13.sp)
                }
                Box(Modifier.width(12.dp))
                Box(
                    Modifier
                        .border(1.5.dp, T.hairline)
                        .clickable { onSkip() }
                        .padding(horizontal = 18f.tdp, vertical = 12f.tdp),
                ) {
                    Text("SKIP", color = T.mutedFg, fontFamily = MONO, fontSize = 12.sp)
                }
            }
        }
    }
}
