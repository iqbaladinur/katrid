package id.iqbaldev.kartrid

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import id.iqbaldev.kartrid.core.Game
import id.iqbaldev.kartrid.core.Settings
import id.iqbaldev.kartrid.core.Sys
import id.iqbaldev.kartrid.core.Tab
import id.iqbaldev.kartrid.ui.Cartridge
import id.iqbaldev.kartrid.ui.CartStyle
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
 * Output: PNG di ~/hobbies/kartrid/previews/ — biar user bisa nilai desain tanpa install.
 * Qualifier mdpi = density 240 seperti KPA → 1dp=1px, pas sama kanvas desain.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h640dp-land-mdpi")
class PreviewTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private val artDir = "/home/iqbaladinur/hobbies/kartrid/design/art"

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
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/01-shelf.png") {
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
                    sfx = Sfx(ctx),
                )
            }
        }
    }

    @Test
    fun cartStyleAPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/08-cart-style-A.png") {
            MockCanvas {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cartridge(
                        sys = Sys.GBA,
                        art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                        w = 288f,
                        h = 206f,
                        focused = true,
                        style = CartStyle.A,
                    )
                }
            }
        }
    }

    @Test
    fun cartStyleBPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/09-cart-style-B.png") {
            MockCanvas {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cartridge(
                        sys = Sys.GBA,
                        art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                        w = 288f,
                        h = 206f,
                        focused = true,
                        style = CartStyle.B,
                    )
                }
            }
        }
    }

    @Test
    fun cartStyleCPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/10-cart-style-C.png") {
            MockCanvas {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cartridge(
                        sys = Sys.GBA,
                        art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                        w = 288f,
                        h = 206f,
                        focused = true,
                        style = CartStyle.C,
                    )
                }
            }
        }
    }

    @Test
    fun cartStyleDPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/11-cart-style-D.png") {
            MockCanvas {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cartridge(
                        sys = Sys.GBA,
                        art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                        w = 176f,
                        h = 229f,
                        focused = true,
                        style = CartStyle.D,
                    )
                }
            }
        }
    }

    @Test
    fun cartStyleEPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/12-cart-style-E.png") {
            MockCanvas {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cartridge(
                        sys = Sys.GBA,
                        art = art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
                        w = 196f,
                        h = 272f,
                        focused = true,
                        style = CartStyle.E,
                    )
                }
            }
        }
    }

    @Test
    fun settingsPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/02-settings.png") {
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
                    sfx = Sfx(ctx),
                )
            }
        }
    }

    @Test
    fun listPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/03-list.png") {
            MockCanvas {
                ListScreen(
                    tab = Tab.GBA,
                    games = games(),
                    artOf = { art("gba_Pokemon_-_Emerald_Version__USA__Europe_.png") },
                    artVersion = 0,
                    onLaunch = {}, onClose = {}, onTab = {},
                    sfx = Sfx(ctx),
                )
            }
        }
    }

    @Test
    fun folderPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/04-folder.png") {
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
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/05-settings-emulator.png") {
            settingsBlock(initialGroup = 1)
        }
    }

    @Test
    fun corePickerPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/06-core-picker.png") {
            settingsBlock(initialGroup = 1, initialPick = "Core GBA")
        }
    }

    @Test
    fun permissionPreview() {
        captureRoboImage("/home/iqbaladinur/hobbies/kartrid/previews/07-permission.png") {
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
            sfx = Sfx(ctx),
            initialGroup = initialGroup,
            initialPick = initialPick,
        )
    }
}