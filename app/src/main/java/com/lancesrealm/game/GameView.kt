package com.lancesrealm.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.lancesrealm.core.GameState
import kotlin.math.min

/**
 * Hosts the game loop. Everything is drawn in a fixed virtual resolution of [Gfx.W] x [Gfx.H]
 * and scaled, letterboxed, to fit the device.
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {
    private val gfx = Gfx()
    private val prefs = context.getSharedPreferences("lances", Context.MODE_PRIVATE)
    private var lastFrameNanos = 0L
    private var running = false

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    /** The campaign in progress, if any. */
    var game: GameState? = null
        private set

    var screen: Screen = TitleScreen(this)
        private set

    init {
        screen.onShow()
    }

    val hasSave: Boolean get() = prefs.contains(SAVE_KEY)

    fun show(next: Screen) {
        screen = next
        next.onShow()
    }

    fun startCampaign(state: GameState) {
        game = state
        save()
    }

    fun loadCampaign(): Boolean {
        val state = prefs.getString(SAVE_KEY, null)?.let { GameState.deserialize(it) } ?: return false
        game = state
        return true
    }

    fun save() {
        val g = game ?: return
        if (g.outcome != null) prefs.edit().remove(SAVE_KEY).apply()
        else prefs.edit().putString(SAVE_KEY, g.serialize()).apply()
    }

    fun endCampaign() {
        game = null
        prefs.edit().remove(SAVE_KEY).apply()
    }

    fun handleBack(): Boolean = screen.onBack()

    fun resume() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        save()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.05f)
        lastFrameNanos = frameTimeNanos
        gfx.time += dt
        screen.update(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = min(w / Gfx.W, h / Gfx.H)
        offsetX = (w - Gfx.W * scale) / 2f
        offsetY = (h - Gfx.H * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Palette.BLACK)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, Gfx.W, Gfx.H)
        gfx.canvas = canvas
        screen.draw(gfx)
        screen.drawButtons(gfx)
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = (event.x - offsetX) / scale
        val y = (event.y - offsetY) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> screen.dispatchDown(x, y)
            MotionEvent.ACTION_MOVE -> screen.dispatchMove(x, y)
            MotionEvent.ACTION_UP -> screen.dispatchUp(x, y)
            MotionEvent.ACTION_CANCEL -> screen.dispatchCancel()
        }
        return true
    }

    companion object {
        private const val SAVE_KEY = "campaign"
    }
}
