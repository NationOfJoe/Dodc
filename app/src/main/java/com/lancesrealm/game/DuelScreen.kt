package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.Duel
import com.lancesrealm.core.Height
import kotlin.math.cos
import kotlin.math.sin

/**
 * A night raid on a rival castle: fight through the guards. Left half of the screen blocks,
 * right half strikes; the upper half is high, the lower half low.
 */
class DuelScreen(view: GameView, private val lordId: Int) : Screen(view) {
    private val duel = Duel(game.hero.swordplay, game.raidGuards(lordId))
    private val guardColor = Palette.lord(lordId)
    private val continueButton = button(180f, 236f, 120f, 22f, "Continue", Palette.GREEN) {
        val news = game.concludeRaid(lordId, duel.playerWon)
        view.show(ChronicleScreen(view, if (duel.playerWon) "A Daring Raid" else "Caught!", listOf(news)))
    }
    private var finishDelay = 0f

    init {
        continueButton.visible = false
    }

    override fun onBack(): Boolean = true

    override fun touchDown(x: Float, y: Float) {
        if (duel.isOver) return
        val h = if (y < ZONE_SPLIT) Height.HIGH else Height.LOW
        if (x < Gfx.W / 2) duel.block(h) else duel.attack(h)
    }

    override fun update(dt: Float) {
        duel.update(dt)
        if (duel.isOver) finishDelay += dt
        continueButton.visible = duel.isOver && finishDelay > 1f
    }

    override fun draw(g: Gfx) {
        // Torchlit hall.
        g.clear(Palette.darker(Palette.DARK_GREY, 0.6f))
        for (row in 0 until 9) for (col in 0 until 13) {
            val off = if (row % 2 == 0) 0f else 20f
            g.frame(col * 40f - off, row * 22f, 40f, 22f, Palette.darker(Palette.DARK_GREY, 0.8f))
        }
        for (tx in listOf(90f, 390f)) {
            g.rect(tx - 2f, 70f, 4f, 16f, Palette.BROWN)
            val flicker = sin(g.time * 23f + tx) * 2f
            g.oval(tx - 5f, 54f + flicker, 10f, 18f - flicker, Palette.ORANGE)
            g.oval(tx - 3f, 60f + flicker, 6f, 10f, Palette.YELLOW)
        }
        g.rect(0f, GROUND, Gfx.W, Gfx.H - GROUND, Palette.BROWN)
        g.rect(0f, GROUND, Gfx.W, 3f, Palette.darker(Palette.BROWN, 0.6f))

        drawPlayer(g)
        drawGuard(g)
        drawHud(g)
    }

    private fun drawPlayer(g: Gfx) {
        val x = 190f
        val (angle, handY, lean) = when {
            duel.swingTime > 0f -> Triple(if (duel.swingHeight == Height.HIGH) 5f else -20f, if (duel.swingHeight == Height.HIGH) 30f else 18f, 4f)
            duel.blockTime > 0f -> Triple(if (duel.blockHeight == Height.HIGH) 80f else -75f, if (duel.blockHeight == Height.HIGH) 38f else 16f, -2f)
            else -> Triple(40f, 24f, 0f)
        }
        val hurt = duel.playerLost
        fighter(g, x, 1, Palette.lord(0), angle, handY, if (hurt) -10f else lean, if (hurt) 0.5f else 1f)
    }

    private fun drawGuard(g: Gfx) {
        val x = 290f
        val h = duel.enemyHeight
        val (angle, handY, lean) = when (duel.enemyState) {
            Duel.EnemyState.GUARD -> Triple(if (h == Height.HIGH) 45f else -35f, if (h == Height.HIGH) 30f else 18f, 0f)
            Duel.EnemyState.WINDUP -> Triple(if (h == Height.HIGH) 130f else -150f, if (h == Height.HIGH) 40f else 14f, -3f)
            Duel.EnemyState.RECOVER -> Triple(-10f, 22f, 5f)
            Duel.EnemyState.STAGGER -> Triple(-60f, 18f, -8f)
            Duel.EnemyState.HURT -> Triple(20f, 24f, -6f)
            Duel.EnemyState.DOWN -> Triple(-90f, 10f, -20f)
        }
        val down = duel.enemyState == Duel.EnemyState.DOWN || duel.playerWon
        val color = if (duel.enemyState == Duel.EnemyState.HURT && (g.time * 20).toInt() % 2 == 0) Palette.WHITE else guardColor
        if (!(duel.playerWon && duel.guardIndex >= duel.guardCount)) {
            fighter(g, x, -1, color, angle, handY, lean, if (down) 0.5f else 1f)
        }
        if (duel.enemyState == Duel.EnemyState.WINDUP) {
            val y = if (h == Height.HIGH) 70f else 160f
            val urgent = duel.enemyStateTime / duel.windupDuration
            val c = if (urgent > 0.6f && (g.time * 16).toInt() % 2 == 0) Palette.YELLOW else Palette.LIGHT_RED
            g.text("!", x + 40f, y, 26f, c, Paint.Align.CENTER, shadow = true)
            g.text(if (h == Height.HIGH) "HIGH" else "LOW", x + 40f, y + 12f, 8f, c, Paint.Align.CENTER, shadow = true)
        }
    }

    /**
     * Draws a swordsman. [angle] is the blade's elevation in degrees (0 = forward), [handY] the
     * hand's height above the floor in units, [lean] tilts the torso, [stand] squashes when fallen.
     */
    private fun fighter(g: Gfx, x: Float, facing: Int, color: Int, angle: Float, handY: Float, lean: Float, stand: Float) {
        val s = 3.2f
        val f = facing.toFloat()
        val hip = GROUND - 16f * s * stand
        g.line(x - 3f * s, GROUND, x - 1f * s + lean, hip, Palette.BROWN, 3.5f * s / 2)
        g.line(x + 3f * s, GROUND, x + 1f * s + lean, hip, Palette.BROWN, 3.5f * s / 2)
        val shoulderY = hip - 14f * s * stand
        val sx = x + lean * s * 0.4f
        g.poly(color, sx - 5f * s, shoulderY, sx + 5f * s, shoulderY, x + 4f * s + lean, hip, x - 4f * s + lean, hip)
        g.rect(sx - 3.5f * s, shoulderY - 8f * s * stand, 7f * s, 8f * s * stand, Palette.LIGHT_GREY)
        g.rect(sx + f * 0.5f * s - 1f, shoulderY - 5.5f * s * stand, f * 3f * s, 1.2f * s, Palette.BLACK)
        val hx = sx + f * 7f * s
        val hy = GROUND - handY * s * stand
        g.line(sx + f * 3f * s, shoulderY + 2f * s, hx, hy, color, 2.2f * s / 2)
        val rad = Math.toRadians(angle.toDouble())
        val bx = hx + (cos(rad) * 20f * s).toFloat() * f
        val by = hy - (sin(rad) * 20f * s).toFloat()
        g.line(hx, hy, bx, by, Palette.WHITE, 2.2f)
        g.line(hx - 2f * s * sin(rad).toFloat() * f, hy - 2f * s * cos(rad).toFloat(), hx + 2f * s * sin(rad).toFloat() * f, hy + 2f * s * cos(rad).toFloat(), Palette.YELLOW, 2f)
        // Shield on the off arm.
        g.oval(sx - f * 6f * s - 3f * s, shoulderY + 1f * s, 6f * s, 9f * s, Palette.darker(color, 0.7f))
    }

    private fun drawHud(g: Gfx) {
        g.panel(4f, 4f, 170f, 30f)
        g.text("You", 10f, 18f, 8f, Palette.YELLOW)
        for (i in 0 until duel.playerMaxHp) g.rect(34f + i * 12f, 11f, 10f, 8f, if (i < duel.playerHp) Palette.LIGHT_RED else Palette.DARK_GREY)
        g.text("Guard ${minOf(duel.guardIndex + 1, duel.guardCount)} of ${duel.guardCount}", 10f, 30f, 7f, Palette.LIGHT_GREY)
        g.panel(Gfx.W - 124f, 4f, 120f, 30f)
        g.text("Guard", Gfx.W - 118f, 18f, 8f, Palette.YELLOW)
        for (i in 0 until duel.guardMaxHp) g.rect(Gfx.W - 80f + i * 14f, 11f, 12f, 8f, if (i < duel.guardHp) guardColor else Palette.DARK_GREY)

        if (duel.messageTime < 1.2f && duel.message.isNotEmpty()) {
            g.text(duel.message, Gfx.W / 2, 58f, 12f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        }
        when {
            duel.playerWon -> g.text("THE TREASURY IS YOURS!", Gfx.W / 2, 120f, 18f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
            duel.playerLost -> g.text("OVERPOWERED!", Gfx.W / 2, 120f, 18f, Palette.LIGHT_RED, Paint.Align.CENTER, shadow = true)
            else -> {
                val c = Palette.withAlpha(Palette.WHITE, 110)
                g.text("BLOCK HIGH", 8f, 90f, 8f, c)
                g.text("BLOCK LOW", 8f, 230f, 8f, c)
                g.text("STRIKE HIGH", Gfx.W - 8f, 90f, 8f, c, Paint.Align.RIGHT)
                g.text("STRIKE LOW", Gfx.W - 8f, 230f, 8f, c, Paint.Align.RIGHT)
                g.line(Gfx.W / 2, 40f, Gfx.W / 2, GROUND, Palette.withAlpha(Palette.WHITE, 30), 1f)
                g.line(0f, ZONE_SPLIT, Gfx.W, ZONE_SPLIT, Palette.withAlpha(Palette.WHITE, 30), 1f)
            }
        }
        if (duel.guardIndex == 0 && duel.messageTime > 0f && duel.message.isEmpty()) {
            g.text("Block when the guard shows '!' at that height, then strike!", Gfx.W / 2, 262f, 8f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
        }
    }

    companion object {
        private const val GROUND = 236f
        private const val ZONE_SPLIT = 150f
    }
}
