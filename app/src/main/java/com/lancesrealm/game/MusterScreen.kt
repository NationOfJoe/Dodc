package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.Music
import com.lancesrealm.core.Prices
import com.lancesrealm.core.Sfx

/** Hire troops and move them between the field host and the Highmere garrison. */
class MusterScreen(view: GameView) : Screen(view) {
    private var note = ""
    private val transfers = mutableListOf<Button>()

    init {
        val c1 = 40f
        val c2 = 250f
        button(c1, 92f, 190f, 20f, "Hire 10 men (${Prices.SOLDIER * 10}g)", Palette.BROWN) { buy(soldiers = 10) }
        button(c1, 118f, 190f, 20f, "Hire a knight (${Prices.KNIGHT}g)", Palette.BROWN) { buy(knights = 1) }
        button(c1, 144f, 190f, 20f, "Build a catapult (${Prices.CATAPULT}g)", Palette.BROWN) { buy(catapults = 1) }
        transfers += button(c2, 92f, 190f, 20f, "10 men to keep", Palette.BLUE) { move(10, 0) }
        transfers += button(c2, 118f, 190f, 20f, "10 men to host", Palette.BLUE) { move(-10, 0) }
        transfers += button(c2, 144f, 190f, 20f, "Knight to keep", Palette.BLUE) { move(0, 1) }
        transfers += button(c2, 170f, 190f, 20f, "Knight to host", Palette.BLUE) { move(0, -1) }
        button(180f, 236f, 120f, 22f, "Done", Palette.GREEN) { done() }
    }

    private fun done() {
        view.save()
        view.show(MapScreen(view))
    }

    override fun onBack(): Boolean { done(); return true }

    private fun buy(soldiers: Int = 0, knights: Int = 0, catapults: Int = 0) {
        note = if (game.buy(soldiers, knights, catapults)) "" else "Your treasury cannot bear it."
        if (note.isEmpty()) view.audio.sfx(Sfx.CLICK)
    }

    override fun update(dt: Float) {
        transfers.forEach { it.visible = game.armyAtHome }
    }

    private fun move(soldiers: Int, knights: Int) {
        note = if (game.transferToGarrison(soldiers, knights)) "" else "There are not enough men to send."
    }

    override fun draw(g: Gfx) {
        drawLandscape(g)
        g.panel(24f, 12f, 432f, 214f)
        val p = game.player
        g.text("Muster", Gfx.W / 2, 32f, 16f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        g.text("Treasury: ${p.gold} gold     Upkeep: ${game.upkeepOf(p)} gold a season", Gfx.W / 2, 48f, 9f, Palette.YELLOW, Paint.Align.CENTER)
        g.text("Field host", 135f, 66f, 10f, Palette.WHITE, Paint.Align.CENTER)
        g.text("${p.army.soldiers} men  ${plural(p.army.knights, "knight")}  ${plural(p.army.catapults, "catapult")}", 135f, 80f, 8f, Palette.LIGHT_GREY, Paint.Align.CENTER)
        g.text("Highmere garrison", 345f, 66f, 10f, Palette.WHITE, Paint.Align.CENTER)
        g.text("${p.garrison.soldiers} men  ${p.garrison.knights} knights", 345f, 80f, 8f, Palette.LIGHT_GREY, Paint.Align.CENTER)
        g.paragraph(
            "Knights fight as three men. Catapults batter castle walls before a storm. " +
                "The garrison guards Highmere; your host also rides home to lift a siege.",
            40f, 204f, 400f, 7.5f, Palette.LIGHT_GREY,
        )
        if (note.isNotEmpty()) g.text(note, 135f, 186f, 8f, Palette.LIGHT_RED, Paint.Align.CENTER)
        if (!game.armyAtHome) {
            g.paragraph(
                "Your host is camped at ${game.territories[game.armyAt].name}. New recruits wait in the garrison " +
                    "until it returns to Highmere.",
                250f, 104f, 190f, 8.5f, Palette.YELLOW,
            )
        }
    }
}

/** Pick a rival's champion and a stake, then ride in the lists. */
class TournamentScreen(view: GameView) : Screen(view) {
    private var rivalId = game.livingRivals.first().id
    private var forLand = false
    private val rivalButtons = mutableMapOf<Int, Button>()
    private val purse: Button
    private val land: Button

    init {
        game.livingRivals.forEachIndexed { i, lord ->
            rivalButtons[lord.id] = button(40f + i * 136f, 74f, 128f, 22f, lord.name, Palette.lord(lord.id), 9f) { rivalId = lord.id }
        }
        purse = button(80f, 150f, 150f, 22f, "For a purse", Palette.DARK_GREY, 9f) { forLand = false }
        land = button(250f, 150f, 150f, 22f, "For land", Palette.DARK_GREY, 9f) { forLand = true }
        button(250f, 236f, 120f, 22f, "To the lists!", Palette.RED) {
            view.show(JoustScreen(view, rivalId, forLand))
        }
        button(110f, 236f, 120f, 22f, "Back", Palette.DARK_GREY) { view.show(MapScreen(view)) }
    }

    override fun onBack(): Boolean { view.show(MapScreen(view)); return true }

    override fun onShow() {
        view.audio.music(Music.JOUST)
    }

    override fun update(dt: Float) {
        rivalButtons.forEach { (id, b) -> b.color = if (id == rivalId) Palette.lord(id) else Palette.DARK_GREY }
        purse.color = if (!forLand) Palette.GREEN else Palette.DARK_GREY
        land.color = if (forLand) Palette.GREEN else Palette.DARK_GREY
    }

    override fun draw(g: Gfx) {
        drawLandscape(g)
        g.panel(24f, 12f, 432f, 214f)
        g.text("The Tournament", Gfx.W / 2, 36f, 16f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        g.text("Whose champion will you face?", Gfx.W / 2, 62f, 9f, Palette.WHITE, Paint.Align.CENTER)
        val skill = game.rival(rivalId).jousting
        g.text("Champion's skill in the lists: " + "*".repeat(skill), Gfx.W / 2, 114f, 9f, Palette.LIGHT_GREY, Paint.Align.CENTER)
        g.text("What is at stake?", Gfx.W / 2, 140f, 9f, Palette.WHITE, Paint.Align.CENTER)
        val stake = if (forLand) "Win: take one of their lands. Lose: yield one of yours."
        else "Win: 150 gold. Lose: up to 100 gold."
        g.text(stake, Gfx.W / 2, 192f, 8.5f, Palette.YELLOW, Paint.Align.CENTER)
        g.text("Your skill in the lists: " + "*".repeat(game.hero.jousting), Gfx.W / 2, 208f, 8.5f, Palette.CYAN, Paint.Align.CENTER)
    }
}
