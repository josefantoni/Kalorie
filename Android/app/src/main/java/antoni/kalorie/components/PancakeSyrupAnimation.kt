package antoni.kalorie.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val LOOP_SECONDS = 9.0

@Composable
fun PancakeSyrupAnimation(modifier: Modifier = Modifier) {
    // MARK: - Properties

    val time = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        time.animateTo(LOOP_SECONDS.toFloat(), tween(durationMillis = 9000, easing = LinearEasing))
    }

    // MARK: - Body

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clipToBounds()
            .graphicsLayer {
                alpha = fadeAt(time.value.toDouble()).toFloat()
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        drawPancakeSyrupFrame(time.value)
    }
}

private fun DrawScope.drawPancakeSyrupFrame(timeSeconds: Float) {
    val t = timeSeconds.toDouble().coerceIn(0.0, LOOP_SECONDS)
    val s = min(size.width, size.height) / 400f
    withTransform({
        translate((size.width - 400f * s) / 2f, (size.height - 400f * s) / 2f)
        scale(s, s, Offset.Zero)
    }) {
        drawScene(t)
    }
}

private object Pal {
    val syrup = Color(0xFF8C4210)
    val syrupHi = Color(0xFFC9772A)
    val panTop = Color(0xFFE3A444)
    val panSide = Color(0xFFECC983)
    val panLine = Color(0xFFA2641C)
    val panSpot = Color(0xFFCF8C2E)
    val panDot = Color(0xFFA8691F)
    val plateTop = Color(0xFFFFFFFF)
    val plateBack = Color(0xFFE9E6DC)
    val plateLine = Color(0xFFC9C4B6)
    val plateWell = Color(0xFFDCD8CC)
    val shadow = Color(0xFFD6D1C3)
    val bottle = Color(0xFFE08A2B)
    val bottleLine = Color(0xFF8A4B0D)
    val bottleHi = Color(0xFFF5B862)
    val bottleMouth = Color(0xFF5A2A08)
    val bottleCollar = Color(0xFF7A3D0C)
    val label = Color(0xFFF7ECD2)
    val labelDot = Color(0xFFB3361A)
}

private class Spline(private val x1: Double, private val y1: Double, private val x2: Double, private val y2: Double) {
    private val isLinear = x1 == y1 && x2 == y2

    fun ease(x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        if (isLinear) return x
        var lo = 0.0
        var hi = 1.0
        repeat(BISECTION_STEPS) {
            val mid = (lo + hi) / 2
            if (bezier(mid, x1, x2) < x) lo = mid else hi = mid
        }
        return bezier((lo + hi) / 2, y1, y2)
    }

    private fun bezier(s: Double, c1: Double, c2: Double): Double {
        val u = 1 - s
        return 3 * u * u * s * c1 + 3 * u * s * s * c2 + s * s * s
    }

    companion object {
        const val BISECTION_STEPS = 32
        val Linear = Spline(0.0, 0.0, 1.0, 1.0)
    }
}

private class Track(
    private val keys: DoubleArray,
    private val values: DoubleArray,
    private val splines: List<Spline>? = null,
) {
    init {
        require(keys.size == values.size)
    }

    fun value(t: Double): Double {
        if (t <= keys[0]) return values[0]
        if (t >= keys[keys.size - 1]) return values[values.size - 1]
        var i = 0
        while (i < keys.size - 2 && t >= keys[i + 1]) i++
        val k0 = keys[i]
        val k1 = keys[i + 1]
        val p = if (k1 > k0) (t - k0) / (k1 - k0) else 1.0
        val e = splines?.get(i)?.ease(p) ?: p
        return values[i] + (values[i + 1] - values[i]) * e
    }
}

private fun track(keys: DoubleArray, values: DoubleArray, splines: List<Spline>? = null) = Track(keys, values, splines)

private fun clamp01(x: Double): Double = min(1.0, max(0.0, x))

private fun DrawScope.oval(
    color: Color,
    cx: Double,
    cy: Double,
    rx: Double,
    ry: Double,
    alpha: Float = 1f,
    style: DrawStyle = Fill,
) {
    drawOval(
        color = color,
        topLeft = Offset((cx - rx).toFloat(), (cy - ry).toFloat()),
        size = Size((2 * rx).toFloat(), (2 * ry).toFloat()),
        alpha = alpha,
        style = style,
    )
}

private fun ovalPath(cx: Double, cy: Double, rx: Double, ry: Double): Path = Path().apply {
    addOval(Rect((cx - rx).toFloat(), (cy - ry).toFloat(), (cx + rx).toFloat(), (cy + ry).toFloat()))
}

private fun polygon(xs: DoubleArray, ys: DoubleArray): Path = Path().apply {
    moveTo(xs[0].toFloat(), ys[0].toFloat())
    for (i in 1 until xs.size) lineTo(xs[i].toFloat(), ys[i].toFloat())
    close()
}

private inline fun DrawScope.translated(x: Double, y: Double, crossinline block: DrawScope.() -> Unit) {
    withTransform({ translate(x.toFloat(), y.toFloat()) }) { block() }
}

private val FADE = track(doubleArrayOf(0.0, 7.5, 8.4, 9.0), doubleArrayOf(1.0, 1.0, 0.0, 0.0))

private fun fadeAt(t: Double): Double = FADE.value(t)

private val PLATE_X = track(
    doubleArrayOf(0.0, 0.6, 9.0),
    doubleArrayOf(420.0, 0.0, 0.0),
    listOf(Spline(0.2, 0.7, 0.2, 1.0), Spline.Linear),
)

private fun dropTrack(ts: Double, tl: Double) = track(
    doubleArrayOf(0.0, ts, tl, tl + 0.06, tl + 0.12, 9.0),
    doubleArrayOf(-330.0, -330.0, 0.0, -5.0, 0.0, 0.0),
    listOf(
        Spline.Linear,
        Spline(0.5, 0.0, 1.0, 1.0),
        Spline(0.0, 0.4, 0.4, 1.0),
        Spline(0.6, 0.0, 1.0, 1.0),
        Spline.Linear,
    ),
)

private val DROP_BOTTOM = dropTrack(0.5, 1.0)
private val DROP_TOP = dropTrack(0.95, 1.45)

private val SHADOW_ALPHA = track(doubleArrayOf(0.0, 0.5, 1.0, 9.0), doubleArrayOf(0.0, 0.0, 0.85, 0.85))

private val POOL_SCALE = track(
    doubleArrayOf(0.0, 2.25, 2.75, 3.2, 3.6, 8.91, 9.0),
    doubleArrayOf(0.0, 0.0, 0.2, 1.05, 1.0, 1.0, 0.0),
)

private val BOTTLE_KEYS = doubleArrayOf(0.0, 1.4, 1.8, 2.05, 2.2, 3.3, 4.0, 9.0)
private val BOTTLE_SPLINES = listOf(
    Spline.Linear,
    Spline(0.3, 0.0, 0.2, 1.0),
    Spline(0.5, 0.0, 0.3, 1.0),
    Spline(0.3, 0.0, 0.3, 1.0),
    Spline.Linear,
    Spline(0.3, 0.0, 0.2, 1.0),
    Spline.Linear,
)
private val BOTTLE_X = track(BOTTLE_KEYS, doubleArrayOf(480.0, 480.0, 250.0, 250.0, 250.0, 250.0, 480.0, 480.0), BOTTLE_SPLINES)
private val BOTTLE_Y = track(BOTTLE_KEYS, doubleArrayOf(0.0, 0.0, 130.0, 130.0, 130.0, 130.0, 0.0, 0.0), BOTTLE_SPLINES)
private val BOTTLE_ROT = track(BOTTLE_KEYS, doubleArrayOf(0.0, 0.0, 12.0, -140.0, -130.0, -130.0, 0.0, 0.0), BOTTLE_SPLINES)

private val DASH_ALPHA = track(
    doubleArrayOf(0.0, 2.15, 2.25, 2.45, 2.55, 2.65, 2.8, 9.0),
    doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 0.0),
)
private val SPLASH_ALPHA = track(
    doubleArrayOf(0.0, 2.75, 2.85, 3.3, 3.4, 9.0),
    doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0),
)

private val BITE_CUTS = doubleArrayOf(4.5, 5.3, 6.1)
private val BITE_X = doubleArrayOf(240.0, 165.0, 0.0)
private val SCALLOPS = listOf(262.0 to 24.0, 298.0 to 22.0)

private val BITE_SHAPES: List<List<Path>> = (0 until 3).map { k ->
    val xb = BITE_X[k]
    val shapes = ArrayList<Path>()
    shapes += Path().apply { addRect(Rect(xb.toFloat(), 225f, 440f, 355f)) }
    if (k < 2) {
        for ((y, r) in SCALLOPS) shapes += ovalPath(xb, y, r, r)
    }
    shapes
}

private class Cake(val cx: Double, val cy: Double, val rx: Double, val ry: Double, phase: Double) {
    val path: Path

    init {
        val n = 72
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        for (i in 0 until n) {
            val th = 2 * Math.PI * i / n
            val rf = 1 + 0.018 * sin(3 * th + phase) + 0.012 * sin(5 * th - 2 * phase)
            xs[i] = cx + rx * rf * cos(th)
            ys[i] = cy + ry * rf * sin(th)
        }
        path = polygon(xs, ys)
    }
}

private val BOTTOM_CAKE = Cake(200.0, 283.0, 106.0, 24.0, 0.6)
private val TOP_CAKE = Cake(192.0, 271.0, 94.0, 21.0, 2.1)
private const val CAKE_THICKNESS = 5

private class Mottle(val cx: Double, val cy: Double, val rx: Double, val ry: Double)
private class Dot(val cx: Double, val cy: Double, val r: Double)

private val MOTTLES = listOf(
    Mottle(-9.0, 2.4, 10.0, 3.2), Mottle(80.6, -1.4, 15.0, 3.2), Mottle(-59.9, 0.5, 12.0, 3.2),
    Mottle(24.7, 11.9, 17.0, 3.2), Mottle(-77.1, -8.0, 14.0, 3.2), Mottle(29.2, 4.7, 11.0, 3.2),
    Mottle(5.4, -17.8, 16.0, 3.2), Mottle(-58.9, -10.4, 13.0, 3.2), Mottle(-11.3, 13.9, 10.0, 3.2),
)
private val DOTS = listOf(
    Dot(3.6, 5.7, 1.50), Dot(-8.1, -9.0, 1.85), Dot(64.6, 8.4, 2.20), Dot(-35.1, -10.9, 1.50),
    Dot(50.6, -4.0, 1.85), Dot(65.9, -4.6, 2.20), Dot(-81.1, 5.2, 1.50), Dot(52.9, -9.3, 1.85),
    Dot(56.4, -13.0, 2.20), Dot(11.3, -2.1, 1.50), Dot(-58.8, 9.4, 1.85), Dot(-72.9, -3.2, 2.20),
    Dot(-37.2, 15.6, 1.50), Dot(-55.0, -4.3, 1.85), Dot(-48.8, 4.1, 2.20), Dot(-24.4, -1.9, 1.50),
    Dot(14.2, 14.8, 1.85), Dot(-47.6, -12.6, 2.20), Dot(72.6, 4.2, 1.50), Dot(-14.9, -16.0, 1.85),
    Dot(-46.2, 13.1, 2.20), Dot(18.3, -8.4, 1.50), Dot(13.7, -14.9, 1.85), Dot(-3.4, 9.3, 2.20),
    Dot(-80.7, 1.9, 1.50), Dot(45.0, 8.2, 1.85), Dot(-28.2, 7.5, 2.20),
)

private const val P0X = 250.0
private const val P0Y = 130.0
private const val P1X = 237.0
private const val P1Y = 141.0
private const val P2X = 208.0
private const val P2Y = 205.0
private const val P3X = 199.0
private const val P3Y = 272.0

private fun bezX(s: Double): Double {
    val u = 1 - s
    return u * u * u * P0X + 3 * u * u * s * P1X + 3 * u * s * s * P2X + s * s * s * P3X
}

private fun bezY(s: Double): Double {
    val u = 1 - s
    return u * u * u * P0Y + 3 * u * u * s * P1Y + 3 * u * s * s * P2Y + s * s * s * P3Y
}

private fun dbezX(s: Double): Double {
    val u = 1 - s
    return 3 * u * u * (P1X - P0X) + 6 * u * s * (P2X - P1X) + 3 * s * s * (P3X - P2X)
}

private fun dbezY(s: Double): Double {
    val u = 1 - s
    return 3 * u * u * (P1Y - P0Y) + 6 * u * s * (P2Y - P1Y) + 3 * s * s * (P3Y - P2Y)
}

private const val ARC_N = 1000
private val ARC_X = DoubleArray(ARC_N + 1) { bezX(it.toDouble() / ARC_N) }
private val ARC_Y = DoubleArray(ARC_N + 1) { bezY(it.toDouble() / ARC_N) }
private val ARC_CUM = DoubleArray(ARC_N + 1).also { cum ->
    for (i in 1..ARC_N) cum[i] = cum[i - 1] + hypot(ARC_X[i] - ARC_X[i - 1], ARC_Y[i] - ARC_Y[i - 1])
}

private fun pointAtArc(f: Double): Pair<Double, Double> {
    val target = clamp01(f) * ARC_CUM[ARC_N]
    var lo = 0
    var hi = ARC_N
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (ARC_CUM[mid] < target) lo = mid + 1 else hi = mid
    }
    if (lo == 0) return ARC_X[0] to ARC_Y[0]
    val seg = ARC_CUM[lo] - ARC_CUM[lo - 1]
    val q = if (seg > 0) (target - ARC_CUM[lo - 1]) / seg else 0.0
    return (ARC_X[lo - 1] + (ARC_X[lo] - ARC_X[lo - 1]) * q) to
        (ARC_Y[lo - 1] + (ARC_Y[lo] - ARC_Y[lo - 1]) * q)
}

private val ARC_Y_STEPS = doubleArrayOf(
    130.0, 142.13, 155.56, 169.44, 183.6, 197.94, 212.5, 227.19, 242.03, 256.95, 272.0,
)

private fun arcYLerp(f: Double): Double {
    val x = clamp01(f) * 10
    val i = min(9, x.toInt())
    return ARC_Y_STEPS[i] + (ARC_Y_STEPS[i + 1] - ARC_Y_STEPS[i]) * (x - i)
}

private fun DrawScope.drawScene(t: Double) {
    drawPlate(t)

    val clips = ArrayList<Path>()
    for (k in 0 until 3) if (t >= BITE_CUTS[k]) clips += BITE_SHAPES[k]
    clipOutAll(clips, 0) { drawFood(t) }

    drawCrumbs(t)
    drawPour(t)
    drawBottle(t)
}

private fun DrawScope.clipOutAll(paths: List<Path>, i: Int, block: DrawScope.() -> Unit) {
    if (i >= paths.size) {
        block()
    } else {
        clipPath(paths[i], ClipOp.Difference) { clipOutAll(paths, i + 1, block) }
    }
}

private fun DrawScope.drawPlate(t: Double) {
    translated(PLATE_X.value(t), 0.0) {
        val plateStroke = Stroke(width = 3.5f)
        oval(Pal.plateBack, 200.0, 314.0, 150.0, 36.0)
        oval(Pal.plateLine, 200.0, 314.0, 150.0, 36.0, style = plateStroke)
        oval(Pal.plateTop, 200.0, 304.0, 150.0, 36.0)
        oval(Pal.plateLine, 200.0, 304.0, 150.0, 36.0, style = plateStroke)
        oval(Pal.plateWell, 200.0, 304.0, 124.0, 28.0, style = Stroke(width = 3f))
    }
}

private fun DrawScope.drawFood(t: Double) {
    val sa = SHADOW_ALPHA.value(t)
    if (sa > 0) oval(Pal.shadow, 202.0, 292.0, 112.0, 27.0, alpha = sa.toFloat())
    drawPancake(BOTTOM_CAKE, DROP_BOTTOM.value(t))
    drawPancake(TOP_CAKE, DROP_TOP.value(t))
    drawSyrup(t)
}

private fun DrawScope.drawPancake(cake: Cake, dropY: Double) {
    translated(0.0, dropY) {
        val outlineStyle = Stroke(width = 4.5f, join = StrokeJoin.Round)
        for (k in CAKE_THICKNESS downTo 0) {
            translated(0.0, k.toDouble()) { drawPath(cake.path, Pal.panLine, style = outlineStyle) }
        }
        for (k in CAKE_THICKNESS downTo 0) {
            translated(0.0, k.toDouble()) { drawPath(cake.path, Pal.panSide) }
        }
        drawPath(cake.path, Pal.panTop)
        drawPath(cake.path, Pal.panLine, style = Stroke(width = 2.5f, join = StrokeJoin.Round))

        clipPath(cake.path) {
            withTransform({
                translate(cake.cx.toFloat(), cake.cy.toFloat())
                scale((cake.rx / 100.0).toFloat(), (cake.ry / 22.0).toFloat(), Offset.Zero)
            }) {
                for (m in MOTTLES) oval(Pal.panSpot, m.cx, m.cy, m.rx, m.ry, alpha = 0.8f)
                for (d in DOTS) oval(Pal.panDot, d.cx, d.cy, d.r, d.r)
            }
        }
    }
}

private val TOP_CLIP = ovalPath(192.0, 271.0, 88.0, 17.0)
private val RIPPLES = listOf(2.8 to 3.2, 3.0 to 3.4, 3.2 to 3.6)

private class Drip(val x: Double, val ts: Double, val te: Double, val len: Double)

private val DRIPS = listOf(Drip(165.0, 3.0, 3.9, 20.0), Drip(200.0, 3.1, 3.7, 14.0), Drip(232.0, 3.1, 4.0, 24.0))
private val DRIP_EASE = Spline(0.35, 0.0, 0.65, 1.0)

private fun poolBlob(phase: Double): Path {
    val n = 56
    val xs = DoubleArray(n)
    val ys = DoubleArray(n)
    for (i in 0 until n) {
        val th = 2 * Math.PI * i / n
        val rf = 1 + 0.035 * sin(3 * th + 2 * Math.PI * phase) + 0.025 * sin(5 * th - 4 * Math.PI * phase)
        xs[i] = 62 * rf * cos(th)
        ys[i] = 11 * rf * sin(th)
    }
    return polygon(xs, ys)
}

private fun DrawScope.drawSyrup(t: Double) {
    clipPath(TOP_CLIP) {
        val ps = POOL_SCALE.value(t)
        if (ps > 0.001) {
            withTransform({
                translate(198f, 273f)
                scale(ps.toFloat(), ps.toFloat(), Offset.Zero)
            }) {
                drawPath(poolBlob((t % 1.4) / 1.4), Pal.syrup)
                val hl = Path().apply {
                    moveTo(-30f, -2f)
                    quadraticBezierTo(-12f, -7f, 8f, -5f)
                }
                drawPath(hl, Pal.syrupHi, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
        }
        for ((a, b) in RIPPLES) {
            if (t > a && t < b) {
                val q = (t - a) / (b - a)
                oval(
                    Pal.syrupHi,
                    199.0,
                    272.0,
                    34 * q,
                    6.5 * q,
                    alpha = (0.7 * (1 - q)).toFloat(),
                    style = Stroke(width = 2f),
                )
            }
        }
    }

    for (d in DRIPS) {
        if (t < d.ts) continue
        val u = (d.x - 198.0) / 62.0
        val y0 = 273.0 + 11.0 * sqrt(max(0.0, 1 - u * u)) - 2.0
        val q = DRIP_EASE.ease(clamp01((t - d.ts) / (d.te - d.ts)))
        val len = d.len * q
        val r = 5.0 * clamp01((t - d.ts) / 0.1)
        drawRect(
            Pal.syrup,
            topLeft = Offset((d.x - 4).toFloat(), y0.toFloat()),
            size = Size(8f, max(len, 0.001).toFloat()),
        )
        drawCircle(Pal.syrup, radius = r.toFloat(), center = Offset(d.x.toFloat(), (y0 + len).toFloat()))
    }
}

private class Crumb(val x: Double, val y: Double, val size: Double, val bite: Int, val spot: Boolean)

private val CRUMBS = listOf(
    Crumb(268.0, 307.0, 1.2, 0, false),
    Crumb(287.0, 314.0, 0.9, 0, true),
    Crumb(212.0, 311.0, 1.1, 1, false),
    Crumb(188.0, 318.0, 0.8, 1, true),
    Crumb(150.0, 309.0, 1.0, 2, false),
    Crumb(124.0, 315.0, 1.2, 2, true),
    Crumb(162.0, 322.0, 0.8, 2, false),
)
private val CRUMB_PATH = polygon(
    doubleArrayOf(-3.0, -1.0, 2.0, 3.0, 0.0, -3.0),
    doubleArrayOf(-1.0, -3.0, -2.0, 1.0, 3.0, 2.0),
)

private fun DrawScope.drawCrumbs(t: Double) {
    for (c in CRUMBS) {
        val tin = BITE_CUTS[c.bite] + 0.08
        val fin = c.size * 1.35
        val sc = track(
            doubleArrayOf(0.0, tin, tin + 0.08, tin + 0.16, 9.0),
            doubleArrayOf(0.0, 0.0, fin * 1.3, fin, fin),
        ).value(t)
        if (sc <= 0.001) continue
        withTransform({
            translate(c.x.toFloat(), c.y.toFloat())
            scale(sc.toFloat(), sc.toFloat(), Offset.Zero)
        }) {
            drawPath(CRUMB_PATH, if (c.spot) Pal.panSpot else Pal.panTop)
            drawPath(CRUMB_PATH, Pal.panLine, style = Stroke(width = 1f, join = StrokeJoin.Round))
        }
    }
}

private const val FALL0 = 2.35
private const val FALL1 = 2.75
private const val RET0 = 3.3
private const val RET1 = 3.5
private const val HEAD_START = 2.35 + 0.0005 * 9

private fun streamPath(phase: Double): Path {
    val n = 44
    val lx = DoubleArray(n + 1)
    val ly = DoubleArray(n + 1)
    val rx = DoubleArray(n + 1)
    val ry = DoubleArray(n + 1)
    val b = 0.12 + 0.76 * phase
    val amp = sin(Math.PI * phase)
    for (i in 0..n) {
        val s = i.toDouble() / n
        var cx = bezX(s)
        var cy = bezY(s)
        val dx = dbezX(s)
        val dy = dbezY(s)
        val ln = hypot(dx, dy)
        val nx = -dy / ln
        val ny = dx / ln
        var w = 15 - 5 * s.pow(0.8)
        w += 1.0 * sin(2 * Math.PI * (1.6 * s - phase))
        w += 0.6 * sin(2 * Math.PI * (3.1 * s - 2 * phase))
        w += 2.6 * amp * exp(-((s - b) * (s - b)) / 0.006)
        val sway = 0.9 * s * sin(2 * Math.PI * (1.1 * s - phase))
        cx += nx * sway
        cy += ny * sway
        lx[i] = cx + nx * w / 2
        ly[i] = cy + ny * w / 2
        rx[i] = cx - nx * w / 2
        ry[i] = cy - ny * w / 2
    }
    val xs = DoubleArray(2 * (n + 1))
    val ys = DoubleArray(2 * (n + 1))
    for (i in 0..n) {
        xs[i] = lx[i]
        ys[i] = ly[i]
        xs[n + 1 + i] = rx[n - i]
        ys[n + 1 + i] = ry[n - i]
    }
    return polygon(xs, ys)
}

private val FALL_EASE = Spline(0.55, 0.0, 1.0, 0.9)

private class FallingDrop(val ts: Double, val dur: Double, val r: Double)

private val FALLING_DROPS = listOf(FallingDrop(2.0, 0.3, 5.0), FallingDrop(2.175, 0.275, 4.2))

private class SplashDrop(val dx: Double, val h: Double, val r: Double, val dur: Double, val begin: Double)

private val SPLASH_DROPS = listOf(
    SplashDrop(-22.0, 10.0, 3.0, 0.6, 0.0),
    SplashDrop(-10.0, 14.0, 3.6, 0.55, 0.15),
    SplashDrop(10.0, 14.0, 3.6, 0.55, 0.08),
    SplashDrop(22.0, 10.0, 3.0, 0.6, 0.25),
)

private fun DrawScope.drawPour(t: Double) {
    if (t >= FALL0 && t < RET1) {
        val bottom = if (t < FALL1) arcYLerp((t - FALL0) / (FALL1 - FALL0)) else 272.0
        val top = if (t < RET0) 0.0 else arcYLerp((t - RET0) / (RET1 - RET0))
        if (bottom - top > 0.01) {
            clipRect(0f, top.toFloat(), 400f, bottom.toFloat()) {
                drawPath(streamPath((t % 0.9) / 0.9), Pal.syrup)
            }
        }
    }

    if (t >= FALL0 && t < FALL1 + 0.025) {
        val f = clamp01((t - HEAD_START) / (FALL1 - HEAD_START))
        val (px, py) = pointAtArc(f)
        val r = 9.0 + (6.5 - 9.0) * f
        val a = when {
            t < FALL0 + 0.01 -> (t - FALL0) / 0.01
            t <= FALL1 -> 1.0
            else -> 1 - (t - FALL1) / 0.025
        }
        drawCircle(Pal.syrup, radius = r.toFloat(), center = Offset(px.toFloat(), py.toFloat()), alpha = clamp01(a).toFloat())
    }

    for (d in FALLING_DROPS) {
        val te = d.ts + d.dur
        if (t < d.ts || t > te + 0.02) continue
        val e = track(
            doubleArrayOf(0.0, d.ts, te, 9.0),
            doubleArrayOf(0.0, 0.0, 1.0, 1.0),
            listOf(Spline.Linear, FALL_EASE, Spline.Linear),
        ).value(t)
        val (px, py) = pointAtArc(e)
        val a = if (t <= te) 1.0 else 1 - (t - te) / 0.02
        drawCircle(Pal.syrup, radius = d.r.toFloat(), center = Offset(px.toFloat(), py.toFloat()), alpha = clamp01(a).toFloat())
    }

    val sa = SPLASH_ALPHA.value(t)
    if (sa > 0.001) {
        clipPath(TOP_CLIP) {
            for (d in SPLASH_DROPS) {
                val local = t - d.begin
                if (local < 0) continue
                val p = (local % d.dur) / d.dur
                val x4 = p * 4
                val i = min(3, x4.toInt())
                val f = x4 - i
                val q0 = i / 4.0
                val q1 = (i + 1) / 4.0
                val x0 = d.dx * q0
                val y0 = -4 * d.h * q0 * (1 - q0)
                val x1 = d.dx * q1
                val y1 = -4 * d.h * q1 * (1 - q1)
                val ox = x0 + (x1 - x0) * f
                val oy = y0 + (y1 - y0) * f
                val alpha = if (p < 0.25) {
                    p * 4
                } else if (p <= 0.75) {
                    1.0
                } else {
                    (1 - p) * 4
                }
                drawCircle(
                    Pal.syrup,
                    radius = d.r.toFloat(),
                    center = Offset((199 + ox).toFloat(), (270 + oy).toFloat()),
                    alpha = (clamp01(alpha) * sa).toFloat(),
                )
            }
        }
    }

    val da = DASH_ALPHA.value(t)
    if (da > 0.001) {
        val dashStyle = Stroke(width = 3f, cap = StrokeCap.Round)
        val dash = Path().apply {
            moveTo(237f, 124f)
            lineTo(230f, 119f)
            moveTo(243f, 115f)
            lineTo(240f, 108f)
            moveTo(232f, 134f)
            lineTo(224f, 133f)
        }
        drawPath(dash, Pal.syrupHi, alpha = da.toFloat(), style = dashStyle)
    }
}

private val BOTTLE_BODY = Path().apply {
    moveTo(-9f, 0f)
    lineTo(9f, 0f)
    lineTo(9f, 24f)
    cubicTo(9f, 34f, 32f, 38f, 32f, 62f)
    lineTo(32f, 106f)
    quadraticBezierTo(32f, 120f, 18f, 120f)
    lineTo(-18f, 120f)
    quadraticBezierTo(-32f, 120f, -32f, 106f)
    lineTo(-32f, 62f)
    cubicTo(-32f, 38f, -9f, 34f, -9f, 24f)
    close()
}

private fun DrawScope.drawBottle(t: Double) {
    val x = BOTTLE_X.value(t)
    if (x >= 470) return
    withTransform({
        translate(x.toFloat(), BOTTLE_Y.value(t).toFloat())
        rotate(BOTTLE_ROT.value(t).toFloat(), Offset.Zero)
    }) {
        drawPath(BOTTLE_BODY, Pal.bottle)
        drawPath(BOTTLE_BODY, Pal.bottleLine, style = Stroke(width = 3f, join = StrokeJoin.Round))

        drawLine(
            Pal.bottleHi,
            start = Offset(-23f, 70f),
            end = Offset(-23f, 104f),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
        oval(Pal.bottleMouth, 0.0, 0.0, 9.0, 2.6)
        drawRoundRect(
            Pal.bottleCollar,
            topLeft = Offset(-12f, 20f),
            size = Size(24f, 6f),
            cornerRadius = CornerRadius(3f, 3f),
        )
        drawRoundRect(
            Pal.label,
            topLeft = Offset(-20f, 66f),
            size = Size(40f, 36f),
            cornerRadius = CornerRadius(7f, 7f),
        )
        drawRoundRect(
            Pal.bottleLine,
            topLeft = Offset(-20f, 66f),
            size = Size(40f, 36f),
            cornerRadius = CornerRadius(7f, 7f),
            style = Stroke(width = 2.5f),
        )
        drawCircle(Pal.labelDot, radius = 8f, center = Offset(0f, 84f))
    }
}
