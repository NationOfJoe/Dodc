package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.GameOutcome
import com.lancesrealm.core.GameState
import com.lancesrealm.core.Hero
import com.lancesrealm.core.Music
import com.lancesrealm.core.Story
import kotlin.math.sin

fun soundLabel(view: GameView) = if (view.audio.muted) "Sound off" else "Sound on"

/** A small toggle for music and effects, shared by the title and the map. */
fun Screen.soundButton(view: GameView, x: Float, y: Float): Button =
    addButton(Button(x, y, 72f, 16f, soundLabel(view), Palette.DARK_GREY, 8f) { view.audio.toggleMute() })

/** Shared backdrop: dusk sky, hills and a distant castle. */
fun drawLandscape(g: Gfx, horizon: Float = 190f) {
    g.clear(Palette.BLUE)
    for (i in 0 until 6) g.rect(0f, 40f + i * 22f, Gfx.W, 22f, Palette.darker(Palette.LIGHT_BLUE, 0.55f + i * 0.08f))
    val drift = (g.time * 6f) % (Gfx.W + 60f)
    Sprites.cloud(g, drift - 60f, 50f, 1.4f, Palette.LIGHT_BLUE)
    Sprites.cloud(g, (drift * 0.6f + 240f) % (Gfx.W + 60f) - 60f, 80f, 1f, Palette.LIGHT_BLUE)
    g.path.reset()
    g.path.moveTo(0f, horizon)
    var x = 0f
    while (x <= Gfx.W) { g.path.lineTo(x, horizon - 18f - sin(x / 40f) * 10f - sin(x / 17f) * 4f); x += 8f }
    g.path.lineTo(Gfx.W, Gfx.H); g.path.lineTo(0f, Gfx.H); g.path.close()
    g.fillPath(Palette.darker(Palette.GREEN, 0.6f))
    g.rect(0f, horizon, Gfx.W, Gfx.H - horizon, Palette.GREEN)
}

class TitleScreen(view: GameView) : Screen(view) {
    private val cont = button(170f, 196f, 140f, 22f, "Continue", Palette.GREEN) {
        if (view.loadCampaign()) view.show(MapScreen(view))
    }

    private val sound = soundButton(view, Gfx.W - 78f, 6f)

    init {
        button(170f, 226f, 140f, 22f, "New Campaign") { view.show(HeroSelectScreen(view)) }
    }

    override fun onShow() {
        cont.visible = view.hasSave
        view.audio.music(Music.TITLE)
    }

    override fun update(dt: Float) {
        sound.label = soundLabel(view)
    }

    override fun draw(g: Gfx) {
        drawLandscape(g, 186f)
        Sprites.castle(g, 330f, 186f, 90f, 60f, Palette.LIGHT_GREY, Palette.LIGHT_BLUE)
        Sprites.tree(g, 60f, 190f, 1.4f); Sprites.tree(g, 80f, 194f, 1.1f); Sprites.tree(g, 300f, 192f)
        val riderX = ((g.time * 30f) % 560f) - 40f
        Sprites.knight(g, riderX, 200f, Palette.LIGHT_BLUE, 1, g.time * 12f, 1.3f)
        g.text("LANCES", Gfx.W / 2, 62f, 44f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        g.text("of the REALM", Gfx.W / 2, 92f, 22f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
        g.text("A tale of tournaments, raids and sieges", Gfx.W / 2, 112f, 9f, Palette.LIGHT_GREY, Paint.Align.CENTER, shadow = true)
    }
}

class HeroSelectScreen(view: GameView) : Screen(view) {
    init {
        Hero.entries.forEachIndexed { i, hero ->
            button(16f + i * 116f, 212f, 104f, 22f, "Ride forth", Palette.RED, 10f) {
                view.startCampaign(GameState(hero))
                view.show(CutsceneScreen(view, Story.intro(hero), Music.TITLE) { view.show(MapScreen(view)) })
            }
        }
        button(8f, 246f, 70f, 18f, "Back", Palette.DARK_GREY, 9f) { view.show(TitleScreen(view)) }
    }

    override fun onBack(): Boolean { view.show(TitleScreen(view)); return true }

    override fun draw(g: Gfx) {
        drawLandscape(g)
        g.text("Choose your champion", Gfx.W / 2, 26f, 16f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        Hero.entries.forEachIndexed { i, hero ->
            val x = 16f + i * 116f
            g.panel(x, 40f, 104f, 166f)
            Sprites.knight(g, x + 46f, 100f, Palette.lord(0), 1, g.time * 3f + i, 2f)
            g.paragraph(hero.displayName, x + 7f, 118f, 92f, 9f, Palette.WHITE)
            g.paragraph(hero.epithet, x + 7f, 142f, 92f, 7f, Palette.LIGHT_GREY)
            stat(g, "Lead", hero.leadership, x + 7f, 168f)
            stat(g, "Joust", hero.jousting, x + 7f, 180f)
            stat(g, "Sword", hero.swordplay, x + 7f, 192f)
        }
    }

    private fun stat(g: Gfx, label: String, v: Int, x: Float, y: Float) {
        g.text(label, x, y, 8f, Palette.CYAN)
        for (i in 0 until 5) g.rect(x + 40f + i * 10f, y - 7f, 8f, 7f, if (i < v) Palette.YELLOW else Palette.DARK_GREY)
    }
}

/** Shows news and then returns to the map (or the end of the game). */
class ChronicleScreen(view: GameView, private val title: String, private val lines: List<String>) : Screen(view) {
    init {
        button(180f, 236f, 120f, 22f, "Continue", Palette.GREEN) { proceed() }
    }

    private fun proceed() = view.continueStory()

    override fun onBack(): Boolean { proceed(); return true }

    override fun draw(g: Gfx) {
        drawLandscape(g)
        g.panel(40f, 18f, 400f, 208f, Palette.BLUE)
        g.text(title, Gfx.W / 2, 42f, 16f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
        var y = 64f
        for (line in lines) {
            y = g.paragraph(line, 56f, y, 368f, 9.5f, Palette.WHITE) + 4f
        }
    }
}

class GameOverScreen(view: GameView, private val outcome: GameOutcome) : Screen(view) {
    init {
        view.endCampaign()
        button(170f, 230f, 140f, 22f, "Title", Palette.GREEN) { view.show(TitleScreen(view)) }
    }

    override fun onBack(): Boolean { view.show(TitleScreen(view)); return true }

    override fun draw(g: Gfx) {
        drawLandscape(g)
        if (outcome == GameOutcome.VICTORY) {
            Sprites.castle(g, 190f, 190f, 100f, 70f, Palette.LIGHT_GREY, Palette.LIGHT_BLUE)
            g.text("LONG LIVE THE KING!", Gfx.W / 2, 48f, 24f, Palette.YELLOW, Paint.Align.CENTER, shadow = true)
            g.text("Every rival has fallen. Valdria is yours.", Gfx.W / 2, 72f, 10f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
        } else {
            Sprites.castle(g, 190f, 190f, 100f, 70f, Palette.DARK_GREY, Palette.RED)
            g.text("HIGHMERE HAS FALLEN", Gfx.W / 2, 48f, 24f, Palette.LIGHT_RED, Paint.Align.CENTER, shadow = true)
            g.text("Your banner is torn down. Another will wear the crown.", Gfx.W / 2, 72f, 10f, Palette.WHITE, Paint.Align.CENTER, shadow = true)
        }
    }
}
