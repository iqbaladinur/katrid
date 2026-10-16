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
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Boxart dari CDN resmi libretro-thumbnails (PLAN §8).
 *
 * Strategi cepat: fetch INDEX folder (listing HTML) sekali per sistem, cocokin nama
 * game lokal ke daftar itu, baru download file yang beneran ada. Tanpa ini, tiap game
 * nyoba 8 URL sekuensial (banyak timeout) → 2167 game = berjam-jam. Dengan index:
 * ~3 request daftar + download yang cocok doang.
 *
 * Hasil di-downsample ke <= 256 px lalu disimpan sebagai PNG di filesDir/art.
 */
class ArtCache(ctx: Context) {

    private val dir: File = File(ctx.filesDir, "art").apply { mkdirs() }
    private val mem = LruCache<String, Bitmap>(64)
    private val indexMem = ConcurrentHashMap<String, List<String>>()

    fun fileOf(id: String): File = File(dir, "$id.png")

    fun cacheSize(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        mem.evictAll()
        indexMem.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** buang bitmap dari memori kalau art diganti/dihapus manual */
    fun invalidate(id: String) {
        mem.remove(id)
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

    /** pastikan index nama boxart sistem ini ada (dari CDN / cache file, TTL 3 hari) */
    suspend fun ensureIndex(sys: Sys) = withContext(Dispatchers.IO) {
        if (indexMem.containsKey(sys.key)) return@withContext
        val file = File(dir, "idx-${sys.key}.txt")
        var names: List<String>? =
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 3L * 24 * 3600_000L) {
                file.readLines().filter { it.isNotBlank() }.ifEmpty { null }
            } else null
        if (names == null) {
            // retry sekali; null = gagal total → fallback suffix di candidates()
            names = fetchIndex(sys) ?: fetchIndex(sys)
            if (!names.isNullOrEmpty()) runCatching { file.writeText(names.joinToString("\n")) }
        }
        if (names != null) indexMem[sys.key] = names
    }

    private fun indexUrl(sys: Sys): String =
        "https://thumbnails.libretro.com/${folder(sys)}/Named_Boxarts/"

    /** ambil daftar nama file .png dari listing direktori CDN; null kalau gagal */
    private fun fetchIndex(sys: Sys): List<String>? = runCatching {
        val conn = (URL(indexUrl(sys)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Kartrid/0.1 (Android)")
        }
        try {
            if (conn.responseCode != 200) return@runCatching null
            val html = conn.inputStream.bufferedReader().readText()
            Regex("""href="([^"]+\.png)"""").findAll(html)
                .map { URLDecoder.decode(it.groupValues[1], "UTF-8") }
                .toList()
        } finally {
            conn.disconnect()
        }
    }.getOrElse { null }

    private fun folder(sys: Sys): String = when (sys) {
        Sys.GB -> "Nintendo - Game Boy"
        Sys.GBC -> "Nintendo - Game Boy Color"
        Sys.GBA -> "Nintendo - Game Boy Advance"
    }

    /** bagian judul (sebelum tag region pertama) — dipakai pencocokan index */
    private fun titlePart(name: String): String =
        name.substringBefore(" (").trim().lowercase()

    /** ranking region: USA/World paling sering dipakai libretro */
    private fun regionRank(name: String): Int {
        val inner = name.substringAfter("(", "").substringBefore(")").trim().lowercase()
        val i = REGION_PREF.indexOf(inner)
        return if (i < 0) REGION_PREF.size else i
    }

    private fun candidates(g: Game): List<String> {
        val sys = g.sys
        val rawName = File(g.path).nameWithoutExtension
        val base = RomScanner.baseName(rawName)
        if (base.isBlank()) return emptyList()
        val root = indexUrl(sys)
        val idx = indexMem[sys.key]

        if (idx != null && idx.isNotEmpty()) {
            // cocokin ke index CDN → 0-1 URL beneran ada (gak nembak 8 URL buta)
            val key = base.lowercase()
            val best = idx.filter { titlePart(it) == key }
                .minByOrNull { regionRank(it) }
                ?: return emptyList()
            return listOf(root + enc(best))
        }

        // fallback (index gagal ke-fetch): coba varian region umum
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
            connectTimeout = 8_000
            readTimeout = 20_000
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

    companion object {
        private val REGION_PREF = listOf(
            "(usa, europe)", "(world)", "(usa)", "(europe)", "(japan)", "(usa, australia)", "(en,ja)", "",
        )
    }
}