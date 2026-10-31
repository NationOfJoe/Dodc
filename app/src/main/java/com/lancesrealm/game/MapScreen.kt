package com.lancesrealm.game

import android.graphics.Bitmap
import android.graphics.Paint
import kotlin.math.sin
import com.lancesrealm.core.Music
import com.lancesrealm.core.NEUTRAL
import com.lancesrealm.core.Sfx
import com.lancesrealm.core.Story
import com.lancesrealm.core.PLAYER
import com.lancesrealm.core.Siege
import com.lancesrealm.core.Territory
import com.lancesrealm.core.WorldMap

/** The realm of Valdria: pick a territory, then choose the season's deed. */
class MapScreen(view: GameView) : Screen(view) {
    private val bitmap = Bitmap.createBitmap(WorldMap.GRID_W, WorldMap.GRID_H, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(WorldMap.GRID_W * WorldMap.GRID_H)
    private var bitmapKey = ""
    private var selected: Territory? = null

    private val move: Button
    private val march: Button
    private val raid: Button
    private val tourney: Button
    private val sound = soundButton(view, Gfx.W - 80f, 6f)

    init {
        val bx = SIDE_X + 6f
        val bw = Gfx.W - SIDE_X - 12f
        button(bx, 108f, bw, 18f, "Muster troops", Palette.BROWN, 9.5f) { view.show(MusterScreen(view)) }
        move = button(bx, 129f, bw, 18f, "Move host here", Palette.BLUE, 9.5f) {
            selected?.let { if (game.moveArmy(it)) view.audio.sfx(Sfx.CLICK) }
        }
        march = button(bx, 150f, bw, 18f, "March on", Palette.RED, 9.5f) { selected?.let(::marchOn) }
        raid = button(bx, 171f, bw, 18f, "Raid castle", Palette.PURPLE, 9.5f) { selected?.let(::raidOn) }
        tourney = button(bx, 192f, bw, 18f, "Hold a tournament", Palette.ORANGE, 9.5f) { view.show(TournamentScreen(view)) }
        button(bx, 240f, bw, 20f, "End season", Palette.GREEN) {
            val news = game.endSeason()
            view.show(ChronicleScreen(view, "${game.season.label}, Year ${game.year}", news))
        }
        selected = game.territories[game.armyAt]
    }

    override fun onShow() {
        view.audio.music(Music.MAP)
    }

    override fun onBack(): Boolean {
        view.save()
        view.show(TitleScreen(view))
        return true
    }

    override fun update(dt: Float) {
        val t = selected
        move.enabled = t != null && game.canMoveTo(t)
        march.enabled = t != null && game.canMarchOn(t)
        raid.enabled = t != null && game.canRaid(t)
        tourney.enabled = !game.deedDone && game.livingRivals.isNotEmpty()
        march.label = if (t != null && t.owner != PLAYER && game.planAttack(t).isSiege) "Besiege" else "March on"
        raid.label = if (t != null && t.owner != NEUTRAL && t.owner != PLAYER && game.isRescue(t.owner)) "Rescue ${Story.SIBLING}" else "Raid castle"
        sound.label = soundLabel(view)
    }

    private fun marchOn(t: Territory) {
        val plan = game.planAttack(t)
        if (plan.isSiege && game.player.army.catapults > 0) view.show(SiegeScreen(view, plan))
        else view.show(BattleScreen(view, plan, if (plan.isSiege) Siege.maxFortification(plan.wallHp) else 1f, 0f))
    }

    private fun raidOn(t: Territory) {
        view.show(DuelScreen(view, t.owner))
    }

    override fun touchUp(x: Float, y: Float) {
        if (x in MAP_X..MAP_X + MAP_W && y in MAP_Y..MAP_Y + MAP_H) {
            val id = WorldMap.territoryAt((x - MAP_X) / MAP_W, (y - MAP_Y) / MAP_H)
            selected = if (id == WorldMap.SEA) null else game.territories[id]
        }
    }

    override fun draw(g: Gfx) {
        g.clear(Palette.BLACK)
        val blink = ((g.time * 2.5f).toInt() % 2) == 0
        rebuildBitmap(blink)
        g.rect(MAP_X - 2, MAP_Y - 2, MAP_W + 4, MAP_H + 4, Palette.LIGHT_GREY)
        g.bitmap(bitmap, MAP_X, MAP_Y, MAP_W, MAP_H)

        for (t in game.territories) {
            val cx = MAP_X + t.x * MAP_W
            val cy = MAP_Y + t.y * MAP_H
            if (t.isCastle) {
                val owner = t.owner
                Sprites.castle(g, cx - 9f, cy + 4f, 18f, 11f, Palette.LIGHT_GREY, Palette.lord(owner))
            } else {
                g.circle(cx, cy - 2f, 2f, Palette.BLACK)
                g.circle(cx, cy - 2f, 1.3f, Palette.lord(t.owner))
            }
            g.text(t.name, cx, cy + (if (t.isCastle) 12f else 7f), 6.5f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
            if (t.isCastle && t.owner != PLAYER && t.owner != NEUTRAL) {
                if (game.isRescue(t.owner) && game.territories[game.lords[t.owner].home] == t) {
                    g.text("${Story.SIBLING} held here", cx, cy + 20f, 6f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
                }
                if (game.atTruce(t.owner)) g.text("truce", cx, cy - 18f, 6f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
            }
        }
        drawHost(g)

        drawSidebar(g)
        drawInfo(g)
    }

    /** The player's banner where the host is camped, with marching range highlighted. */
    private fun drawHost(g: Gfx) {
        val t = game.territories[game.armyAt]
        val x = MAP_X + t.x * MAP_W + 12f
        val y = MAP_Y + t.y * MAP_H - 4f
        g.line(x, y, x, y - 18f, Palette.WHITE, 1.2f)
        val wave = sin(g.time * 6f) * 1.5f
        g.poly(Palette.lord(PLAYER), x, y - 18f, x + 12f, y - 15f + wave, x, y - 11f)
        g.rect(x - 1.5f, y - 19f, 3f, 2f, Palette.YELLOW)
        g.text("${game.player.army.strength}", x + 2f, y - 21f, 6f, Palette.YELLOW, shadow = true)
    }

    private fun drawSidebar(g: Gfx) {
        val p = game.player
        g.panel(SIDE_X, 2f, Gfx.W - SIDE_X - 2f, Gfx.H - 4f)
        val x = SIDE_X + 8f
        g.text(game.hero.displayName.substringBefore(' '), x, 17f, 10f, Palette.YELLOW, shadow = true)
        g.text("${game.season.label}, Year ${game.year}", x, 30f, 8f, Palette.LIGHT_GREY)
        g.text("Gold  ${p.gold}  (+${game.incomeOf(PLAYER)})", x, 46f, 9f, Palette.YELLOW)
        g.text("Fame  ${p.fame}", x, 58f, 8f, Palette.CYAN)
        g.text("Host  ${p.army.soldiers} men, ${p.army.knights} knights", x, 72f, 8f, Palette.WHITE)
        g.text("      " + plural(p.army.catapults, "catapult"), x, 82f, 8f, Palette.WHITE)
        g.text("Keep  ${p.garrison.soldiers} men, ${p.garrison.knights} knights", x, 92f, 8f, Palette.LIGHT_GREY)
        g.text("At ${game.territories[game.armyAt].name}  moves ${game.movesLeft}", x, 103f, 7f, Palette.CYAN)
        val status = if (game.deedDone) "Your deed is done this season." else "One great deed per season."
        g.text(status, x, 223f, 7f, if (game.deedDone) Palette.LIGHT_RED else Palette.LIGHT_GREEN)
        g.text("Rivals left: ${game.livingRivals.size}", x, 233f, 7f, Palette.LIGHT_GREY)
    }

    private fun drawInfo(g: Gfx) {
        g.panel(MAP_X - 2, 202f, MAP_W + 4, 66f, Palette.darker(Palette.BLUE, 0.8f))
        val x = MAP_X + 6f
        val t = selected
        if (t == null) {
            g.paragraph(
                "Tap a territory. Move your host (blue banner) through your lands, march on a neighbour, " +
                    "raid a rival's castle, or win purses and land in the tournament lists.",
                x, 218f, MAP_W - 12f, 8f, Palette.LIGHT_GREY,
            )
            return
        }
        g.rect(x, 210f, 8f, 8f, Palette.lord(t.owner))
        g.text(t.name + if (t.isCastle) " (castle)" else "", x + 12f, 218f, 10f, Palette.WHITE)
        g.text("Held by ${game.ownerName(t.owner)}   Taxes ${t.income} gold", x, 232f, 8f, Palette.LIGHT_GREY)
        if (t.owner == PLAYER) {
            g.text("Your vassals: ${t.levy} men", x, 246f, 8f, Palette.LIGHT_GREEN)
            val hint = when {
                t.id == game.armyAt -> "Your host is camped here."
                game.canMoveTo(t) -> "Your host can move here."
                else -> "Far from your host: unrest may stir."
            }
            g.text(hint, x, 259f, 8f, Palette.YELLOW)
            return
        }
        val plan = game.planAttack(t)
        val d = plan.defenders
        g.text("Defenders: ${d.soldiers} men, ${d.knights} knights" + if (plan.isSiege) " behind walls" else "", x, 246f, 8f, Palette.LIGHT_RED)
        val hint = when {
            game.atTruce(t.owner) -> "A truce holds with ${game.ownerName(t.owner)} for ${game.truce[t.owner]} more seasons."
            game.canMarchOn(t) -> "Your host stands ready to march here."
            t.owner != NEUTRAL && game.canRaid(t) -> "Beyond your host's reach, but you may raid it."
            game.deedDone -> "Wait for the next season."
            else -> "Move your host next to it to attack."
        }
        g.text(hint, x, 259f, 8f, Palette.YELLOW)
    }

    private fun rebuildBitmap(blink: Boolean) {
        val sel = selected?.id ?: -1
        val key = game.territories.joinToString(",") { it.owner.toString() } + "/$sel/$blink"
        if (key == bitmapKey) return
        bitmapKey = key
        for (gy in 0 until WorldMap.GRID_H) for (gx in 0 until WorldMap.GRID_W) {
            val c = WorldMap.cellAt(gx, gy)
            val h = WorldMap.hash(gx, gy)
            pixels[gy * WorldMap.GRID_W + gx] = if (c == WorldMap.SEA) {
                if (h < 0.08f && (gx + gy) % 3 == 0) Palette.LIGHT_BLUE else Palette.BLUE
            } else {
                val base = Palette.lord(game.territories[c].owner)
                when {
                    WorldMap.isBorder(gx, gy) -> if (c == sel && blink) Palette.YELLOW else Palette.darker(base, 0.4f)
                    c == sel && blink -> Palette.lighter(base)
                    h < 0.14f -> Palette.darker(base, 0.82f)
                    else -> base
                }
            }
        }
        bitmap.setPixels(pixels, 0, WorldMap.GRID_W, 0, 0, WorldMap.GRID_W, WorldMap.GRID_H)
    }

    companion object {
        const val MAP_X = 4f
        const val MAP_Y = 4f
        const val MAP_W = 288f
        const val MAP_H = 192f
        const val SIDE_X = 296f
    }
}
