package com.lancesrealm.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class GameOutcome { VICTORY, DEFEAT }

/** What the player faces when marching on a territory. */
data class AttackPlan(
    val target: Territory,
    val defenderName: String,
    val defenders: Army,
    val defenderLeadership: Int,
    /** True when storming a rival's home castle; a catapult bombardment comes first. */
    val isSiege: Boolean,
)

/**
 * The whole campaign: the realm, its lords and the rules for a season. Pure logic so it can be
 * tested off-device; the Android layer only renders it and feeds it the minigame results.
 */
class GameState(val hero: Hero, private val rng: Random = Random.Default) {
    val territories: List<Territory> = WorldMap.defs.mapIndexed { i, d ->
        Territory(
            id = i, name = d.name, x = d.x, y = d.y, income = d.income, isCastle = d.castle,
            owner = if (d.castle) i else NEUTRAL,
            levy = if (d.castle) 10 else 6 + d.income / 2 + rng.nextInt(7),
        ).also { it.neighbors += WorldMap.adjacency[i] }
    }

    val lords: List<Lord> = buildList {
        add(Lord(PLAYER, hero.displayName, gold = 300, army = Army(30, 4, 1), garrison = Army(20, 2), home = 0))
        Rival.entries.forEachIndexed { i, r ->
            add(Lord(i + 1, r.displayName, gold = 250, army = Army(26, 5), garrison = Army(24, 3), home = i + 1))
        }
    }

    val player: Lord get() = lords[PLAYER]

    /** Seasons elapsed since the start of the campaign. */
    var turn = 0
        private set
    val season: Season get() = Season.entries[turn % 4]
    val year: Int get() = 1 + turn / 4

    /** Each season the player may take one great deed: a march, a raid or a tournament. */
    var deedDone = false
        private set

    val outcome: GameOutcome?
        get() = when {
            territories[player.home].owner != PLAYER -> GameOutcome.DEFEAT
            lords.drop(1).none { it.alive } -> GameOutcome.VICTORY
            else -> null
        }

    fun rival(lordId: Int): Rival = Rival.entries[lordId - 1]

    fun ownerName(owner: Int): String = if (owner == NEUTRAL) "Free folk" else lords[owner].name

    fun territoriesOf(owner: Int): List<Territory> = territories.filter { it.owner == owner }

    fun incomeOf(owner: Int): Int = territoriesOf(owner).sumOf { it.income }

    fun leadershipOf(owner: Int): Int = if (owner == PLAYER) hero.leadership else 3

    fun bordersPlayer(t: Territory): Boolean = t.neighbors.any { territories[it].owner == PLAYER }

    fun canMarchOn(t: Territory): Boolean =
        !deedDone && t.owner != PLAYER && bordersPlayer(t) && !player.army.isEmpty

    fun canRaid(t: Territory): Boolean =
        !deedDone && t.owner != PLAYER && isHomeCastle(t)

    val livingRivals: List<Lord> get() = lords.drop(1).filter { it.alive }

    // --- Mustering -------------------------------------------------------------------------

    fun buy(soldiers: Int = 0, knights: Int = 0, catapults: Int = 0): Boolean {
        val cost = soldiers * Prices.SOLDIER + knights * Prices.KNIGHT + catapults * Prices.CATAPULT
        if (cost > player.gold) return false
        player.gold -= cost
        player.army.soldiers += soldiers
        player.army.knights += knights
        player.army.catapults += catapults
        return true
    }

    /** Moves men between the field army and the home garrison; negative counts move them back out. */
    fun transferToGarrison(soldiers: Int, knights: Int = 0): Boolean {
        val a = player.army
        val g = player.garrison
        if (soldiers > a.soldiers || knights > a.knights || -soldiers > g.soldiers || -knights > g.knights) return false
        a.soldiers -= soldiers; g.soldiers += soldiers
        a.knights -= knights; g.knights += knights
        return true
    }

    // --- Great deeds -----------------------------------------------------------------------

    fun planAttack(t: Territory): AttackPlan {
        val siege = isHomeCastle(t)
        val defenders = Army(soldiers = t.levy, knights = if (t.owner == NEUTRAL) 0 else t.levy / 8)
        if (siege) {
            val g = lords[t.owner].garrison
            defenders.soldiers += g.soldiers
            defenders.knights += g.knights
        }
        return AttackPlan(t, ownerName(t.owner), defenders, leadershipOf(t.owner), siege)
    }

    fun startBattle(plan: AttackPlan, fortification: Float = 1f, garrisonLoss: Float = 0f): Battle {
        val defenders = plan.defenders.copyArmy()
        defenders.soldiers = (defenders.soldiers * (1f - garrisonLoss)).toInt()
        defenders.knights = (defenders.knights * (1f - garrisonLoss)).toInt()
        return Battle(player.army.copyArmy(), defenders, hero.leadership, plan.defenderLeadership, fortification, rng)
    }

    /** Applies a finished battle the player fought against [plan]. Returns a line for the chronicle. */
    fun concludeAttack(plan: AttackPlan, battle: Battle): String {
        check(battle.isOver)
        deedDone = true
        val catapults = player.army.catapults
        player.army.soldiers = battle.attacker.soldiers
        player.army.knights = battle.attacker.knights
        val t = plan.target
        return when (battle.outcome!!) {
            BattleOutcome.ATTACKER_WINS -> {
                if (plan.isSiege) {
                    // A stormed castle costs a catapult or two to the defenders' sallies.
                    player.army.catapults = max(0, catapults - 1)
                    val g = lords[t.owner].garrison
                    g.soldiers = 0; g.knights = 0
                }
                player.fame += if (plan.isSiege) 5 else 1
                capture(t, PLAYER)
            }
            BattleOutcome.DEFENDER_WINS -> {
                writeBackDefenders(plan, battle)
                "Your host is thrown back from ${t.name}."
            }
            BattleOutcome.RETREATED -> {
                writeBackDefenders(plan, battle)
                "You withdraw from ${t.name} to fight another day."
            }
        }
    }

    private fun writeBackDefenders(plan: AttackPlan, battle: Battle) {
        val t = plan.target
        val survivors = battle.defender
        if (plan.isSiege) {
            val g = lords[t.owner].garrison
            val levy = min(t.levy, survivors.soldiers)
            t.levy = levy
            g.soldiers = survivors.soldiers - levy
            g.knights = survivors.knights
        } else {
            t.levy = max(2, survivors.soldiers)
        }
    }

    fun raidGuards(lordId: Int): List<Int> {
        val s = rival(lordId).swordplay
        return listOf(max(1, s - 1), s, min(5, s + turn / 12))
    }

    fun concludeRaid(lordId: Int, won: Boolean): String {
        deedDone = true
        val lord = lords[lordId]
        return if (won) {
            val loot = max(lord.gold / 2, 40)
            lord.gold = max(0, lord.gold - loot)
            player.gold += loot
            player.fame += 2
            "You slip out of ${territories[lord.home].name} with $loot gold from ${lord.name}'s treasury!"
        } else {
            val ransom = min(player.gold, 60)
            player.gold -= ransom
            player.fame = max(0, player.fame - 1)
            "Captured in ${lord.name}'s halls, you pay $ransom gold in ransom."
        }
    }

    /**
     * Settles a tournament joust against [lordId]'s champion. When [forLand] the stake is a
     * territory rather than a purse.
     */
    fun concludeTournament(lordId: Int, forLand: Boolean, won: Boolean): String {
        deedDone = true
        val lord = lords[lordId]
        if (won) {
            player.fame += 3
            if (forLand) {
                val prize = territoriesOf(lordId).filter { it.id != lord.home }.randomOrNull(rng)
                if (prize != null) {
                    prize.owner = PLAYER
                    return "Victory in the lists! ${lord.name} yields ${prize.name} to you."
                }
                player.gold += 200
                return "Victory! ${lord.name} has no land to spare and pays 200 gold instead."
            }
            player.gold += 150
            return "Victory in the lists! You win a purse of 150 gold."
        }
        if (forLand) {
            val forfeit = territoriesOf(PLAYER).filter { it.id != player.home }.randomOrNull(rng)
            if (forfeit != null) {
                forfeit.owner = lordId
                return "Defeat. You must yield ${forfeit.name} to ${lord.name}."
            }
        }
        val fee = min(player.gold, 100)
        player.gold -= fee
        return "Defeat in the lists. It costs you $fee gold."
    }

    private fun capture(t: Territory, newOwner: Int): String {
        val oldOwner = t.owner
        val conqueror = lords[newOwner].name
        if (oldOwner != NEUTRAL && lords[oldOwner].home == t.id) {
            val fallen = lords[oldOwner]
            fallen.alive = false
            territoriesOf(oldOwner).forEach { it.owner = newOwner; it.levy = 4 + it.income / 4 }
            lords[newOwner].gold += fallen.gold
            fallen.gold = 0
            fallen.army.soldiers = 0; fallen.army.knights = 0
            return "${t.name} falls! ${fallen.name} is overthrown and all their lands pass to $conqueror."
        }
        t.owner = newOwner
        t.levy = 4 + t.income / 4
        return "$conqueror takes ${t.name}."
    }

    // --- The turning of the seasons ----------------------------------------------------------

    /** Lets the rivals act, collects taxes and advances the calendar. Returns the chronicle. */
    fun endSeason(): List<String> {
        val news = mutableListOf<String>()
        for (lord in livingRivals) {
            if (!lord.alive) continue
            lord.gold += incomeOf(lord.id) + 20
            payUpkeep(lord)
            aiMuster(lord)
            aiCampaign(lord)?.let(news::add)
            if (outcome == GameOutcome.DEFEAT) return news
        }
        val income = incomeOf(PLAYER)
        player.gold += income
        news.add("Your lands yield $income gold in taxes.")
        payUpkeep(player)?.let(news::add)
        territories.forEach { t ->
            val cap = 6 + t.income / 2
            if (t.levy < cap) t.levy = min(cap, t.levy + 2)
        }
        turn++
        deedDone = false
        return news
    }

    fun upkeepOf(lord: Lord): Int =
        (lord.army.soldiers + lord.garrison.soldiers) / 5 + lord.army.knights + lord.garrison.knights

    /** Pays the troops; unpaid soldiers desert. Returns news when the coffers ran dry. */
    private fun payUpkeep(lord: Lord): String? {
        val upkeep = upkeepOf(lord)
        if (lord.gold >= upkeep) {
            lord.gold -= upkeep
            return null
        }
        lord.gold = 0
        val deserters = lord.army.soldiers / 10 + lord.army.knights / 10
        lord.army.soldiers -= lord.army.soldiers / 10
        lord.army.knights -= lord.army.knights / 10
        return if (deserters > 0) "Unpaid, $deserters of your men desert!" else null
    }

    /** Rivals keep growing hosts as the years pass, but never without bound. */
    private fun aiArmyCap(): Int = 70 + turn * 7

    private fun aiMuster(lord: Lord) {
        val garrisonTarget = min(25 + turn * 2, 90)
        if (lord.garrison.strength < garrisonTarget) {
            val n = min((garrisonTarget - lord.garrison.strength), lord.gold / Prices.SOLDIER)
            lord.garrison.soldiers += n
            lord.gold -= n * Prices.SOLDIER
        }
        val room = aiArmyCap() - lord.army.strength
        if (room <= 0) return
        val budget = min((lord.gold * 0.7f).toInt(), room * Prices.SOLDIER * 2)
        val knights = budget * 4 / 10 / Prices.KNIGHT
        val soldiers = (budget - knights * Prices.KNIGHT) / Prices.SOLDIER
        lord.army.knights += knights
        lord.army.soldiers += soldiers
        lord.gold -= knights * Prices.KNIGHT + soldiers * Prices.SOLDIER
    }

    private fun aiCampaign(lord: Lord): String? {
        val r = rival(lord.id)
        if (lord.army.strength < 20 || rng.nextFloat() > r.aggression) return null
        val options = territories.filter { t ->
            t.owner != lord.id && t.neighbors.any { territories[it].owner == lord.id } &&
                !(isHomeCastle(t) && turn < GRACE_SEASONS)
        }
        val scored = options.map { t ->
            val plan = planAiDefence(t)
            val needed = plan.first.strength * plan.second * if (isHomeCastle(t)) 1.5f else 1.25f
            val home = territories[lord.home]
            val distance = hypot((t.x - home.x).toDouble(), (t.y - home.y).toDouble()).toFloat()
            val score = t.income * 1.5f - plan.first.strength * 0.8f - distance * 40f +
                when (t.owner) {
                    NEUTRAL -> 12f
                    PLAYER -> 10f
                    // Rivals squabble among themselves only when nothing better is left.
                    else -> -35f
                }
            Triple(t, needed, score)
        }.filter { lord.army.strength > it.second }
        val (target, _, _) = scored.maxByOrNull { it.third } ?: return null
        val (defenders, fort) = planAiDefence(target)
        val battle = Battle(lord.army.copyArmy(), defenders, 3, leadershipOf(target.owner), fort, rng)
        val result = battle.autoResolve()
        lord.army.soldiers = battle.attacker.soldiers
        lord.army.knights = battle.attacker.knights
        val defenderName = ownerName(target.owner)
        val siege = isHomeCastle(target)
        val relief = siege && target.owner == PLAYER
        if (siege) {
            // Survivors return to the garrison first, then (for the player) to the field army.
            val owner = lords[target.owner]
            var s = max(0, battle.defender.soldiers - target.levy)
            var k = battle.defender.knights
            val inGarrison = owner.garrison.copyArmy()
            owner.garrison.soldiers = min(s, inGarrison.soldiers); s -= owner.garrison.soldiers
            owner.garrison.knights = min(k, inGarrison.knights); k -= owner.garrison.knights
            if (relief) { player.army.soldiers = s; player.army.knights = k }
        } else if (result != BattleOutcome.ATTACKER_WINS) {
            target.levy = max(2, battle.defender.soldiers)
        }
        return if (result == BattleOutcome.ATTACKER_WINS) {
            val news = capture(target, lord.id)
            if (!siege) {
                // Leave a company behind so the prize is not lost again at once.
                val company = min(lord.army.soldiers / 5, 15)
                lord.army.soldiers -= company
                target.levy += company
            }
            news
        } else if (relief) {
            "${lord.name} besieges ${target.name}! Your host rides to its defence and drives them off."
        } else {
            "${lord.name} marches on ${target.name}, but $defenderName holds firm."
        }
    }

    private fun isHomeCastle(t: Territory) = t.isCastle && t.owner != NEUTRAL && lords[t.owner].home == t.id

    private fun planAiDefence(t: Territory): Pair<Army, Float> {
        val defenders = Army(soldiers = t.levy, knights = if (t.owner == NEUTRAL) 0 else t.levy / 8)
        if (!isHomeCastle(t)) return defenders to 1f
        val g = lords[t.owner].garrison
        defenders.soldiers += g.soldiers
        defenders.knights += g.knights
        if (t.owner == PLAYER) {
            // The player's field host comes home to lift a siege.
            defenders.soldiers += player.army.soldiers
            defenders.knights += player.army.knights
        }
        return defenders to 1.9f
    }

    // --- Save games ------------------------------------------------------------------------

    fun serialize(): String = buildString {
        appendLine("v=1")
        appendLine("hero=${hero.name}")
        appendLine("turn=$turn")
        appendLine("deed=$deedDone")
        lords.forEach { l ->
            appendLine(
                "lord${l.id}=" + listOf(
                    l.gold, l.army.soldiers, l.army.knights, l.army.catapults,
                    l.garrison.soldiers, l.garrison.knights, if (l.alive) 1 else 0, l.fame,
                ).joinToString(",")
            )
        }
        territories.forEach { appendLine("terr${it.id}=${it.owner},${it.levy}") }
    }

    companion object {
        /** Rivals leave castles alone for the first year while they carve up the free lands. */
        const val GRACE_SEASONS = 4

        fun deserialize(text: String, rng: Random = Random.Default): GameState? = runCatching {
            val map = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            if (map["v"] != "1") return null
            val state = GameState(Hero.valueOf(map.getValue("hero")), rng)
            state.turn = map.getValue("turn").toInt()
            state.deedDone = map.getValue("deed").toBoolean()
            state.lords.forEach { l ->
                val f = map.getValue("lord${l.id}").split(',').map { it.toInt() }
                l.gold = f[0]
                l.army.soldiers = f[1]; l.army.knights = f[2]; l.army.catapults = f[3]
                l.garrison.soldiers = f[4]; l.garrison.knights = f[5]
                l.alive = f[6] == 1
                l.fame = f[7]
            }
            state.territories.forEach { t ->
                val f = map.getValue("terr${t.id}").split(',').map { it.toInt() }
                t.owner = f[0]; t.levy = f[1]
            }
            state
        }.getOrNull()
    }
}
