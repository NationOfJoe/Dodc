package com.lancesrealm.core

/** A playable hero. Stats run 1..5. */
enum class Hero(
    val displayName: String,
    val epithet: String,
    val leadership: Int,
    val jousting: Int,
    val swordplay: Int,
) {
    ALDRIC("Aldric Ashford", "a cunning commander", 5, 2, 2),
    MAREN("Maren of Highcliff", "champion of the lists", 2, 5, 2),
    CORWIN("Corwin Greymantle", "a blade without equal", 2, 2, 5),
    EDDA("Edda Thornwood", "steady in all things", 3, 3, 3),
}

/** Rival lords controlled by the computer. Index 0 is reserved for the player. */
enum class Rival(val displayName: String, val jousting: Int, val swordplay: Int, val aggression: Float) {
    VASK("Baron Vask", 3, 4, 0.8f),
    MORLAN("Duke Morlan", 4, 2, 0.5f),
    YSOLDE("Countess Ysolde", 2, 3, 0.65f),
}

const val NEUTRAL = -1
const val PLAYER = 0

data class Army(var soldiers: Int = 0, var knights: Int = 0, var catapults: Int = 0) {
    /** Fighting strength; catapults only matter during sieges. */
    val strength: Int get() = soldiers + knights * KNIGHT_WEIGHT
    val isEmpty: Boolean get() = soldiers <= 0 && knights <= 0

    fun copyArmy() = Army(soldiers, knights, catapults)

    companion object {
        const val KNIGHT_WEIGHT = 3
    }
}

class Lord(
    val id: Int,
    val name: String,
    var gold: Int,
    val army: Army,
    val garrison: Army,
    var home: Int,
    var alive: Boolean = true,
    var fame: Int = 0,
)

class Territory(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val income: Int,
    val isCastle: Boolean,
    var owner: Int,
    /** Defenders raised from the land itself (vassals, militia). */
    var levy: Int,
) {
    val neighbors = mutableSetOf<Int>()
}

object Prices {
    const val SOLDIER = 4
    const val KNIGHT = 18
    const val CATAPULT = 60
}

enum class Season { SPRING, SUMMER, AUTUMN, WINTER;
    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}
