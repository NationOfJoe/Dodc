package com.lancesrealm.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The fixed land of Valdria. Territories are seeds of a warped Voronoi diagram rasterised to a
 * coarse grid; the grid gives both the chunky retro map picture and the adjacency graph.
 */
object WorldMap {
    const val GRID_W = 144
    const val GRID_H = 96
    const val SEA = -2

    data class Def(val name: String, val x: Float, val y: Float, val income: Int, val castle: Boolean = false)

    /** The first four entries are castles: player, then one per [Rival]. */
    val defs = listOf(
        Def("Highmere", 0.14f, 0.80f, 30, castle = true),
        Def("Ravensgate", 0.84f, 0.18f, 30, castle = true),
        Def("Duskhold", 0.15f, 0.19f, 30, castle = true),
        Def("Saltmarsh Keep", 0.87f, 0.79f, 30, castle = true),
        Def("Cold Fell", 0.33f, 0.14f, 10),
        Def("Greyharrow", 0.51f, 0.13f, 15),
        Def("Hollin", 0.68f, 0.11f, 12),
        Def("Brackenford", 0.12f, 0.48f, 18),
        Def("Wyrmwood", 0.31f, 0.36f, 12),
        Def("Oxley", 0.50f, 0.34f, 22),
        Def("Fallowmere", 0.69f, 0.37f, 20),
        Def("Northwatch", 0.88f, 0.44f, 14),
        Def("Elmshire", 0.29f, 0.60f, 24),
        Def("Amberfield", 0.48f, 0.57f, 30),
        Def("Mirefen", 0.67f, 0.60f, 12),
        Def("Larkspur", 0.84f, 0.62f, 16),
        Def("Thistlemoor", 0.33f, 0.84f, 14),
        Def("Kestrel Downs", 0.52f, 0.82f, 20),
        Def("Stonebridge", 0.70f, 0.83f, 22),
    )

    /** Territory id per grid cell, or [SEA]. Row-major, [GRID_W] x [GRID_H]. */
    val cells: IntArray by lazy { rasterise() }

    val adjacency: List<Set<Int>> by lazy { computeAdjacency() }

    fun cellAt(gx: Int, gy: Int): Int =
        if (gx !in 0 until GRID_W || gy !in 0 until GRID_H) SEA else cells[gy * GRID_W + gx]

    /** Territory at normalised map coordinates, or [SEA]. */
    fun territoryAt(nx: Float, ny: Float): Int =
        cellAt(floor(nx * GRID_W).toInt(), floor(ny * GRID_H).toInt())

    fun isBorder(gx: Int, gy: Int): Boolean {
        val c = cellAt(gx, gy)
        if (c == SEA) return false
        return cellAt(gx + 1, gy) != c || cellAt(gx, gy + 1) != c ||
            cellAt(gx - 1, gy) != c || cellAt(gx, gy - 1) != c
    }

    private fun rasterise(): IntArray {
        val out = IntArray(GRID_W * GRID_H)
        val aspect = GRID_W.toFloat() / GRID_H
        for (gy in 0 until GRID_H) {
            for (gx in 0 until GRID_W) {
                val nx = (gx + 0.5f) / GRID_W
                val ny = (gy + 0.5f) / GRID_H
                val coast = min(min(nx, 1f - nx) * aspect, min(ny, 1f - ny))
                if (coast + (noise(nx * 9f, ny * 9f) - 0.5f) * 0.07f < 0.035f) {
                    out[gy * GRID_W + gx] = SEA
                    continue
                }
                // Warp the sample point so borders wander like rivers and ridgelines.
                val wx = nx + (noise(nx * 7f + 11f, ny * 7f) - 0.5f) * 0.07f
                val wy = ny + (noise(nx * 7f, ny * 7f + 23f) - 0.5f) * 0.07f
                var best = 0
                var bestD = Float.MAX_VALUE
                defs.forEachIndexed { i, d ->
                    val dx = (wx - d.x) * aspect
                    val dy = wy - d.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < bestD) { bestD = dist; best = i }
                }
                out[gy * GRID_W + gx] = best
            }
        }
        return out
    }

    private fun computeAdjacency(): List<Set<Int>> {
        val sets = List(defs.size) { mutableSetOf<Int>() }
        for (gy in 0 until GRID_H) for (gx in 0 until GRID_W) {
            val c = cellAt(gx, gy)
            if (c == SEA) continue
            for ((ox, oy) in arrayOf(1 to 0, 0 to 1)) {
                val n = cellAt(gx + ox, gy + oy)
                if (n != SEA && n != c) { sets[c].add(n); sets[n].add(c) }
            }
        }
        return sets
    }

    /** Smooth deterministic value noise in [0,1]. */
    fun noise(x: Float, y: Float): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = hash(x0, y0); val b = hash(x0 + 1, y0)
        val c = hash(x0, y0 + 1); val d = hash(x0 + 1, y0 + 1)
        val top = a + (b - a) * sx
        val bottom = c + (d - c) * sx
        return top + (bottom - top) * sy
    }

    fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return abs(h % 10000) / 10000f
    }
}
