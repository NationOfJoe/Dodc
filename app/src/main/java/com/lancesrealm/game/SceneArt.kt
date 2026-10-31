package com.lancesrealm.game

import com.lancesrealm.core.Scene
import com.lancesrealm.core.WorldMap
import kotlin.math.sin

/**
 * Full-width illustrations for story panels, drawn in the top [H] units of the screen. Everything
 * is procedural so no image assets are needed.
 */
object SceneArt {
    const val H = 180f
    private const val W = Gfx.W

    fun draw(g: Gfx, scene: Scene, lordId: Int) {
        val lordColor = if (lordId in 1..3) Palette.lord(lordId) else Palette.RED
        when (scene) {
            Scene.THRONE -> throneRoom(g, empty = true, Palette.DARK_GREY)
            Scene.CORONATION -> throneRoom(g, empty = false, Palette.lord(0))
            Scene.RIVALS -> rivals(g)
            Scene.HIGHMERE -> highmere(g)
            Scene.MESSENGER -> messenger(g)
            Scene.PLAGUE -> plague(g)
            Scene.POOR_HARVEST -> field(g, good = false)
            Scene.GOOD_HARVEST -> field(g, good = true)
            Scene.FIRE -> burning(g, Palette.lord(0), fallen = false)
            Scene.FALL -> burning(g, Palette.lord(0), fallen = true)
            Scene.MERCENARIES -> mercenaries(g)
            Scene.KNIGHT -> knightErrant(g)
            Scene.TOWER -> tower(g, lordColor)
            Scene.REBELS -> rebels(g)
            Scene.TRUCE -> truce(g, lordColor)
            Scene.TAUNT -> portrait(g, lordId, lordColor)
            Scene.REUNION -> reunion(g)
        }
    }

    // --- Shared pieces ---------------------------------------------------------------------

    private fun sky(g: Gfx, top: Int, bottom: Int, bands: Int = 8, height: Float = H) {
        val bh = height / bands
        for (i in 0 until bands) {
            val f = i / (bands - 1f)
            g.rect(0f, i * bh, W, bh + 1, blend(top, bottom, f))
        }
    }

    fun blend(a: Int, b: Int, f: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) * (1 - f) + (b shr shift and 0xFF) * f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun stars(g: Gfx, count: Int = 40) {
        for (i in 0 until count) {
            val x = WorldMap.hash(i, 3) * W
            val y = WorldMap.hash(i, 7) * H * 0.55f
            val tw = if (sin(g.time * 3f + i) > 0.6f) Palette.WHITE else Palette.LIGHT_GREY
            g.rect(x, y, 1.2f, 1.2f, tw)
        }
    }

    private fun hills(g: Gfx, y: Float, color: Int, amp: Float = 10f, freq: Float = 40f) {
        g.path.reset()
        g.path.moveTo(0f, H)
        var x = 0f
        while (x <= W) { g.path.lineTo(x, y - sin(x / freq) * amp - sin(x / 13f) * amp * 0.3f); x += 6f }
        g.path.lineTo(W, H); g.path.close()
        g.fillPath(color)
    }

    private fun flame(g: Gfx, x: Float, y: Float, s: Float, seed: Int) {
        val f = sin(g.time * 17f + seed) * 0.25f + 1f
        g.oval(x - 4f * s, y - 14f * s * f, 8f * s, 14f * s * f, Palette.ORANGE)
        g.oval(x - 2.5f * s, y - 9f * s * f, 5f * s, 9f * s * f, Palette.YELLOW)
    }

    private fun torch(g: Gfx, x: Float, y: Float, seed: Int) {
        g.line(x, y, x, y + 14f, Palette.BROWN, 1.5f)
        flame(g, x, y, 0.6f, seed)
    }

    /** Head-and-shoulders figure; [crowned] adds a circlet, otherwise a helm. */
    private fun bust(g: Gfx, cx: Float, cy: Float, s: Float, color: Int, crowned: Boolean = false, hair: Int = Palette.BROWN) {
        g.oval(cx - 22f * s, cy + 6f * s, 44f * s, 40f * s, color)
        g.rect(cx - 5f * s, cy - 2f * s, 10f * s, 10f * s, Palette.LIGHT_RED)
        g.oval(cx - 11f * s, cy - 26f * s, 22f * s, 28f * s, Palette.LIGHT_RED)
        if (crowned) {
            g.oval(cx - 12f * s, cy - 30f * s, 24f * s, 14f * s, hair)
            g.poly(Palette.YELLOW, cx - 11f * s, cy - 26f * s, cx - 11f * s, cy - 34f * s, cx - 6f * s, cy - 29f * s,
                cx, cy - 36f * s, cx + 6f * s, cy - 29f * s, cx + 11f * s, cy - 34f * s, cx + 11f * s, cy - 26f * s)
        } else {
            g.oval(cx - 12f * s, cy - 30f * s, 24f * s, 20f * s, Palette.LIGHT_GREY)
            g.rect(cx - 12f * s, cy - 20f * s, 24f * s, 4f * s, Palette.GREY)
        }
        g.rect(cx - 6f * s, cy - 14f * s, 3f * s, 2f * s, Palette.BLACK)
        g.rect(cx + 3f * s, cy - 14f * s, 3f * s, 2f * s, Palette.BLACK)
    }

    /** A standing figure in a robe or dress. */
    private fun figure(g: Gfx, x: Float, foot: Float, s: Float, color: Int, hair: Int) {
        g.poly(color, x - 7f * s, foot, x + 7f * s, foot, x + 3.5f * s, foot - 22f * s, x - 3.5f * s, foot - 22f * s)
        g.rect(x - 4f * s, foot - 28f * s, 8f * s, 8f * s, color)
        g.circle(x, foot - 32f * s, 3.6f * s, Palette.LIGHT_RED)
        g.oval(x - 4f * s, foot - 37f * s, 8f * s, 5f * s, hair)
    }

    // --- Scenes ----------------------------------------------------------------------------

    private fun throneRoom(g: Gfx, empty: Boolean, banner: Int) {
        g.rect(0f, 0f, W, H, Palette.darker(Palette.DARK_GREY, 0.7f))
        for (row in 0 until 8) for (col in 0 until 13) {
            g.frame(col * 40f - (row % 2) * 20f, row * 18f, 40f, 18f, Palette.darker(Palette.DARK_GREY, 0.5f))
        }
        // Floor and carpet.
        g.rect(0f, 130f, W, 50f, Palette.darker(Palette.BROWN, 0.7f))
        g.poly(Palette.RED, 220f, 130f, 260f, 130f, 330f, H, 150f, H)
        // Pillars and banners.
        for (px in listOf(60f, 140f, 340f, 420f)) {
            g.rect(px - 10f, 10f, 20f, 120f, Palette.GREY)
            g.rect(px - 13f, 10f, 26f, 6f, Palette.LIGHT_GREY)
            g.rect(px - 13f, 124f, 26f, 6f, Palette.LIGHT_GREY)
            val b = if (empty) banner else Palette.lord(0)
            g.poly(b, px - 9f, 22f, px + 9f, 22f, px + 9f, 70f, px, 62f + sin(g.time * 2f + px) * 2f, px - 9f, 70f)
        }
        // Dais and throne.
        g.rect(190f, 118f, 100f, 14f, Palette.GREY)
        g.rect(200f, 110f, 80f, 10f, Palette.LIGHT_GREY)
        g.rect(218f, 40f, 44f, 72f, Palette.ORANGE)
        g.poly(Palette.YELLOW, 218f, 40f, 240f, 22f, 262f, 40f)
        g.rect(224f, 50f, 32f, 50f, Palette.RED)
        g.rect(212f, 84f, 56f, 8f, Palette.ORANGE)
        for ((cx, seed) in listOf(180f to 1, 300f to 2)) {
            g.rect(cx - 2f, 96f, 4f, 22f, Palette.WHITE)
            flame(g, cx, 96f, 0.5f, seed)
        }
        if (empty) {
            // The crown waits on its cushion.
            g.oval(296f, 112f, 26f, 8f, Palette.PURPLE)
            g.poly(Palette.YELLOW, 300f, 113f, 300f, 104f, 305f, 109f, 309f, 101f, 313f, 109f, 318f, 104f, 318f, 113f)
        } else {
            figure(g, 240f, 112f, 1.6f, Palette.lord(0), Palette.BROWN)
            g.poly(Palette.YELLOW, 234f, 52f, 234f, 46f, 237f, 49f, 240f, 44f, 243f, 49f, 246f, 46f, 246f, 52f)
            for (i in 0 until 14) {
                val x = 20f + i * 34f
                if (x in 150f..330f) continue
                figure(g, x, 178f, 1.1f, Palette.lord(if (i % 3 == 0) 0 else -1), Palette.BLACK)
            }
        }
    }

    private fun rivals(g: Gfx) {
        sky(g, Palette.BLACK, Palette.darker(Palette.PURPLE, 0.5f))
        val names = listOf("VASK", "MORLAN", "YSOLDE")
        for (i in 0 until 3) {
            val cx = 90f + i * 150f
            g.rect(cx - 56f, 20f, 112f, 150f, Palette.ORANGE)
            g.rect(cx - 52f, 24f, 104f, 142f, Palette.darker(Palette.lord(i + 1), 0.45f))
            bust(g, cx, 92f, 1.6f, Palette.lord(i + 1), crowned = i == 2, hair = if (i == 2) Palette.RED else Palette.BROWN)
            g.text(names[i], cx, 160f, 10f, Palette.YELLOW, android.graphics.Paint.Align.CENTER, shadow = true)
        }
    }

    private fun highmere(g: Gfx) {
        sky(g, Palette.LIGHT_BLUE, Palette.CYAN)
        Sprites.cloud(g, (g.time * 8f) % 520f - 40f, 30f, 1.4f, Palette.WHITE)
        hills(g, 128f, Palette.darker(Palette.GREEN, 0.7f), 12f)
        g.rect(0f, 150f, W, 30f, Palette.GREEN)
        Sprites.castle(g, 180f, 150f, 130f, 80f, Palette.LIGHT_GREY, Palette.lord(0))
        figure(g, 190f, 150f - 48f, 0.8f, Palette.lord(0), Palette.BROWN)
        figure(g, 202f, 150f - 48f, 0.75f, Palette.GREEN, Palette.ORANGE)
        Sprites.tree(g, 60f, 154f, 1.6f); Sprites.tree(g, 84f, 158f, 1.2f); Sprites.tree(g, 400f, 156f, 1.5f)
    }

    private fun messenger(g: Gfx) {
        sky(g, Palette.darker(Palette.PURPLE, 0.6f), Palette.ORANGE)
        g.circle(380f, 120f, 26f, Palette.YELLOW)
        hills(g, 140f, Palette.darker(Palette.BROWN, 0.6f), 8f)
        Sprites.castle(g, 330f, 146f, 90f, 54f, Palette.darker(Palette.DARK_GREY, 0.8f), Palette.lord(0))
        val x = 60f + (g.time * 40f) % 200f
        Sprites.knight(g, x, 166f, Palette.BLACK, 1, g.time * 16f, 2.2f)
        for (i in 0 until 5) g.oval(x - 40f - i * 12f, 158f + sin(g.time * 9 + i) * 2, 14f, 8f, Palette.withAlpha(Palette.LIGHT_GREY, 120 - i * 20))
    }

    private fun plague(g: Gfx) {
        sky(g, Palette.DARK_GREY, Palette.darker(Palette.LIGHT_GREEN, 0.5f))
        hills(g, 130f, Palette.darker(Palette.GREEN, 0.4f), 6f)
        for (i in 0 until 5) {
            val x = 50f + i * 90f
            g.poly(Palette.LIGHT_GREY, x - 24f, 150f, x + 24f, 150f, x, 112f)
            g.poly(Palette.BLACK, x - 5f, 150f, x + 5f, 150f, x, 134f)
        }
        g.rect(0f, 150f, W, 30f, Palette.darker(Palette.GREEN, 0.35f))
        for (i in 0 until 3) {
            val fx = (g.time * (12f + i * 5)) % 560f - 40f
            g.rect(fx, 120f + i * 14f, 140f, 5f, Palette.withAlpha(Palette.LIGHT_GREEN, 60))
        }
        for (i in 0 until 6) {
            val bx = 80f + i * 60f + sin(g.time + i) * 20f
            val by = 40f + (i % 3) * 14f
            g.line(bx - 6f, by - 3f, bx, by, Palette.BLACK, 1.5f)
            g.line(bx, by, bx + 6f, by - 3f, Palette.BLACK, 1.5f)
        }
    }

    private fun field(g: Gfx, good: Boolean) {
        if (good) {
            sky(g, Palette.LIGHT_BLUE, Palette.CYAN)
            g.circle(400f, 40f, 20f, Palette.YELLOW)
        } else {
            sky(g, Palette.DARK_GREY, Palette.GREY)
        }
        hills(g, 100f, if (good) Palette.GREEN else Palette.darker(Palette.GREEN, 0.5f), 6f, 60f)
        val ground = if (good) Palette.YELLOW else Palette.darker(Palette.BROWN, 0.8f)
        g.rect(0f, 110f, W, 70f, ground)
        for (row in 0 until 4) for (i in 0 until 10) {
            val x = 20f + i * 48f + row * 12f
            val y = 122f + row * 16f
            val c = if (good) Palette.ORANGE else Palette.GREY
            if (good) g.poly(c, x - 6f, y + 10f, x + 6f, y + 10f, x + 2f, y - 8f, x - 2f, y - 8f)
            else g.poly(c, x - 8f, y + 10f, x + 8f, y + 10f, x + 9f, y + 4f, x - 9f, y + 6f)
        }
        if (!good) {
            for (i in 0 until 60) {
                val x = (WorldMap.hash(i, 1) * W + g.time * 60f) % W
                val y = (WorldMap.hash(i, 2) * H + g.time * 220f) % H
                g.line(x, y, x - 3f, y + 9f, Palette.withAlpha(Palette.LIGHT_BLUE, 160), 1f)
            }
        }
    }

    private fun burning(g: Gfx, flag: Int, fallen: Boolean) {
        sky(g, Palette.BLACK, Palette.darker(Palette.RED, 0.6f))
        stars(g, 20)
        hills(g, 150f, Palette.BLACK, 6f)
        Sprites.castle(g, 160f, 160f, 160f, 90f, Palette.DARK_GREY, if (fallen) Palette.RED else flag)
        for (i in 0 until 9) flame(g, 170f + i * 18f, 160f - (i % 3) * 20f - 20f, 2f, i)
        for (i in 0 until 5) {
            val drift = (g.time * 10f + i * 30f) % 120f
            g.oval(200f + i * 25f + drift * 0.3f, 40f - drift * 0.2f, 40f, 18f, Palette.withAlpha(Palette.DARK_GREY, 150))
        }
        g.rect(0f, 160f, W, 20f, Palette.BLACK)
        if (fallen) {
            g.line(70f, 170f, 90f, 120f, Palette.BROWN, 2f)
            g.poly(Palette.lord(0), 90f, 120f, 112f, 128f, 100f, 132f, 108f, 140f, 88f, 136f)
        }
    }

    private fun mercenaries(g: Gfx) {
        sky(g, Palette.darker(Palette.BLUE, 0.8f), Palette.LIGHT_RED)
        hills(g, 130f, Palette.darker(Palette.GREEN, 0.5f), 8f)
        g.rect(0f, 150f, W, 30f, Palette.darker(Palette.GREEN, 0.6f))
        for (i in 0 until 3) {
            val x = 60f + i * 70f
            g.poly(if (i % 2 == 0) Palette.RED else Palette.YELLOW, x - 26f, 150f, x + 26f, 150f, x, 108f)
            g.poly(Palette.BLACK, x - 6f, 150f, x + 6f, 150f, x, 132f)
        }
        flame(g, 290f, 152f, 1.6f, 4)
        for (i in 0 until 9) Sprites.soldier(g, 320f + i * 16f, 168f - (i % 2) * 4f, Palette.GREY, -1, 0f, 1.6f)
    }

    private fun knightErrant(g: Gfx) {
        sky(g, Palette.PURPLE, Palette.ORANGE)
        g.circle(240f, 130f, 34f, Palette.YELLOW)
        hills(g, 150f, Palette.BLACK, 18f, 70f)
        Sprites.knight(g, 240f, 128f, Palette.BLACK, 1, 0f, 2.6f)
        g.line(260f, 90f, 262f, 60f, Palette.BLACK, 1.5f)
        g.poly(Palette.LIGHT_RED, 262f, 60f, 280f, 64f + sin(g.time * 5f) * 2f, 262f, 68f)
    }

    private fun tower(g: Gfx, lordColor: Int) {
        sky(g, Palette.BLACK, Palette.BLUE)
        stars(g)
        g.circle(90f, 40f, 14f, Palette.LIGHT_GREY)
        g.circle(96f, 36f, 13f, SceneArt.blend(Palette.BLACK, Palette.BLUE, 0.15f))
        hills(g, 160f, Palette.BLACK, 4f)
        g.rect(250f, 20f, 60f, 160f, Palette.DARK_GREY)
        for (i in 0 until 6) g.rect(250f + i * 11f, 12f, 6f, 8f, Palette.DARK_GREY)
        val lit = if (sin(g.time * 6f) > -0.8f) Palette.YELLOW else Palette.ORANGE
        g.rect(272f, 50f, 16f, 22f, lit)
        figure(g, 280f, 72f, 0.5f, Palette.BLACK, Palette.BLACK)
        g.poly(lordColor, 280f, 0f, 296f, 4f + sin(g.time * 4f) * 2f, 280f, 8f)
        g.line(280f, 0f, 280f, 12f, Palette.LIGHT_GREY)
    }

    private fun rebels(g: Gfx) {
        sky(g, Palette.BLACK, Palette.darker(Palette.BLUE, 0.7f))
        stars(g, 25)
        for (i in 0 until 4) {
            val x = 40f + i * 120f
            g.rect(x, 104f, 60f, 40f, Palette.darker(Palette.BROWN, 0.7f))
            g.poly(Palette.darker(Palette.ORANGE, 0.6f), x - 6f, 104f, x + 66f, 104f, x + 30f, 80f)
            g.rect(x + 22f, 120f, 14f, 24f, Palette.BLACK)
        }
        g.rect(0f, 144f, W, 36f, Palette.darker(Palette.BROWN, 0.5f))
        for (i in 0 until 16) {
            val x = 20f + i * 29f
            val y = 172f - (i % 3) * 5f
            figure(g, x, y, 1f, if (i % 2 == 0) Palette.BROWN else Palette.GREY, Palette.BLACK)
            if (i % 3 == 0) torch(g, x + 6f, y - 46f, i)
            else {
                g.line(x + 6f, y - 18f, x + 6f, y - 50f, Palette.BROWN, 1.2f)
                g.line(x + 3f, y - 50f, x + 3f, y - 56f, Palette.LIGHT_GREY, 0.8f)
                g.line(x + 9f, y - 50f, x + 9f, y - 56f, Palette.LIGHT_GREY, 0.8f)
                g.line(x + 3f, y - 50f, x + 9f, y - 50f, Palette.LIGHT_GREY, 0.8f)
            }
        }
    }

    private fun truce(g: Gfx, lordColor: Int) {
        g.rect(0f, 0f, W, H, Palette.darker(Palette.BROWN, 0.5f))
        for (i in 0 until 12) g.rect(0f, i * 15f, W, 1f, Palette.darker(Palette.BROWN, 0.35f))
        g.rect(120f, 18f, 240f, 150f, Palette.YELLOW)
        g.rect(112f, 12f, 256f, 12f, Palette.ORANGE)
        g.rect(112f, 162f, 256f, 12f, Palette.ORANGE)
        for (i in 0 until 7) {
            val w = 180f - (i % 3) * 30f
            g.line(150f, 44f + i * 13f, 150f + w, 44f + i * 13f, Palette.BROWN, 1f)
        }
        g.circle(190f, 148f, 12f, lordColor)
        g.circle(290f, 148f, 12f, Palette.lord(0))
        g.line(330f, 40f, 400f, 0f, Palette.WHITE, 2f)
    }

    private fun portrait(g: Gfx, lordId: Int, lordColor: Int) {
        sky(g, Palette.BLACK, Palette.darker(lordColor, 0.6f))
        g.rect(150f, 8f, 180f, 168f, Palette.ORANGE)
        g.rect(156f, 14f, 168f, 156f, Palette.darker(lordColor, 0.4f))
        bust(g, 240f, 104f, 2.4f, lordColor, crowned = lordId == 3, hair = if (lordId == 3) Palette.RED else Palette.BROWN)
        // A sly grin.
        g.line(228f, 92f, 252f, 90f, Palette.BLACK, 1.5f)
        g.line(252f, 90f, 256f, 86f, Palette.BLACK, 1.5f)
        for ((x, seed) in listOf(110f to 1, 370f to 2)) torch(g, x, 70f, seed)
    }

    private fun reunion(g: Gfx) {
        g.rect(0f, 0f, W, H, Palette.darker(Palette.BROWN, 0.45f))
        for (row in 0 until 7) for (col in 0 until 13) {
            g.frame(col * 40f - (row % 2) * 20f, row * 20f, 40f, 20f, Palette.darker(Palette.BROWN, 0.3f))
        }
        g.rect(0f, 150f, W, 30f, Palette.darker(Palette.BROWN, 0.7f))
        // Hearth.
        g.rect(320f, 70f, 110f, 80f, Palette.DARK_GREY)
        g.rect(334f, 96f, 82f, 54f, Palette.BLACK)
        for (i in 0 until 4) flame(g, 348f + i * 18f, 150f, 1.4f, i)
        figure(g, 200f, 168f, 2.2f, Palette.lord(0), Palette.BROWN)
        figure(g, 234f, 168f, 2.0f, Palette.GREEN, Palette.ORANGE)
        g.line(208f, 120f, 228f, 118f, Palette.LIGHT_RED, 3f)
        for (px in listOf(60f, 120f)) {
            g.poly(Palette.lord(0), px - 12f, 20f, px + 12f, 20f, px + 12f, 80f, px, 70f, px - 12f, 80f)
        }
    }
}
