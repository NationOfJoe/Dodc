package com.lancesrealm.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class Tactic(val label: String, val hint: String) {
    CHARGE("Charge", "Strike hard; beats a flank"),
    HOLD("Hold", "Brace the line; beats a charge"),
    FLANK("Flank", "Ride round; beats a held line"),
    RETREAT("Retreat", "Withdraw with losses"),
    ;

    /** Rock-paper-scissors: the tactic this one counters. */
    val counters: Tactic?
        get() = when (this) {
            HOLD -> CHARGE
            CHARGE -> FLANK
            FLANK -> HOLD
            RETREAT -> null
        }
}

enum class BattleOutcome { ATTACKER_WINS, DEFENDER_WINS, RETREATED }

data class RoundReport(
    val attackerTactic: Tactic,
    val defenderTactic: Tactic,
    val attackerLost: Army,
    val defenderLost: Army,
) {
    fun describe(attackerName: String, defenderName: String): String = when {
        attackerTactic == Tactic.RETREAT -> "$attackerName sounds the retreat!"
        attackerTactic.counters == defenderTactic -> "$attackerName's ${attackerTactic.label.lowercase()} breaks the ${defenderTactic.label.lowercase()}!"
        defenderTactic.counters == attackerTactic -> "$defenderName's ${defenderTactic.label.lowercase()} breaks the ${attackerTactic.label.lowercase()}!"
        else -> "${attackerTactic.label} meets ${defenderTactic.label.lowercase()}. The lines grind on."
    }
}

/**
 * A field battle fought in rounds. [attacker] and [defender] are mutated as men fall, so pass copies
 * if the originals must survive. [fortification] above 1 shelters the defender behind walls.
 */
class Battle(
    val attacker: Army,
    val defender: Army,
    private val attackerLeadership: Int,
    private val defenderLeadership: Int,
    val fortification: Float = 1f,
    private val rng: Random = Random.Default,
) {
    var round = 0
        private set
    var outcome: BattleOutcome? = null
        private set
    val isOver: Boolean get() = outcome != null

    fun defenderTactic(): Tactic {
        val r = rng.nextFloat()
        // Defenders behind walls prefer to hold.
        val holdBias = if (fortification > 1f) 0.5f else 0.34f
        return when {
            r < holdBias -> Tactic.HOLD
            r < holdBias + (1f - holdBias) / 2 -> Tactic.CHARGE
            else -> Tactic.FLANK
        }
    }

    fun fight(attackerTactic: Tactic, defenderTactic: Tactic = defenderTactic()): RoundReport {
        check(!isOver) { "Battle already decided" }
        round++
        if (attackerTactic == Tactic.RETREAT) {
            val lost = Army(soldiers = (attacker.soldiers * 0.15f).toInt(), knights = (attacker.knights * 0.1f).toInt())
            attacker.soldiers -= lost.soldiers
            attacker.knights -= lost.knights
            outcome = BattleOutcome.RETREATED
            return RoundReport(attackerTactic, defenderTactic, lost, Army())
        }

        val attHp = damage(attacker, attackerLeadership, attackerTactic, defenderTactic) /
            (fortification * takenMult(defenderTactic))
        val defHp = damage(defender, defenderLeadership, defenderTactic, attackerTactic) *
            (1f + (fortification - 1f) * 0.3f) / takenMult(attackerTactic)

        val defLost = applyLosses(defender, attHp)
        val attLost = applyLosses(attacker, defHp)

        outcome = when {
            defender.isEmpty -> BattleOutcome.ATTACKER_WINS
            attacker.isEmpty -> BattleOutcome.DEFENDER_WINS
            round >= MAX_ROUNDS -> BattleOutcome.DEFENDER_WINS
            else -> null
        }
        return RoundReport(attackerTactic, defenderTactic, attLost, defLost)
    }

    /** Fight to the end with both sides choosing at random; used for battles the player does not see. */
    fun autoResolve(): BattleOutcome {
        while (!isOver) {
            val t = listOf(Tactic.CHARGE, Tactic.HOLD, Tactic.FLANK).random(rng)
            fight(t)
        }
        return outcome!!
    }

    private fun damage(army: Army, leadership: Int, mine: Tactic, theirs: Tactic): Float {
        var mult = when (mine) {
            Tactic.CHARGE -> 1.35f
            Tactic.HOLD -> 0.75f
            Tactic.FLANK -> 1.0f
            Tactic.RETREAT -> 0f
        }
        if (mine.counters == theirs) mult *= 1.6f
        val lead = 1f + 0.08f * (leadership - 3)
        val luck = 0.8f + rng.nextFloat() * 0.4f
        return army.strength * BASE_RATE * mult * lead * luck
    }

    /** How exposed a side is while using [t]; higher means it takes less damage. */
    private fun takenMult(t: Tactic): Float = when (t) {
        Tactic.CHARGE -> 0.87f
        Tactic.HOLD -> 1.25f
        else -> 1f
    }

    private fun applyLosses(army: Army, hp: Float): Army {
        val total = army.strength
        if (total <= 0 || hp <= 0f) return Army()
        val soldierShare = army.soldiers.toFloat() / total
        var lossS = stochasticRound(hp * soldierShare)
        var lossK = stochasticRound(hp * (1f - soldierShare) / Army.KNIGHT_WEIGHT)
        if (lossS == 0 && lossK == 0 && hp >= 0.5f) {
            if (army.soldiers > 0) lossS = 1 else lossK = 1
        }
        lossS = min(lossS, army.soldiers)
        lossK = min(lossK, army.knights)
        army.soldiers -= lossS
        army.knights -= lossK
        return Army(soldiers = lossS, knights = lossK)
    }

    private fun stochasticRound(v: Float): Int {
        val f = floor(max(v, 0f))
        return f.toInt() + if (rng.nextFloat() < v - f) 1 else 0
    }

    companion object {
        const val MAX_ROUNDS = 8
        const val BASE_RATE = 0.2f
    }
}
