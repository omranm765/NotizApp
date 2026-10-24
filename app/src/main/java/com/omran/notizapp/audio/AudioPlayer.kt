package com.omran.notizapp.audio

import android.media.MediaPlayer
import java.io.File

class AudioPlayer {

    private var player: MediaPlayer? = null

    fun play(
        file: File,
        onFinished: () -> Unit
    ) {
        stop()

        val newPlayer = MediaPlayer()

        try {
            newPlayer.setDataSource(file.absolutePath)

            newPlayer.setOnCompletionListener { completed ->
                if (player === completed) {
                    stop()
                    onFinished()
                }
            }

            newPlayer.prepare()
            player = newPlayer
            newPlayer.start()

        } catch (exception: Exception) {
            if (player === newPlayer) {
                player = null
            }

            newPlayer.release()
            throw exception
        }
    }

    fun stop() {
        val current = player
        player = null
        current?.release()
    }
}
