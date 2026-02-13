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
        "C" to 261.63, "C#" to 277.18, "D" to 293.66, "Eb" to 311.13,
        "E" to 329.63, "F" to 349.23, "F#" to 369.99, "G" to 392.00,
        "G#" to 415.30, "A" to 440.00, "Bb" to 466.16, "B" to 493.88,
        "C2" to 523.25, "C#2" to 554.37, "D2" to 587.33
    )

    fun start(isBoss: Boolean) {
        stop()
        isPlaying = true
        // テンポを速めて緊迫感を出す
        bpm = if (isBoss) 180.0 else 140.0
        
        // 緊迫感のあるマイナー調・半音階メロディ
        val melody = if (isBoss) {
            listOf("A", "Bb", "A", "G#", "A", "C2", "Bb", "A", "F", "G", "Ab", "G", "F", "E", "D", "E")
        } else {
            listOf("D", "F", "G#", "G", "D", "F", "G#", "B")
        }

        try {
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
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            thread = Thread {
                try {
                    audioTrack?.play()
                    var noteIndex = 0
                    while (isPlaying) {
                        val freq = notes[melody[noteIndex]] ?: 440.0
                        // 1音を短くしてスピード感を出す
                        playTone(freq, 30.0 / bpm) 
                        noteIndex = (noteIndex + 1) % melody.size
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            thread?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playTone(freq: Double, durationSec: Double) {
        val count = (sampleRate * durationSec).toInt()
        val samples = ShortArray(count)
        val period = sampleRate / freq
        for (i in 0 until count) {
            // 矩形波（Square Wave）に変更して8bit風の尖った音にする
            val angle = i.toDouble() % period
            val value = if (angle < period / 2.0) 1.0 else -1.0
            
            // 音量エンベロープ（少し減衰させて歯切れを良くする）
            val envelope = (count - i).toDouble() / count
            samples[i] = (value * 7000 * envelope).toInt().toShort()
        }
        audioTrack?.write(samples, 0, count)
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
        thread = null
    }
}