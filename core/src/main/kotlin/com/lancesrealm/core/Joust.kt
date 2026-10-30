package com.lancesrealm.core

import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class PassResult(val label: String) {
    UNHORSED("Unhorsed!"),
    STRUCK("Lance shattered on the shield"),
    MISSED("A miss"),
}

/**
 * A joust seen down the lance. The rival's shield sways as their horse gallops in; the player steers
 * the lance tip toward it. Aim coordinates are normalised to [-1, 1] on both axes.
 */
class Joust(
    private val playerSkill: Int,
    private val rivalSkill: Int,
    private val rng: Random = Random.Default,
) {
    enum class Phase { READY, RIDING, RESULT, FINISHED }

    var phase = Phase.READY
        private set
    var phaseTime = 0f
        private set
    var pass = 1
        private set
    var playerPoints = 0
        private set
    var rivalPoints = 0
        private set
    var lastPlayerResult: PassResult? = null
        private set
    var lastRivalResult: PassResult? = null
        private set
    /** True once finished and the player carried the day. */
    var playerWon = false
        private set

    var lanceX = 0f
        private set
    var lanceY = 0.6f
        private set
    var shieldX = 0f
        private set
    var shieldY = 0f
        private set

    private var aimX = 0f
    private var aimY = 0.6f
    private var phaseA = 0f
    private var phaseB = 0f

    /** 0 when the rival is far away, 1 at the moment of impact. */
    val approach: Float get() = if (phase == Phase.RIDING) (phaseTime / RIDE_TIME).coerceIn(0f, 1f) else if (phase == Phase.READY) 0f else 1f

    fun aim(x: Float, y: Float) {
        aimX = x.coerceIn(-1f, 1f)
        aimY = y.coerceIn(-1f, 1f)
    }

    fun update(dt: Float) {
        phaseTime += dt
        when (phase) {
            Phase.READY -> if (phaseTime >= READY_TIME) startRide()
            Phase.RIDING -> {
                moveShield()
                moveLance(dt)
                if (phaseTime >= RIDE_TIME) resolvePass()
            }
            Phase.RESULT -> if (phaseTime >= RESULT_TIME) nextPass()
            Phase.FINISHED -> Unit
        }
    }

    private fun startRide() {
        phase = Phase.RIDING
        phaseTime = 0f
        phaseA = rng.nextFloat() * 6.28f
        phaseB = rng.nextFloat() * 6.28f
        lanceX = 0f; lanceY = 0.6f
        aimX = lanceX; aimY = lanceY
        moveShield()
    }

    private fun moveShield() {
        val t = phaseTime
        val sway = 0.25f + 0.07f * rivalSkill
        shieldX = sin(t * 1.6f + phaseA) * sway * 0.8f
        shieldY = sin(t * 5.2f) * 0.18f + sin(t * 2.1f + phaseB) * sway * 0.7f
    }

    private fun moveLance(dt: Float) {
        val speed = 1.0f + 0.22f * playerSkill
        val dx = aimX - lanceX
        val dy = aimY - lanceY
        val d = hypot(dx, dy)
        val step = speed * dt
        if (d <= step) { lanceX = aimX; lanceY = aimY } else { lanceX += dx / d * step; lanceY += dy / d * step }
        // The gallop jolts the lance; better jousters steady it.
        lanceY += sin(phaseTime * 11f) * 0.012f * (6 - playerSkill)
        lanceX = lanceX.coerceIn(-1f, 1f)
        lanceY = lanceY.coerceIn(-1f, 1f)
    }

    private fun resolvePass() {
        val d = hypot(lanceX - shieldX, lanceY - shieldY)
        val mine = when {
            d < UNHORSE_RADIUS -> PassResult.UNHORSED
            d < SHIELD_RADIUS -> PassResult.STRUCK
            else -> PassResult.MISSED
        }
        val r = rng.nextFloat()
        val pUnhorse = 0.03f + 0.035f * rivalSkill - 0.012f * playerSkill
        val pStrike = 0.2f + 0.09f * rivalSkill
        val theirs = when {
            r < pUnhorse -> PassResult.UNHORSED
            r < pUnhorse + pStrike -> PassResult.STRUCK
            else -> PassResult.MISSED
        }
        lastPlayerResult = mine
        lastRivalResult = theirs
        if (mine == PassResult.STRUCK) playerPoints++
        if (theirs == PassResult.STRUCK) rivalPoints++
        phase = Phase.RESULT
        phaseTime = 0f
    }

    private fun nextPass() {
        val mine = lastPlayerResult
        val theirs = lastRivalResult
        val decided = when {
            mine == PassResult.UNHORSED && theirs != PassResult.UNHORSED -> { playerWon = true; true }
            theirs == PassResult.UNHORSED && mine != PassResult.UNHORSED -> { playerWon = false; true }
            pass >= PASSES && playerPoints != rivalPoints -> { playerWon = playerPoints > rivalPoints; true }
            pass >= MAX_PASSES -> { playerWon = playerPoints > rivalPoints; true }
            else -> false
        }
        phaseTime = 0f
        if (decided) {
            phase = Phase.FINISHED
        } else {
            pass++
            phase = Phase.READY
        }
    }

    companion object {
        const val READY_TIME = 1.4f
        const val RIDE_TIME = 3.2f
        const val RESULT_TIME = 2.4f
        const val PASSES = 3
        const val MAX_PASSES = 5
        const val SHIELD_RADIUS = 0.22f
        const val UNHORSE_RADIUS = 0.07f
    }
}
