package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.Duel
import com.lancesrealm.core.Height
import com.lancesrealm.core.Music
import com.lancesrealm.core.Sfx
import com.lancesrealm.core.Story
import com.lancesrealm.core.WorldMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * A night raid on a rival castle: fight through the guards. Left half of the screen blocks,
 * right half strikes; the upper half is high, the lower half low.
 */
class DuelScreen(view: GameView, private val lordId: Int) : Screen(view) {
    private val rooms = game.raidRooms(lordId)
    private val rescue = game.isRescue(lordId)
    private val duel = Duel(game.hero.swordplay, rooms.map { it.guards })
    private var intro = 1.6f
    private var lastMessageTime = 0f
    private val guardColor = Palette.lord(lordId)
    private val continueButton = button(180f, 236f, 120f, 22f, "Continue", Palette.GREEN) {
        val news = game.concludeRaid(lordId, duel.playerWon)
        val title = if (duel.playerWon) (if (rescue) "A Daring Rescue" else "A Daring Raid") else "Caught!"
        view.show(ChronicleScreen(view, title, listOf(news)))
    }
    private var finishDelay = 0f

    init {
        continueButton.visible = false
    }

    override fun onBack(): Boolean = true

    override fun onShow() {
        view.audio.music(Music.RAID)
    }

    override fun touchDown(x: Float, y: Float) {
        if (duel.isOver || intro > 0f) return
        val h = if (y < ZONE_SPLIT) Height.HIGH else Height.LOW
        if (x < Gfx.W / 2) duel.block(h) else duel.attack(h)
    }

    override fun update(dt: Float) {
        if (intro > 0f) { intro -= dt; return }
        val wasOver = duel.isOver
        duel.update(dt)
        if (duel.messageTime < lastMessageTime && duel.message.isNotEmpty()) soundFor(duel.message)
        lastMessageTime = duel.messageTime
        if (duel.isOver && !wasOver) view.audio.music(if (duel.playerWon) Music.VICTORY else Music.DEFEAT)
        if (duel.isOver) finishDelay += dt
        continueButton.visible = duel.isOver && finishDelay > 1f
    }

    private fun soundFor(message: String) {
        val m = message.lowercase()
        view.audio.sfx(
            when {
                "parried" in m || "blocked" in m -> Sfx.CLASH
                "wounded" in m || "you fall" in m -> Sfx.GLOOM
                else -> Sfx.HIT
            }
        )
    }

    override fun draw(g: Gfx) {
        drawRoom(g, duel.roomIndex)
        drawPlayer(g)
        drawGuard(g)
        drawHud(g)
        val card = when {
            intro > 0f -> intro / 1.6f
            duel.transition > 0f -> duel.transition / 2.2f
            else -> 0f
        }
        if (card > 0f) {
            val a = (minOf(card, 1f - card) * 6f).coerceIn(0f, 1f)
            g.rect(0f, 0f, Gfx.W, Gfx.H, Palette.withAlpha(Palette.BLACK, (230 * a).toInt()))
            g.text(rooms[duel.roomIndex].name, Gfx.W / 2, 130f, 22f, Palette.withAlpha(Palette.YELLOW, (255 * a).toInt()), Paint.Align.CENTER)
            g.text("Room ${duel.roomIndex + 1} of ${duel.roomCount}", Gfx.W / 2, 148f, 9f, Palette.withAlpha(Palette.LIGHT_GREY, (255 * a).toInt()), Paint.Align.CENTER)
        }
    }

    /** Each chamber of the castle has its own look; the last is the treasury or the prison tower. */
    private fun drawRoom(g: Gfx, room: Int) {
        val stone = when (room) {
            0 -> Palette.GREY
            1 -> Palette.darker(Palette.DARK_GREY, 0.6f)
            else -> if (rescue) Palette.darker(Palette.BLUE, 0.5f) else Palette.darker(Palette.BROWN, 0.6f)
        }
        g.clear(stone)
        for (row in 0 until 9) for (col in 0 until 13) {
            val off = if (row % 2 == 0) 0f else 20f
            g.frame(col * 40f - off, row * 22f, 40f, 22f, Palette.darker(stone, 0.75f))
        }
        when (room) {
            0 -> {
                // Gate arch with the portcullis half raised.
                g.rect(170f, 40f, 140f, 200f, Palette.BLACK)
                g.oval(170f, 0f, 140f, 90f, Palette.BLACK)
                for (i in 0 until 8) g.line(178f + i * 18f, 10f, 178f + i * 18f, 110f, Palette.DARK_GREY, 3f)
                for (j in 0 until 5) g.line(172f, 20f + j * 22f, 308f, 20f + j * 22f, Palette.DARK_GREY, 3f)
                g.rect(176f, 160f, 128f, 80f, Palette.darker(Palette.BLUE, 0.4f))
                for (i in 0 until 12) g.rect(180f + WorldMap.hash(i, 5) * 120f, 165f + WorldMap.hash(i, 6) * 30f, 1.2f, 1.2f, Palette.WHITE)
            }
            1 -> {
                for (bx in listOf(150f, 330f)) {
                    g.poly(Palette.lord(lordId), bx - 16f, 20f, bx + 16f, 20f, bx + 16f, 110f, bx, 96f, bx - 16f, 110f)
                }
                g.rect(30f, 200f, 420f, 8f, Palette.BROWN)
                g.rect(40f, 208f, 6f, 28f, Palette.BROWN)
                g.rect(434f, 208f, 6f, 28f, Palette.BROWN)
            }
            else -> if (rescue) {
                // Winding stair up to the cell where the hostage is kept.
                for (i in 0 until 7) g.rect(360f + i * 6f, 230f - i * 24f, 120f - i * 6f, 8f, Palette.GREY)
                g.rect(400f, 30f, 60f, 50f, Palette.BLACK)
                for (i in 0 until 4) g.line(408f + i * 14f, 30f, 408f + i * 14f, 80f, Palette.LIGHT_GREY, 2f)
                if (duel.playerWon) {
                    g.text("${Story.SIBLING}!", 430f, 24f, 10f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
                }
                g.circle(430f, 52f, 6f, Palette.LIGHT_RED)
                g.rect(424f, 58f, 12f, 20f, Palette.GREEN)
            } else {
                for (i in 0 until 4) {
                    val cx = 40f + i * 110f
                    g.rect(cx, 196f, 50f, 36f, Palette.BROWN)
                    g.rect(cx, 196f, 50f, 6f, Palette.ORANGE)
                    for (k in 0 until 5) g.circle(cx + 8f + k * 8f, 194f, 4f, Palette.YELLOW)
                }
            }
        }
        for (tx in listOf(90f, 390f)) {
            g.rect(tx - 2f, 70f, 4f, 16f, Palette.BROWN)
            val flicker = sin(g.time * 23f + tx) * 2f
            g.oval(tx - 5f, 54f + flicker, 10f, 18f - flicker, Palette.ORANGE)
            g.oval(tx - 3f, 60f + flicker, 6f, 10f, Palette.YELLOW)
        }
        g.rect(0f, GROUND, Gfx.W, Gfx.H - GROUND, Palette.BROWN)
        g.rect(0f, GROUND, Gfx.W, 3f, Palette.darker(Palette.BROWN, 0.6f))
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
        if (!duel.playerWon && duel.transition <= 0f) {
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
        g.text("${rooms[duel.roomIndex].name}  -  guard ${minOf(duel.guardIndex + 1, duel.guardCount)}/${duel.guardCount}", 10f, 30f, 7f, Palette.LIGHT_GREY)
        g.panel(Gfx.W - 124f, 4f, 120f, 30f)
        g.text("Guard", Gfx.W - 118f, 18f, 8f, Palette.YELLOW)
        for (i in 0 until duel.guardMaxHp) g.rect(Gfx.W - 80f + i * 14f, 11f, 12f, 8f, if (i < duel.guardHp) guardColor else Palette.DARK_GREY)

        if (duel.messageTime < 1.2f && duel.message.isNotEmpty()) {
            g.text(duel.message, Gfx.W / 2, 58f, 12f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        }
        when {
            duel.playerWon -> g.text(
                if (rescue) "${Story.SIBLING.uppercase()} IS FREE!" else "THE TREASURY IS YOURS!",
                Gfx.W / 2, 120f, 18f, Palette.YELLOW, Paint.Align.CENTER, shadow = true,
            )
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
