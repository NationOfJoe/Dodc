package com.lancesrealm.core

import kotlin.random.Random

enum class Height { HIGH, LOW }

/**
 * Sword fight against a line of guards. Guards raise their blade high or low before striking; block
 * at the same height to stagger them, or strike where their guard is not.
 */
class Duel(
    swordplay: Int,
    /** Guard skills grouped by the room they defend; the raider fights through rooms in order. */
    private val rooms: List<List<Int>>,
    private val rng: Random = Random.Default,
) {
    private val guardSkills = rooms.flatten()
    private val roomOf = rooms.flatMapIndexed { r, guards -> List(guards.size) { r } }

    enum class EnemyState { GUARD, WINDUP, RECOVER, STAGGER, HURT, DOWN }

    val playerMaxHp = 3 + swordplay
    var playerHp = playerMaxHp
        private set
    private val attackCooldownTime = 0.5f - 0.04f * swordplay
    private val blockWindow = 0.32f + 0.05f * swordplay

    var guardIndex = 0
        private set
    val guardCount: Int get() = guardSkills.size
    val guardMaxHp = GUARD_HP
    var guardHp = GUARD_HP
        private set
    var enemyState = EnemyState.GUARD
        private set
    var enemyHeight = Height.HIGH
        private set
    var enemyStateTime = 0f
        private set
    private var enemyStateDuration = 1f

    var attackCooldown = 0f
        private set
    var blockCooldown = 0f
        private set
    var blockTime = 0f
        private set
    var blockHeight = Height.HIGH
        private set
    var swingTime = 0f
        private set
    var swingHeight = Height.HIGH
        private set

    var message = ""
        private set
    var messageTime = 0f
        private set

    val roomCount: Int get() = rooms.size
    val roomIndex: Int get() = roomOf[guardIndex.coerceAtMost(roomOf.lastIndex)]
    /** Seconds left of the pause between rooms; nothing happens while it runs. */
    var transition = 0f
        private set

    val playerWon: Boolean get() = guardIndex >= guardSkills.size
    val playerLost: Boolean get() = playerHp <= 0
    val isOver: Boolean get() = playerWon || playerLost

    private val skill: Int get() = guardSkills[guardIndex.coerceAtMost(guardSkills.lastIndex)]
    val windupDuration: Float get() = 0.78f - 0.07f * skill

    init {
        enterGuard()
    }

    fun update(dt: Float) {
        if (isOver) return
        if (transition > 0f) {
            transition = (transition - dt).coerceAtLeast(0f)
            if (transition == 0f) enterGuard()
            return
        }
        attackCooldown = (attackCooldown - dt).coerceAtLeast(0f)
        blockCooldown = (blockCooldown - dt).coerceAtLeast(0f)
        blockTime = (blockTime - dt).coerceAtLeast(0f)
        swingTime = (swingTime - dt).coerceAtLeast(0f)
        messageTime += dt
        enemyStateTime += dt

        when (enemyState) {
            EnemyState.GUARD -> {
                // Shift guard now and then to keep the player honest.
                if (rng.nextFloat() < dt * 0.9f) enemyHeight = other(enemyHeight)
                if (enemyStateTime >= enemyStateDuration) {
                    setEnemy(EnemyState.WINDUP, windupDuration)
                    enemyHeight = if (rng.nextBoolean()) Height.HIGH else Height.LOW
                }
            }
            EnemyState.WINDUP -> if (enemyStateTime >= enemyStateDuration) enemyStrike()
            EnemyState.RECOVER, EnemyState.STAGGER, EnemyState.HURT ->
                if (enemyStateTime >= enemyStateDuration) enterGuard()
            EnemyState.DOWN -> if (enemyStateTime >= enemyStateDuration) {
                guardIndex++
                if (!playerWon) {
                    guardHp = GUARD_HP
                    if (roomOf[guardIndex] != roomOf[guardIndex - 1]) {
                        // Catch your breath on the stair before the next room.
                        playerHp = (playerHp + 1).coerceAtMost(playerMaxHp)
                        transition = ROOM_PAUSE
                        say("")
                    } else {
                        say("Another guard steps forward!")
                        enterGuard()
                    }
                }
            }
        }
    }

    fun attack(height: Height) {
        if (isOver || transition > 0f || attackCooldown > 0f || enemyState == EnemyState.DOWN) return
        attackCooldown = attackCooldownTime
        swingTime = SWING_TIME
        swingHeight = height
        when (enemyState) {
            EnemyState.GUARD -> if (enemyHeight == height) {
                say("Parried!")
                // A parry invites a swift riposte.
                setEnemy(EnemyState.WINDUP, windupDuration * 0.85f)
                enemyHeight = if (rng.nextBoolean()) Height.HIGH else Height.LOW
            } else {
                hitEnemy(1)
            }
            EnemyState.WINDUP, EnemyState.RECOVER -> hitEnemy(1)
            EnemyState.STAGGER -> hitEnemy(2)
            EnemyState.HURT, EnemyState.DOWN -> Unit
        }
    }

    fun block(height: Height) {
        if (isOver || transition > 0f || blockCooldown > 0f) return
        blockCooldown = 0.45f
        blockTime = blockWindow
        blockHeight = height
    }

    private fun enemyStrike() {
        if (blockTime > 0f && blockHeight == enemyHeight) {
            say("Blocked! He staggers!")
            setEnemy(EnemyState.STAGGER, 0.9f)
        } else {
            playerHp--
            say(if (playerHp > 0) "You are wounded!" else "You fall!")
            setEnemy(EnemyState.RECOVER, 0.45f)
        }
    }

    private fun hitEnemy(damage: Int) {
        guardHp -= damage
        if (guardHp <= 0) {
            guardHp = 0
            say(if (guardIndex == guardSkills.lastIndex) "The last guard falls!" else "He falls!")
            setEnemy(EnemyState.DOWN, 1.1f)
        } else {
            say(if (damage > 1) "A telling blow!" else "A hit!")
            setEnemy(EnemyState.HURT, 0.35f)
        }
    }

    private fun enterGuard() {
        setEnemy(EnemyState.GUARD, (1.6f - 0.18f * skill) * (0.6f + rng.nextFloat() * 0.8f))
    }

    private fun setEnemy(state: EnemyState, duration: Float) {
        enemyState = state
        enemyStateTime = 0f
        enemyStateDuration = duration
    }

    private fun say(text: String) {
        message = text
        messageTime = 0f
    }

    private fun other(h: Height) = if (h == Height.HIGH) Height.LOW else Height.HIGH

    companion object {
        const val GUARD_HP = 3
        const val SWING_TIME = 0.22f
        const val ROOM_PAUSE = 2.2f
    }
}
