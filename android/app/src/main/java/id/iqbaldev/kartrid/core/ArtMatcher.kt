package id.iqbaldev.kartrid.core

/**
 * Logika pencocokan boxart libretro-thumbnails — murni, tanpa Android.
 *
 * Kenapa dipisah dari [ArtCache]: bisa di-unit-test (JVM) tanpa Context/Robolectric,
 * dan bisa divalidasi ulang terhadap dataset nyata (2167 game) yang gw ukur.
 *
 * Strategi (tervalidasi 20 Okt 2026, 2167 game asli):
 *  - Tier 0: judul inti apa adanya
 *  - Tier 1: variasi aman (", The", " the", " - " <-> " ", version/edition, roman <-> arabic,
 *            prefix subtitle)
 *  - Tier 2: variasi agresif (buang angka akhir, stopword, tanda baca)
 *  - Tier 3: fuzzy (Levenshtein ≥ 0.84 + prefilter dice bigram)
 *  - Gate nomor seri: judul CDN bernomor seri LAIN dari ROM = art salah → tolak
 *
 * Angka (device GT78-VN): coverage 1131/2167 (52%) → 1722/2167 (79.5%), regression 0.
 */
internal object ArtMatcher {

    private val TAG_RE = Regex("\\([^)]*\\)|\\[[^]]*\\]")
    private val WS_RE = Regex("\\s+")
    private val YEAR_RE = Regex("(19|20)\\d{2}")
    private val TRAIL_NUM_RE = Regex("\\s+([ivx]+|\\d+)$")
    private val TRAIL_NUM_STR_RE = Regex("\\s+\\d+$")
    private val TRAIL_THE_RE = Regex("\\s+the$")
    private val DASH_RE = Regex("\\s*-\\s*")
    private val REGION_TAG_RE = Regex("\\(([^()]*)\\)")
    private val LANG_RE = Regex("\\b(en|fr|de|es|it|ja|pt|nl|sv|no|da|fi|ko|zh)\\b")
    private val REV_RE = Regex("\\brev\\b")

    private val ROMAN = mapOf(
        "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5,
        "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10,
    )

    private val REGION_NAMES = setOf(
        "usa", "europe", "world", "japan", "australia", "spain", "france",
        "germany", "italy", "korea", "china", "brazil", "canada",
    )

    /** urutan preferensi region (TANPA kurung — versi lama pakai kurung, jadi nggak kepas) */
    private val REGION_PREF = listOf(
        listOf("usa", "europe"), listOf("world"), listOf("usa"), listOf("europe"),
        listOf("japan"), listOf("usa", "australia"), listOf("world", "usa", "europe"),
        listOf("japan", "usa"), listOf("europe", "usa"), listOf("australia"),
        listOf("spain"), listOf("france"), listOf("germany"), listOf("italy"),
        listOf("korea"), listOf("china"), listOf("brazil"), listOf("canada"),
        listOf(""),
    )

    private val EXTRA_WORDS = listOf(
        "beta", "proto", "alternate", "sample", "demo",
        "virtual console", "switch online",
    )

    /** ambang similarity fuzzy (Levenshtein) — tervalidasi di 2167 game asli */
    const val FUZZY_MIN = 0.84

    /** prefilter dice bigram minimum sebelum hitung Levenshtein */
    private const val DICE_MIN = 0.25

    // ------------------------------------------------------------------ normalisasi

    /**
     * Judul inti: tanpa ekstensi, tanpa tag (region/hack), underscore = spasi, lowercase.
     * Dipakai sebagai kunci perbandingan DI kedua sisi (nama ROM lokal & nama file CDN).
     */
    fun core(s: String): String =
        s.removeSuffix(".png")
            .replace('_', ' ')
            .replace(TAG_RE, " ")
            .replace(WS_RE, " ")
            .trim()
            .lowercase()

    /** token nomor seri (roman/arabic) di nama — tahun & token ber-hyphen di-exclude */
    fun seriesOf(t: String): List<Int> {
        val out = ArrayList<Int>(2)
        for (tok in t.split(' ')) {
            if (tok.contains('-')) continue
            if (YEAR_RE.matches(tok)) continue
            val r = ROMAN[tok]
            if (r != null) out.add(r)
            else if (tok.isNotEmpty() && tok.all { it.isDigit() }) {
                val n = tok.toInt()
                if (n in 1..20) out.add(n)
            }
        }
        return out
    }

    /** normalisasi nomor seri di akhir judul: "tetris ii" -> "tetris 2" */
    fun canonNum(t: String): String {
        val m = TRAIL_NUM_RE.find(t) ?: return t
        val tok = m.groupValues[1]
        val n = ROMAN[tok] ?: tok.toIntOrNull()?.takeIf { it in 1..20 } ?: return t
        val stem = t.removeRange(m.range).trim()
        return if (n == 1) stem else "$stem $n"
    }

    /**
     * Lapisan kandidat, dari paling aman ke paling longgar.
     *  0 = judul inti apa adanya
     *  1 = variasi aman (", The", " the", " - " <-> " ", version/edition, roman <-> arabic, titik)
     *  2 = variasi agresif (buang angka akhir, stopword, tanda baca)
     */
    fun tiers(t: String): List<Set<String>> {
        val t1 = HashSet<String>(14)
        t1.add(t.replace(", the", "").trim())
        t1.add(t.replace(TRAIL_THE_RE, ""))
        t1.add(t.replace(DASH_RE, " ").trim())
        t1.add(t.replace("'", ""))
        t1.add("$t version")
        t1.add(t.replace(" version", "").trim())
        t1.add("$t edition")
        t1.add(t.replace(" edition", "").trim())
        t1.add(canonNum(t))
        // titik: "Dr Franken" -> "Dr. Franken"
        t1.add(t.replace(".", "").replace(WS_RE, " ").trim())

        val t2 = HashSet<String>(7)
        t2.add(t.replace(TRAIL_NUM_STR_RE, ""))
        t2.add(t.replace(" of ", " ").replace(" and ", " ").replace(" & ", " "))
        t2.add(t.replace(TRAIL_THE_RE, ""))
        t2.add(t.replace(",", "").replace("'", "").replace("’", ""))
        t2.add(t.replace(".", "").replace(WS_RE, " ").trim())

        return listOf(setOf(t), t1, t2)
    }

    // ------------------------------------------------------------------ scoring

    /**
     * Rating region: prefer USA/Europe > World > USA > Europe > Japan.
     * Tag tambahan (Rev, Enhanced, bahasa, Beta) menambah penalti 1 poin tiap tag.
     */
    fun regionRank(name: String): Int {
        val stem = name.removeSuffix(".png")
        val groups = REGION_TAG_RE.findAll(stem).map { it.groupValues[1] }.toList()
        var score = REGION_PREF.size
        for (g in groups) {
            val parts = g.split(',').map { it.trim().lowercase() }
            if (parts.any { it in REGION_NAMES }) {
                var found = false
                for (i in REGION_PREF.indices) {
                    if (REGION_PREF[i] == parts) { score = i; found = true; break }
                }
                if (!found) {
                    for (i in REGION_PREF.indices) {
                        if (parts.isNotEmpty() && parts[0] == REGION_PREF[i][0]) { score = i; break }
                    }
                }
                break
            }
        }
        var extra = 0
        for (g in groups) {
            val gl = g.lowercase()
            if (REV_RE.containsMatchIn(gl)) extra++
            if (gl.contains("enhanced")) extra++
            if (LANG_RE.containsMatchIn(gl)) extra++
            if (EXTRA_WORDS.any { gl.contains(it) }) extra++
        }
        return score * 10 + extra
    }

    /**
     * Gate nomor seri: judul CDN yang membawa nomor seri LAIN dari ROM = art salah → tolak.
     * "Dr Franken I" tidak boleh dapat art "Dr. Franken II".
     */
    fun seriesOk(romTokens: List<Int>, cdnTitle: String): Boolean {
        if (romTokens.isEmpty()) return true
        val cdn = seriesOf(core(cdnTitle))
        if (cdn.isEmpty()) return true
        return romTokens[romTokens.size - 1] in cdn
    }

    /** similarity Levenshtein → 1.0 = identik */
    fun levRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        val m = maxOf(a.length, b.length)
        if (a.isEmpty()) return 1.0 - b.length.toDouble() / m
        if (b.isEmpty()) return 1.0 - a.length.toDouble() / m
        var prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur.copyOf()
        }
        return 1.0 - prev[b.length].toDouble() / m
    }

    /** dice bigram — prefilter murah sebelum Levenshtein */
    fun dice(a: String, b: String): Double {
        if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0
        val ga = HashSet<String>(a.length)
        val gb = HashSet<String>(b.length)
        for (i in 0 until a.length - 1) ga.add(a.substring(i, i + 2))
        for (i in 0 until b.length - 1) gb.add(b.substring(i, i + 2))
        if (ga.isEmpty() || gb.isEmpty()) return 0.0
        var inter = 0
        for (g in ga) if (g in gb) inter++
        return 2.0 * inter / (ga.size + gb.size)
    }

    // ------------------------------------------------------------------ matching

    /** index per tier: tier → (judul inti → daftar nama file CDN) */
    class TierIndex private constructor(
        val maps: List<Map<String, List<String>>>,
        val coreTitles: List<String>,
        val all: List<String>,
    ) {
        companion object {
            fun build(index: List<String>): TierIndex {
                val maps = ArrayList<Map<String, List<String>>>(3)
                for (tier in 0..2) {
                    val m = HashMap<String, MutableList<String>>()
                    for (n in index) {
                        val ct = core(n.substringBefore(" ("))
                        if (ct.isBlank()) continue
                        for (key in tiers(ct)[tier]) {
                            m.getOrPut(key) { ArrayList(1) }.add(n)
                        }
                    }
                    maps.add(m)
                }
                val titles = index.map { core(it.substringBefore(" (")) }
                    .filter { it.isNotBlank() }.distinct().sorted()
                return TierIndex(maps, titles, index)
            }
        }
    }

    /**
     * Cari nama file boxart di index CDN untuk satu game.
     * Tier 0..2 = pencocokan exact dengan variasi; tier 3 = fuzzy (Levenshtein ≥ FUZZY_MIN).
     * Balikin null kalau nggak ada yang cocok.
     */
    fun match(tIdx: TierIndex, rawName: String): String? {
        val tp = core(rawName)
        if (tp.isBlank()) return null
        val romTokens = seriesOf(tp)
        val tk = tiers(tp)

        for (tier in 0..2) {
            val map = tIdx.maps[tier]
            val cands = LinkedHashSet<String>()
            for (key in tk[tier]) map[key]?.let { cands.addAll(it) }
            // tier 1: prefix subtitle ("men in black 2" -> "Men in Black 2 - The Series")
            if (cands.isEmpty() && tier == 1) {
                for (probe in listOf(tp, canonNum(tp)) + tk[1]) {
                    for (n in tIdx.all) {
                        val ct = core(n.substringBefore(" ("))
                        if (ct.substringBefore(" - ") == probe) cands.add(n)
                    }
                }
            }
            if (cands.isEmpty()) continue
            val kept = cands.filter { seriesOk(romTokens, core(it.substringBefore(" ("))) }
            if (kept.isEmpty()) continue
            return kept.minByOrNull { regionRank(it) }
        }

        // tier 3: fuzzy
        var best: String? = null
        var bestScore = 0.0
        for (t in tIdx.coreTitles) {
            if (kotlin.math.abs(t.length - tp.length) > maxOf(4, (tp.length * 0.35).toInt())) continue
            if (t[0] != tp[0]) continue
            if (!seriesOk(romTokens, t)) continue
            if (dice(tp, t) < DICE_MIN) continue
            val s = levRatio(tp, t)
            if (s > bestScore) { bestScore = s; best = t }
        }
        if (best == null || bestScore < FUZZY_MIN) return null
        val cands = tIdx.all.filter { core(it.substringBefore(" (")) == best }
        if (cands.isEmpty()) return null
        return cands.minByOrNull { regionRank(it) }
    }
}
