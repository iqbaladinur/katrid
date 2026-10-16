package id.iqbaldev.kartrid.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Boxart dari CDN resmi libretro-thumbnails (PLAN §8).
 * Nama file = konvensi No-Intro, jadi dicoba berurutan beberapa varian region.
 * Hasil di-downsample ke <= 256 px lalu disimpan sebagai PNG di filesDir/art.
 */
class ArtCache(ctx: Context) {

    private val dir: File = File(ctx.filesDir, "art").apply { mkdirs() }
    private val mem = LruCache<String, Bitmap>(64)

    fun fileOf(id: String): File = File(dir, "$id.png")

    fun cacheSize(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        mem.evictAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** bitmap dari disk/memori (null kalau belum ada) */
    fun get(g: Game): Bitmap? {
        mem.get(g.id)?.let { return it }
        val f = if (g.artFile != null) File(g.artFile) else fileOf(g.id)
        if (!f.exists() || f.length() == 0L) return null
        val b = decodeScaled(f, 512) ?: return null
        mem.put(g.id, b)
        return b
    }

    /** true kalau berhasil dapat + simpan */
    suspend fun fetch(g: Game, targetPx: Int = 256): Boolean = withContext(Dispatchers.IO) {
        if (get(g) != null) return@withContext true
        for (url in candidates(g)) {
            val bmp = tryDownload(url, targetPx) ?: continue
            val f = fileOf(g.id)
            runCatching {
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            mem.put(g.id, bmp)
            return@withContext true
        }
        false
    }

    private fun folder(sys: Sys): String = when (sys) {
        Sys.GB -> "Nintendo - Game Boy"
        Sys.GBC -> "Nintendo - Game Boy Color"
        Sys.GBA -> "Nintendo - Game Boy Advance"
    }

    private fun candidates(g: Game): List<String> {
        val sys = g.sys
        val rawName = File(g.path).nameWithoutExtension
        val base = RomScanner.baseName(rawName)
        if (base.isBlank()) return emptyList()
        val root = "https://thumbnails.libretro.com/${folder(sys)}/Named_Boxarts/"
        val suffixes = listOf(
            " (USA, Europe)", " (World)", " (USA)", " (Europe)", " (Japan)",
            " (USA, Australia)", " (En,Ja)", "",
        )
        return suffixes.map { root + enc(base + it) + ".png" }
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%2C", ",")

    private fun tryDownload(url: String, targetPx: Int): Bitmap? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Kartrid/0.1 (Android)")
        }
        try {
            if (conn.responseCode != 200) return@runCatching null
            val bytes = conn.inputStream.readBytes()
            if (bytes.size > 6_000_000) return@runCatching null

            val b0 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b0)
            var sample = 1
            while (b0.outWidth / (sample * 2) >= targetPx) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@runCatching null
            if (bmp.width > targetPx * 2) {
                val scale = targetPx.toFloat() / bmp.width
                val small = Bitmap.createScaledBitmap(
                    bmp,
                    targetPx,
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
                if (small != bmp) bmp.recycle()
                small
            } else bmp
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun decodeScaled(f: File, targetPx: Int): Bitmap? = runCatching {
        val b0 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, b0)
        var sample = 1
        while (b0.outWidth / (sample * 2) >= targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(f.absolutePath, opts)
    }.getOrNull()
}
