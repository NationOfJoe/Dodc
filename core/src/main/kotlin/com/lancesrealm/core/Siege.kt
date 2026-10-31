package com.lancesrealm.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Catapult bombardment before storming a castle. Knock stones out of the curtain wall to weaken the
 * defence, or lob shot over it to thin the garrison. World units: x to the right, y up, ground at 0.
 */
class Siege(val shots: Int, val wallHp: Int = 1, private val rng: Random = Random.Default) {
    enum class Phase { AIMING, FLYING, FINISHED }

    var phase = if (shots > 0) Phase.AIMING else Phase.FINISHED
        private set
    var shotsLeft = shots
        private set
    /** Remaining hit points of each wall block, indexed [col * ROWS + row], row 0 at the ground. */
    val blocks = IntArray(COLS * ROWS) { wallHp }
    var interiorHits = 0
        private set
    var wind = 0f
        private set
    var message = "Drag back from the catapult and release"
        private set

    var projX = 0f
        private set
    var projY = 0f
        private set
    private var velX = 0f
    private var velY = 0f
    val trail = ArrayList<Float>()

    init {
        rollWind()
    }

    /** Bumped on every impact so the screen can shake and throw debris. */
    var impacts = 0
        private set
    var impactX = 0f
        private set
    var impactY = 0f
        private set
    var lastHitWall = false
        private set

    val blocksStanding: Int get() = blocks.count { it > 0 }

    /** Fraction of the wall's total strength battered away; cracked stone counts for something. */
    val breach: Float get() = 1f - blocks.sum().toFloat() / (blocks.size * wallHp)

    /** Defensive multiplier handed to the storming [Battle]. */
    val fortification: Float get() = 1f + (maxFortification(wallHp) - 1f) * (1f - breach)

    /** Fraction of the garrison lost to shot falling inside the walls. */
    val garrisonLoss: Float get() = (interiorHits * 0.07f).coerceAtMost(0.5f)

    fun isBlockStanding(col: Int, row: Int) = blocks[col * ROWS + row] > 0

    fun blockHp(col: Int, row: Int) = blocks[col * ROWS + row]

    fun blockLeft(col: Int) = WALL_X + col * BLOCK_W
    fun blockBottom(row: Int) = row * BLOCK_H

    /** [angleDeg] above the horizon, [power] in 0..1. */
    fun fire(angleDeg: Float, power: Float) {
        if (phase != Phase.AIMING) return
        val a = Math.toRadians(angleDeg.coerceIn(MIN_ANGLE, MAX_ANGLE).toDouble())
        val speed = launchSpeed(power)
        velX = (cos(a) * speed).toFloat()
        velY = (sin(a) * speed).toFloat()
        projX = LAUNCH_X
        projY = LAUNCH_Y
        trail.clear()
        shotsLeft--
        phase = Phase.FLYING
    }

    fun update(dt: Float) {
        if (phase != Phase.FLYING) return
        val steps = 8
        val h = dt / steps
        repeat(steps) {
            velX += wind * h
            velY -= GRAVITY * h
            projX += velX * h
            projY += velY * h
            if (checkImpact()) return
        }
        trail.add(projX); trail.add(projY)
        if (projX > FIELD_END || projX < -20f) endShot("Wide of the mark")
    }

    private fun checkImpact(): Boolean {
        val col = ((projX - WALL_X) / BLOCK_W).toInt()
        val row = (projY / BLOCK_H).toInt()
        if (projX >= WALL_X && col in 0 until COLS && row in 0 until ROWS && blocks[col * ROWS + row] > 0) {
            val i = col * ROWS + row
            blocks[i]--
            // Masonry above or beside may be shaken loose too.
            if (rng.nextFloat() < 0.45f) {
                val r2 = (row + if (rng.nextBoolean()) 1 else -1).coerceIn(0, ROWS - 1)
                val j = col * ROWS + r2
                if (blocks[j] > 0) blocks[j]--
            }
            impact(true)
            endShot(if (blocks[i] == 0) "Stones crash from the wall!" else "The wall cracks but holds!")
            return true
        }
        if (projY <= 0f) {
            projY = 0f
            impact(false)
            if (projX > WALL_END && projX < KEEP_END) {
                interiorHits++
                endShot("A hit inside the bailey!")
            } else {
                endShot(if (projX < WALL_X) "Short! The stone ploughs the field" else "Over the castle entirely")
            }
            return true
        }
        return false
    }

    private fun impact(wall: Boolean) {
        impacts++
        impactX = projX
        impactY = projY
        lastHitWall = wall
    }

    private fun endShot(text: String) {
        message = text
        trail.add(projX); trail.add(projY)
        if (shotsLeft <= 0) {
            phase = Phase.FINISHED
        } else {
            phase = Phase.AIMING
            rollWind()
        }
    }

    private fun rollWind() {
        wind = (rng.nextFloat() - 0.5f) * 8f
    }

    companion object {
        const val GRAVITY = 20f
        const val LAUNCH_X = 6f
        const val LAUNCH_Y = 8f
        const val WALL_X = 150f
        const val BLOCK_W = 5f
        const val BLOCK_H = 6f
        const val COLS = 2
        const val ROWS = 7
        const val WALL_END = WALL_X + COLS * BLOCK_W
        const val KEEP_END = 210f
        const val FIELD_END = 240f
        const val MIN_ANGLE = 15f
        const val MAX_ANGLE = 75f

        fun launchSpeed(power: Float) = 30f + 42f * power.coerceIn(0f, 1f)

        /** Defence of an unbroken wall whose blocks have [wallHp] hit points. */
        fun maxFortification(wallHp: Int) = 1.6f + 0.15f * wallHp
    }
}
