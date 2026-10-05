package com.example.sedayevazheh

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class PersianTts(
    context: Context,
    private val stateChanged: (ready: Boolean, message: String) -> Unit
) : TextToSpeech.OnInitListener {

    private val engine = TextToSpeech(context.applicationContext, this)

    var ready: Boolean = false
        private set

    private var initialized = false

    override fun onInit(status: Int) {
        initialized = status == TextToSpeech.SUCCESS
        if (!initialized) {
            ready = false
            stateChanged(false, "موتور خواندن متن روی این گوشی آماده نیست")
            return
        }

        engine.setSpeechRate(0.80f)
        engine.setPitch(1.02f)
        configurePersianVoice()
    }

    fun refresh() {
        if (initialized) configurePersianVoice()
    }

    private fun configurePersianVoice() {
        val exact = Locale.forLanguageTag("fa-IR")
        val base = Locale("fa")

        val installedPersianVoices = engine.voices.orEmpty()
            .filter { voice ->
                voice.locale.language.equals("fa", ignoreCase = true) &&
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }

        val preferredVoice: Voice? =
            installedPersianVoices.firstOrNull { it.locale.country.equals("IR", ignoreCase = true) }
                ?: installedPersianVoices.firstOrNull()

        if (preferredVoice != null && engine.setVoice(preferredVoice) == TextToSpeech.SUCCESS) {
            ready = true
            stateChanged(true, "صدای فارسی آماده است")
            return
        }

        val exactResult = engine.setLanguage(exact)
        if (isSupported(exactResult)) {
            ready = true
            stateChanged(true, "صدای فارسی آماده است")
            return
        }

        val baseResult = engine.setLanguage(base)
        if (isSupported(baseResult)) {
            ready = true
            stateChanged(true, "صدای فارسی آماده است")
            return
        }

        ready = false
        val message = if (
            exactResult == TextToSpeech.LANG_MISSING_DATA ||
            baseResult == TextToSpeech.LANG_MISSING_DATA
        ) {
            "دادهٔ صدای فارسی روی گوشی نصب نیست"
        } else {
            "موتور فعلی گوشی صدای فارسی ندارد"
        }
        stateChanged(false, message)
    }

    private fun isSupported(result: Int): Boolean =
        result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED &&
            result != TextToSpeech.ERROR

    fun speak(text: String): Boolean {
        if (!ready) return false
        return engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "fa_${System.nanoTime()}"
        ) == TextToSpeech.SUCCESS
    }

    fun shutdown() = engine.shutdown()
}
