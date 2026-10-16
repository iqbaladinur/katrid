package id.iqbaldev.kartrid.core

import kotlinx.serialization.Serializable

/**
 * Tiga sistem yang di-scope. Nama core = nama file .so di
 * /data/data/<pkg-retroarch>/cores/ (lihat PLAN §7).
 */
enum class Sys(
    val key: String,
    val label: String,
    val ext: String,
    val shell: Long,
    val cores: List<String>,
    val rounded: Boolean = false,
) {
    GB(
        key = "GB", label = "GB", ext = "gb", shell = 0xFFBFBCCB,
        cores = listOf(
            "gambatte_libretro_android.so",
            "sameboy_libretro_android.so",
            "gearboy_libretro_android.so",
        ),
    ),
    GBC(
        key = "GBC", label = "GBC", ext = "gbc", shell = 0xFF6E9FC7,
        cores = listOf(
            "gambatte_libretro_android.so",
            "sameboy_libretro_android.so",
            "gearboy_libretro_android.so",
        ),
        rounded = true,
    ),
    GBA(
        key = "GBA", label = "GBA", ext = "gba", shell = 0xFF5A4C93,
        cores = listOf(
            "mgba_libretro_android.so",
            "gpsp_libretro_android.so",
            "vbam_libretro_android.so",
        ),
    );

    /** label sistem yang dicetak di band kartrid */
    val longLabel: String
        get() = when (this) {
            GB -> "GAME BOY"
            GBC -> "GAME BOY COLOR"
            GBA -> "GAME BOY ADVANCE"
        }

    companion object {
        val order = listOf(GB, GBC, GBA)
        fun of(k: String?): Sys = entries.firstOrNull { it.key == k } ?: GBA
    }
}

const val ART_NEVER = "NEVER"
const val ART_OK = "OK"
const val ART_FAILED = "FAILED"

@Serializable
data class Game(
    val id: String,
    val system: String,
    val path: String,
    val title: String,
    val region: String? = null,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val fav: Boolean = false,
    val hidden: Boolean = false,
    val artFile: String? = null,
    val artState: String = ART_NEVER,
    val plays: Int = 0,
    val totalMs: Long = 0L,
    val lastPlayedAt: Long? = null,
) {
    val sys: Sys get() = Sys.of(system)
}

@Serializable
data class Library(
    val games: List<Game> = emptyList(),
    val scannedAt: Long = 0L,
)

@Serializable
data class Settings(
    val folders: Map<String, List<String>> = emptyMap(),
    val cores: Map<String, String> = emptyMap(),
    val retroPkg: String? = null,
    val cfgPath: String? = null,
    val quitFocus: Boolean = false,
    val force60: Boolean = true,
    val autoScan: Boolean = true,
    val autoArt: Boolean = true,
    val artOverride: Map<String, String> = emptyMap(),
    val onboarded: Boolean = false,
    val lastIndex: Map<String, Int> = emptyMap(),
    val animEnabled: Boolean = true,
)
