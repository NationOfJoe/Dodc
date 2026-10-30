package com.lancesrealm.game

import android.graphics.Bitmap
import android.graphics.Paint
import com.lancesrealm.core.NEUTRAL
import com.lancesrealm.core.PLAYER
import com.lancesrealm.core.Territory
import com.lancesrealm.core.WorldMap

/** The realm of Valdria: pick a territory, then choose the season's deed. */
class MapScreen(view: GameView) : Screen(view) {
    private val bitmap = Bitmap.createBitmap(WorldMap.GRID_W, WorldMap.GRID_H, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(WorldMap.GRID_W * WorldMap.GRID_H)
    private var bitmapKey = ""
    private var selected: Territory? = null

    private val march: Button
    private val raid: Button
    private val tourney: Button

    init {
        val bx = SIDE_X + 6f
        val bw = Gfx.W - SIDE_X - 12f
        button(bx, 112f, bw, 20f, "Muster troops", Palette.BROWN) { view.show(MusterScreen(view)) }
        march = button(bx, 136f, bw, 20f, "March on", Palette.RED) { selected?.let(::marchOn) }
        raid = button(bx, 160f, bw, 20f, "Raid castle", Palette.PURPLE) { selected?.let(::raidOn) }
        tourney = button(bx, 184f, bw, 20f, "Hold a tournament", Palette.ORANGE) { view.show(TournamentScreen(view)) }
        button(bx, 236f, bw, 22f, "End season", Palette.GREEN) {
            val news = game.endSeason()
            view.show(ChronicleScreen(view, "${game.season.label}, Year ${game.year}", news))
        }
    }

    override fun onBack(): Boolean {
        view.save()
        view.show(TitleScreen(view))
        return true
    }

    override fun update(dt: Float) {
        val t = selected
        march.enabled = t != null && game.canMarchOn(t)
        raid.enabled = t != null && game.canRaid(t)
        tourney.enabled = !game.deedDone && game.livingRivals.isNotEmpty()
        march.label = if (t != null && game.planAttack(t).isSiege) "Besiege" else "March on"
    }

    private fun marchOn(t: Territory) {
        val plan = game.planAttack(t)
        if (plan.isSiege && game.player.army.catapults > 0) view.show(SiegeScreen(view, plan))
        else view.show(BattleScreen(view, plan, if (plan.isSiege) 1.9f else 1f, 0f))
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
        }

        drawSidebar(g)
        drawInfo(g)
    }

    private fun drawSidebar(g: Gfx) {
        val p = game.player
        g.panel(SIDE_X, 2f, Gfx.W - SIDE_X - 2f, Gfx.H - 4f)
        val x = SIDE_X + 8f
        g.text(game.hero.displayName, x, 17f, 10f, Palette.YELLOW, shadow = true)
        g.text("${game.season.label}, Year ${game.year}", x, 30f, 8f, Palette.LIGHT_GREY)
        g.text("Gold  ${p.gold}  (+${game.incomeOf(PLAYER)})", x, 46f, 9f, Palette.YELLOW)
        g.text("Fame  ${p.fame}", x, 58f, 8f, Palette.CYAN)
        g.text("Host  ${p.army.soldiers} men, ${p.army.knights} knights", x, 72f, 8f, Palette.WHITE)
        g.text("      " + plural(p.army.catapults, "catapult"), x, 82f, 8f, Palette.WHITE)
        g.text("Keep  ${p.garrison.soldiers} men, ${p.garrison.knights} knights", x, 94f, 8f, Palette.LIGHT_GREY)
        val status = if (game.deedDone) "Your deed is done this season." else "One great deed per season."
        g.text(status, x, 218f, 7f, if (game.deedDone) Palette.LIGHT_RED else Palette.LIGHT_GREEN)
        g.text("Rivals left: ${game.livingRivals.size}", x, 229f, 7f, Palette.LIGHT_GREY)
    }

    private fun drawInfo(g: Gfx) {
        g.panel(MAP_X - 2, 202f, MAP_W + 4, 66f, Palette.darker(Palette.BLUE, 0.8f))
        val x = MAP_X + 6f
        val t = selected
        if (t == null) {
            g.paragraph(
                "Tap a territory. March on lands that border yours, raid a rival's castle for gold, " +
                    "or win purses and land in the tournament lists.",
                x, 218f, MAP_W - 12f, 8f, Palette.LIGHT_GREY,
            )
            return
        }
        g.rect(x, 210f, 8f, 8f, Palette.lord(t.owner))
        g.text(t.name + if (t.isCastle) " (castle)" else "", x + 12f, 218f, 10f, Palette.WHITE)
        g.text("Held by ${game.ownerName(t.owner)}   Taxes ${t.income} gold", x, 232f, 8f, Palette.LIGHT_GREY)
        if (t.owner == PLAYER) {
            g.text("Your vassals: ${t.levy} men", x, 246f, 8f, Palette.LIGHT_GREEN)
            return
        }
        val plan = game.planAttack(t)
        val d = plan.defenders
        g.text("Defenders: ${d.soldiers} men, ${d.knights} knights" + if (plan.isSiege) " behind walls" else "", x, 246f, 8f, Palette.LIGHT_RED)
        val hint = when {
            game.canMarchOn(t) -> "Your host can march here."
            t.owner != NEUTRAL && game.canRaid(t) -> "Too far to march, but you may raid it."
            game.deedDone -> "Wait for the next season."
            else -> "Not on your borders."
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
