package id.iqbaldev.kartrid.ui

import id.iqbaldev.kartrid.R

/**
 * Data 27 frame putaran kartrid (GENERATED — jangan diedit tangan).
 * Sumber: design/cart3d/seq.html + seq.py. Regenerate kalau model berubah.
 *
 * Frame hanya berisi SHELL plastik dengan lubang label transparan; label (art + panel
 * sistem) digambar runtime ke quad [QUADS] pakai setPolyToPoly → 27 PNG ±1,2 MB total,
 * boxart tetap dinamis (custom art per game jalan).
 */
object CartFrames {
    const val MIN_ANGLE = -26f
    const val MAX_ANGLE = 26f
    const val STEP = 2f
    val ANGLES = floatArrayOf(-26f, -24f, -22f, -20f, -18f, -16f, -14f, -12f, -10f, -8f, -6f, -4f, -2f, 0f, 2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f, 18f, 20f, 22f, 24f, 26f)

    const val IMG_W = 331
    const val IMG_H = 241

    /** lebar kartrid pada 0° di PNG (px) — pembagi skala: scale = cartWidthDp / UNIT_W */
    const val UNIT_W = 330f

    /** pivot (pusat kartrid) di koordinat PNG — sama untuk semua frame */
    const val CENTER_X = 165.36f
    const val CENTER_Y = 120.4f

    /** 4 titik pojok label (TL, TR, BR, BL) per frame, koordinat PNG */
    val QUADS: Array<FloatArray> = arrayOf(
        floatArrayOf(36.84f, 32.74f, 295.52f, 22.52f, 295.52f, 218.27f, 36.84f, 208.05f),
        floatArrayOf(34.74f, 32.4f, 297.72f, 22.92f, 297.72f, 217.88f, 34.74f, 208.39f),
        floatArrayOf(32.79f, 32.04f, 299.73f, 23.31f, 299.73f, 217.48f, 32.79f, 208.75f),
        floatArrayOf(30.97f, 31.68f, 301.55f, 23.71f, 301.55f, 217.08f, 30.97f, 209.11f),
        floatArrayOf(29.3f, 31.32f, 303.18f, 24.11f, 303.18f, 216.68f, 29.3f, 209.47f),
        floatArrayOf(27.77f, 30.94f, 304.61f, 24.52f, 304.61f, 216.27f, 27.77f, 209.85f),
        floatArrayOf(26.4f, 30.57f, 305.86f, 24.93f, 305.86f, 215.86f, 26.4f, 210.23f),
        floatArrayOf(25.18f, 30.18f, 306.92f, 25.34f, 306.92f, 215.45f, 25.18f, 210.61f),
        floatArrayOf(24.12f, 29.8f, 307.8f, 25.75f, 307.8f, 215.04f, 24.12f, 211f),
        floatArrayOf(23.23f, 29.4f, 308.49f, 26.16f, 308.49f, 214.63f, 23.23f, 211.39f),
        floatArrayOf(22.49f, 29.01f, 308.99f, 26.57f, 308.99f, 214.22f, 22.49f, 211.78f),
        floatArrayOf(21.93f, 28.61f, 309.31f, 26.98f, 309.31f, 213.81f, 21.93f, 212.18f),
        floatArrayOf(21.54f, 28.2f, 309.44f, 27.39f, 309.44f, 213.4f, 21.54f, 212.59f),
        floatArrayOf(21.32f, 27.8f, 309.4f, 27.8f, 309.4f, 212.99f, 21.32f, 212.99f),
        floatArrayOf(21.28f, 27.39f, 309.19f, 28.2f, 309.19f, 212.59f, 21.28f, 213.4f),
        floatArrayOf(21.42f, 26.98f, 308.79f, 28.61f, 308.79f, 212.18f, 21.42f, 213.81f),
        floatArrayOf(21.74f, 26.57f, 308.23f, 29.01f, 308.23f, 211.78f, 21.74f, 214.22f),
        floatArrayOf(22.24f, 26.16f, 307.5f, 29.4f, 307.5f, 211.39f, 22.24f, 214.63f),
        floatArrayOf(22.93f, 25.75f, 306.6f, 29.8f, 306.6f, 211f, 22.93f, 215.04f),
        floatArrayOf(23.8f, 25.34f, 305.54f, 30.18f, 305.54f, 210.61f, 23.8f, 215.45f),
        floatArrayOf(24.86f, 24.93f, 304.33f, 30.57f, 304.33f, 210.23f, 24.86f, 215.86f),
        floatArrayOf(26.11f, 24.52f, 302.95f, 30.94f, 302.95f, 209.85f, 26.11f, 216.27f),
        floatArrayOf(27.55f, 24.11f, 301.43f, 31.32f, 301.43f, 209.47f, 27.55f, 216.68f),
        floatArrayOf(29.18f, 23.71f, 299.76f, 31.68f, 299.76f, 209.11f, 29.18f, 217.08f),
        floatArrayOf(31f, 23.31f, 297.94f, 32.04f, 297.94f, 208.75f, 31f, 217.48f),
        floatArrayOf(33f, 22.92f, 295.98f, 32.4f, 295.98f, 208.39f, 33f, 217.88f),
        floatArrayOf(35.2f, 22.52f, 293.89f, 32.74f, 293.89f, 208.05f, 35.2f, 218.27f),
    )

    val RES: IntArray = intArrayOf(
        R.drawable.cf_00, R.drawable.cf_01, R.drawable.cf_02, R.drawable.cf_03, R.drawable.cf_04, R.drawable.cf_05, R.drawable.cf_06, R.drawable.cf_07, R.drawable.cf_08, R.drawable.cf_09, R.drawable.cf_10, R.drawable.cf_11, R.drawable.cf_12, R.drawable.cf_13, R.drawable.cf_14, R.drawable.cf_15, R.drawable.cf_16, R.drawable.cf_17, R.drawable.cf_18, R.drawable.cf_19, R.drawable.cf_20, R.drawable.cf_21, R.drawable.cf_22, R.drawable.cf_23, R.drawable.cf_24, R.drawable.cf_25, R.drawable.cf_26,
    )

    /** index frame terdekat untuk sudut (derajat) */
    fun indexFor(deg: Float): Int {
        val i = ((deg - MIN_ANGLE) / STEP).toInt().coerceIn(0, RES.size - 1)
        return i
    }
}
