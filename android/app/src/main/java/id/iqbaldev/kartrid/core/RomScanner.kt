package id.iqbaldev.kartrid.core

import android.content.Context
import android.os.Environment
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Scan ROM: .gb/.gbc/.gba + arsip .zip/.7z dari folder yang dikonfigurasi.
 * - Incremental: folder yang mtime-nya nggak berubah sejak scan terakhir di-skip
 *   (hasil lama dipakai lagi) — scan tiap launch jadi cepet.
 * - Paralel: WORKERS walker jalan barengan di folder beda.
 * Jalan di Dispatchers.IO dari pemanggil; progress lewat callback.
 */
object RomScanner {

    private const val MAX_DEPTH = 4
    private const val WORKERS = 4

    /** hasil scan + stamp mtime tiap folder (dipakai incremental di run berikutnya) */
    data class ScanOut(val games: List<Game>, val stamps: Map<String, Long>)

    private val HINT = Regex("(?i)rom|game|gb|gbc|gba|retro|emul|kartrid|nintendo|backup")
    private val SKIP = Regex("(?i)^Android$|^\\.|thumbnails|DCIM|Movies|Music|Pictures|WhatsApp|Telegram|MIUI|Download$")

    /** Cari sendiri folder yang isinya ROM, biar user nggak perlu nunjuk manual. */
    fun discover(ctx: Context): Map<String, List<String>> {
        val found = HashMap<String, MutableList<String>>()
        for (vol in volumeRoots(ctx)) {
            val root = File(vol)
            if (!root.isDirectory || !root.canRead()) continue
            val stack = ArrayDeque<Pair<File, Int>>()
            root.listFiles()?.forEach { stack.addLast(it to 1) }
            var visited = 0
            while (stack.isNotEmpty() && visited < 3000) {
                val (d, depth) = stack.removeLast()
                visited++
                if (!d.isDirectory || !d.canRead()) continue
                val children = runCatching { d.listFiles() }.getOrNull() ?: continue
                val exts = children.filter { it.isFile }.mapTo(HashSet()) { it.extension.lowercase() }
                for (s in Sys.entries) {
                    if (exts.any { s.accepts(it) }) found.getOrPut(s.key) { mutableListOf() }.add(d.absolutePath)
                }
                if (depth < 3) {
                    children.forEach {
                        if (it.isDirectory && !SKIP.containsMatchIn(it.name) &&
                            (depth < 2 || HINT.containsMatchIn(it.name) || exts.isNotEmpty())
                        ) stack.addLast(it to depth + 1)
                    }
                }
            }
        }
        return found.mapValues { (_, v) -> v.distinct() }
    }

    /** akar tiap volume storage (internal + SD card) dari daftar dir app */
    private fun volumeRoots(ctx: Context): List<String> {
        val out = LinkedHashSet<String>()
        runCatching {
            ctx.getExternalFilesDirs(null).filterNotNull().forEach { f ->
                val p = f.absolutePath
                val i = p.indexOf("/Android/")
                if (i > 0) out.add(p.substring(0, i))
            }
        }
        out.add(Environment.getExternalStorageDirectory().absolutePath)
        return out.toList()
    }

    /** Kandidat folder yang dicoba otomatis pas pertama kali. */
    fun candidates(sys: Sys): List<String> {
        val roots = listOf(
            Environment.getExternalStorageDirectory().absolutePath,
            "/storage/emulated/0",
            "/sdcard",
            "/storage/emulated/0/Download",
            "/mnt/sdcard",
        ).distinct()
        val sub = listOf(
            "Roms/${sys.label}", "Roms/${sys.ext.uppercase()}", "ROMs/${sys.label}",
            "roms/${sys.label}", "Roms/${sys.longLabel}", "Games/${sys.label}",
            "Kartrid/${sys.label}", "RetroArch/roms/${sys.label}", sys.label,
        )
        val out = ArrayList<String>()
        for (r in roots) for (s in sub) {
            val f = File(r, s)
            if (f.isDirectory) out.add(f.absolutePath)
        }
        return out.distinct()
    }

    suspend fun scan(
        folders: Map<String, List<String>>,
        prior: List<Game> = emptyList(),
        stamps: Map<String, Long> = emptyMap(),
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): ScanOut {
        val results = ConcurrentHashMap<String, List<Game>>()
        val freshStamps = ConcurrentHashMap<String, Long>()
        val sem = Semaphore(WORKERS)
        val found = AtomicInteger()

        coroutineScope {
            for ((sysKey, paths) in folders) {
                val sys = Sys.of(sysKey)
                for (p in paths) {
                    launch(Dispatchers.IO) {
                        sem.withPermit {
                            val key = "$sysKey|$p"
                            val root = File(p)
                            if (!root.isDirectory || !root.canRead()) return@withPermit
                            val stamp = runCatching { root.lastModified() }.getOrDefault(0L)
                            if (stamps[key] == stamp && stamp != 0L) {
                                // folder nggak berubah sejak scan terakhir → pakai hasil lama
                                results[key] = prior.filter { it.system == sys.key && it.path.startsWith(p) }
                                freshStamps[key] = stamp
                                return@withPermit
                            }
                            val files = walkFiles(root) { f ->
                                !f.name.startsWith(".") && sys.accepts(f.extension)
                            }
                            results[key] = files.map { make(sys, it) }
                            freshStamps[key] = stamp
                            found.addAndGet(files.size)
                            onProgress(found.get(), "")
                        }
                    }
                }
            }
        }

        val games = results.values.flatten()
            .sortedWith(compareBy({ it.system }, { it.title.lowercase() }))
        return ScanOut(games, freshStamps)
    }

    /** walk manual sampai MAX_DEPTH; lebih ringan dari FileTreeWalk (nggak bikin iterator per file) */
    private fun walkFiles(root: File, accept: (File) -> Boolean): List<File> {
        val out = ArrayList<File>()
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (d, depth) = stack.removeLast()
            if (depth > MAX_DEPTH) continue
            val children = runCatching { d.listFiles() }.getOrNull() ?: continue
            for (f in children) {
                if (f.isFile) {
                    if (accept(f)) out.add(f)
                } else if (f.isDirectory && !f.name.startsWith(".") && depth < MAX_DEPTH) {
                    stack.addLast(f to depth + 1)
                }
            }
        }
        return out
    }

    private fun make(sys: Sys, f: File): Game {
        val base = f.nameWithoutExtension
        return Game(
            id = sha1("${sys.key}|${f.absolutePath}").take(16),
            system = sys.key,
            path = f.absolutePath,
            title = cleanTitle(base),
            region = regionOf(base),
            sizeBytes = f.length(),
            modifiedAt = f.lastModified(),
        )
    }

    private val TAG_RE = Regex("""\([^)]*\)|\[[^\]]*]""")

    /** nama tampilan: buang tag No-Intro/GoodTools biar kebaca manusia */
    fun cleanTitle(base: String): String {
        val t = base.replace('_', ' ').replace(TAG_RE, " ")
            .replace(Regex("\\s+"), " ").trim()
        return t.ifBlank { base }
    }

    /** teks mentah tanpa tag — dipakai buat matching art (§8) */
    fun baseName(base: String): String {
        val t = base.replace('_', ' ').replace(TAG_RE, " ")
            .replace(Regex("\\s+"), " ").trim()
        return t.ifBlank { base }
    }

    private fun regionOf(base: String): String? =
        Regex("""\(([^)]*)\)""").findAll(base)
            .map { it.groupValues[1] }
            .firstOrNull { r ->
                val up = r.uppercase()
                up.contains("USA") || up.contains("EUROPE") || up.contains("JAPAN") ||
                    up.contains("WORLD") || up.contains("KOREA") || up.contains("CHINA") ||
                    up.contains("ASIA") || up.contains("AUSTRALIA")
            }
            ?.let { r ->
                r.split(',').map { s -> s.trim().replace("Japan", "JPN") }.joinToString(", ")
            }

    private fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}