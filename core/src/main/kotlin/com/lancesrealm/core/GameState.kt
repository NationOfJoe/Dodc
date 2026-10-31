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
    /** Hit points of each block in the curtain wall; grows with the defender's power. */
    val wallHp: Int = 1,
)

/** One stage of a castle raid. */
data class RaidRoom(val name: String, val guards: List<Int>)

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

    /** Territory where the player's field host stands. */
    var armyAt = 0
        private set
    var movesLeft = MOVES_PER_SEASON
        private set
    val armyAtHome: Boolean get() = armyAt == player.home

    /** Seasons of truce remaining with each lord, indexed by lord id. */
    val truce = IntArray(4)

    /** Lord holding the player's sister hostage, or [NEUTRAL] if she is not a captive. */
    var captor = NEUTRAL
        private set
    var siblingFreed = false
        private set

    /** Season of each rival's last gloating letter, so they do not write every time. */
    private val lastTaunt = IntArray(4) { -99 }

    /** Story panels waiting to be shown, oldest first. */
    val events = ArrayDeque<StoryEvent>()

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

    fun atTruce(owner: Int): Boolean = owner in 1..3 && truce[owner] > 0

    fun canMoveTo(t: Territory): Boolean =
        movesLeft > 0 && t.owner == PLAYER && t.id in territories[armyAt].neighbors

    fun canMarchOn(t: Territory): Boolean =
        !deedDone && t.owner != PLAYER && t.id in territories[armyAt].neighbors &&
            !player.army.isEmpty && !atTruce(t.owner)

    fun canRaid(t: Territory): Boolean =
        !deedDone && t.owner != PLAYER && isHomeCastle(t) && !atTruce(t.owner)

    fun isRescue(lordId: Int): Boolean = captor == lordId && !siblingFreed

    val livingRivals: List<Lord> get() = lords.drop(1).filter { it.alive }

    /** How stoutly a lord's castle is built: 1..3, rising as their realm grows. */
    fun wallHp(owner: Int): Int = if (owner == PLAYER) 2 else 1 + min(2, (territoriesOf(owner).size - 1) / 3)

    // --- The field host --------------------------------------------------------------------

    fun moveArmy(t: Territory): Boolean {
        if (!canMoveTo(t)) return false
        armyAt = t.id
        movesLeft--
        return true
    }

    /** Steps between two territories across any land. */
    fun distance(from: Int, to: Int): Int {
        if (from == to) return 0
        val seen = mutableSetOf(from)
        var frontier = listOf(from)
        var d = 0
        while (frontier.isNotEmpty()) {
            d++
            frontier = frontier.flatMap { territories[it].neighbors }.filter { seen.add(it) }
            if (to in frontier) return d
        }
        return Int.MAX_VALUE
    }

    /** The host only lifts a siege of Highmere if it is close enough to ride home in time. */
    val hostCanRelieveHome: Boolean get() = distance(armyAt, player.home) <= 1

    // --- Mustering -------------------------------------------------------------------------

    /** Recruits muster at Highmere: they join the host there, or wait in the garrison. */
    fun buy(soldiers: Int = 0, knights: Int = 0, catapults: Int = 0): Boolean {
        val cost = soldiers * Prices.SOLDIER + knights * Prices.KNIGHT + catapults * Prices.CATAPULT
        if (cost > player.gold) return false
        player.gold -= cost
        val into = if (armyAtHome) player.army else player.garrison
        into.soldiers += soldiers
        into.knights += knights
        into.catapults += catapults
        return true
    }

    /** Moves men between the host and the garrison; only possible while the host is at Highmere. */
    fun transferToGarrison(soldiers: Int, knights: Int = 0): Boolean {
        if (!armyAtHome) return false
        val a = player.army
        val g = player.garrison
        if (soldiers > a.soldiers || knights > a.knights || -soldiers > g.soldiers || -knights > g.knights) return false
        a.soldiers -= soldiers; g.soldiers += soldiers
        a.knights -= knights; g.knights += knights
        collectCatapults()
        return true
    }

    /** Siege engines built while the host was away roll out to join it when it comes home. */
    private fun collectCatapults() {
        if (!armyAtHome) return
        player.army.catapults += player.garrison.catapults
        player.garrison.catapults = 0
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
        return AttackPlan(t, ownerName(t.owner), defenders, leadershipOf(t.owner), siege, if (siege) wallHp(t.owner) else 1)
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
                    // A stormed castle costs a catapult to the defenders' sallies.
                    player.army.catapults = max(0, catapults - 1)
                    val g = lords[t.owner].garrison
                    g.soldiers = 0; g.knights = 0
                }
                player.fame += if (plan.isSiege) 5 else 1
                armyAt = t.id
                movesLeft = 0
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

    /** The chambers a raider must fight through, ending at the treasury or, for a rescue, the tower. */
    fun raidRooms(lordId: Int): List<RaidRoom> {
        val s = rival(lordId).swordplay
        val late = turn / 10
        fun c(v: Int) = v.coerceIn(1, 5)
        val rooms = mutableListOf(
            RaidRoom("The Gatehouse", listOf(c(s - 1))),
            RaidRoom("The Great Hall", listOf(c(s - 1), c(s))),
        )
        rooms += if (isRescue(lordId)) {
            RaidRoom("The Tower Stair", listOf(c(s + late), c(s + 1)))
        } else {
            RaidRoom("The Treasury", listOf(c(s + late)))
        }
        return rooms
    }

    fun concludeRaid(lordId: Int, won: Boolean): String {
        deedDone = true
        val lord = lords[lordId]
        return if (won) {
            val loot = max(lord.gold / 2, 40)
            lord.gold = max(0, lord.gold - loot)
            player.gold += loot
            player.fame += 2
            if (isRescue(lordId)) {
                freeSibling()
                "You fight your way up the tower and carry $SIBLING_NAME out of ${territories[lord.home].name}, with $loot gold besides!"
            } else {
                "You slip out of ${territories[lord.home].name} with $loot gold from ${lord.name}'s treasury!"
            }
        } else {
            val ransom = min(player.gold, 60)
            player.gold -= ransom
            player.fame = max(0, player.fame - 1)
            "Captured in ${lord.name}'s halls, you pay $ransom gold in ransom."
        }
    }

    private fun freeSibling() {
        siblingFreed = true
        captor = NEUTRAL
        player.army.knights += 6
        player.fame += 5
        events += StoryEvent(
            EventKind.SIBLING_FREED, "Reunited",
            "$SIBLING_NAME is free! Her household knights, sworn to Highmere, ride out to meet you: six knights " +
                "join your host. Word of the rescue runs through every hall in Valdria.",
            Scene.REUNION,
        )
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
            val forfeit = territoriesOf(PLAYER).filter { it.id != player.home && it.id != armyAt }.randomOrNull(rng)
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
            if (captor == oldOwner) {
                if (newOwner == PLAYER) {
                    freeSibling()
                } else {
                    captor = newOwner
                    events += StoryEvent(
                        EventKind.SIBLING_MOVED, "A Prisoner Changes Hands",
                        "With ${fallen.name} overthrown, $SIBLING_NAME is dragged in chains to " +
                            "${territories[lords[newOwner].home].name}, a hostage now of ${lords[newOwner].name}.",
                        Scene.TOWER, lordId = newOwner,
                    )
                }
            }
            return "${t.name} falls! ${fallen.name} is overthrown and all their lands pass to $conqueror."
        }
        t.owner = newOwner
        t.levy = 4 + t.income / 4
        return "$conqueror takes ${t.name}."
    }

    // --- Story events ----------------------------------------------------------------------

    /** Applies the player's choice for an event that offered one. Returns a line of outcome. */
    fun resolveEvent(event: StoryEvent, choice: Int): String = when (event.kind) {
        EventKind.MERCENARIES -> if (choice == 0) {
            if (player.gold >= event.cost) {
                player.gold -= event.cost
                player.army.soldiers += event.amount
                "${event.amount} mercenaries march under your banner."
            } else {
                "You cannot meet their price. They drift away toward ${ownerName(livingRivals.firstOrNull()?.id ?: NEUTRAL)}."
            }
        } else {
            "The sellswords shrug and take the road east."
        }
        EventKind.TRUCE_OFFER -> if (choice == 0) {
            truce[event.lordId] = TRUCE_SEASONS
            "You seal the truce with ${lords[event.lordId].name}. It will hold for a year."
        } else {
            player.fame += 1
            "You send the envoy home with a curt reply. ${lords[event.lordId].name} will not forget it."
        }
        else -> ""
    }

    private fun rollSeasonEvent() {
        if (turn < 1 || rng.nextFloat() > 0.35f) return
        val income = incomeOf(PLAYER)
        val e = when (rng.nextInt(6)) {
            0 -> {
                if (player.army.soldiers < 10) return
                val lost = player.army.soldiers * 15 / 100
                player.army.soldiers -= lost
                StoryEvent(
                    EventKind.PLAGUE, "Sickness in the Camp",
                    "A fever creeps through the tents of your host. Before it passes, $lost men are dead or too weak to march.",
                    Scene.PLAGUE, amount = lost,
                )
            }
            1 -> {
                val loss = min(player.gold, income / 2)
                player.gold -= loss
                StoryEvent(
                    EventKind.POOR_HARVEST, "A Blighted Harvest",
                    "Rain rots the wheat in the fields. Your stewards come home with $loss gold less than you were promised.",
                    Scene.POOR_HARVEST, amount = loss,
                )
            }
            2 -> {
                val gain = income / 2 + 20
                player.gold += gain
                StoryEvent(
                    EventKind.GOOD_HARVEST, "Golden Fields",
                    "The barns of your lands are full to the rafters. The extra tithe brings in $gain gold.",
                    Scene.GOOD_HARVEST, amount = gain,
                )
            }
            3 -> {
                val cost = min(player.gold, 40 + turn * 4)
                player.gold -= cost
                val burned = player.garrison.soldiers / 10
                player.garrison.soldiers -= burned
                StoryEvent(
                    EventKind.FIRE, "Fire at Highmere",
                    "A blaze in the stables spreads to the storehouses. $burned of the garrison die fighting it, " +
                        "and repairs cost $cost gold.",
                    Scene.FIRE, amount = cost,
                )
            }
            4 -> {
                val men = 15 + turn
                val cost = men * 3
                StoryEvent(
                    EventKind.MERCENARIES, "Swords for Hire",
                    "A company of $men hard-bitten mercenaries rides up to your camp. Their captain offers their " +
                        "service for $cost gold, paid up front.",
                    Scene.MERCENARIES, amount = men, cost = cost,
                    choices = listOf("Hire them ($cost gold)", "Send them away"),
                )
            }
            else -> {
                val name = Story.errantKnight(turn)
                player.army.knights += 2
                StoryEvent(
                    EventKind.WANDERING_KNIGHT, "A Knight Errant",
                    "$name, landless but hungry for glory, kneels before you and offers a lance and a squire. " +
                        "Two knights join your host.",
                    Scene.KNIGHT,
                )
            }
        }
        events += e
    }

    private fun rollTruceOffer() {
        if (turn < GRACE_SEASONS || rng.nextFloat() > 0.12f) return
        val lord = livingRivals.filter { truce[it.id] == 0 }.randomOrNull(rng) ?: return
        events += StoryEvent(
            EventKind.TRUCE_OFFER, "An Envoy from ${lord.name}",
            "${lord.name}'s herald bows low. His lord proposes a truce of one year: neither of you will march on " +
                "or raid the other until it ends.",
            Scene.TRUCE, lordId = lord.id,
            choices = listOf("Accept the truce", "Refuse"),
        )
    }

    /** Lands far from the host, taxed for war, may throw off your rule. */
    private fun rollRebellion() {
        if (turn < GRACE_SEASONS) return
        val held = territoriesOf(PLAYER).filter { it.id != player.home }
        val overstretch = max(0, held.size - 4) * 0.015f
        for (t in held.shuffled(rng)) {
            if (t.id == armyAt || armyAt in t.neighbors) continue
            val chance = min(0.12f, overstretch + if (t.levy < 6) 0.02f else 0f)
            if (rng.nextFloat() < chance) {
                t.owner = NEUTRAL
                t.levy = 10 + t.income / 2
                events += StoryEvent(
                    EventKind.REBELLION, "Rebellion in ${t.name}!",
                    "Weary of war taxes, the folk of ${t.name} drive out your reeves and bar the roads. " +
                        "They will have to be won back by force.",
                    Scene.REBELS, territoryId = t.id,
                )
                return
            }
        }
    }

    private fun rollCapture() {
        if (turn != CAPTURE_TURN || captor != NEUTRAL || siblingFreed) return
        val lord = livingRivals.maxByOrNull { territoriesOf(it.id).size } ?: return
        captor = lord.id
        val castle = territories[lord.home].name
        events += StoryEvent(
            EventKind.SIBLING_TAKEN, "A Rider in the Night",
            "A wounded rider gallops into Highmere with dire news: your sister $SIBLING_NAME has been seized on " +
                "the Elmshire road and carried off to $castle. ${lord.name} means to hold her as a hostage " +
                "against you. Raid $castle and fight up its tower to bring her home.",
            Scene.MESSENGER, lordId = lord.id,
        )
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
        for (i in truce.indices) if (truce[i] > 0) truce[i]--
        turn++
        deedDone = false
        movesLeft = MOVES_PER_SEASON
        collectCatapults()
        rollCapture()
        rollRebellion()
        rollSeasonEvent()
        rollTruceOffer()
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
        // Castles of great lords grow ever harder to take.
        val garrisonTarget = min(25 + turn * 2 + territoriesOf(lord.id).size * 5, 130)
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
                !(isHomeCastle(t) && turn < GRACE_SEASONS) &&
                !(t.owner == PLAYER && truce[lord.id] > 0)
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
        val hostDefends = hostDefends(target)
        val battle = Battle(lord.army.copyArmy(), defenders, 3, leadershipOf(target.owner), fort, rng)
        val result = battle.autoResolve()
        lord.army.soldiers = battle.attacker.soldiers
        lord.army.knights = battle.attacker.knights
        val defenderName = ownerName(target.owner)
        val siege = isHomeCastle(target)
        writeBackAiDefence(target, battle, siege, hostDefends)
        return if (result == BattleOutcome.ATTACKER_WINS) {
            val tookFromPlayer = target.owner == PLAYER
            val news = capture(target, lord.id)
            if (!siege) {
                // Leave a company behind so the prize is not lost again at once.
                val company = min(lord.army.soldiers / 5, 15)
                lord.army.soldiers -= company
                target.levy += company
            }
            if (hostDefends && target.id == armyAt) armyAt = player.home
            if (tookFromPlayer && outcome == null && turn - lastTaunt[lord.id] >= 4) {
                lastTaunt[lord.id] = turn
                events += StoryEvent(
                    EventKind.TAUNT, "A Message from ${lord.name}", Story.taunt(lord.id, target.name),
                    Scene.TAUNT, lordId = lord.id, territoryId = target.id,
                )
            }
            news
        } else if (hostDefends) {
            "${lord.name} falls on ${target.name}, but your host stands and drives them off!"
        } else {
            "${lord.name} marches on ${target.name}, but $defenderName holds firm."
        }
    }

    /** True when the player's host fights to defend [t]: where it stands, or a nearby Highmere. */
    private fun hostDefends(t: Territory): Boolean =
        t.owner == PLAYER && !player.army.isEmpty &&
            (t.id == armyAt || (t.id == player.home && hostCanRelieveHome))

    /** Returns battle survivors to the levy and garrison first, then to the player's host. */
    private fun writeBackAiDefence(t: Territory, battle: Battle, siege: Boolean, hostDefends: Boolean) {
        var s = battle.defender.soldiers
        var k = battle.defender.knights
        val levy = min(s, t.levy)
        s -= levy
        if (siege) {
            val owner = lords[t.owner]
            owner.garrison.soldiers = min(s, owner.garrison.soldiers); s -= owner.garrison.soldiers
            owner.garrison.knights = min(k, owner.garrison.knights); k -= owner.garrison.knights
        } else {
            k -= min(k, t.levy / 8)
        }
        if (hostDefends) {
            player.army.soldiers = s
            player.army.knights = k
        }
        t.levy = max(2, levy)
    }

    private fun isHomeCastle(t: Territory) = t.isCastle && t.owner != NEUTRAL && lords[t.owner].home == t.id

    private fun planAiDefence(t: Territory): Pair<Army, Float> {
        val defenders = Army(soldiers = t.levy, knights = if (t.owner == NEUTRAL) 0 else t.levy / 8)
        if (hostDefends(t)) {
            defenders.soldiers += player.army.soldiers
            defenders.knights += player.army.knights
        }
        if (!isHomeCastle(t)) return defenders to 1f
        val g = lords[t.owner].garrison
        defenders.soldiers += g.soldiers
        defenders.knights += g.knights
        return defenders to Siege.maxFortification(wallHp(t.owner))
    }

    // --- Save games ------------------------------------------------------------------------

    fun serialize(): String = buildString {
        appendLine("v=2")
        appendLine("hero=${hero.name}")
        appendLine("turn=$turn")
        appendLine("deed=$deedDone")
        appendLine("army=$armyAt,$movesLeft")
        appendLine("truce=${truce.joinToString(",")}")
        appendLine("sibling=$captor,${if (siblingFreed) 1 else 0}")
        lords.forEach { l ->
            appendLine(
                "lord${l.id}=" + listOf(
                    l.gold, l.army.soldiers, l.army.knights, l.army.catapults,
                    l.garrison.soldiers, l.garrison.knights, if (l.alive) 1 else 0, l.fame, l.garrison.catapults,
                ).joinToString(",")
            )
        }
        territories.forEach { appendLine("terr${it.id}=${it.owner},${it.levy}") }
    }

    companion object {
        /** Rivals leave castles alone for the first year while they carve up the free lands. */
        const val GRACE_SEASONS = 4
        const val MOVES_PER_SEASON = 2
        const val TRUCE_SEASONS = 4
        /** The season in which the player's sister is taken hostage. */
        const val CAPTURE_TURN = 3
        const val SIBLING_NAME = Story.SIBLING

        fun deserialize(text: String, rng: Random = Random.Default): GameState? = runCatching {
            val map = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            val version = map["v"]
            if (version != "1" && version != "2") return null
            val state = GameState(Hero.valueOf(map.getValue("hero")), rng)
            state.turn = map.getValue("turn").toInt()
            state.deedDone = map.getValue("deed").toBoolean()
            map["army"]?.split(',')?.map { it.toInt() }?.let { state.armyAt = it[0]; state.movesLeft = it[1] }
            map["truce"]?.split(',')?.map { it.toInt() }?.forEachIndexed { i, v -> state.truce[i] = v }
            map["sibling"]?.split(',')?.map { it.toInt() }?.let { state.captor = it[0]; state.siblingFreed = it[1] == 1 }
            if (version == "1" && state.turn > CAPTURE_TURN) state.siblingFreed = true
            state.lords.forEach { l ->
                val f = map.getValue("lord${l.id}").split(',').map { it.toInt() }
                l.gold = f[0]
                l.army.soldiers = f[1]; l.army.knights = f[2]; l.army.catapults = f[3]
                l.garrison.soldiers = f[4]; l.garrison.knights = f[5]
                l.alive = f[6] == 1
                l.fame = f[7]
                if (f.size > 8) l.garrison.catapults = f[8]
            }
            state.territories.forEach { t ->
                val f = map.getValue("terr${t.id}").split(',').map { it.toInt() }
                t.owner = f[0]; t.levy = f[1]
            }
            state
        }.getOrNull()
    }
}
