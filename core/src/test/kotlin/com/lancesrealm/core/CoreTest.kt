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
    private fun runPass(j: Joust, couchWell: Boolean = true, aim: () -> Pair<Float, Float>) {
        val startPass = j.pass
        var guard = 0
        while (j.phase != Joust.Phase.FINISHED && j.pass == startPass && guard++ < 10_000) {
            if (couchWell && j.phase == Joust.Phase.COUCH && kotlin.math.abs(j.meter - 0.5f) < 0.03f) j.tap()
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

    @Test
    fun aGoodCouchBeatsAFumble() {
        fun wins(couchWell: Boolean) = (0 until 40).count { seed ->
            val j = Joust(playerSkill = 3, rivalSkill = 3, rng = Random(seed))
            while (j.phase != Joust.Phase.FINISHED) runPass(j, couchWell) { j.shieldX to j.shieldY }
            j.playerWon
        }
        assertTrue(wins(true) > wins(false))
    }

    @Test
    fun tappingInTheBandIsAPerfectCouch() {
        val j = Joust(3, 3, Random(1))
        while (j.phase != Joust.Phase.COUCH) j.update(1f / 60f)
        while (kotlin.math.abs(j.meter - 0.5f) > 0.02f) j.update(1f / 120f)
        j.tap()
        assertEquals(1f, j.couch)
    }
}

class DuelTest {
    @Test
    fun perfectBlockerWins() {
        val d = Duel(swordplay = 3, rooms = listOf(listOf(2), listOf(3, 3), listOf(3)), rng = Random(4))
        val seenRooms = mutableSetOf<Int>()
        var t = 0f
        while (!d.isOver && t < 300f) {
            if (d.enemyState == Duel.EnemyState.WINDUP && d.enemyStateTime > d.windupDuration - 0.2f) d.block(d.enemyHeight)
            if (d.enemyState == Duel.EnemyState.STAGGER) d.attack(Height.HIGH)
            d.update(1f / 60f)
            if (!d.isOver) seenRooms += d.roomIndex
            t += 1f / 60f
        }
        assertTrue(d.playerWon)
        assertEquals(setOf(0, 1, 2), seenRooms)
    }

    @Test
    fun idlePlayerLoses() {
        val d = Duel(swordplay = 3, rooms = listOf(listOf(3)), rng = Random(1))
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
            val s = Siege(1, rng = Random(0))
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
        assertEquals(Siege.maxFortification(1), s.fortification, 0.001f)
    }

    @Test
    fun strongerWallsTakeMoreShots() {
        val s = Siege(20, wallHp = 3, rng = Random(0))
        var cracked = false
        repeat(20) {
            // Find a wall-hitting shot by scanning power at a fixed angle.
            for (p in 0..40) {
                if (s.phase != Siege.Phase.AIMING) break
                val probe = Siege(1, wallHp = 3, rng = Random(0))
                probe.fire(40f, p / 40f)
                while (probe.phase == Siege.Phase.FLYING) probe.update(1f / 60f)
                if (probe.lastHitWall) {
                    s.fire(40f, p / 40f)
                    while (s.phase == Siege.Phase.FLYING) s.update(1f / 60f)
                    break
                }
            }
            if (s.blocksStanding == Siege.COLS * Siege.ROWS && s.breach > 0f) cracked = true
        }
        assertTrue(cracked, "a hit on a stout wall should crack it before it falls")
        assertTrue(s.fortification < Siege.maxFortification(3))
    }
}

class GameStateTest {
    @Test
    fun conqueringANeighbourTransfersIt() {
        val g = GameState(Hero.ALDRIC, Random(1))
        val target = g.territories.first { g.canMarchOn(it) }
        g.player.army.soldiers = 500
        val plan = g.planAttack(target)
        val battle = g.startBattle(plan)
        while (!battle.isOver) battle.fight(Tactic.CHARGE)
        g.concludeAttack(plan, battle)
        assertEquals(PLAYER, target.owner)
        assertEquals(target.id, g.armyAt, "the host advances into the conquered land")
        assertTrue(g.deedDone)
        assertTrue(g.territories.none { g.canMarchOn(it) })
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
    fun hostMovesOnlyThroughOwnLands() {
        val g = GameState(Hero.ALDRIC, Random(5))
        val neighbour = g.territories.first { it.id in g.territories[0].neighbors }
        assertFalse(g.moveArmy(neighbour), "cannot walk into land you do not hold")
        neighbour.owner = PLAYER
        assertTrue(g.moveArmy(neighbour))
        assertFalse(g.armyAtHome)
        // Recruits now wait at Highmere and transfers are impossible.
        val army = g.player.army.soldiers
        g.buy(soldiers = 10)
        assertEquals(army, g.player.army.soldiers)
        assertFalse(g.transferToGarrison(5))
        assertTrue(g.moveArmy(g.territories[0]))
        assertEquals(0, g.movesLeft)
    }

    @Test
    fun theSisterIsTakenAndCanBeRescued() {
        val g = GameState(Hero.CORWIN, Random(9))
        while (g.turn < GameState.CAPTURE_TURN) g.endSeason()
        if (g.outcome != null) return
        assertTrue(g.events.any { it.kind == EventKind.SIBLING_TAKEN })
        val captor = g.captor
        assertTrue(captor in 1..3)
        assertTrue(g.isRescue(captor))
        assertEquals("The Tower Stair", g.raidRooms(captor).last().name)
        val knights = g.player.army.knights
        g.concludeRaid(captor, won = true)
        assertTrue(g.siblingFreed)
        assertEquals(knights + 6, g.player.army.knights)
        assertTrue(g.events.any { it.kind == EventKind.SIBLING_FREED })
    }

    @Test
    fun aTruceStopsMarchesAndRaids() {
        val g = GameState(Hero.EDDA, Random(4))
        val offer = StoryEvent(EventKind.TRUCE_OFFER, "", "", Scene.TRUCE, lordId = 2, choices = listOf("a", "b"))
        g.resolveEvent(offer, 0)
        assertTrue(g.atTruce(2))
        assertFalse(g.canRaid(g.territories[2]))
        repeat(GameState.TRUCE_SEASONS) { g.endSeason() }
        assertFalse(g.atTruce(2))
    }

    @Test
    fun mercenariesCostGold() {
        val g = GameState(Hero.EDDA)
        val offer = StoryEvent(EventKind.MERCENARIES, "", "", Scene.MERCENARIES, amount = 20, cost = 60, choices = listOf("a", "b"))
        val gold = g.player.gold
        val men = g.player.army.soldiers
        g.resolveEvent(offer, 0)
        assertEquals(gold - 60, g.player.gold)
        assertEquals(men + 20, g.player.army.soldiers)
    }

    @Test
    fun overstretchedRealmsRebel() {
        var rebellions = 0
        repeat(20) { seed ->
            val g = GameState(Hero.ALDRIC, Random(seed))
            g.territories.filter { !it.isCastle }.forEach { it.owner = PLAYER; it.levy = 3 }
            repeat(8) { g.endSeason() }
            rebellions += g.events.count { it.kind == EventKind.REBELLION }
        }
        assertTrue(rebellions > 0)
    }

    @Test
    fun rivalCastlesHardenAsTheyGrow() {
        val g = GameState(Hero.ALDRIC)
        assertEquals(1, g.wallHp(1))
        g.territories.filter { !it.isCastle }.take(7).forEach { it.owner = 1 }
        assertEquals(3, g.wallHp(1))
    }

    @Test
    fun saveRoundTrips() {
        val g = GameState(Hero.CORWIN, Random(3))
        g.buy(soldiers = 5)
        repeat(4) { g.endSeason() }
        g.territories[5].owner = PLAYER
        g.truce[3] = 2
        val restored = assertNotNull(GameState.deserialize(g.serialize()))
        assertEquals(g.serialize(), restored.serialize())
        assertNull(GameState.deserialize("garbage"))
    }
}

class ChiptuneTest {
    @Test
    fun everySongVoiceLinesUp() {
        for ((music, song) in Chiptune.songs) {
            val lengths = song.voices.map { Chiptune.length(it) }.toSet()
            assertEquals(1, lengths.size, "$music voices differ in length: $lengths")
            song.voices.flatMap { it.notes.split(' ') }.filter { it.isNotBlank() }.forEach { tok ->
                val pitch = tok.substringBefore('.')
                assertTrue(pitch == "R" || pitch == "x" || Chiptune.midi(pitch) != null, "bad note $tok in $music")
            }
        }
    }

    @Test
    fun rendersAudibleAudio() {
        val pcm = Chiptune.render(Chiptune.songs.getValue(Music.TITLE))
        assertTrue(pcm.size > Chiptune.SAMPLE_RATE * 10)
        assertTrue(pcm.any { kotlin.math.abs(it.toInt()) > 3000 })
        Sfx.entries.forEach { assertTrue(Chiptune.sfx(it).isNotEmpty()) }
        assertEquals(69, Chiptune.midi("A4"))
        assertEquals(70, Chiptune.midi("Bb4"))
    }
}
