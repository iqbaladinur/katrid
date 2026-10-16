package id.iqbaldev.kartrid.core

import android.os.Environment
import java.io.File

/**
 * Baca daftar core dari playlist RetroArch (.lpl di /sdcard/RetroArch/playlists).
 * Ini fallback yang jauh lebih andal daripada broadcast QUERY_INSTALLED_CORES
 * (di beberapa build RetroArch receiver-nya nggak ke-declare / nggak exported).
 * Format .lpl: pasangan `path = "..."`, `core = ".../cores/<name>.so"`.
 */
object PlaylistReader {

    private val CORE_RE = Regex("core\\s*=\\s*\"([^\"]+\\.so)\"", RegexOption.IGNORE_CASE)

    fun playlistsDir(): List<String> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        return listOf(
            "$root/RetroArch/playlists",
            "/storage/emulated/0/RetroArch/playlists",
            "$root/Android/data/com.retroarch/files/playlists",
            "$root/Android/data/com.retroarch.aarch64/files/playlists",
        ).distinct()
    }

    /** nama file core yang dipakai di playlist (mis. mgba_libretro_android.so) */
    fun cores(): List<String> {
        val out = LinkedHashSet<String>()
        for (r in playlistsDir()) {
            val d = File(r)
            if (!d.isDirectory || !d.canRead()) continue
            d.listFiles { f -> f.isFile && f.name.endsWith(".lpl", ignoreCase = true) }?.forEach { f ->
                runCatching {
                    f.readLines().forEach { line ->
                        CORE_RE.find(line)?.groupValues?.get(1)?.let { out.add(it.substringAfterLast('/')) }
                    }
                }
            }
        }
        return out.toList()
    }

    /** path ROM yang dikutip di playlist (buat fallback kalau discover mentok) */
    fun romPaths(): List<String> {
        val out = LinkedHashSet<String>()
        for (r in playlistsDir()) {
            val d = File(r)
            if (!d.isDirectory || !d.canRead()) continue
            d.listFiles { f -> f.isFile && f.name.endsWith(".lpl", ignoreCase = true) }?.forEach { f ->
                runCatching {
                    f.readLines().forEach { line ->
                        val m = Regex("path\\s*=\\s*\"([^\"]+)\"").find(line)
                        m?.groupValues?.get(1)?.let { out.add(it) }
                    }
                }
            }
        }
        return out.toList()
    }
}