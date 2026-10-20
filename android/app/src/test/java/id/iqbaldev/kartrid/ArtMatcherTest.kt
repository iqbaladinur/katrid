package id.iqbaldev.kartrid

import id.iqbaldev.kartrid.core.ArtMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test logika pencocokan boxart libretro-thumbnails.
 *
 * Index CDN di-hardcode (sample nyata dari listing Apache thumbnails.libretro.com):
 * GB 1638 / GBC 1522 / GBA 5959 file. Sample ini representatif — nama file asli
 * libretro, bukan karangan.
 *
 * Fokus: regresi dari matcher lama yang cuma exact-match nama No-Intro.
 */
class ArtMatcherTest {

    private val gbIndex = listOf(
        "Addams Family, The (Europe) (En,Fr,De).png",
        "Addams Family, The (Japan).png",
        "Addams Family, The (USA).png",
        "Addams Family, The - Pugsley's Scavenger Hunt (USA, Europe).png",
        "Adventure Island (USA, Europe).png",
        "Adventure Island II - Aliens in Paradise (USA, Europe).png",
        "A-mazing Tater (USA).png",
        "Aerostar (Japan).png",
        "Aerostar (USA, Europe).png",
        "Aladdin (Europe) (SGB Enhanced).png",
        "Aladdin (USA) (SGB Enhanced).png",
        "Dr. Franken (Europe) (En,Fr,De,Es,It,Nl,Sv).png",
        "Dr. Franken (Japan).png",
        "Dr. Franken (USA).png",
        "Dr. Franken II (USA, Europe) (En,Fr,De,Es,It,Nl,Sv).png",
        "Pokemon - Brown Version.png",
        "Tetris (Japan) (En).png",
        "Tetris (World) (Rev 1).png",
        "Tetris 2 (USA).png",
        "Tetris 2 (USA, Europe) (SGB Enhanced).png",
        "4-in-1 Fun Pak (Japan).png",
        "4-in-1 Fun Pak (USA, Europe).png",
        "Alien vs Predator (Japan).png",
        "Alien vs Predator - The Last of His Clan (USA).png",
        "Brain Bender (USA).png",
        "Daedalian Opus (USA).png",
    )

    private val gbcIndex = listOf(
        "Dance Dance Revolution GB (Japan).png",
        "Dance Dance Revolution GB 2 (Japan).png",
        "Dance Dance Revolution GB 3 (Japan).png",
        "Legend of the River King 2 (USA) (SGB Enhanced) (GB Compatible).png",
        "Legend of the River King GB (Europe) (SGB Enhanced) (GB Compatible).png",
        "Men in Black - The Series (USA, Europe) (SGB Enhanced) (GB Compatible).png",
        "Men in Black 2 - The Series (USA) (En,Fr,De).png",
        "Pokemon - Crystal Version (Australia).png",
        "Pokemon - Crystal Version (USA).png",
        "Pokemon - Crystal Version (USA, Europe) (Rev 1).png",
    )

    private val gbaIndex = listOf(
        "007 - Everything or Nothing (Japan).png",
        "007 - Everything or Nothing (USA, Europe) (En,Fr,De).png",
        "Mega Man Zero (USA, Europe).png",
        "Mega Man Zero 2 (USA).png",
        "Mega Man Zero 3 (USA).png",
        "Pokemon - FireRed Version (USA, Europe).png",
        "Shaman King - Master of Spirits (USA).png",
        "Shaman King - Master of Spirits 2 (USA).png",
        "Sonic Advance (USA) (En,Ja).png",
        "Sonic Advance 2 (USA) (En,Ja).png",
        "Summon Night - Swordcraft Story (USA).png",
        "Summon Night - Swordcraft Story 2 (USA).png",
        "Super Mario Advance (USA, Europe).png",
        "Super Mario Advance 2 - Super Mario World (USA, Australia).png",
        "Super Mario Advance 3 - Yoshi's Island (USA).png",
        "Mega Man Battle Network (USA).png",
        "Mega Man Battle Network 2 (USA).png",
    )

    private val gb = ArtMatcher.TierIndex.build(gbIndex)
    private val gbc = ArtMatcher.TierIndex.build(gbcIndex)
    private val gba = ArtMatcher.TierIndex.build(gbaIndex)

    // ---------------------------------------------------------------- normalisasi

    @Test
    fun core_stripsTagsAndExtension() {
        assertEquals("addams family, the", ArtMatcher.core("Addams Family, The (USA).png"))
        assertEquals("pokemon - brown version", ArtMatcher.core("Pokemon - Brown Version.png"))
        assertEquals("mega man zero", ArtMatcher.core("Mega Man Zero (USA, Europe).png"))
    }

    @Test
    fun core_underscoreBecomesSpace() {
        assertEquals(
            "arcade classic no. 1 - asteroids missile command",
            ArtMatcher.core("Arcade Classic No. 1 - Asteroids _ Missile Command (USA).png"),
        )
    }

    /** bug lama: titlePart buat nama tanpa paren jadi bawa ".png" → mustahil match */
    @Test
    fun match_namesWithoutParensStillMatch() {
        val hit = ArtMatcher.match(gb, "Pokemon - Brown Version")
        assertEquals("Pokemon - Brown Version.png", hit)
    }

    // ---------------------------------------------------------------- region ranking

    @Test
    fun regionPrefersUsaEuropeOverJapan() {
        assertEquals(
            "Addams Family, The (USA).png",
            ArtMatcher.match(gb, "Addams Family, The"),
        )
    }

    @Test
    fun regionPrefersUsaEuropeWhenBothExist() {
        val hit = ArtMatcher.match(gb, "Aerostar")
        assertEquals("Aerostar (USA, Europe).png", hit)
    }

    /** harus pilih art polos (tanpa tag SGB Enhanced) kalau ada */
    @Test
    fun regionPrefersPlainArt() {
        val hit = ArtMatcher.match(gb, "Tetris")
        assertNotNull(hit)
        assertTrue(
            "dapat art: $hit",
            hit == "Tetris (World) (Rev 1).png" || hit == "Tetris (Japan) (En).png",
        )
    }

    // ---------------------------------------------------------------- variasi nama

    @Test
    fun match_commaTheAdded() {
        assertEquals(
            "Addams Family, The (USA).png",
            ArtMatcher.match(gb, "Addams Family"),
        )
    }

    @Test
    fun match_versionSuffixAdded() {
        val hit = ArtMatcher.match(gbc, "Pokemon - Crystal")
        assertNotNull(hit)
        assertTrue("dapat art: $hit", hit!!.startsWith("Pokemon - Crystal Version"))
    }

    @Test
    fun match_apostropheRemoved() {
        val hit = ArtMatcher.match(gba, "MegaMan Battle Network")
        assertEquals("Mega Man Battle Network (USA).png", hit)
    }

    @Test
    fun match_dashSpacingNormalized() {
        val hit = ArtMatcher.match(gb, "A-mazing Tater")
        assertEquals("A-mazing Tater (USA).png", hit)
    }

    @Test
    fun match_romanToArabicSeries() {
        val hit = ArtMatcher.match(gb, "Tetris II")
        assertNotNull(hit)
        assertTrue("dapat art: $hit", hit!!.startsWith("Tetris 2"))
    }

    // ---------------------------------------------------------------- nomor seri (gate)

    /** "Dr Franken I" HARUS dapat art seri 1, bukan art "Dr. Franken II" */
    @Test
    fun seriesGate_doesNotGiveWrongSequelArt() {
        val hit = ArtMatcher.match(gb, "Dr Franken I")
        assertNotNull(hit)
        assertTrue(
            "dapat art: $hit -- harus art seri 1, bukan II",
            hit == "Dr. Franken (USA).png" ||
                hit == "Dr. Franken (Japan).png" ||
                hit == "Dr. Franken (Europe) (En,Fr,De,Es,It,Nl,Sv).png",
        )
    }

    @Test
    fun seriesGate_secondGameGetsSecondArt() {
        val hit = ArtMatcher.match(gb, "Dr Franken II")
        assertEquals("Dr. Franken II (USA, Europe) (En,Fr,De,Es,It,Nl,Sv).png", hit)
    }

    @Test
    fun seriesGate_marioAdvance2NotFirst() {
        val hit = ArtMatcher.match(gba, "Super Mario Advance 2")
        assertNotNull(hit)
        assertTrue("dapat art: $hit", hit!!.startsWith("Super Mario Advance 2"))
    }

    @Test
    fun seriesGate_marioAdvance3NotFirst() {
        val hit = ArtMatcher.match(gba, "Super Mario Advance III")
        assertNotNull(hit)
        assertTrue("dapat art: $hit", hit!!.startsWith("Super Mario Advance 3"))
    }

    @Test
    fun seriesGate_rom2WithRomanGetsArabicArt() {
        val hit = ArtMatcher.match(gba, "Sonic Advance II")
        assertNotNull(hit)
        assertTrue("dapat art: $hit", hit!!.startsWith("Sonic Advance 2"))
    }

    @Test
    fun seriesGate_megaManZero3() {
        val hit = ArtMatcher.match(gba, "Mega Man Zero III")
        assertEquals("Mega Man Zero 3 (USA).png", hit)
    }

    // ---------------------------------------------------------------- prefix subtitle

    @Test
    fun match_prefixSubtitle() {
        val hit = ArtMatcher.match(gba, "Shaman King - Master of Spirits 2")
        assertEquals("Shaman King - Master of Spirits 2 (USA).png", hit)
    }

    @Test
    fun match_prefixSubtitleWithSeries() {
        val hit = ArtMatcher.match(gbc, "Men In Black 2 - The Series")
        assertNotNull(hit)
        assertTrue("dapat art: $hit", hit!!.startsWith("Men in Black 2 - The Series"))
    }

    // ---------------------------------------------------------------- fuzzy

    @Test
    fun fuzzy_matchesSlightlyDifferentTitle() {
        val hit = ArtMatcher.match(gb, "Alien vs Predator - Last of His Clan")
        assertNotNull(hit)
    }

    @Test
    fun fuzzy_rejectsUnrelatedTitle() {
        assertNull(ArtMatcher.match(gb, "Zzzqqq Xxxyyy"))
    }

    // ---------------------------------------------------------------- no false positives

    @Test
    fun noMatch_whenTitleAbsent() {
        assertNull(ArtMatcher.match(gb, "Adventures of Pinocchio"))
        assertNull(ArtMatcher.match(gb, "Ant Soldiers"))
    }

    @Test
    fun noMatch_whenTitleBelongsToOtherSystem() {
        // judul GBC jangan dikira GB
        assertNull(ArtMatcher.match(gb, "Pokemon - Crystal"))
    }

    // ---------------------------------------------------------------- regression guard

    /** semua art yang matched harus punya judul inti yang nyambung ke nama ROM */
    @Test
    fun everyMatchHasRelatedTitle() {
        val cases = listOf(
            Triple(gb, "Addams Family", "addams family"),
            Triple(gb, "Aero Star", "aerostar"),
            Triple(gb, "A-mazing Tater", "a-mazing tater"),
            Triple(gb, "Tetris", "tetris"),
            Triple(gbc, "Pokemon - Crystal", "pokemon - crystal"),
            Triple(gba, "Super Mario Advance 2", "super mario advance 2"),
            Triple(gba, "Shaman King - Master of Spirits 2", "shaman king - master of spirits 2"),
        )
        for ((idx, rom, expect) in cases) {
            val hit = ArtMatcher.match(idx, rom)
            assertNotNull("ROM '$rom' harus ketemu", hit)
            val ct = ArtMatcher.core(hit!!.substringBefore(" ("))
            assertTrue(
                "ROM '$rom' -> '$hit' (core '$ct') harus mengandung '$expect'",
                ct.contains(expect) || expect.contains(ct),
            )
        }
    }
}
