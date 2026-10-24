package com.example.openassistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Настроение котёнка: от него зависят глаза, рот, уши и хвост. */
enum class Mood { NORMAL, HAPPY, LOVE, SLEEP, YAWN, EAT, THINK, LISTEN, DIZZY }

/**
 * Милый рыжий котёнок, нарисованный прямо на Canvas.
 * Сам дышит, моргает, виляет хвостом, шевелит ушами и открывает рот, когда говорит.
 * Прыжки, тряска и прочее делает MainActivity через обычные свойства View
 * (translationY, scaleX, ...), поэтому здесь только «внутренняя» жизнь.
 */
class CatView(context: Context) : View(context) {

    var mood: Mood = Mood.NORMAL
        set(value) { field = value; invalidate() }

    /** true — котёнок шевелит ртом (пока звучит голос) */
    @Volatile
    var talking: Boolean = false

    private val t0 = SystemClock.uptimeMillis()

    private val fur = Color.parseColor("#FF9E40")
    private val furDark = Color.parseColor("#E07B1A")
    private val cream = Color.parseColor("#FFE9C9")
    private val pink = Color.parseColor("#FFB3C1")
    private val noseColor = Color.parseColor("#F06292")
    private val ink = Color.parseColor("#3E2723")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        val s = min(width, height) / 200f
        canvas.save()
        canvas.scale(s, s)
        drawCat(canvas, t)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    // ---------- вспомогательное рисование ----------

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, outline: Boolean = false) {
        rect.set(l, t, r, b)
        fill.color = color
        c.drawOval(rect, fill)
        if (outline) {
            line.color = furDark
            line.strokeWidth = 2.5f
            c.drawOval(rect, line)
        }
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, sz: Float, color: Int) {
        path.reset()
        path.moveTo(cx, cy + sz * 0.9f)
        path.cubicTo(cx - sz * 1.4f, cy - sz * 0.1f, cx - sz * 0.6f, cy - sz * 1.1f, cx, cy - sz * 0.35f)
        path.cubicTo(cx + sz * 0.6f, cy - sz * 1.1f, cx + sz * 1.4f, cy - sz * 0.1f, cx, cy + sz * 0.9f)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun openEye(c: Canvas, x: Float, y: Float, k: Float, open: Float, dx: Float, dy: Float) {
        val h = 13f * k * open.coerceAtLeast(0.08f)
        val ox = x + dx
        val oy = y + dy
        oval(c, ox - 9.5f * k, oy - h, ox + 9.5f * k, oy + h, ink)
        if (open > 0.5f) {
            fill.color = Color.WHITE
            c.drawCircle(ox - 3f * k, oy - 5f * k, 3.4f * k, fill)
            c.drawCircle(ox + 3.5f * k, oy + 3.5f * k, 1.7f * k, fill)
        }
    }

    private fun happyEye(c: Canvas, x: Float, y: Float) {
        line.color = ink; line.strokeWidth = 4f
        path.reset()
        path.moveTo(x - 9f, y + 4f)
        path.quadTo(x, y - 10f, x + 9f, y + 4f)
        c.drawPath(path, line)
    }

    private fun sleepEye(c: Canvas, x: Float, y: Float) {
        line.color = ink; line.strokeWidth = 3.5f
        path.reset()
        path.moveTo(x - 9f, y - 1f)
        path.quadTo(x, y + 9f, x + 9f, y - 1f)
        c.drawPath(path, line)
    }

    private fun squintEye(c: Canvas, x: Float, y: Float, dir: Int) {
        val s = dir.toFloat()
        line.color = ink; line.strokeWidth = 3.5f
        path.reset()
        path.moveTo(x - 8f * s, y - 7f)
        path.lineTo(x + 5f * s, y)
        path.lineTo(x - 8f * s, y + 7f)
        c.drawPath(path, line)
    }

    private fun spiralEye(c: Canvas, x: Float, y: Float, t: Float) {
        line.color = ink; line.strokeWidth = 2.5f
        path.reset()
        for (i in 0..40) {
            val a = i / 40f * 6f * PI.toFloat()
            val r = a * 0.55f
            val ang = a + t * 8f
            val px = x + cos(ang) * r
            val py = y + sin(ang) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        c.drawPath(path, line)
    }

    private fun earFlick(t: Float): Float {
        val p = t % 5.5f
        return if (p in 4.8f..5.2f) sin((p - 4.8f) / 0.4f * PI.toFloat() * 2f) * 5f else 0f
    }

    // ---------- сам котёнок ----------

    private fun drawCat(c: Canvas, t: Float) {
        val m = mood
        val sleeping = m == Mood.SLEEP
        val breath = sin(t * (if (sleeping) 1.4f else 2.2f)) * (if (sleeping) 2.2f else 1.4f)

        // хвост
        val wagSpeed = when (m) {
            Mood.HAPPY, Mood.LOVE, Mood.EAT -> 5.5f
            Mood.SLEEP -> 0.8f
            else -> 2.2f
        }
        val swing = sin(t * wagSpeed) * (if (sleeping) 6f else 20f)
        val tipX = 172f + swing
        val tipY = 98f + abs(swing) * 0.2f
        line.color = fur; line.strokeWidth = 15f
        path.reset()
        path.moveTo(135f, 168f)
        path.cubicTo(186f, 172f, 196f + swing * 0.4f, 130f, tipX, tipY)
        c.drawPath(path, line)
        fill.color = cream
        c.drawCircle(tipX, tipY, 8.5f, fill)

        // тело, пузико, лапки
        oval(c, 58f, 112f + breath * 0.5f, 142f, 190f, fur, true)
        oval(c, 76f, 138f, 124f, 188f, cream)
        oval(c, 66f, 176f, 94f, 194f, cream, true)
        oval(c, 106f, 176f, 134f, 194f, cream, true)

        // голова
        c.save()
        val tilt = when (m) {
            Mood.SLEEP -> -13f
            Mood.THINK -> 7f
            Mood.YAWN -> 0f
            else -> sin(t * 1.2f) * 1.8f
        }
        c.translate(0f, (if (sleeping) 12f else 0f) + breath * 0.4f)
        c.rotate(tilt, 100f, 128f)

        // уши
        val flick = earFlick(t)
        val up = when (m) {
            Mood.LISTEN -> -9f
            Mood.SLEEP -> 7f
            else -> 0f
        }
        for (side in intArrayOf(-1, 1)) {
            val sx = side.toFloat()
            val shift = if (side == 1) flick else 0f
            path.reset()
            path.moveTo(100f + sx * 50f, 68f)
            path.lineTo(100f + sx * 40f + shift, 12f + up)
            path.lineTo(100f + sx * 12f, 44f)
            path.close()
            fill.color = fur
            c.drawPath(path, fill)
            line.color = furDark; line.strokeWidth = 2.5f
            c.drawPath(path, line)
            path.reset()
            path.moveTo(100f + sx * 43f, 62f)
            path.lineTo(100f + sx * 38f + shift, 27f + up)
            path.lineTo(100f + sx * 21f, 46f)
            path.close()
            fill.color = pink
            c.drawPath(path, fill)
        }

        oval(c, 45f, 40f, 155f, 134f, fur, true)

        // полоски на лбу
        line.color = furDark; line.strokeWidth = 3.5f
        c.drawLine(100f, 42f, 100f, 56f, line)
        c.drawLine(90f, 43.5f, 92f, 55f, line)
        c.drawLine(110f, 43.5f, 108f, 55f, line)

        // мордочка и щёчки
        oval(c, 76f, 92f, 124f, 120f, cream)
        val blush = if (m == Mood.HAPPY || m == Mood.LOVE || m == Mood.EAT) 140 else 70
        fill.color = Color.argb(blush, 255, 140, 165)
        c.drawCircle(64f, 100f, 8f, fill)
        c.drawCircle(136f, 100f, 8f, fill)

        // глаза
        val ey = 80f
        val lx = 78f
        val rx = 122f
        val blink = run {
            val b = t % 3.8f
            if (b > 3.6f) 1f - sin((b - 3.6f) / 0.2f * PI.toFloat()) else 1f
        }
        when (m) {
            Mood.HAPPY, Mood.EAT -> { happyEye(c, lx, ey); happyEye(c, rx, ey) }
            Mood.LOVE -> {
                val k = 1f + 0.12f * sin(t * 9f)
                heart(c, lx, ey, 9f * k, Color.parseColor("#E91E63"))
                heart(c, rx, ey, 9f * k, Color.parseColor("#E91E63"))
            }
            Mood.SLEEP -> { sleepEye(c, lx, ey + 4f); sleepEye(c, rx, ey + 4f) }
            Mood.YAWN -> { squintEye(c, lx, ey, 1); squintEye(c, rx, ey, -1) }
            Mood.DIZZY -> { spiralEye(c, lx, ey, t); spiralEye(c, rx, ey, t) }
            else -> {
                val big = if (m == Mood.LISTEN) 1.15f else 1f
                var dx = sin(t * 0.7f) * 1.5f
                var dy = 0f
                if (m == Mood.THINK) { dx = 4f; dy = -5f }
                openEye(c, lx, ey, big, blink, dx, dy)
                openEye(c, rx, ey, big, blink, dx, dy)
            }
        }

        // носик
        path.reset()
        path.moveTo(94f, 96f)
        path.lineTo(106f, 96f)
        path.lineTo(100f, 103f)
        path.close()
        fill.color = noseColor
        c.drawPath(path, fill)

        // ротик
        val o = when {
            talking -> abs(sin(t * 13f))
            m == Mood.YAWN -> 1f
            m == Mood.EAT -> abs(sin(t * 9f)) * 0.8f
            m == Mood.HAPPY || m == Mood.LOVE -> 0.45f
            else -> 0f
        }
        if (o > 0.08f) {
            val h = 4f + o * 13f
            val w = 6f + o * 3f
            rect.set(100f - w, 104f, 100f + w, 104f + h)
            fill.color = Color.parseColor("#7B1F2B")
            c.drawOval(rect, fill)
            if (o > 0.4f) {
                rect.set(100f - w * 0.55f, 104f + h * 0.55f, 100f + w * 0.55f, 104f + h - 0.5f)
                fill.color = Color.parseColor("#FF8A9B")
                c.drawOval(rect, fill)
            }
        } else {
            line.color = ink; line.strokeWidth = 2.2f
            path.reset()
            path.moveTo(100f, 103f)
            path.quadTo(94f, 113f, 86f, 105f)
            path.moveTo(100f, 103f)
            path.quadTo(106f, 113f, 114f, 105f)
            c.drawPath(path, line)
        }

        // усики
        line.color = Color.parseColor("#8D6E63"); line.strokeWidth = 1.8f
        val wig = if (talking) sin(t * 13f) * 1.5f else 0f
        for (sx in intArrayOf(-1, 1)) {
            for (k in -1..1) {
                c.drawLine(100f + sx * 26f, 104f + k * 4f, 100f + sx * 62f, 104f + k * 9f + wig, line)
            }
        }
        c.restore()

        // сердечки над головой
        if (m == Mood.LOVE) {
            for (i in 0..2) {
                val p = (t * 0.7f + i / 3f) % 1f
                val hx = 100f + (i - 1) * 34f + sin(p * 6f + i) * 6f
                val hy = 42f - p * 40f
                heart(c, hx, hy, 6f, Color.argb(((1f - p) * 255).toInt(), 233, 30, 99))
            }
        }
    }
}
