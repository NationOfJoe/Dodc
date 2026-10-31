package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.Army
import com.lancesrealm.core.AttackPlan
import com.lancesrealm.core.Battle
import com.lancesrealm.core.BattleOutcome
import com.lancesrealm.core.Music
import com.lancesrealm.core.Sfx
import com.lancesrealm.core.Tactic
import kotlin.math.min
import kotlin.math.sin

/** A field battle or castle storm, fought round by round with the player's chosen tactic. */
class BattleScreen(
    view: GameView,
    private val plan: AttackPlan,
    private val fortification: Float,
    garrisonLoss: Float,
) : Screen(view) {
    private val battle: Battle = game.startBattle(plan, fortification, garrisonLoss)
    private val startAttacker = battle.attacker.copyArmy()
    private val startDefender = battle.defender.copyArmy()
    private val log = ArrayDeque<String>()
    private val tacticButtons = mutableListOf<Button>()
    private var clash = 0f
    /** Seconds of the opening title card still to run. */
    private var intro = 1.8f
    private var lastTactic: Tactic? = null
    private val continueButton: Button

    init {
        log.add(
            if (plan.isSiege) {
                if (fortification > 1.5f) "The walls of ${plan.target.name} stand unbroken." else "You storm the breach at ${plan.target.name}!"
            } else {
                "Your host faces ${plan.defenderName}'s men at ${plan.target.name}."
            }
        )
        Tactic.entries.forEachIndexed { i, t ->
            tacticButtons += button(8f + i * 118f, 240f, 110f, 22f, t.label, if (t == Tactic.RETREAT) Palette.DARK_GREY else Palette.RED) { fight(t) }
        }
        continueButton = button(180f, 240f, 120f, 22f, "Continue", Palette.GREEN) { finish() }
        continueButton.visible = false
    }

    override fun onShow() {
        view.audio.music(Music.BATTLE)
        view.audio.sfx(Sfx.FANFARE)
    }

    private fun fight(t: Tactic) {
        if (battle.isOver || clash > 0f || intro > 0f) return
        view.audio.sfx(if (t == Tactic.RETREAT) Sfx.GLOOM else Sfx.CLASH)
        lastTactic = t
        val report = battle.fight(t)
        clash = if (t == Tactic.RETREAT) 0.01f else 1.2f
        log.addFirst(
            report.describe("Your host", plan.defenderName) +
                "  You lose ${lossText(report.attackerLost)}, they lose ${lossText(report.defenderLost)}."
        )
        while (log.size > 4) log.removeLast()
        if (battle.isOver) {
            view.audio.music(if (battle.outcome == BattleOutcome.ATTACKER_WINS) Music.VICTORY else Music.DEFEAT)
        }
    }

    private fun lossText(a: Army) = if (a.knights > 0) "${a.soldiers}+${a.knights}k" else "${a.soldiers}"

    private fun finish() {
        val news = game.concludeAttack(plan, battle)
        view.show(ChronicleScreen(view, title(), listOf(news)))
    }

    private fun title() = when (battle.outcome) {
        BattleOutcome.ATTACKER_WINS -> "Victory!"
        BattleOutcome.RETREATED -> "Retreat"
        else -> "Defeat"
    }

    override fun onBack(): Boolean = true

    override fun touchUp(x: Float, y: Float) {
        intro = 0f
    }

    override fun update(dt: Float) {
        val wasClashing = clash > 0.6f
        clash = (clash - dt).coerceAtLeast(0f)
        if (wasClashing && clash <= 0.6f) view.audio.sfx(Sfx.HIT)
        intro = (intro - dt).coerceAtLeast(0f)
        val waiting = clash > 0f || intro > 0f
        tacticButtons.forEach { it.visible = !battle.isOver; it.enabled = !waiting }
        continueButton.visible = battle.isOver && !waiting
    }

    override fun draw(g: Gfx) {
        drawLandscape(g, 120f)
        g.rect(0f, 120f, Gfx.W, 110f, Palette.darker(Palette.GREEN, 0.9f))
        if (fortification > 1f) {
            val ruin = (1.9f - fortification) / 0.9f
            Sprites.castle(g, 360f, 132f, 110f, 70f * (1f - ruin * 0.35f), Palette.LIGHT_GREY, Palette.lord(plan.target.owner))
        }
        val advance = if (clash > 0f) sin((1.2f - clash) / 1.2f * Math.PI.toFloat()) * 60f else 0f
        drawArmy(g, battle.attacker, startAttacker, 30f + advance, 1, Palette.lord(0))
        drawArmy(g, battle.defender, startDefender, 340f - advance * 0.6f, -1, Palette.lord(plan.target.owner))
        if (clash > 0.7f) drawVolley(g, (1.2f - clash) / 0.5f)
        if (clash in 0.2f..0.9f) {
            for (i in 0 until 10) {
                val x = 200f + (i * 37 % 80) + sin(g.time * 30f + i) * 6f
                val y = 135f + (i * 23 % 45)
                g.oval(x - 10f, y - 4f, 22f, 10f, Palette.withAlpha(Palette.LIGHT_GREY, 110))
                g.line(x - 4, y - 4, x + 4, y + 4, Palette.WHITE, 1.2f)
                g.line(x - 4, y + 4, x + 4, y - 4, Palette.WHITE, 1.2f)
            }
        }
        if (clash in 0.55f..0.65f) g.rect(0f, 0f, Gfx.W, Gfx.H, Palette.withAlpha(Palette.WHITE, 90))

        g.panel(4f, 4f, 180f, 30f)
        g.text("Your host", 10f, 16f, 8f, Palette.YELLOW)
        g.text("${battle.attacker.soldiers} men  ${battle.attacker.knights} knights", 10f, 28f, 8f, Palette.WHITE)
        g.panel(Gfx.W - 184f, 4f, 180f, 30f)
        g.text(plan.defenderName, Gfx.W - 178f, 16f, 8f, Palette.YELLOW)
        g.text("${battle.defender.soldiers} men  ${battle.defender.knights} knights", Gfx.W - 178f, 28f, 8f, Palette.WHITE)
        g.text("Round ${battle.round} of ${Battle.MAX_ROUNDS}", Gfx.W / 2, 16f, 9f, Palette.WHITE, Paint.Align.CENTER, shadow = true)

        g.panel(4f, 182f, Gfx.W - 8f, 54f, Palette.darker(Palette.BLUE, 0.8f))
        log.forEachIndexed { i, line ->
            g.text(line, 12f, 196f + i * 11f, 7.5f, if (i == 0) Palette.WHITE else Palette.LIGHT_GREY)
        }
        if (intro > 0f) {
            drawTitleCard(g)
            return
        }
        if (battle.isOver && clash <= 0f) {
            val (text, color) = when (battle.outcome) {
                BattleOutcome.ATTACKER_WINS -> "VICTORY!" to Palette.YELLOW
                BattleOutcome.RETREATED -> "YOU WITHDRAW" to Palette.LIGHT_GREY
                else -> "DEFEAT" to Palette.LIGHT_RED
            }
            g.text(text, Gfx.W / 2, 70f, 28f, color, Paint.Align.CENTER, shadow = true)
        } else if (clash <= 0f) {
            val hint = lastTactic?.let { "Choose your next order" } ?: "Choose your order: Hold beats Charge, Charge beats Flank, Flank beats Hold"
            g.text(hint, Gfx.W / 2, 50f, 8f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
        }
    }

    /** Arrows arcing from both lines toward the middle of the field. */
    private fun drawVolley(g: Gfx, t: Float) {
        for (i in 0 until 12) {
            val fromLeft = i % 2 == 0
            val sx = if (fromLeft) 90f else 380f
            val ex = if (fromLeft) 330f else 140f
            val lane = (i / 2) * 7f
            val x = sx + (ex - sx) * t
            val y = 130f + lane - sin(t * Math.PI.toFloat()) * 60f
            val dir = if (fromLeft) 1f else -1f
            g.line(x - 5f * dir, y + (if (t < 0.5f) 2f else -2f), x, y, Palette.WHITE, 0.8f)
        }
    }

    private fun drawTitleCard(g: Gfx) {
        val a = (minOf(intro, 0.3f) / 0.3f)
        g.rect(0f, 0f, Gfx.W, 40f * a, Palette.BLACK)
        g.rect(0f, 70f, Gfx.W, 46f, Palette.withAlpha(Palette.BLACK, (180 * a).toInt()))
        val title = if (plan.isSiege) "The Storming of ${plan.target.name}" else "The Battle of ${plan.target.name}"
        g.text(title, Gfx.W / 2, 98f, 18f, Palette.withAlpha(Palette.YELLOW, (255 * a).toInt()), Paint.Align.CENTER, shadow = a > 0.9f)
        g.text("${battle.attacker.strength} against ${battle.defender.strength}", Gfx.W / 2, 111f, 8f, Palette.withAlpha(Palette.WHITE, (255 * a).toInt()), Paint.Align.CENTER)
    }

    /** Draws up to a few dozen figures standing for the whole force. */
    private fun drawArmy(g: Gfx, army: Army, start: Army, x0: Float, facing: Int, color: Int) {
        val perFigure = maxOf(1, (start.soldiers + start.knights + 23) / 24)
        val men = (army.soldiers + perFigure - 1) / perFigure
        val knights = min(army.knights, 8)
        val step = g.time * 10f
        for (i in 0 until men) {
            val col = i / 6
            val row = i % 6
            val x = x0 + col * 11f * facing + (if (facing < 0) 60f else 30f) + row % 2 * 4f
            Sprites.soldier(g, x, 130f + row * 9f, color, facing, if (clash > 0) step + i else 0f)
        }
        for (i in 0 until knights) {
            val x = x0 + (if (facing < 0) 110f + (i / 4) * 22f else -(i / 4) * 22f)
            Sprites.knight(g, x, 136f + (i % 4) * 12f, color, facing, if (clash > 0) step + i else 0f)
        }
    }
}
