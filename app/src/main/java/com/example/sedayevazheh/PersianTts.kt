package com.example.sedayevazheh

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class PersianTts(context: Context, private val stateChanged: (Boolean) -> Unit) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    var ready: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.forLanguageTag("fa-IR")
            val support = engine.isLanguageAvailable(locale)
            ready = support != TextToSpeech.LANG_MISSING_DATA && support != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) {
                engine.language = locale
                engine.setSpeechRate(0.78f)
                engine.setPitch(1.03f)
            }
        }
        stateChanged(ready)
    }

    fun speak(text: String) {
        if (!ready) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fa_${System.nanoTime()}")
    }

    fun shutdown() = engine.shutdown()
}
