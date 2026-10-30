package com.lancesrealm.game

import kotlin.math.sin

/** Small procedural figures; no bitmap assets are needed. */
object Sprites {
    /** A curtain wall with two towers, [x],[y] being the bottom-left corner. */
    fun castle(g: Gfx, x: Float, y: Float, w: Float, h: Float, stone: Int, flag: Int) {
        val towerW = w * 0.22f
        val wallH = h * 0.6f
        g.rect(x + towerW * 0.5f, y - wallH, w - towerW, wallH, stone)
        crenels(g, x + towerW * 0.5f, y - wallH, w - towerW, stone)
        for (tx in listOf(x, x + w - towerW)) {
            g.rect(tx, y - h, towerW, h, Palette.darker(stone, 0.85f))
            crenels(g, tx, y - h, towerW, Palette.darker(stone, 0.85f))
            g.rect(tx + towerW * 0.4f, y - h * 0.75f, towerW * 0.2f, h * 0.12f, Palette.BLACK)
        }
        // Gate.
        g.rect(x + w / 2 - w * 0.1f, y - wallH * 0.55f, w * 0.2f, wallH * 0.55f, Palette.BLACK)
        g.oval(x + w / 2 - w * 0.1f, y - wallH * 0.55f - w * 0.1f, w * 0.2f, w * 0.2f, Palette.BLACK)
        // Keep and pennant.
        val kx = x + w / 2 - towerW * 0.6f
        g.rect(kx, y - h * 1.35f, towerW * 1.2f, h * 0.75f, Palette.darker(stone, 0.7f))
        crenels(g, kx, y - h * 1.35f, towerW * 1.2f, Palette.darker(stone, 0.7f))
        val px = x + w / 2
        val py = y - h * 1.35f - 2
        g.line(px, py, px, py - h * 0.35f, Palette.WHITE, 1f)
        val wave = sin(g.time * 6f) * 1.5f
        g.poly(flag, px, py - h * 0.35f, px + h * 0.3f, py - h * 0.3f + wave, px, py - h * 0.22f)
    }

    private fun crenels(g: Gfx, x: Float, y: Float, w: Float, color: Int) {
        val n = (w / 4f).toInt().coerceAtLeast(2)
        val cw = w / (n * 2 - 1)
        for (i in 0 until n) g.rect(x + i * cw * 2, y - cw, cw, cw, color)
    }

    /** A foot soldier with a spear, standing on ([x],[y]). */
    fun soldier(g: Gfx, x: Float, y: Float, color: Int, facing: Int, step: Float = 0f, s: Float = 1f) {
        val bob = sin(step) * 0.6f * s
        g.rect(x - 1.5f * s, y - 4f * s, 1.2f * s, 4f * s + bob, Palette.BROWN)
        g.rect(x + 0.3f * s, y - 4f * s, 1.2f * s, 4f * s - bob, Palette.BROWN)
        g.rect(x - 2f * s, y - 9f * s, 4f * s, 5.5f * s, color)
        g.circle(x, y - 10.5f * s, 1.7f * s, Palette.LIGHT_RED)
        g.rect(x - 1.8f * s, y - 12.6f * s, 3.6f * s, 1.3f * s, Palette.LIGHT_GREY)
        g.line(x + facing * 2.5f * s, y - 1f * s, x + facing * 3.5f * s, y - 16f * s, Palette.LIGHT_GREY, 0.6f * s)
    }

    /** A mounted knight with a couched lance. */
    fun knight(g: Gfx, x: Float, y: Float, color: Int, facing: Int, step: Float = 0f, s: Float = 1f) {
        val f = facing.toFloat()
        val gallop = sin(step) * 1.2f * s
        // Horse legs.
        g.line(x - 4f * s, y - 5f * s, x - 4f * s - gallop, y, Palette.BROWN, 1.2f * s)
        g.line(x + 4f * s, y - 5f * s, x + 4f * s + gallop, y, Palette.BROWN, 1.2f * s)
        // Barded horse.
        g.oval(x - 7f * s, y - 10f * s, 14f * s, 6f * s, Palette.LIGHT_GREY)
        g.rect(x - 6f * s, y - 8f * s, 12f * s, 3f * s, color)
        g.poly(Palette.LIGHT_GREY, x + f * 5f * s, y - 9f * s, x + f * 10f * s, y - 13f * s, x + f * 11f * s, y - 10f * s, x + f * 7f * s, y - 6.5f * s)
        // Rider.
        g.rect(x - 2f * s, y - 16f * s, 4f * s, 7f * s, color)
        g.rect(x - 2f * s, y - 19.5f * s, 4f * s, 4f * s, Palette.LIGHT_GREY)
        g.rect(x - 0.5f * s + f * 0.6f * s, y - 18.5f * s, 1.6f * s, 0.8f * s, Palette.BLACK)
        g.line(x - f * 3f * s, y - 12f * s, x + f * 16f * s, y - 15f * s, Palette.YELLOW, 0.9f * s)
    }

    fun cloud(g: Gfx, x: Float, y: Float, s: Float, color: Int = Palette.LIGHT_GREY) {
        g.oval(x, y, 24f * s, 8f * s, color)
        g.oval(x + 6f * s, y - 5f * s, 14f * s, 10f * s, color)
    }

    fun tree(g: Gfx, x: Float, y: Float, s: Float = 1f) {
        g.rect(x - 1f * s, y - 5f * s, 2f * s, 5f * s, Palette.BROWN)
        g.poly(Palette.GREEN, x, y - 16f * s, x + 5f * s, y - 4f * s, x - 5f * s, y - 4f * s)
    }
}
