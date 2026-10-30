package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.AttackPlan
import com.lancesrealm.core.Siege
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Bombard a rival castle with your catapults, then storm it. Drag back and release to fire. */
class SiegeScreen(view: GameView, private val plan: AttackPlan) : Screen(view) {
    private val siege = Siege(game.player.army.catapults * 2)
    private var dragging = false
    private var startX = 0f
    private var startY = 0f
    private var angle = 45f
    private var power = 0.5f
    private val storm = button(Gfx.W - 128f, 8f, 120f, 20f, "Storm now", Palette.RED, 9f) { storm() }

    override fun onBack(): Boolean = true

    private fun storm() {
        view.show(BattleScreen(view, plan, siege.fortification, siege.garrisonLoss))
    }

    override fun update(dt: Float) {
        siege.update(dt)
        storm.label = if (siege.phase == Siege.Phase.FINISHED) "Storm the castle!" else "Storm now"
        storm.color = if (siege.phase == Siege.Phase.FINISHED) Palette.GREEN else Palette.RED
    }

    override fun touchDown(x: Float, y: Float) {
        if (siege.phase != Siege.Phase.AIMING) return
        dragging = true
        startX = x; startY = y
    }

    override fun touchMove(x: Float, y: Float) {
        if (!dragging) return
        val dx = startX - x
        val dy = y - startY
        angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat().coerceIn(Siege.MIN_ANGLE, Siege.MAX_ANGLE)
        power = (hypot(dx, dy) / 110f).coerceIn(0f, 1f)
    }

    override fun touchUp(x: Float, y: Float) {
        if (!dragging) return
        dragging = false
        touchMove(x, y)
        if (power > 0.05f) siege.fire(angle, power)
    }

    private fun sx(wx: Float) = 20f + wx * K
    private fun sy(wy: Float) = GROUND - wy * K

    override fun draw(g: Gfx) {
        drawLandscape(g, GROUND)
        // Courtyard and keep beyond the wall.
        g.rect(sx(Siege.WALL_END), GROUND - 4f, sx(Siege.KEEP_END) - sx(Siege.WALL_END), 4f, Palette.BROWN)
        for (i in 0 until 4) {
            val tx = sx(Siege.WALL_END + 8f + i * 12f)
            g.poly(Palette.lord(plan.target.owner), tx - 6f, GROUND - 4f, tx + 6f, GROUND - 4f, tx, GROUND - 16f)
        }
        val keepX = sx(Siege.KEEP_END + 2f)
        g.rect(keepX, GROUND - 110f, 40f, 110f, Palette.darker(Palette.LIGHT_GREY, 0.75f))
        for (i in 0 until 5) g.rect(keepX + i * 9f, GROUND - 116f, 5f, 6f, Palette.darker(Palette.LIGHT_GREY, 0.75f))
        g.line(keepX + 20f, GROUND - 116f, keepX + 20f, GROUND - 136f, Palette.WHITE)
        g.poly(Palette.lord(plan.target.owner), keepX + 20f, GROUND - 136f, keepX + 36f, GROUND - 131f + sin(g.time * 6f) * 2f, keepX + 20f, GROUND - 126f)

        // Curtain wall: each block can be knocked out.
        for (c in 0 until Siege.COLS) for (r in 0 until Siege.ROWS) {
            if (!siege.isBlockStanding(c, r)) continue
            val x = sx(siege.blockLeft(c))
            val y = sy(siege.blockBottom(r) + Siege.BLOCK_H)
            val w = Siege.BLOCK_W * K
            val h = Siege.BLOCK_H * K
            g.rect(x, y, w, h, Palette.LIGHT_GREY)
            g.frame(x, y, w, h, Palette.DARK_GREY)
        }

        drawCatapult(g)

        if (siege.phase == Siege.Phase.FLYING || siege.trail.isNotEmpty()) {
            val t = siege.trail
            var i = 0
            while (i + 1 < t.size) { g.circle(sx(t[i]), sy(t[i + 1]), 0.8f, Palette.WHITE); i += 6 }
        }
        if (siege.phase == Siege.Phase.FLYING) g.circle(sx(siege.projX), sy(siege.projY), 3f, Palette.DARK_GREY)
        if (dragging) drawPreview(g)

        drawHud(g)
    }

    private fun drawCatapult(g: Gfx) {
        val bx = sx(0f)
        val by = GROUND
        g.rect(bx - 14f, by - 6f, 34f, 5f, Palette.BROWN)
        g.circle(bx - 8f, by - 2f, 4f, Palette.darker(Palette.BROWN, 0.7f))
        g.circle(bx + 14f, by - 2f, 4f, Palette.darker(Palette.BROWN, 0.7f))
        g.poly(Palette.BROWN, bx, by - 6f, bx + 4f, by - 6f, bx + 3f, by - 20f, bx + 1f, by - 20f)
        val a = if (siege.phase == Siege.Phase.AIMING) Math.toRadians((180f - angle * (1f - power * 0.3f)).toDouble()) else Math.toRadians(80.0)
        val px = bx + 2f
        val py = by - 18f
        g.line(px, py, px + (cos(a) * 26f).toFloat(), py - (sin(a) * 26f).toFloat(), Palette.ORANGE, 2.5f)
        if (siege.phase == Siege.Phase.AIMING) g.circle(px + (cos(a) * 26f).toFloat(), py - (sin(a) * 26f).toFloat(), 3f, Palette.DARK_GREY)
    }

    /** Shows only the first stretch of the arc so judging the range stays a skill. */
    private fun drawPreview(g: Gfx) {
        val rad = Math.toRadians(angle.toDouble())
        val v = Siege.launchSpeed(power)
        var vx = (cos(rad) * v).toFloat()
        var vy = (sin(rad) * v).toFloat()
        var x = Siege.LAUNCH_X
        var y = Siege.LAUNCH_Y
        for (i in 0 until 14) {
            for (j in 0 until 4) {
                val h = 1f / 60f
                vy -= Siege.GRAVITY * h
                x += vx * h; y += vy * h
            }
            g.circle(sx(x), sy(y), 1.2f, Palette.YELLOW)
        }
    }

    private fun drawHud(g: Gfx) {
        g.panel(4f, 4f, 230f, 44f)
        g.text("Siege of ${plan.target.name}", 10f, 17f, 9f, Palette.YELLOW)
        g.text("Stones left: ${siege.shotsLeft}   Wall: ${(100 * (1 - siege.breach)).toInt()}%", 10f, 29f, 8f, Palette.WHITE)
        val windDir = if (siege.wind >= 0) ">" else "<"
        val windStrength = (kotlin.math.abs(siege.wind) * 1.5f).toInt()
        g.text("Wind: " + if (windStrength == 0) "calm" else windDir.repeat(windStrength), 10f, 41f, 8f, Palette.CYAN)
        g.text(siege.message, Gfx.W / 2, 64f, 9f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
        if (dragging) {
            g.text("Angle ${angle.toInt()}  Power ${(power * 100).toInt()}%", Gfx.W / 2, 78f, 8f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        }
        if (siege.phase == Siege.Phase.FINISHED) {
            g.panel(120f, 90f, 240f, 56f)
            g.text("The bombardment is over", Gfx.W / 2, 108f, 10f, Palette.YELLOW, Paint.Align.CENTER)
            g.text("Wall breached: ${(siege.breach * 100).toInt()}%", Gfx.W / 2, 122f, 8.5f, Palette.WHITE, Paint.Align.CENTER)
            g.text("Garrison lost: ${(siege.garrisonLoss * 100).toInt()}%", Gfx.W / 2, 136f, 8.5f, Palette.WHITE, Paint.Align.CENTER)
        }
    }

    companion object {
        private const val GROUND = 236f
        private const val K = 1.8f
    }
}
