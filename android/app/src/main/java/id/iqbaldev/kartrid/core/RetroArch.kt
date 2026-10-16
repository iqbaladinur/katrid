package id.iqbaldev.kartrid.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Kontrak launch ke RetroArch Android (PLAN §7, diverifikasi dari source
 * `RetroActivityFuture` / `RetroActivityCommon`).
 *
 * Kunci: extra CONFIGFILE yang bikin RetroArch jalan di mode "viaLauncher"
 * (langsung muat ROM, nggak nampilin menu sendiri).
 */
object RetroArch {

    val PACKAGES = listOf(
        "com.retroarch",
        "com.retroarch.aarch64",
        "com.retroarch.ra32",
        "com.retroarch.quest",
    )

    const val ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"
    const val ACTION_QUERY_CORES = "com.retroarch.QUERY_INSTALLED_CORES"
    const val ACTION_CORES_RESULT = "com.retroarch.INSTALLED_CORES_RESULT"

    fun installed(ctx: Context): List<String> = PACKAGES.filter { p ->
        runCatching { ctx.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
    }

    fun pickPackage(ctx: Context, preferred: String?): String? =
        preferred?.takeIf { it in installed(ctx) } ?: installed(ctx).firstOrNull()

    fun corePath(pkg: String, core: String): String = "/data/data/$pkg/cores/$core"

    /**
     * Config default RetroArch Android. Bukan /data/data — config aslinya ada di
     * external files dir app RetroArch (terverifikasi dari log RetroArch:
     * "Set config file to : /storage/emulated/0/Android/data/<pkg>/files/retroarch.cfg").
     * Extra ini tetap dikirim walau file-nya nggak kebaca app kita: RetroArch yang
     * buka sendiri pakai uid-nya. Kalau file nggak ada, RetroArch pakai default.
     */
    fun defaultCfg(pkg: String): String =
        "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg"

    fun pickCore(ctx: Context, pkg: String, sys: Sys, preferred: String?): String? {
        val have = queryCores(ctx)
        preferred?.takeIf { it in have }?.let { return it }
        return sys.cores.firstOrNull { it in have } ?: sys.cores.firstOrNull()
    }

    /**
     * Tanya ke RetroArch core apa saja yang terpasang (broadcast resmi).
     * Blocking max 1.2 s — panggil dari Dispatchers.IO.
     */
    fun queryCores(ctx: Context): List<String> {
        val latch = CountDownLatch(1)
        val slot = java.util.concurrent.atomic.AtomicReference<List<String>>(emptyList())
        val recv = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i?.getStringArrayExtra("CORES")?.let { slot.set(it.toList()) }
                latch.countDown()
            }
        }
        val ok = runCatching {
            ContextCompat.registerReceiver(
                ctx, recv, IntentFilter(ACTION_CORES_RESULT),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        if (!ok) return emptyList()
        return try {
            ctx.sendBroadcast(Intent(ACTION_QUERY_CORES))
            latch.await(1200, TimeUnit.MILLISECONDS)
            slot.get()
        } finally {
            runCatching { ctx.unregisterReceiver(recv) }
        }
    }

    fun launchIntent(
        pkg: String,
        rom: String,
        core: String,
        cfg: String?,
        quitFocus: Boolean,
        force60: Boolean,
    ): Intent = Intent().apply {
        setClassName(pkg, ACTIVITY)
        putExtra("ROM", rom)
        putExtra("LIBRETRO", core)
        putExtra("CONFIGFILE", cfg ?: defaultCfg(pkg))
        if (quitFocus) putExtra("QUITFOCUS", true)
        if (force60) putExtra("REFRESH", "60")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
