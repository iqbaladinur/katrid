package id.iqbaldev.kartrid.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import id.iqbaldev.kartrid.R

/**
 * SFX klik kartrid. SoundPool (bukan MediaPlayer) karena latency rendah —
 * penting buat klik. File .ogg di res/raw + noCompress supaya bisa di-load.
 */
class Sfx(ctx: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val insertId = pool.load(ctx, R.raw.insert, 1)
    private val ejectId = pool.load(ctx, R.raw.eject, 1)
    private val tickId = pool.load(ctx, R.raw.tick, 1)

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    fun insert(volume: Float = 1f) = play(insertId, volume)
    fun eject(volume: Float = 0.95f) = play(ejectId, volume)
    fun tick(volume: Float = 0.7f) = play(tickId, volume)

    private fun play(id: Int, volume: Float) {
        runCatching { pool.play(id, volume, volume, 1, 0, 1f) }
    }

    /** haptic klik ~18 ms — diam kalau device nggak punya motor getar */
    fun haptic(ms: Long = 18L) {
        val v = vibrator ?: return
        if (!runCatching { v.hasVibrator() }.getOrDefault(false)) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, 140))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    fun release() {
        runCatching { pool.release() }
    }
}
