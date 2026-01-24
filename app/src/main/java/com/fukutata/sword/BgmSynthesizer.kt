package com.fukutata.sword

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

class BgmSynthesizer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var thread: Thread? = null

    private val sampleRate = 44100
    private var bpm = 120.0

    private val notes = mapOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23,
        "G" to 392.00, "A" to 440.00, "B" to 493.88, "C2" to 523.25
    )

    fun start(isBoss: Boolean) {
        if (isPlaying) stop()
        isPlaying = true
        bpm = if (isBoss) 150.0 else 110.0
        
        val melody = if (isBoss) {
            listOf("A", "F", "A", "F", "G", "E", "G", "E")
        } else {
            listOf("C", "E", "G", "E", "F", "A", "C2", "A")
        }

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(sampleRate)
            .build()

        thread = Thread {
            audioTrack?.play()
            var noteIndex = 0
            while (isPlaying) {
                val freq = notes[melody[noteIndex]] ?: 440.0
                playTone(freq, 60.0 / bpm)
                noteIndex = (noteIndex + 1) % melody.size
            }
        }
        thread?.start()
    }

    private fun playTone(freq: Double, durationSec: Double) {
        val count = (sampleRate * durationSec).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val angle = 2.0 * Math.PI * i.toDouble() / (sampleRate / freq)
            samples[i] = (sin(angle) * 6000).toInt().toShort()
        }
        audioTrack?.write(samples, 0, count)
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
        thread = null
    }
}