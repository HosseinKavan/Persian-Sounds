package com.example.sedayevazheh

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class PersianSpeechRecognizer(context: Context) {
    private val appContext = context.applicationContext
    val isOnDevice: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    private val recognizer: SpeechRecognizer? = when {
        isOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        SpeechRecognizer.isRecognitionAvailable(appContext) ->
            SpeechRecognizer.createSpeechRecognizer(appContext)
        else -> null
    }

    private var onReady: (() -> Unit)? = null
    private var onResult: ((List<String>) -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onReady?.invoke() }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "مشکل در دریافت صدا"
                    SpeechRecognizer.ERROR_CLIENT -> "شنیدن متوقف شد"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازهٔ میکروفون لازم است"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "مشکل ارتباط با سرویس تشخیص صدا"
                    SpeechRecognizer.ERROR_NO_MATCH -> "صدا را واضح تشخیص ندادم؛ دوباره بگو"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "کمی صبر کن و دوباره بگو"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی نشنیدم؛ دوباره تلاش کن"
                    SpeechRecognizer.ERROR_SERVER -> "سرویس تشخیص صدا پاسخ نداد"
                    else -> "تشخیص صدا انجام نشد؛ دوباره تلاش کن"
                }
                onFailure?.invoke(message)
            }

            override fun onResults(results: Bundle?) {
                val alternatives = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (alternatives.isEmpty()) onFailure?.invoke("صدا را واضح تشخیص ندادم؛ دوباره بگو")
                else onResult?.invoke(alternatives)
            }
        })
    }

    fun start(ready: () -> Unit, result: (List<String>) -> Unit, failure: (String) -> Unit) {
        val service = recognizer ?: run {
            failure("روی این دستگاه سرویس تشخیص صدا پیدا نشد")
            return
        }
        onReady = ready
        onResult = result
        onFailure = failure

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "صدای حرف را بگو")
        }
        service.startListening(intent)
    }

    fun stop() = recognizer?.stopListening()
    fun destroy() = recognizer?.destroy()
}
