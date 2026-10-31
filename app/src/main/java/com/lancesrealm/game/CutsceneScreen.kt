package com.lancesrealm.game

import android.graphics.Paint
import com.lancesrealm.core.Music
import com.lancesrealm.core.Sfx
import com.lancesrealm.core.StoryEvent

/**
 * Illustrated story panels shown one after another with a title card and typewriter text.
 * Panels that offer choices wait for one before moving on.
 */
class CutsceneScreen(
    view: GameView,
    private val panels: List<StoryEvent>,
    private val music: Music? = null,
    private val onDone: () -> Unit,
) : Screen(view) {
    private var index = 0
    private var time = 0f
    private var result: String? = null
    private val choiceButtons = mutableListOf<Button>()

    private val panel: StoryEvent get() = panels[index]
    private val body: String get() = result ?: panel.text
    private val revealed: Boolean get() = (time - TITLE_TIME) * CHARS_PER_SECOND >= body.length

    override fun onShow() {
        music?.let(view.audio::music)
        startPanel()
    }

    private fun startPanel() {
        time = 0f
        result = null
        buttons.removeAll(choiceButtons)
        choiceButtons.clear()
        panel.choices.forEachIndexed { i, label ->
            val w = 200f
            val x = Gfx.W / 2 - (panel.choices.size * (w + 10f) - 10f) / 2 + i * (w + 10f)
            choiceButtons += button(x, 150f, w, 22f, label, if (i == 0) Palette.GREEN else Palette.RED, 9f) { choose(i) }
        }
        choiceButtons.forEach { it.visible = false }
    }

    private fun choose(i: Int) {
        view.audio.sfx(Sfx.CLICK)
        result = view.game?.resolveEvent(panel, i) ?: ""
        time = TITLE_TIME
        choiceButtons.forEach { it.visible = false }
    }

    private fun advance() {
        if (!revealed) {
            time = TITLE_TIME + body.length / CHARS_PER_SECOND + 0.01f
            return
        }
        if (panel.choices.isNotEmpty() && result == null) return
        view.audio.sfx(Sfx.CLICK)
        if (index < panels.lastIndex) {
            index++
            startPanel()
        } else {
            onDone()
        }
    }

    override fun onBack(): Boolean { advance(); return true }

    override fun touchUp(x: Float, y: Float) = advance()

    override fun update(dt: Float) {
        time += dt
        choiceButtons.forEach { it.visible = revealed && result == null }
    }

    override fun draw(g: Gfx) {
        g.clear(Palette.BLACK)
        SceneArt.draw(g, panel.scene, panel.lordId)
        // Letterbox and text panel.
        g.rect(0f, 0f, Gfx.W, 10f, Palette.BLACK)
        g.rect(0f, SceneArt.H, Gfx.W, Gfx.H - SceneArt.H, Palette.BLACK)
        g.rect(0f, SceneArt.H, Gfx.W, 1.5f, Palette.ORANGE)

        // Title card first, then the title settles into the panel.
        if (time < TITLE_TIME) {
            val a = (minOf(time, TITLE_TIME - time) / 0.3f).coerceIn(0f, 1f)
            g.rect(0f, 60f, Gfx.W, 50f, Palette.withAlpha(Palette.BLACK, (170 * a).toInt()))
            g.text(panel.title, Gfx.W / 2, 94f, 22f, Palette.withAlpha(Palette.YELLOW, (255 * a).toInt()), Paint.Align.CENTER, shadow = a > 0.9f)
        } else {
            g.text(panel.title, 14f, SceneArt.H + 16f, 11f, Palette.YELLOW)
            val shown = ((time - TITLE_TIME) * CHARS_PER_SECOND).toInt().coerceIn(0, body.length)
            g.paragraph(body.substring(0, shown), 14f, SceneArt.H + 31f, Gfx.W - 28f, 8.5f, if (result != null) Palette.CYAN else Palette.WHITE)
        }
        if (revealed && (panel.choices.isEmpty() || result != null) && (g.time * 2).toInt() % 2 == 0) {
            g.text("Tap to continue", Gfx.W - 12f, Gfx.H - 6f, 7f, Palette.LIGHT_GREY, Paint.Align.RIGHT)
        }
        if (panels.size > 1) {
            g.text("${index + 1}/${panels.size}", 12f, Gfx.H - 6f, 7f, Palette.DARK_GREY)
        }
        // Fade in from black.
        if (time < FADE) g.rect(0f, 0f, Gfx.W, Gfx.H, Palette.withAlpha(Palette.BLACK, (255 * (1 - time / FADE)).toInt()))
    }

    companion object {
        private const val TITLE_TIME = 1.4f
        private const val CHARS_PER_SECOND = 55f
        private const val FADE = 0.4f
    }
}
