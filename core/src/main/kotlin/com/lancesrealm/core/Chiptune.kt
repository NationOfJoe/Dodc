package com.lancesrealm.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class Wave { SQUARE, PULSE, TRIANGLE, NOISE }

/**
 * One line of a tune. Notes are space-separated tokens `<pitch>.<sixteenths>`: pitch is a note name,
 * optional `#` or `b`, and octave (`D4`, `F#5`, `Bb2`), `R` for a rest or `x` for a drum hit.
 */
class Voice(val wave: Wave, val notes: String, val volume: Float)

class Song(val bpm: Int, val loop: Boolean, val voices: List<Voice>)

enum class Music { TITLE, MAP, BATTLE, JOUST, RAID, VICTORY, DEFEAT }

enum class Sfx { CLASH, HIT, IMPACT, FANFARE, CHEER, CLICK, LAUNCH, COUCH, GLOOM }

/** A tiny three-channel synthesiser in the manner of 8-bit sound chips. */
object Chiptune {
    const val SAMPLE_RATE = 22050

    private fun bars(vararg bar: String) = bar.joinToString(" ")

    /** Repeats a pumping octave bass: root then the octave above, eighth notes. */
    private fun pump(vararg roots: String): String = roots.joinToString(" ") { r ->
        val octave = r.last().digitToInt()
        val up = r.dropLast(1) + (octave + 1)
        List(4) { "$r.2 $up.2" }.joinToString(" ")
    }

    private fun repeat(pattern: String, times: Int) = List(times) { pattern }.joinToString(" ")

    val songs: Map<Music, Song> = mapOf(
        Music.TITLE to Song(
            96, true, listOf(
                Voice(Wave.PULSE, bars(
                    "D5.4 A4.4 D5.4 F5.4", "E5.6 D5.2 C5.4 A4.4", "Bb4.4 C5.4 D5.4 E5.4", "F5.8 E5.8",
                    "D5.4 F5.4 A5.4 G5.4", "F5.6 E5.2 D5.4 C5.4", "Bb4.4 A4.4 G4.4 E4.4", "D4.16",
                ), 0.20f),
                Voice(Wave.SQUARE, bars(
                    "A4.8 F4.8", "G4.8 A4.8", "D4.8 G4.8", "C#5.16",
                    "F4.8 A4.8", "A4.8 F4.8", "D4.8 C#4.8", "D4.16",
                ), 0.08f),
                Voice(Wave.TRIANGLE, bars(
                    "D3.8 A2.8", "C3.8 F2.8", "G2.8 Bb2.8", "A2.16",
                    "D3.8 F3.8", "C3.8 A2.8", "G2.8 A2.8", "D3.16",
                ), 0.32f),
                Voice(Wave.NOISE, repeat("x.4 R.4 x.2 x.2 R.4", 8), 0.06f),
            )
        ),
        Music.MAP to Song(
            80, true, listOf(
                Voice(Wave.PULSE, bars(
                    "A4.4 C5.4 D5.8", "E5.4 D5.4 C5.4 A4.4", "G4.4 A4.4 C5.8", "A4.16",
                    "A4.4 C5.4 D5.8", "F5.4 E5.4 D5.4 C5.4", "D5.4 E5.4 C5.4 G4.4", "A4.16",
                ), 0.15f),
                Voice(Wave.TRIANGLE, bars(
                    "A2.16", "F2.16", "C3.16", "E2.16", "A2.16", "D3.16", "G2.16", "A2.16",
                ), 0.30f),
            )
        ),
        Music.BATTLE to Song(
            140, true, listOf(
                Voice(Wave.PULSE, bars(
                    "D5.2 D5.2 F5.2 D5.2 A5.4 G5.4", "F5.2 E5.2 D5.2 E5.2 F5.4 E5.4",
                    "D5.2 D5.2 F5.2 D5.2 Bb5.4 A5.4", "G5.2 F5.2 E5.2 C5.2 D5.8",
                    "A5.4 G5.4 F5.4 E5.4", "F5.2 G5.2 A5.2 F5.2 E5.8",
                    "D5.4 E5.4 F5.4 G5.4", "A5.2 G5.2 F5.2 E5.2 D5.8",
                ), 0.17f),
                Voice(Wave.TRIANGLE, pump("D2", "D2", "Bb1", "C2", "F2", "C2", "Bb1", "A1"), 0.34f),
                Voice(Wave.NOISE, repeat("x.2 R.2 x.2 x.2", 16), 0.07f),
            )
        ),
        Music.JOUST to Song(
            120, true, listOf(
                Voice(Wave.PULSE, bars(
                    "D5.2 D5.2 D5.2 F#5.2 A5.8", "F#5.4 A5.4 D6.8", "B5.4 A5.4 G5.4 F#5.4", "E5.16",
                    "D5.2 D5.2 D5.2 F#5.2 A5.8", "B5.4 A5.4 F#5.4 D5.4", "E5.4 F#5.4 E5.4 C#5.4", "D5.16",
                ), 0.17f),
                Voice(Wave.TRIANGLE, bars(
                    "D3.8 A2.8", "D3.8 F#3.8", "G2.8 D3.8", "A2.16",
                    "D3.8 A2.8", "G2.8 B2.8", "A2.8 A2.8", "D3.16",
                ), 0.32f),
                Voice(Wave.NOISE, repeat("x.4 x.2 x.2 x.4 x.4", 8), 0.06f),
            )
        ),
        Music.RAID to Song(
            100, true, listOf(
                Voice(Wave.TRIANGLE, bars(
                    "E4.2 R.2 E4.2 F4.2 E4.4 R.4", "E4.2 R.2 E4.2 G4.2 F#4.4 R.4",
                    "E4.2 R.2 E4.2 F4.2 E4.4 B3.4", "C4.4 B3.4 A#3.4 B3.4",
                ), 0.30f),
                Voice(Wave.SQUARE, repeat("E2.4 E2.4 E2.4 E2.4", 4), 0.07f),
                Voice(Wave.NOISE, repeat("x.2 R.6 x.2 R.6", 4), 0.05f),
            )
        ),
        Music.VICTORY to Song(
            110, false, listOf(
                Voice(Wave.PULSE, bars("D5.2 F#5.2 A5.2 D6.10", "B5.4 C#6.4 D6.8"), 0.2f),
                Voice(Wave.TRIANGLE, bars("D3.16", "A2.8 D3.8"), 0.32f),
            )
        ),
        Music.DEFEAT to Song(
            70, false, listOf(
                Voice(Wave.PULSE, bars("A4.8 G4.8", "F4.8 E4.8", "D4.16"), 0.18f),
                Voice(Wave.TRIANGLE, bars("D3.16", "A2.16", "D2.16"), 0.32f),
            )
        ),
    )

    private val noteIndex = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** MIDI note number for a pitch like `F#5`, or null for rests and drums. */
    fun midi(pitch: String): Int? {
        val base = noteIndex[pitch.firstOrNull() ?: return null] ?: return null
        var i = 1
        var semis = base
        when (pitch.getOrNull(1)) {
            '#' -> { semis++; i++ }
            'b' -> { semis--; i++ }
        }
        val octave = pitch.substring(i).toInt()
        return 12 * (octave + 1) + semis
    }

    fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Length of a voice in sixteenth notes. */
    fun length(voice: Voice): Int = voice.notes.split(' ').filter { it.isNotBlank() }.sumOf { it.substringAfter('.').toInt() }

    fun render(song: Song, rate: Int = SAMPLE_RATE): ShortArray {
        val sixteenth = rate * 60.0 / song.bpm / 4.0
        val total = song.voices.maxOf { length(it) }
        val frames = (total * sixteenth).toInt() + if (song.loop) 0 else rate / 2
        val mix = FloatArray(frames)
        for (v in song.voices) renderVoice(v, sixteenth, mix, rate)
        return toPcm(mix)
    }

    private fun renderVoice(v: Voice, sixteenth: Double, mix: FloatArray, rate: Int) {
        var pos = 0.0
        val noise = Random(7)
        for (token in v.notes.split(' ')) {
            if (token.isBlank()) continue
            val pitch = token.substringBefore('.')
            val len = token.substringAfter('.').toInt() * sixteenth
            val start = pos.toInt()
            val n = len.toInt()
            pos += len
            when {
                pitch == "R" -> Unit
                pitch == "x" -> {
                    val hit = (rate * 0.07).toInt()
                    for (i in 0 until minOf(hit, n)) {
                        val idx = start + i
                        if (idx >= mix.size) break
                        mix[idx] += (noise.nextFloat() * 2 - 1) * v.volume * exp(-i / (rate * 0.018)).toFloat()
                    }
                }
                else -> {
                    val f = frequency(midi(pitch) ?: continue)
                    val release = (n * 0.15).toInt().coerceAtLeast(1)
                    for (i in 0 until n) {
                        val idx = start + i
                        if (idx >= mix.size) break
                        val phase = (i * f / rate) % 1.0
                        val s = wave(v.wave, phase, noise)
                        val attack = (i / (rate * 0.006)).coerceAtMost(1.0)
                        val decay = 0.7 + 0.3 * exp(-i / (rate * 0.08))
                        val tail = if (i > n - release) (n - i).toDouble() / release else 1.0
                        mix[idx] += (s * attack * decay * tail).toFloat() * v.volume
                    }
                }
            }
        }
    }

    private fun wave(w: Wave, phase: Double, noise: Random): Float = when (w) {
        Wave.SQUARE -> if (phase < 0.5) 1f else -1f
        Wave.PULSE -> if (phase < 0.25) 1f else -1f
        Wave.TRIANGLE -> (if (phase < 0.5) 4 * phase - 1 else 3 - 4 * phase).toFloat()
        Wave.NOISE -> noise.nextFloat() * 2 - 1
    }

    fun sfx(effect: Sfx, rate: Int = SAMPLE_RATE): ShortArray {
        val noise = Random(effect.ordinal + 11)
        fun buf(seconds: Double) = FloatArray((rate * seconds).toInt())
        val out: FloatArray = when (effect) {
            Sfx.CLASH -> buf(0.22).also { b ->
                for (i in b.indices) {
                    val t = i.toDouble() / rate
                    val ring = if (((t * 1870) % 1.0) < 0.5) 1f else -1f
                    val ring2 = if (((t * 2630) % 1.0) < 0.5) 1f else -1f
                    b[i] = ((noise.nextFloat() * 2 - 1) * 0.5f * exp(-t / 0.03).toFloat() +
                        (ring + ring2) * 0.18f * exp(-t / 0.08).toFloat())
                }
            }
            Sfx.HIT -> sweep(buf(0.16), rate, 240.0, 70.0, Wave.SQUARE, 0.5f)
            Sfx.IMPACT -> buf(0.45).also { b ->
                var lp = 0f
                for (i in b.indices) {
                    val t = i.toDouble() / rate
                    lp += ((noise.nextFloat() * 2 - 1) - lp) * 0.12f
                    b[i] = lp * 1.6f * exp(-t / 0.12).toFloat() + sin(2 * PI * 55 * t).toFloat() * 0.5f * exp(-t / 0.15).toFloat()
                }
            }
            Sfx.FANFARE -> melody(rate, listOf("D5" to 0.08, "F#5" to 0.08, "A5" to 0.08, "D6" to 0.35), Wave.PULSE, 0.35f)
            Sfx.CHEER -> buf(1.1).also { b ->
                var lp = 0f
                for (i in b.indices) {
                    val t = i.toDouble() / rate
                    lp += ((noise.nextFloat() * 2 - 1) - lp) * 0.3f
                    val env = sin(PI * t / 1.1).toFloat()
                    b[i] = lp * env * 0.7f * (0.8f + 0.2f * sin(2 * PI * 7 * t).toFloat())
                }
            }
            Sfx.CLICK -> melody(rate, listOf("A5" to 0.03), Wave.SQUARE, 0.2f)
            Sfx.LAUNCH -> buf(0.3).also { b ->
                var lp = 0f
                for (i in b.indices) {
                    val t = i.toDouble() / rate
                    val k = (0.05 + 0.4 * t / 0.3).toFloat()
                    lp += ((noise.nextFloat() * 2 - 1) - lp) * k
                    b[i] = lp * 0.6f * sin(PI * t / 0.3).toFloat()
                }
            }
            Sfx.COUCH -> melody(rate, listOf("E6" to 0.05, "B6" to 0.12), Wave.TRIANGLE, 0.45f)
            Sfx.GLOOM -> sweep(buf(0.6), rate, 330.0, 110.0, Wave.TRIANGLE, 0.5f)
        }
        return toPcm(out)
    }

    private fun sweep(b: FloatArray, rate: Int, from: Double, to: Double, w: Wave, vol: Float): FloatArray {
        val noise = Random(1)
        var phase = 0.0
        for (i in b.indices) {
            val p = i.toDouble() / b.size
            phase = (phase + (from + (to - from) * p) / rate) % 1.0
            b[i] = wave(w, phase, noise) * vol * (1 - p).toFloat()
        }
        return b
    }

    private fun melody(rate: Int, notes: List<Pair<String, Double>>, w: Wave, vol: Float): FloatArray {
        val out = FloatArray((notes.sumOf { it.second } * rate).toInt() + 1)
        var start = 0
        val noise = Random(3)
        for ((pitch, seconds) in notes) {
            val n = (seconds * rate).toInt()
            val f = frequency(midi(pitch)!!)
            for (i in 0 until n) {
                if (start + i >= out.size) break
                val phase = (i * f / rate) % 1.0
                val tail = if (i > n * 0.7) (n - i) / (n * 0.3) else 1.0
                out[start + i] = wave(w, phase, noise) * vol * tail.toFloat()
            }
            start += n
        }
        return out
    }

    private fun toPcm(mix: FloatArray): ShortArray = ShortArray(mix.size) { i ->
        (mix[i].coerceIn(-1f, 1f) * 30000).toInt().toShort()
    }
}
