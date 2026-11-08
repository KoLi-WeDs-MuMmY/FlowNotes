package com.flownotes.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * 100% On-Device Voice Memo Manager.
 * Records lecture / study audio synced to notebook pages without any server or cloud API.
 */
class AudioMemoManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null

    var isRecording: Boolean = false
        private set

    var isPlaying: Boolean = false
        private set

    fun startRecording(pageId: String): File? {
        stopRecording()
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val file = File(audioDir, "memo_${pageId}_${System.currentTimeMillis()}.m4a")

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            currentRecordingFile = file
            isRecording = true
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            return null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return currentRecordingFile
    }

    fun playAudio(file: File, onComplete: () -> Unit) {
        stopPlayback()
        if (!file.exists()) return

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlaying = false
                    onComplete()
                }
                start()
            }
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            isPlaying = false
        }
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
