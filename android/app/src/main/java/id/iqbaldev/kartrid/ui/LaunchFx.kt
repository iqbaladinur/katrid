package id.iqbaldev.kartrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Launch motion for the focused cartridge: wind-up twist, slot insert, spark burst.
 *
 * The timeline is a single linear clock (ms). Every visual property is a pure function of
 * that clock, so the whole sequence can be rendered at any frame from a preview/test
 * (`ShelfScreen(previewLaunchMs = ...)`) without running coroutines.
 *
 * Why the twist only reaches +-26 degrees: the cartridge shells are 27 pre-rendered
 * frames covering -26..+26 (see [CartFrames]). A 360-degree spin would need ~180 frames
 * (~2.8 MB); going beyond the available range would have to fake rotation with
 * `graphicsLayer.rotationY`, which flattens the plastic shell and was rejected before.
 *
 * Why the scale shrinks toward the bottom edge: the shelf is a soft shadow line, not a
 * 3D slot. Scaling around `TransformOrigin(0.5f, 1f)` keeps the bottom edge pinned to the
 * shelf line while the cartridge shrinks and fades, so it reads as sinking into the shelf.
 * The spark burst spawns exactly on that line.
 */
internal object LaunchAnim {
    // -- timeline (ms) --
    /** anticipation: lift + counter-rotate before the swing */
    const val ANTICIPATE = 70f
    /** end of the wind-up swing (most extreme frame) */
    const val WINDUP_END = 190f
    /** cartridge reaches the shelf line, sparks spawn */
    const val IMPACT = 330f
    /** full-frame flash peak; the launch intent fires here so the window cut is hidden */
    const val FLASH = 380f
    /** clock end — sparks have faded, state can reset */
    const val TAIL = 780f

    /** spark particle lifetime (ms) */
    const val SPARK_LIFE = 420f

    /** spin at rest == [CartFrames.MAX_ANGLE] */
    private const val REST = CartFrames.MAX_ANGLE

    fun spin(ms: Float): Float = when {
        ms <= ANTICIPATE -> seg(ms, 0f, ANTICIPATE, REST, 12f, ::easeOutCubic)
        ms <= WINDUP_END -> seg(ms, ANTICIPATE, WINDUP_END, 12f, -22f, ::easeInOutCubic)
        ms <= IMPACT -> seg(ms, WINDUP_END, IMPACT, -22f, 0f, ::easeOutCubic)
        else -> 0f
    }

    fun tilt(ms: Float): Float = when {
        ms <= ANTICIPATE -> seg(ms, 0f, ANTICIPATE, 0f, -4f, ::easeOutCubic)
        ms <= WINDUP_END -> seg(ms, ANTICIPATE, WINDUP_END, -4f, 2f, ::easeInOutCubic)
        ms <= IMPACT -> seg(ms, WINDUP_END, IMPACT, 2f, 16f, ::easeInQuad)
        else -> 16f
    }

    /** vertical translation in px (positive = down, into the shelf) */
    fun lift(ms: Float): Float = when {
        ms <= ANTICIPATE -> seg(ms, 0f, ANTICIPATE, 0f, -9f, ::easeOutCubic)
        ms <= WINDUP_END -> seg(ms, ANTICIPATE, WINDUP_END, -9f, 0f, ::easeInOutCubic)
        ms <= IMPACT -> seg(ms, WINDUP_END, IMPACT, 0f, 20f, ::easeInQuad)
        else -> 18f
    }

    fun scale(ms: Float): Float = when {
        ms <= ANTICIPATE -> 1f
        ms <= WINDUP_END -> seg(ms, ANTICIPATE, WINDUP_END, 1f, 1.035f, ::easeOutCubic)
        ms <= IMPACT -> seg(ms, WINDUP_END, IMPACT, 1.035f, 0.54f, ::easeInQuad)
        else -> 0.54f
    }

    fun cartAlpha(ms: Float): Float = when {
        ms <= 240f -> 1f
        ms <= IMPACT -> seg(ms, 240f, IMPACT, 1f, 0f, ::easeInQuad)
        else -> 0f
    }

    /** 0..1 intensity of the lime slot line that appears where the cartridge sinks in */
    fun slotGlow(ms: Float): Float = when {
        ms <= 300f -> 0f
        ms <= 340f -> seg(ms, 300f, 340f, 0f, 1f, ::easeOutCubic)
        ms <= 620f -> seg(ms, 340f, 620f, 1f, 0f, ::easeOutQuad)
        else -> 0f
    }

    /** 0..1 intensity of the full-frame flash that covers the activity switch */
    fun flash(ms: Float): Float = when {
        ms <= 352f -> 0f
        ms <= FLASH -> seg(ms, 352f, FLASH, 0f, 1f, ::easeInQuad)
        ms <= 470f -> seg(ms, FLASH, 470f, 1f, 0f, ::easeOutQuad)
        else -> 0f
    }
}

private fun seg(ms: Float, a: Float, b: Float, from: Float, to: Float, ease: (Float) -> Float): Float =
    if (ms <= a) from else if (ms >= b) to else from + (to - from) * ease((ms - a) / (b - a))

private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeInCubic(t: Float): Float = t * t * t
private fun easeInQuad(t: Float): Float = t * t
private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)
private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) * (-2f * t + 2f) / 2f

/** One deterministic spark particle. Built once at class load, never per frame. */
private class Spark(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    /** seconds */
    val life: Float,
    val radius: Float,
    val gold: Boolean,
)

private const val SPARK_GRAVITY = 760f
private const val SPARK_COUNT = 28

/** Offsets relative to the slot origin (0,0 = shelf line under the focused cartridge). */
private val SPARKS: List<Spark> = run {
    val rnd = Random(7331)
    List(SPARK_COUNT) {
        // upward fan: -160deg .. -20deg in screen coordinates (cos/sin with y down)
        val angleDeg = -160f + rnd.nextFloat() * 140f
        val angle = angleDeg * PI.toFloat() / 180f
        val speed = 200f + rnd.nextFloat() * 280f
        Spark(
            x0 = (rnd.nextFloat() - 0.5f) * 190f,
            y0 = (rnd.nextFloat() - 0.5f) * 6f,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            life = 0.26f + rnd.nextFloat() * 0.20f,
            radius = 1.4f + rnd.nextFloat() * 1.6f,
            gold = rnd.nextFloat() < 0.45f,
        )
    }
}

/**
 * Draws the slot glow, spark burst and launch flash on top of the shelf.
 *
 * [clock] is read inside the draw lambda, so the 380 ms launch only invalidates the draw
 * phase — it does not re-compose the shelf, title or hint bar on every frame.
 */
@Composable
internal fun LaunchFxOverlay(
    clock: () -> Float,
    originX: Float,
    originY: Float,
    modifier: Modifier = Modifier,
) {
    // Built once: creating a radial-gradient shader inside the draw lambda allocates and
    // re-compiles a shader on every frame of the flash (measured as jank on mdpi).
    val flashBrush = remember(originX, originY) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFE9FFA8),
                Color(0xFFE9FFA8).copy(alpha = 0.30f),
                Color.Transparent,
            ),
            center = Offset(originX, originY),
            radius = 560f,
        )
    }
    Canvas(modifier.fillMaxSize()) {
        val ms = clock()
        if (ms <= 0f) return@Canvas

        // ---- slot line: bright core + two soft halos (fake glow, no blur filter) ----
        val glow = LaunchAnim.slotGlow(ms)
        if (glow > 0.01f) {
            val half = 104f + 26f * glow
            val core = T.lime.copy(alpha = 0.95f * glow)
            drawLine(T.lime.copy(alpha = 0.08f * glow), Offset(originX - half, originY), Offset(originX + half, originY), 20f, StrokeCap.Round)
            drawLine(T.lime.copy(alpha = 0.18f * glow), Offset(originX - half, originY), Offset(originX + half, originY), 8f, StrokeCap.Round)
            drawLine(core, Offset(originX - half, originY), Offset(originX + half, originY), 2.5f, StrokeCap.Round)
        }

        // ---- impact burst: short horizontal glow + expanding shockwave ----
        val since = ms - LaunchAnim.IMPACT
        if (since in 0f..240f) {
            val k = since / 240f
            val a = (1f - k) * (1f - k)
            // hot core: wide, flat ellipse sitting on the slot line
            drawOval(
                color = T.lime.copy(alpha = 0.30f * a),
                topLeft = Offset(originX - 130f, originY - 22f),
                size = Size(260f, 44f),
            )
            drawOval(
                color = Color(0xFFFFFBE0).copy(alpha = 0.55f * a),
                topLeft = Offset(originX - 70f, originY - 10f),
                size = Size(140f, 20f),
            )
            // shockwave: thin ring expanding sideways along the shelf line
            drawOval(
                color = T.lime.copy(alpha = 0.45f * a),
                topLeft = Offset(originX - (60f + 130f * k), originY - (10f + 26f * k)),
                size = Size(120f + 260f * k, 20f + 52f * k),
                style = Stroke(width = 2.5f),
            )
        }

        // ---- sparks ----
        val age = (ms - LaunchAnim.IMPACT) / 1000f
        if (age >= 0f && age <= LaunchAnim.SPARK_LIFE / 1000f) {
            for (s in SPARKS) {
                if (age >= s.life) continue
                val k = age / s.life
                val a = (1f - k) * (1f - k)
                val x = originX + s.x0 + s.vx * age
                val y = originY + s.y0 + s.vy * age + 0.5f * SPARK_GRAVITY * age * age
                if (y > 660f || x < -20f || x > 980f) continue

                // short motion streak behind the head (cheap, no blur)
                val prev = (age - 0.03f).coerceAtLeast(0f)
                val px = originX + s.x0 + s.vx * prev
                val py = originY + s.y0 + s.vy * prev + 0.5f * SPARK_GRAVITY * prev * prev

                val base = if (s.gold) Color(0xFFFFC94D) else T.lime
                drawLine(base.copy(alpha = 0.55f * a), Offset(px, py), Offset(x, y), s.radius * 0.9f, StrokeCap.Round)
                drawCircle(base.copy(alpha = 0.18f * a), radius = s.radius * 3.2f, center = Offset(x, y))
                drawCircle(base.copy(alpha = a), radius = s.radius, center = Offset(x, y))
            }
        }

        // ---- launch flash (covers the window switch) ----
        // Radial, not full-screen: a flat tint over the whole stage reads as a colour
        // glitch for the 2-3 frames it is visible; a hotspot on the slot reads as energy.
        val flash = LaunchAnim.flash(ms)
        if (flash > 0.01f) {
            drawRect(brush = flashBrush, alpha = 0.34f * flash)
        }
    }
}

/** Idle values for the rack when no launch is running. */
