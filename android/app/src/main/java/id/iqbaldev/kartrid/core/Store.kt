package id.iqbaldev.kartrid.core

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Persistensi sederhana: 2 file JSON di filesDir. Nggak butuh DB buat 400-800 game. */
class Store(ctx: Context) {

    private val dir: File = File(ctx.filesDir, "lib").apply { mkdirs() }
    private val settingsFile = File(dir, "settings.json")
    private val libraryFile = File(dir, "library.json")

    @Volatile
    var settings: Settings = read(settingsFile) { Settings() }
        private set

    @Volatile
    var library: Library = read(libraryFile) { Library() }
        private set

    private inline fun <reified T> read(f: File, fallback: () -> T): T = runCatching {
        if (f.exists()) json.decodeFromString<T>(f.readText()) else fallback()
    }.getOrElse { fallback() }

    fun saveSettings(s: Settings = settings) {
        settings = s
        runCatching { settingsFile.writeText(json.encodeToString(s)) }
    }

    fun saveLibrary(l: Library) {
        library = l
        runCatching { libraryFile.writeText(json.encodeToString(l)) }
    }

    /** gabung hasil scan dengan state lama (fav/hidden/statistik/art) */
    fun mergeScan(found: List<Game>, s: Settings): List<Game> {
        val old = library.games.associateBy { it.id }
        return found.map { g ->
            val o = old[g.id]
            if (o == null) g else g.copy(
                fav = o.fav,
                hidden = o.hidden || s.artOverride["!hide:${g.id}"] == "1",
                artFile = o.artFile,
                artState = o.artState,
                plays = o.plays,
                totalMs = o.totalMs,
                lastPlayedAt = o.lastPlayedAt,
            )
        }
    }
}
