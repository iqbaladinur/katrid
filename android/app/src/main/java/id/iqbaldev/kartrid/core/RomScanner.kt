package id.iqbaldev.kartrid.core

import android.os.Environment
import java.io.File
import java.security.MessageDigest

/**
 * Scan ROM: .gb/.gbc/.gba dari folder yang dikonfigurasi.
 * Jalan di Dispatchers.IO dari pemanggil; progress lewat callback.
 */
object RomScanner {

    private const val MAX_DEPTH = 4

    private val HINT = Regex("(?i)rom|game|gb|gbc|gba|retro|emul|kartrid|nintendo|backup")
    private val SKIP = Regex("(?i)^Android$|^\\.|thumbnails|DCIM|Movies|Music|Pictures|WhatsApp|Telegram|MIUI|Download$")

    /** Cari sendiri folder yang isinya ROM, biar user nggak perlu nunjuk manual. */
    fun discover(ctx: android.content.Context): Map<String, List<String>> {
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
                    if (exts.contains(s.ext)) found.getOrPut(s.key) { mutableListOf() }.add(d.absolutePath)
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
    private fun volumeRoots(ctx: android.content.Context): List<String> {
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
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): List<Game> {
        val out = ArrayList<Game>()
        var found = 0
        for ((sysKey, paths) in folders) {
            val sys = Sys.of(sysKey)
            for (p in paths) {
                val root = File(p)
                if (!root.isDirectory || !root.canRead()) continue
                root.walkTopDown()
                    .maxDepth(MAX_DEPTH)
                    .onFail { _, _ -> }
                    .forEach { f ->
                        // skip junk: dotfile mac (._*), metadata (.*), file nyangkut
                        if (f.isFile && !f.name.startsWith(".") && f.extension.equals(sys.ext, ignoreCase = true)) {
                            found++
                            out.add(make(sys, f))
                            if (found % 12 == 0) onProgress(found, f.absolutePath)
                        }
                    }
            }
        }
        onProgress(found, "")
        return out.sortedWith(compareBy({ it.system }, { it.title.lowercase() }))
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
