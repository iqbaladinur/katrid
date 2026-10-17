package id.iqbaldev.kartrid

import android.content.Context
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Settings
import id.iqbaldev.kartrid.core.Sys
import id.iqbaldev.kartrid.core.Tab
import id.iqbaldev.kartrid.ui.Cartridge
import id.iqbaldev.kartrid.ui.FolderPicker
import id.iqbaldev.kartrid.ui.ListScreen
import id.iqbaldev.kartrid.ui.MockCanvas
import id.iqbaldev.kartrid.ui.SettingsScreen
import id.iqbaldev.kartrid.ui.Sfx
import id.iqbaldev.kartrid.ui.ShelfScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Preview render UI asli (bukan mockup). Jalan: `bash ./gradlew :app:testDebugUnitTest`
 * Output: PNG di android/app/build/previews/ (atau $KARTRID_PREVIEW_DIR) — review desain tanpa install.
 * Qualifier mdpi = density 240 seperti KPA → 1dp=1px, pas sama kanvas desain.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h640dp-land-mdpi")
class PreviewTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** boxart TIDAK ikut repo (hak cipta); taruh PNG 1:1 di design/art kalau mau preview penuh */
    private val artDir: String =
        System.getenv("KARTRID_ART_DIR") ?: File("../../design/art").absolutePath

    private val outDir: File =
        File(System.getenv("KARTRID_PREVIEW_DIR") ?: "build/previews").also { it.mkdirs() }

    private fun out(name: String) = File(outDir, name).absolutePath

    private fun art(name: String): ImageBitmap? =
        runCatching { BitmapFactory.decodeFile("$artDir/$name")?.asImageBitmap() }.getOrNull()

    private fun games(): List<Game> {
        val base = Game(id = "", system = Sys.GBA.key, path = "", title = "", sizeBytes = 12_582_912L)
        fun g(s: Sys, title: String, region: String? = "USA"): Game = base.copy(
            id = title, system = s.key, path = "/storage/emulated/0/Roms/${s.label}/$title.${s.ext}",
            title = title, region = region, sizeBytes = 12_582_912L, plays = 17,
        )
        return listOf(
            g(Sys.GBA, "Pokémon Emerald Version", "USA, Europe"),
            g(Sys.GBA, "Dragon Quest Monsters - Caravan Heart", "Japan"),
            g(Sys.GBA, "Castlevania - Aria of Sorrow", "USA"),
            g(Sys.GBA, "Metroid Fusion", "USA"),
            g(Sys.GBA, "Golden Sun - The Lost Age", "USA, Europe"),
            g(Sys.GBC, "Wario Land 3", "World"),
            g(Sys.GB, "Super Mario Land", "World"),
        )
    }

    @Test
    fun shelfPreview() {
        captureRoboImage(out("01-shelf.png")) {
            MockCanvas {
                ShelfScreen(
                    tab = Tab.GBA,
                    counts = mapOf("FAV" to 1, "GB" to 1, "GBC" to 1, "GBA" to 5),
                    games = games(),
                    index = 0,
                    focusRequester = androidx.compose.ui.focus.FocusRequester(),
                    artOf = { g ->
                        when (g.title) {
                            "Pokémon Emerald Version" -> art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png")
                            "Dragon Quest Monsters - Caravan Heart" -> art("gba_Golden_Sun__USA__Europe_.png")
                            else -> null
                        }
                    },
                    artVersion = 0,
                    scanning = false,
                    artRunning = false,
                    scanNote = "",
                    onTab = {}, onIndex = {}, onLaunch = {}, onFav = {}, onSettings = {}, onSearch = {},
                    onSetArt = {}, onClearArt = {},
                    sfx = Sfx(ctx),
                    preloadArt = { g ->
                        when (g.title) {
                            "Pokémon Emerald Version" -> art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png")
                            "Dragon Quest Monsters - Caravan Heart" -> art("gba_Golden_Sun__USA__Europe_.png")
                            "Castlevania - Aria of Sorrow" -> art("gba_Castlevania_-_Aria_of_Sorrow__USA_.png")
                            "Metroid Fusion" -> art("gba_Metroid_Fusion__USA_.png")
                            "Wario Land 3" -> art("gbc_Wario_Land_3__World___En_Ja_.png")
                            "Super Mario Land" -> art("gb_Super_Mario_Land__World_.png")
                            else -> null
                        }
                    },
                )
            }
        }
    }

    /** kartrid 3D: sudut 0° (frame tengah) — bukti label kepasang pas di recess */
    @Test
    fun cartAngle0Preview() {
        captureRoboImage(out("14-cart-0deg.png")) {
            MockCanvas {
                Box(Modifier.fillMaxSize().background(Color(0xFF16121F)), contentAlignment = Alignment.Center) {
                    Cartridge(
                        sys = Sys.GBA,
                        art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                        title = "Pokémon Emerald Version",
                        meta = "USA · 12.6 MB · main 17×",
                        w = 305f,
                        focused = true,
                        spin = 0f,
                    )
                }
            }
        }
    }

    /** kartrid 3D: diputar −14° + tilt 5° — bukti warp perspektif (setPolyToPoly) */
    @Test
    fun cartAngleSpunPreview() {
        captureRoboImage(out("15-cart-spun.png")) {
            MockCanvas {
                Box(Modifier.fillMaxSize().background(Color(0xFF16121F)), contentAlignment = Alignment.Center) {
                    Cartridge(
                        sys = Sys.GBA,
                        art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                        title = "Pokémon Emerald Version",
                        meta = "USA · 12.6 MB · main 17×",
                        w = 305f,
                        focused = true,
                        spin = -14f,
                        tilt = 5f,
                    )
                }
            }
        }
    }

    /** tiga sistem berdampingan: panel label beda warna */
    @Test
    fun cartSystemsPreview() {
        captureRoboImage(out("16-cart-systems.png")) {
            MockCanvas {
                Box(Modifier.fillMaxSize().background(Color(0xFF16121F))) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cartridge(Sys.GB, art("gb_Super_Mario_Land__World_.png"), "Super Mario Land",
                            "World · 64 KB · main 31×", 200f, true)
                        Cartridge(Sys.GBC, art("gbc_Pokemon_-_Crystal_Version__USA_.png"), "Pokémon Crystal Version",
                            "USA · 2.0 MB · main 24×", 200f, true)
                        Cartridge(Sys.GBA, art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"), "Pokémon Emerald Version",
                            "USA · 12.6 MB · main 17×", 200f, true)
                    }
                }
            }
        }
    }

    /** spinner loading di tengah area label */
    @Test
    fun cartLoadingPreview() {
        captureRoboImage(out("13-cart-loading.png")) {
            MockCanvas {
                Box(Modifier.fillMaxSize().background(Color(0xFF16121F)), contentAlignment = Alignment.Center) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalInspectionMode provides true,
                    ) {
                        Cartridge(
                            sys = Sys.GBA,
                            art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                            title = "Pokémon Emerald Version",
                            meta = "USA · 12.6 MB · main 17×",
                            w = 305f,
                            focused = true,
                            loading = true,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun settingsPreview() {
        captureRoboImage(out("02-settings.png")) {
            MockCanvas {
                SettingsScreen(
                    settings = Settings(
                        folders = mapOf(
                            "GB" to listOf("/storage/7BF4-78FE/Roms/GB"),
                            "GBC" to listOf("/storage/7BF4-78FE/Roms/GBC"),
                            "GBA" to listOf("/storage/7BF4-78FE/Roms/GPSP"),
                        ),
                        cores = mapOf("GB" to "gambatte_libretro_android.so", "GBA" to "mgba_libretro_android.so"),
                        retroPkg = "com.retroarch",
                        autoScan = true,
                        autoArt = true,
                    ),
                    counts = mapOf("GB" to 1, "GBC" to 3, "GBA" to 20),
                    scannedFolders = mapOf(
                        "GB" to listOf("/storage/7BF4-78FE/Roms/GB"),
                        "GBC" to listOf("/storage/7BF4-78FE/Roms/GBC"),
                        "GBA" to listOf("/storage/7BF4-78FE/Roms/GPSP"),
                    ),
                    retroPkg = "com.retroarch",
                    installed = listOf("com.retroarch"),
                    cores = listOf(
                        "gambatte_libretro_android.so",
                        "sameboy_libretro_android.so",
                        "mgba_libretro_android.so",
                    ),
                    cacheBytes = 1_230_000L,
                    onUpdate = {}, onRescan = {}, onClearCache = {}, onResetStats = {},
                    onFolderPick = {}, onClose = {},
                    games = games(),
                    onSetArt = {}, onClearArt = {},
                    sfx = Sfx(ctx),
                )
            }
        }
    }

    @Test
    fun listPreview() {
        captureRoboImage(out("03-list.png")) {
            MockCanvas {
                ListScreen(
                    tab = Tab.GBA,
                    games = games(),
                    artOf = { art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png") },
                    artVersion = 0,
                    onLaunch = {}, onClose = {}, onTab = {}, onFav = {},
                    sfx = Sfx(ctx),
                )
            }
        }
    }

    @Test
    fun folderPreview() {
        captureRoboImage(out("04-folder.png")) {
            MockCanvas {
                FolderPicker(
                    sys = Sys.GBA,
                    initial = "/storage/7BF4-78FE/Roms/GPSP",
                    onPick = {}, onClose = {},
                    sfx = Sfx(ctx),
                )
            }
        }
    }

    @Test
    fun settingsEmulatorPreview() {
        captureRoboImage(out("05-settings-emulator.png")) {
            settingsBlock(initialGroup = 1)
        }
    }

    @Test
    fun corePickerPreview() {
        captureRoboImage(out("06-core-picker.png")) {
            settingsBlock(initialGroup = 1, initialPick = "Core GBA")
        }
    }

    @Test
    fun permissionPreview() {
        captureRoboImage(out("07-permission.png")) {
            MockCanvas {
                PermPanel(onOpen = {}, onSkip = {})
            }
        }
    }

    @Composable
    private fun settingsBlock(initialGroup: Int = 0, initialPick: String? = null) {
        SettingsScreen(
            settings = Settings(
                folders = mapOf(
                    "GB" to listOf("/storage/7BF4-78FE/Roms/GB"),
                    "GBC" to listOf("/storage/7BF4-78FE/Roms/GBC"),
                    "GBA" to listOf("/storage/7BF4-78FE/Roms/GPSP"),
                ),
                cores = mapOf("GB" to "gambatte_libretro_android.so", "GBA" to "mgba_libretro_android.so"),
                retroPkg = "com.retroarch",
                autoScan = true,
                autoArt = true,
            ),
            counts = mapOf("GB" to 1, "GBC" to 3, "GBA" to 20),
            scannedFolders = mapOf(
                "GB" to listOf("/storage/7BF4-78FE/Roms/GB"),
                "GBC" to listOf("/storage/7BF4-78FE/Roms/GBC"),
                "GBA" to listOf("/storage/7BF4-78FE/Roms/GPSP"),
            ),
            retroPkg = "com.retroarch",
            installed = listOf("com.retroarch"),
            cores = listOf(
                "gambatte_libretro_android.so",
                "sameboy_libretro_android.so",
                "mgba_libretro_android.so",
            ),
            cacheBytes = 1_230_000L,
                                onUpdate = {}, onRescan = {}, onClearCache = {}, onResetStats = {},
                                onFolderPick = {}, onClose = {},
                                games = games(),
                                onSetArt = {}, onClearArt = {},
                                sfx = Sfx(ctx),
                                initialGroup = initialGroup,
                                initialPick = initialPick,
        )
    }
}