package com.lancesrealm.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldMapTest {
    @Test
    fun everyTerritoryHasLandAndNeighbours() {
        WorldMap.defs.forEachIndexed { i, d ->
            assertEquals(i, WorldMap.territoryAt(d.x, d.y), "${d.name} centre should lie in its own land")
            assertTrue(WorldMap.adjacency[i].isNotEmpty(), "${d.name} has no neighbours")
        }
    }

    @Test
    fun realmIsConnected() {
        val seen = mutableSetOf(0)
        val queue = ArrayDeque(listOf(0))
        while (queue.isNotEmpty()) {
            for (n in WorldMap.adjacency[queue.removeFirst()]) if (seen.add(n)) queue.add(n)
        }
        assertEquals(WorldMap.defs.size, seen.size)
    }

    @Test
    fun castlesDoNotStartAdjacent() {
        for (a in 0 until 4) for (b in 0 until 4) {
            if (a != b) assertFalse(b in WorldMap.adjacency[a], "castles $a and $b touch")
        }
    }
}

class BattleTest {
    @Test
    fun overwhelmingForceWins() {
        repeat(20) { seed ->
            val b = Battle(Army(100, 20), Army(10, 0), 3, 3, rng = Random(seed))
            assertEquals(BattleOutcome.ATTACKER_WINS, b.autoResolve())
        }
    }

    @Test
    fun wallsFavourTheDefender() {
        var openWins = 0
        var walledWins = 0
        repeat(200) { seed ->
            if (Battle(Army(40, 5), Army(40, 5), 3, 3, 1f, Random(seed)).autoResolve() == BattleOutcome.ATTACKER_WINS) openWins++
            if (Battle(Army(40, 5), Army(40, 5), 3, 3, 1.9f, Random(seed)).autoResolve() == BattleOutcome.ATTACKER_WINS) walledWins++
        }
        assertTrue(walledWins < openWins, "walled=$walledWins open=$openWins")
    }

    @Test
    fun retreatEndsBattle() {
        val b = Battle(Army(30, 2), Army(30, 2), 3, 3)
        b.fight(Tactic.RETREAT)
        assertEquals(BattleOutcome.RETREATED, b.outcome)
    }

    @Test
    fun countersHurtMore() {
        var counterLoss = 0
        var plainLoss = 0
        repeat(200) { seed ->
            counterLoss += Battle(Army(50), Army(50), 3, 3, rng = Random(seed)).fight(Tactic.HOLD, Tactic.CHARGE).defenderLost.soldiers
            plainLoss += Battle(Army(50), Army(50), 3, 3, rng = Random(seed)).fight(Tactic.HOLD, Tactic.HOLD).defenderLost.soldiers
        }
        assertTrue(counterLoss > plainLoss)
    }
}

class JoustTest {
    private fun runPass(j: Joust, aim: () -> Pair<Float, Float>) {
        val startPass = j.pass
        var guard = 0
        while (j.phase != Joust.Phase.FINISHED && j.pass == startPass && guard++ < 10_000) {
            val (x, y) = aim()
            j.aim(x, y)
            j.update(1f / 60f)
        }
    }

    @Test
    fun trackingTheShieldWinsMostJousts() {
        var wins = 0
        repeat(30) { seed ->
            val j = Joust(playerSkill = 5, rivalSkill = 1, rng = Random(seed))
            while (j.phase != Joust.Phase.FINISHED) runPass(j) { j.shieldX to j.shieldY }
            if (j.playerWon) wins++
        }
        assertTrue(wins >= 25, "wins=$wins")
    }

    @Test
    fun aimingAtTheSkyLoses() {
        repeat(10) { seed ->
            val j = Joust(playerSkill = 3, rivalSkill = 3, rng = Random(seed))
            while (j.phase != Joust.Phase.FINISHED) runPass(j) { 1f to -1f }
            assertFalse(j.playerWon)
        }
    }
}

class DuelTest {
    @Test
    fun perfectBlockerWins() {
        val d = Duel(swordplay = 3, guardSkills = listOf(2, 3, 3), rng = Random(4))
        var t = 0f
        while (!d.isOver && t < 300f) {
            if (d.enemyState == Duel.EnemyState.WINDUP && d.enemyStateTime > d.windupDuration - 0.2f) d.block(d.enemyHeight)
            if (d.enemyState == Duel.EnemyState.STAGGER) d.attack(Height.HIGH)
            d.update(1f / 60f)
            t += 1f / 60f
        }
        assertTrue(d.playerWon)
    }

    @Test
    fun idlePlayerLoses() {
        val d = Duel(swordplay = 3, guardSkills = listOf(3), rng = Random(1))
        repeat(60 * 120) { d.update(1f / 60f) }
        assertTrue(d.playerLost)
    }
}

class SiegeTest {
    @Test
    fun aWellAimedShotHitsTheWall() {
        // Search the aim space for any shot that knocks out masonry: the wall must be reachable.
        var hit = false
        for (a in 15..75 step 5) for (p in 0..20) {
            val s = Siege(1, Random(0))
            s.fire(a.toFloat(), p / 20f)
            while (s.phase == Siege.Phase.FLYING) s.update(1f / 60f)
            if (s.blocksStanding < Siege.COLS * Siege.ROWS) hit = true
        }
        assertTrue(hit)
    }

    @Test
    fun noCatapultsMeansNoBombardment() {
        val s = Siege(0)
        assertEquals(Siege.Phase.FINISHED, s.phase)
        assertEquals(1.9f, s.fortification, 0.001f)
    }
}

class GameStateTest {
    @Test
    fun conqueringANeighbourTransfersIt() {
        val g = GameState(Hero.ALDRIC, Random(1))
        val target = g.territories.first { it.owner == NEUTRAL && g.bordersPlayer(it) }
        g.player.army.soldiers = 500
        val plan = g.planAttack(target)
        val battle = g.startBattle(plan)
        while (!battle.isOver) battle.fight(Tactic.CHARGE)
        g.concludeAttack(plan, battle)
        assertEquals(PLAYER, target.owner)
        assertTrue(g.deedDone)
        assertFalse(g.canMarchOn(g.territories.first { it.owner == NEUTRAL && g.bordersPlayer(it) }))
    }

    @Test
    fun takingARivalCastleOverthrowsThem() {
        val g = GameState(Hero.EDDA, Random(2))
        val castle = g.territories[1]
        val elsewhere = g.territories.first { it.owner == NEUTRAL && 1 in it.neighbors }
        elsewhere.owner = 1
        g.player.army.soldiers = 2000
        val plan = g.planAttack(castle)
        assertTrue(plan.isSiege)
        val battle = g.startBattle(plan, fortification = 1.9f)
        while (!battle.isOver) battle.fight(Tactic.CHARGE)
        g.concludeAttack(plan, battle)
        assertFalse(g.lords[1].alive)
        assertEquals(PLAYER, elsewhere.owner)
    }

    @Test
    fun victoryWhenAllRivalsFall() {
        val g = GameState(Hero.MAREN)
        assertNull(g.outcome)
        g.lords.drop(1).forEach { it.alive = false }
        assertEquals(GameOutcome.VICTORY, g.outcome)
    }

    @Test
    fun buyingCostsGold() {
        val g = GameState(Hero.CORWIN)
        val gold = g.player.gold
        assertTrue(g.buy(soldiers = 10, knights = 1))
        assertEquals(gold - 10 * Prices.SOLDIER - Prices.KNIGHT, g.player.gold)
        assertFalse(g.buy(catapults = 100))
    }

    @Test
    fun seasonsPassAndRivalsAct() {
        val g = GameState(Hero.ALDRIC, Random(7))
        repeat(40) {
            if (g.outcome == null) g.endSeason()
        }
        assertTrue(g.turn > 0)
        assertTrue(g.lords.drop(1).any { g.territoriesOf(it.id).size > 1 } || g.outcome != null)
    }

    @Test
    fun saveRoundTrips() {
        val g = GameState(Hero.CORWIN, Random(3))
        g.buy(soldiers = 5)
        g.endSeason()
        g.territories[5].owner = PLAYER
        val restored = assertNotNull(GameState.deserialize(g.serialize()))
        assertEquals(g.serialize(), restored.serialize())
        assertNull(GameState.deserialize("garbage"))
    }
}
