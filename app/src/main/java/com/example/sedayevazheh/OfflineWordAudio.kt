package com.example.sedayevazheh

import android.content.Context
import android.media.MediaPlayer

class OfflineWordAudio(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(wordId: Int, onError: () -> Unit = {}) {
        stop()
        val name = "word_" + wordId.toString().padStart(2, '0')
        val resourceId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resourceId == 0) {
            onError()
            return
        }

        player = MediaPlayer.create(context, resourceId)?.also { mp ->
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            mp.setOnErrorListener { mediaPlayer, _, _ ->
                mediaPlayer.release()
                if (player === mediaPlayer) player = null
                onError()
                true
            }
            mp.start()
        } ?: onError()
    }

    fun stop() {
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
    }

    fun release() = stop()
}
