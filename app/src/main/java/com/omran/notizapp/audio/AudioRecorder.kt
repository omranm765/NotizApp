package com.omran.notizapp.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(
    private val context: Context
) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    /**
     * Startet eine neue Aufnahme.
     *
     * RECORD_AUDIO muss vorher
     * vom Benutzer erlaubt worden sein.
     */
    fun start() {
        check(!isRecording) {
            "Es läuft bereits eine Aufnahme."
        }

        val directory = File(
            context.filesDir,
            "recordings"
        )

        check(directory.isDirectory || directory.mkdirs()) {
            "Audioordner konnte nicht erstellt werden."
        }

        val file = File.createTempFile(
            "recording_",
            ".m4a",
            directory
        )

        val newRecorder = try {
            createRecorder()
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }

        try {
            newRecorder.setAudioSource(
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )


            newRecorder.setOutputFormat(
                MediaRecorder.OutputFormat.MPEG_4
            )

            newRecorder.setAudioEncoder(
                MediaRecorder.AudioEncoder.AAC
            )

            newRecorder.setAudioChannels(1)
            newRecorder.setAudioSamplingRate(44100)
            newRecorder.setAudioEncodingBitRate(128000)

            newRecorder.setOutputFile(
                file.absolutePath
            )

            newRecorder.prepare()
            newRecorder.start()

            outputFile = file
            recorder = newRecorder

        } catch (exception: Exception) {
            newRecorder.release()
            file.delete()
            throw exception
        }
    }

    /**
     * Beendet die Aufnahme und gibt
     * die gespeicherte Audiodatei zurück.
     */
    fun stop(): File {
        val currentRecorder = recorder
            ?: error("Keine Aufnahme aktiv.")

        val file = outputFile
            ?: error("Keine Audiodatei vorhanden.")

        recorder = null
        outputFile = null

        try {
            currentRecorder.stop()

            check(file.length() > 0L) {
                "Die Aufnahme ist leer."
            }

            return file

        } catch (exception: Exception) {
            file.delete()
            throw exception

        } finally {
            currentRecorder.release()
        }
    }

    /**
     * Bricht die Aufnahme ab und
     * entfernt die temporäre Datei.
     */
    fun cancel() {
        val currentRecorder = recorder
        val file = outputFile

        recorder = null
        outputFile = null

        try {
            currentRecorder?.reset()
        } finally {
            currentRecorder?.release()
            file?.delete()
        }
    }
}
