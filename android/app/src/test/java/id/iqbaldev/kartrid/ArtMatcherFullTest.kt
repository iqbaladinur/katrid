package id.iqbaldev.kartrid

import id.iqbaldev.kartrid.core.ArtMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validator penuh: jalankan matcher KOTLIN (bukan Python) terhadap library asli
 * 2167 game dari device GT78-VN, pakai index CDN lengkap.
 *
 * Sumber data (di-generate 20 Okt 2026):
 *  - library.json : `adb shell run-as id.iqbaldev.kartrid cat files/lib/library.json`
 *  - idx.json     : listing Apache thumbnails.libretro.com per sistem
 *
 * File-nya di luar repo (data device + index CDN). Kalau file nggak ada, test di-skip.
 * Jalankan: `bash ./gradlew :app:testDebugUnitTest --tests "*ArtMatcherFullTest*"`
 *
 * Angka yang harusVideos (diukur 20 Okt 2026):
 *  - FAILED 1036 → hit ~596, coverage total ~1727/2167 (79.7%)
 *  - art yang SUDAH OK (1131) tidak boleh berubah: 0 perubahan
 */
class ArtMatcherFullTest {

    private val dataDir = System.getenv("KARTRID_MATCH_DATA")
        ?: "/home/iqbaladinur/.hermes/cache/scratch/kartrid"

    private fun loadLibrary(): List<Map<String, Any>>? {
        val f = File(dataDir, "library.json")
        if (!f.exists()) return null
        val txt = f.readText()
        val games = ArrayList<Map<String, Any>>()
        // parser minimal: ambil object game satu per satu (field flat, tanpa nested object)
        var i = txt.indexOf("\"games\":[")
        if (i < 0) return null
        i += 8
        val rx = Regex("\"system\":\"([^\"]+)\"")
        val rxPath = Regex("\"path\":\"([^\"]+)\"")
        val rxState = Regex("\"artState\":\"([^\"]+)\"")
        while (i < txt.length) {
            val objEnd = txt.indexOf('}', i)
            if (objEnd < 0) break
            val chunk = txt.substring(i, objEnd)
            val sys = rx.find(chunk)?.groupValues?.get(1)
            val path = rxPath.find(chunk)?.groupValues?.get(1)
            val st = rxState.find(chunk)?.groupValues?.get(1)
            if (sys != null && path != null && st != null) {
                games.add(mapOf("system" to sys, "path" to path, "artState" to st))
            }
            val next = txt.indexOf('{', objEnd)
            if (next < 0) break
            i = next
        }
        return games.ifEmpty { null }
    }

    private fun loadIndex(): Map<String, List<String>>? {
        val f = File(dataDir, "idx.json")
        if (!f.exists()) return null
        val txt = f.readText()
        val out = HashMap<String, List<String>>()
        for (key in listOf("GB", "GBC", "GBA")) {
            val i = txt.indexOf("\"$key\"")
            if (i < 0) continue
            val arrStart = txt.indexOf('[', i)
            if (arrStart < 0) continue
            var depth = 0
            var j = arrStart
            var inStr = false
            var esc = false
            while (j < txt.length) {
                val c = txt[j]
                if (inStr) {
                    when {
                        esc -> esc = false
                        c == '\\' -> esc = true
                        c == '"' -> inStr = false
                    }
                } else {
                    when (c) {
                        '"' -> inStr = true
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                }
                j++
            }
            val body = txt.substring(arrStart, minOf(j + 1, txt.length))
            val names = Regex("\"((?:[^\"\\\\]|\\\\.)*\\.png)\"")
                .findAll(body)
                .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
                .toList()
            out[key] = names
        }
        return out.ifEmpty { null }
    }

    @Test
    fun fullLibrary_coverageAndRegression() {
        val games = loadLibrary()
        val idx = loadIndex()
        if (games == null || idx == null) {
            println("SKIP: data matcher tidak ada di $dataDir (butuh library.json + idx.json)")
            return
        }
        println("games=${games.size} index=${idx.mapValues { it.value.size }}")

        val built = idx.mapValues { ArtMatcher.TierIndex.build(it.value) }
        val baseOf = { g: Map<String, Any> ->
            (g["path"] as String).substringAfterLast('/').substringBeforeLast('.')
        }

        var failedTotal = 0
        var failedHit = 0
        var okTotal = 0
        var okSame = 0
        var okChanged = 0
        val changed = ArrayList<String>()
        val sharedArt = HashMap<String, MutableList<String>>()

        for (g in games) {
            val sys = g["system"] as String
            val st = g["artState"] as String
            val tIdx = built[sys] ?: continue
            val hit = ArtMatcher.match(tIdx, baseOf(g))
            if (st == "FAILED") {
                failedTotal++
                if (hit != null) {
                    failedHit++
                    val key = "$sys|${ArtMatcher.core(hit.substringBefore(" ("))}"
                    sharedArt.getOrPut(key) { ArrayList() }.add(baseOf(g))
                }
            } else {
                okTotal++
                val bn = hit?.let { ArtMatcher.core(it.substringBefore(" (")) }
                val rn = ArtMatcher.core(baseOf(g))
                if (bn == null || bn == rn || bn.replace(", the", "") == rn.replace(", the", "")) {
                    okSame++
                } else {
                    okChanged++
                    if (changed.size < 20) changed.add("${baseOf(g)} -> $hit")
                }
            }
        }

        println("FAILED: $failedHit / $failedTotal kepas (${failedHit * 100 / failedTotal}%)")
        println("OK: $okSame sama, $okChanged berubah (dari $okTotal)")
        changed.forEach { println("   CHANGED: $it") }
        val shared = sharedArt.filterValues { it.size > 1 }
        println("art dipakai bareng: ${shared.size} judul / ${shared.values.sumOf { it.size }} game")
        shared.entries.take(10).forEach { println("   ${it.key} <- ${it.value}") }

        // regresi: art yang sebelumnya OK harus tetap sama
        assertTrue("ada art OK yang berubah: $changed", okChanged == 0)
        // coverage minimal
        assertTrue("coverage turun: $failedHit/$failedTotal", failedHit * 100 / failedTotal >= 55)
        // art dipakai bareng harus kecil (konten salah)
        assertTrue(
            "terlalu banyak art dipakai bareng: ${shared.size}",
            shared.values.sumOf { it.size } <= 40,
        )
    }
}
