package com.lancesrealm.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.lancesrealm.core.NEUTRAL

fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

/** A sixteen-colour palette in the spirit of 8-bit home computers. */
object Palette {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val RED = 0xFF883932.toInt()
    const val CYAN = 0xFF67B6BD.toInt()
    const val PURPLE = 0xFF8B3F96.toInt()
    const val GREEN = 0xFF55A049.toInt()
    const val BLUE = 0xFF40318D.toInt()
    const val YELLOW = 0xFFBFCE72.toInt()
    const val ORANGE = 0xFF8B5429.toInt()
    const val BROWN = 0xFF574200.toInt()
    const val LIGHT_RED = 0xFFB86962.toInt()
    const val DARK_GREY = 0xFF505050.toInt()
    const val GREY = 0xFF787878.toInt()
    const val LIGHT_GREEN = 0xFF94E089.toInt()
    const val LIGHT_BLUE = 0xFF7869C4.toInt()
    const val LIGHT_GREY = 0xFF9F9F9F.toInt()

    /** Banner colour for each owner id. */
    fun lord(owner: Int): Int = when (owner) {
        0 -> LIGHT_BLUE
        1 -> RED
        2 -> PURPLE
        3 -> ORANGE
        NEUTRAL -> GREEN
        else -> GREY
    }

    fun darker(c: Int, f: Float = 0.75f): Int {
        val r = ((c shr 16 and 0xFF) * f).toInt()
        val g = ((c shr 8 and 0xFF) * f).toInt()
        val b = ((c and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Blends toward white by [f]. */
    fun lighter(c: Int, f: Float = 0.35f): Int {
        fun ch(v: Int) = (v + (255 - v) * f).toInt()
        return (0xFF shl 24) or (ch(c shr 16 and 0xFF) shl 16) or (ch(c shr 8 and 0xFF) shl 8) or ch(c and 0xFF)
    }

    fun withAlpha(c: Int, a: Int): Int = (a shl 24) or (c and 0xFFFFFF)
}

/** Drawing helpers in virtual coordinates. */
class Gfx {
    lateinit var canvas: Canvas
    /** Seconds since start; drives idle animation. */
    var time = 0f

    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val bitmapPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val rect = RectF()
    val path = Path()

    fun clear(color: Int) = canvas.drawColor(color)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        canvas.drawRect(x, y, x + w, y + h, fill)
    }

    fun frame(x: Float, y: Float, w: Float, h: Float, color: Int, width: Float = 1f) {
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawRect(x, y, x + w, y + h, stroke)
    }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        fill.color = color
        fill.isAntiAlias = true
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, fill)
        fill.isAntiAlias = false
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 1f) {
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawLine(x1, y1, x2, y2, stroke)
    }

    fun circle(x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        fill.isAntiAlias = true
        canvas.drawCircle(x, y, r, fill)
        fill.isAntiAlias = false
    }

    fun ring(x: Float, y: Float, r: Float, color: Int, width: Float = 1f) {
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawCircle(x, y, r, stroke)
    }

    fun oval(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        fill.isAntiAlias = true
        rect.set(x, y, x + w, y + h)
        canvas.drawOval(rect, fill)
        fill.isAntiAlias = false
    }

    /** Fills [path] after the caller has built it. */
    fun fillPath(color: Int) {
        fill.color = color
        fill.isAntiAlias = true
        canvas.drawPath(path, fill)
        fill.isAntiAlias = false
    }

    fun poly(color: Int, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        fillPath(color)
    }

    fun bitmap(b: Bitmap, x: Float, y: Float, w: Float, h: Float) {
        rect.set(x, y, x + w, y + h)
        canvas.drawBitmap(b, null, rect, bitmapPaint)
    }

    fun text(
        s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.LEFT, shadow: Boolean = false,
    ) {
        textPaint.textSize = size
        textPaint.textAlign = align
        if (shadow) {
            textPaint.color = Palette.BLACK
            canvas.drawText(s, x + size * 0.1f, y + size * 0.1f, textPaint)
        }
        textPaint.color = color
        canvas.drawText(s, x, y, textPaint)
    }

    fun textWidth(s: String, size: Float): Float {
        textPaint.textSize = size
        return textPaint.measureText(s)
    }

    /** Draws word-wrapped text and returns the y below the last line. */
    fun paragraph(s: String, x: Float, y: Float, width: Float, size: Float, color: Int, lineGap: Float = 1.35f): Float {
        var cy = y
        for (para in s.split('\n')) {
            var line = ""
            for (word in para.split(' ')) {
                val trial = if (line.isEmpty()) word else "$line $word"
                if (textWidth(trial, size) > width && line.isNotEmpty()) {
                    text(line, x, cy, size, color)
                    cy += size * lineGap
                    line = word
                } else {
                    line = trial
                }
            }
            text(line, x, cy, size, color)
            cy += size * lineGap
        }
        return cy
    }

    /** A bevelled panel like an old game's window. */
    fun panel(x: Float, y: Float, w: Float, h: Float, color: Int = Palette.BLUE) {
        rect(x, y, w, h, Palette.BLACK)
        rect(x + 1, y + 1, w - 2, h - 2, Palette.LIGHT_GREY)
        rect(x + 2, y + 2, w - 4, h - 4, color)
    }

    fun button(b: Button) {
        val base = when {
            !b.enabled -> Palette.DARK_GREY
            b.pressed -> Palette.YELLOW
            else -> b.color
        }
        rect(b.x, b.y + 2, b.w, b.h, Palette.BLACK)
        rect(b.x, b.y, b.w, b.h, Palette.darker(base, 0.6f))
        rect(b.x + 1, b.y + 1, b.w - 2, b.h - 3, base)
        val fg = if (b.enabled) (if (b.pressed) Palette.BLACK else Palette.WHITE) else Palette.GREY
        text(b.label, b.x + b.w / 2, b.y + b.h / 2 + b.textSize * 0.35f, b.textSize, fg, Paint.Align.CENTER, shadow = b.enabled && !b.pressed)
    }

    companion object {
        const val W = 480f
        const val H = 270f
    }
}
