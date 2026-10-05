package com.example.sedayevazheh

import android.content.Context
import android.media.MediaPlayer

class OfflineWordAudio(private val context: Context) {
    private var player: MediaPlayer? = null

    fun playWord(wordId: Int, onDone: () -> Unit = {}, onError: () -> Unit = {}) {
        playResource("word_" + wordId.toString().padStart(2, '0'), onDone, onError)
    }

    fun playFirstQuestion(wordId: Int, onDone: () -> Unit = {}, onError: () -> Unit = {}) {
        playResource("question_first_" + wordId.toString().padStart(2, '0'), onDone, onError)
    }

    fun playLastQuestion(wordId: Int, onDone: () -> Unit = {}, onError: () -> Unit = {}) {
        playResource("question_last_" + wordId.toString().padStart(2, '0'), onDone, onError)
    }

    fun playCorrect(onDone: () -> Unit = {}, onError: () -> Unit = {}) =
        playResource("feedback_correct", onDone, onError)

    fun playWrong(onDone: () -> Unit = {}, onError: () -> Unit = {}) =
        playResource("feedback_wrong", onDone, onError)

    fun playUnclear(onDone: () -> Unit = {}, onError: () -> Unit = {}) =
        playResource("feedback_unclear", onDone, onError)

    fun playComplete(onDone: () -> Unit = {}, onError: () -> Unit = {}) =
        playResource("feedback_complete", onDone, onError)

    fun playListening(onDone: () -> Unit = {}, onError: () -> Unit = {}) =
        playResource("prompt_listening", onDone, onError)

    private fun playResource(name: String, onDone: () -> Unit, onError: () -> Unit) {
        stop()
        val resourceId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resourceId == 0) {
            onError()
            return
        }

        val created = MediaPlayer.create(context, resourceId)
        if (created == null) {
            onError()
            return
        }

        player = created
        created.setOnCompletionListener { mediaPlayer ->
            mediaPlayer.release()
            if (player === mediaPlayer) player = null
            onDone()
        }
        created.setOnErrorListener { mediaPlayer, _, _ ->
            mediaPlayer.release()
            if (player === mediaPlayer) player = null
            onError()
            true
        }
        created.start()
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
