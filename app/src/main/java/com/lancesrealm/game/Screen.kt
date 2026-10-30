package com.lancesrealm.game

class Button(
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var label: String,
    var color: Int = Palette.RED,
    var textSize: Float = 10f,
    var enabled: Boolean = true,
    var visible: Boolean = true,
    val onClick: () -> Unit,
) {
    var pressed = false
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h + 2
}

/** One page of the game. Buttons are handled here; anything else goes to the touch hooks. */
abstract class Screen(protected val view: GameView) {
    protected val buttons = mutableListOf<Button>()
    private var captured: Button? = null

    protected val game get() = view.game!!

    open fun onShow() {}
    open fun update(dt: Float) {}
    abstract fun draw(g: Gfx)
    /** Returns true if back was consumed. */
    open fun onBack(): Boolean = false

    open fun touchDown(x: Float, y: Float) {}
    open fun touchMove(x: Float, y: Float) {}
    open fun touchUp(x: Float, y: Float) {}

    protected fun button(
        x: Float, y: Float, w: Float, h: Float, label: String,
        color: Int = Palette.RED, textSize: Float = 10f, onClick: () -> Unit,
    ): Button = Button(x, y, w, h, label, color, textSize, onClick = onClick).also { buttons += it }

    fun drawButtons(g: Gfx) {
        buttons.forEach { if (it.visible) g.button(it) }
    }

    fun dispatchDown(x: Float, y: Float) {
        captured = buttons.lastOrNull { it.visible && it.enabled && it.contains(x, y) }
        captured?.pressed = true
        if (captured == null) touchDown(x, y)
    }

    fun dispatchMove(x: Float, y: Float) {
        val b = captured
        if (b != null) b.pressed = b.contains(x, y) else touchMove(x, y)
    }

    fun dispatchUp(x: Float, y: Float) {
        val b = captured
        captured = null
        if (b != null) {
            b.pressed = false
            if (b.contains(x, y) && b.enabled && b.visible) b.onClick()
        } else {
            touchUp(x, y)
        }
    }

    fun dispatchCancel() {
        captured?.pressed = false
        captured = null
    }
}
