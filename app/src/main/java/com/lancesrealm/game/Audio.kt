package com.lancesrealm.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.lancesrealm.core.Chiptune
import com.lancesrealm.core.Music
import com.lancesrealm.core.Sfx
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Plays the synthesised chiptune score and sound effects. All rendering happens off the UI thread. */
class Audio(context: Context) {
    private val prefs = context.getSharedPreferences("lances", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val musicPcm = ConcurrentHashMap<Music, ShortArray>()
    private val sfxPcm = ConcurrentHashMap<Sfx, ShortArray>()
    private val playing = ArrayList<Pair<AudioTrack, Long>>()

    private var musicTrack: AudioTrack? = null
    @Volatile private var current: Music? = null
    @Volatile private var paused = false

    var muted: Boolean = prefs.getBoolean(MUTE_KEY, false)
        private set

    fun toggleMute() {
        muted = !muted
        prefs.edit().putBoolean(MUTE_KEY, muted).apply()
        val m = current
        if (muted) worker.execute { stopMusic() } else if (m != null) { current = null; music(m) }
    }

    fun music(m: Music) {
        if (m == current) return
        current = m
        worker.execute {
            stopMusic()
            if (muted || paused || current != m) return@execute
            val pcm = musicPcm.getOrPut(m) { Chiptune.render(Chiptune.songs.getValue(m)) }
            if (current != m) return@execute
            musicTrack = track(pcm, MUSIC_VOLUME).apply {
                if (Chiptune.songs.getValue(m).loop) setLoopPoints(0, pcm.size, -1)
                play()
            }
        }
    }

    fun sfx(effect: Sfx) {
        if (muted || paused) return
        worker.execute {
            reapFinished()
            val pcm = sfxPcm.getOrPut(effect) { Chiptune.sfx(effect) }
            val t = track(pcm, SFX_VOLUME)
            t.play()
            playing += t to System.currentTimeMillis() + pcm.size * 1000L / Chiptune.SAMPLE_RATE + 200
        }
    }

    fun pause() {
        paused = true
        worker.execute { musicTrack?.pause() }
    }

    fun resume() {
        paused = false
        worker.execute {
            val t = musicTrack
            if (t != null && !muted) t.play()
            else current?.let { m -> current = null; music(m) }
        }
    }

    private fun stopMusic() {
        musicTrack?.let { runCatching { it.stop() }; it.release() }
        musicTrack = null
    }

    private fun reapFinished() {
        val now = System.currentTimeMillis()
        playing.removeAll { (t, end) ->
            (now > end).also { if (it) t.release() }
        }
    }

    private fun track(pcm: ShortArray, volume: Float): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Chiptune.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .apply {
                write(pcm, 0, pcm.size)
                setVolume(volume)
            }

    companion object {
        private const val MUTE_KEY = "muted"
        private const val MUSIC_VOLUME = 0.55f
        private const val SFX_VOLUME = 0.9f
    }
}
