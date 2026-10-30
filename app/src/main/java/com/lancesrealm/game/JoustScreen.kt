package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.Joust
import com.lancesrealm.core.PassResult
import kotlin.math.sin

/** The lists, seen down your own lance. Drag to steer the tip onto the rival's shield. */
class JoustScreen(view: GameView, private val rivalId: Int, private val forLand: Boolean) : Screen(view) {
    private val joust = Joust(game.hero.jousting, game.rival(rivalId).jousting)
    private val rivalColor = Palette.lord(rivalId)
    private var aimX = 0f
    private var aimY = 0.6f
    private var lastX = 0f
    private var lastY = 0f
    private val continueButton = button(180f, 240f, 120f, 22f, "Continue", Palette.GREEN) {
        val news = game.concludeTournament(rivalId, forLand, joust.playerWon)
        view.show(ChronicleScreen(view, if (joust.playerWon) "Glory in the Lists" else "Unhorsed", listOf(news)))
    }

    init {
        continueButton.visible = false
    }

    override fun onBack(): Boolean = true

    override fun touchDown(x: Float, y: Float) { lastX = x; lastY = y }

    override fun touchMove(x: Float, y: Float) {
        aimX = (aimX + (x - lastX) / SCALE * 1.2f).coerceIn(-1f, 1f)
        aimY = (aimY + (y - lastY) / SCALE * 1.2f).coerceIn(-1f, 1f)
        lastX = x; lastY = y
    }

    override fun update(dt: Float) {
        if (joust.phase == Joust.Phase.READY) { aimX = 0f; aimY = 0.6f }
        joust.aim(aimX, aimY)
        joust.update(dt)
        continueButton.visible = joust.phase == Joust.Phase.FINISHED
    }

    override fun draw(g: Gfx) {
        val hitByRival = joust.phase == Joust.Phase.RESULT && joust.lastRivalResult != PassResult.MISSED && joust.phaseTime < 0.4f
        if (hitByRival) g.canvas.translate(sin(g.time * 90f) * 4f, 0f)

        // Sky, stands and the field.
        g.clear(Palette.LIGHT_BLUE)
        g.rect(0f, 70f, Gfx.W, 30f, Palette.BROWN)
        for (i in 0 until 60) {
            val c = when (i % 4) { 0 -> Palette.RED; 1 -> Palette.YELLOW; 2 -> Palette.WHITE; else -> Palette.CYAN }
            g.circle(6f + i * 8f, 78f + (i % 3) * 7f + sin(g.time * 8f + i) * 1f, 2.2f, c)
        }
        for (i in 0 until 8) {
            val x = 20f + i * 60f
            g.rect(x, 50f, 2f, 22f, Palette.BROWN)
            g.poly(if (i % 2 == 0) rivalColor else Palette.lord(0), x + 2, 50f, x + 18, 55f + sin(g.time * 5 + i) * 2, x + 2, 60f)
        }
        g.rect(0f, 100f, Gfx.W, Gfx.H - 100f, Palette.GREEN)
        // Ground rushing past.
        val ride = if (joust.phase == Joust.Phase.RIDING) g.time * 3f else 0f
        for (i in 0 until 8) {
            val t = ((i / 8f + ride) % 1f)
            val y = 100f + t * t * 170f
            g.rect(0f, y, Gfx.W, 1f + t * 3f, Palette.darker(Palette.GREEN, 0.85f))
        }
        // The tilt barrier.
        g.poly(Palette.LIGHT_GREY, 238f, 100f, 246f, 100f, 120f, 270f, 70f, 270f)
        g.line(242f, 100f, 95f, 270f, Palette.BROWN, 2f)

        drawRival(g)
        drawLance(g)
        drawHud(g)
    }

    private fun drawRival(g: Gfx) {
        val a = joust.approach
        val s = 0.6f + 2.4f * a * a
        // The shield is drawn exactly where the hit test places it so aiming is honest.
        val sx = CX + joust.shieldX * SCALE
        val sy = CY + joust.shieldY * SCALE
        val bob = if (joust.phase == Joust.Phase.RIDING) sin(g.time * 14f) * 2f * s else 0f
        val unhorsed = joust.phase != Joust.Phase.RIDING && joust.phase != Joust.Phase.READY &&
            joust.lastPlayerResult == PassResult.UNHORSED
        if (unhorsed) {
            g.text("CRASH!", sx, sy, 24f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
            return
        }
        // Horse, seen head on.
        g.oval(sx - 14f * s, sy + 4f * s + bob, 28f * s, 26f * s, Palette.LIGHT_GREY)
        g.rect(sx - 10f * s, sy + 6f * s + bob, 20f * s, 14f * s, rivalColor)
        g.oval(sx - 5f * s, sy + 8f * s + bob, 10f * s, 22f * s, Palette.LIGHT_GREY)
        // Rider.
        g.rect(sx - 8f * s, sy - 18f * s + bob, 16f * s, 22f * s, rivalColor)
        g.rect(sx - 6f * s, sy - 30f * s + bob, 12f * s, 12f * s, Palette.LIGHT_GREY)
        g.rect(sx - 4f * s, sy - 25f * s + bob, 8f * s, 1.5f * s, Palette.BLACK)
        // Rival lance aimed back at us.
        g.line(sx + 6f * s, sy - 4f * s + bob, sx + 2f * s, sy + 2f * s + bob, Palette.YELLOW, 3f * s)
        // Shield: the target.
        val r = Joust.SHIELD_RADIUS * SCALE * (0.5f + 0.5f * a)
        val shieldY = sy
        g.poly(Palette.WHITE, sx - r, shieldY - r, sx + r, shieldY - r, sx + r, shieldY + r * 0.3f, sx, shieldY + r * 1.2f, sx - r, shieldY + r * 0.3f)
        val ir = r * 0.82f
        g.poly(rivalColor, sx - ir, shieldY - ir, sx + ir, shieldY - ir, sx + ir, shieldY + ir * 0.3f, sx, shieldY + ir * 1.2f, sx - ir, shieldY + ir * 0.3f)
        g.circle(sx, shieldY, Joust.UNHORSE_RADIUS * SCALE * (0.5f + 0.5f * a), Palette.YELLOW)
    }

    private fun drawLance(g: Gfx) {
        val tx = CX + joust.lanceX * SCALE
        val ty = CY + joust.lanceY * SCALE
        val bx = 420f
        val by = 300f
        g.line(bx, by, tx, ty, Palette.BROWN, 9f)
        // Painted spiral bands.
        for (i in 1..6) {
            val t = i / 7f
            val x = bx + (tx - bx) * t
            val y = by + (ty - by) * t
            g.circle(x, y, 4.5f - t * 3f, if (i % 2 == 0) Palette.WHITE else Palette.lord(0))
        }
        g.line(bx + (tx - bx) * 0.92f, by + (ty - by) * 0.92f, tx, ty, Palette.LIGHT_GREY, 3f)
        g.poly(Palette.LIGHT_GREY, bx - 30f, by - 50f, bx + 20f, by - 70f, bx + 40f, by - 10f, bx - 10f, by)
        if (joust.phase == Joust.Phase.RIDING) g.ring(tx, ty, 3f, Palette.WHITE, 1f)
    }

    private fun drawHud(g: Gfx) {
        g.panel(4f, 4f, 150f, 30f)
        g.text("You: ${joust.playerPoints}", 12f, 22f, 10f, Palette.YELLOW)
        g.text(game.rival(rivalId).displayName.substringAfter(' ') + ": ${joust.rivalPoints}", 70f, 22f, 10f, Palette.YELLOW)
        g.text("Pass ${joust.pass}", Gfx.W - 10f, 22f, 12f, Palette.WHITE, Paint.Align.RIGHT, shadow = true)
        when (joust.phase) {
            Joust.Phase.READY -> g.text("Pass ${joust.pass} - lower your lance!", Gfx.W / 2, 140f, 16f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
            Joust.Phase.RIDING -> if (joust.pass == 1) {
                g.text("Drag to steer your lance onto the shield. Gold boss unhorses!", Gfx.W / 2, 262f, 8f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
            }
            Joust.Phase.RESULT -> {
                g.panel(90f, 112f, 300f, 46f)
                g.text("You: ${joust.lastPlayerResult?.label}", Gfx.W / 2, 130f, 10f, Palette.WHITE, Paint.Align.CENTER)
                g.text("Rival: ${joust.lastRivalResult?.label}", Gfx.W / 2, 148f, 10f, Palette.LIGHT_RED, Paint.Align.CENTER)
            }
            Joust.Phase.FINISHED -> {
                val text = if (joust.playerWon) "YOU WIN THE JOUST!" else "YOU ARE BEATEN"
                g.text(text, Gfx.W / 2, 140f, 22f, if (joust.playerWon) Palette.YELLOW else Palette.LIGHT_RED, Paint.Align.CENTER, shadow = true)
            }
        }
    }

    companion object {
        private const val CX = 240f
        private const val CY = 140f
        private const val SCALE = 110f
    }
}
