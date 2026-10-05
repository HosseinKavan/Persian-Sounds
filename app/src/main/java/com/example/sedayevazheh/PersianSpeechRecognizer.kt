package com.example.sedayevazheh

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class PersianSpeechRecognizer(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    // Important: use the system/default recognizer. The previous version preferred
    // on-device recognition, which can exist on a phone even when the Persian
    // offline model is not installed and can therefore fail immediately.
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } else {
            null
        }

    private var onReady: (() -> Unit)? = null
    private var onResult: ((List<String>) -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null

    private var partialResults: List<String> = emptyList()
    private var currentLanguage = "fa-IR"
    private var languageFallbackTried = false
    private var quickRetryTried = false
    private var sessionStartedAt = 0L
    private var active = false

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                active = true
                onReady?.invoke()
            }

            override fun onBeginningOfSpeech() {
                active = true
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(results: Bundle?) {
                partialResults = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
            }

            override fun onResults(results: Bundle?) {
                active = false
                val alternatives = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                    .ifEmpty { partialResults }

                if (alternatives.isEmpty()) {
                    onFailure?.invoke("صدا را واضح تشخیص ندادم؛ دوباره بگو")
                } else {
                    onResult?.invoke(alternatives)
                }
            }

            override fun onError(error: Int) {
                active = false

                if (
                    (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) &&
                    !languageFallbackTried
                ) {
                    languageFallbackTried = true
                    currentLanguage = "fa"
                    mainHandler.postDelayed({ startInternal() }, 350L)
                    return
                }

                val elapsed = SystemClock.elapsedRealtime() - sessionStartedAt
                if (
                    (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                    elapsed < 1500L &&
                    !quickRetryTried
                ) {
                    quickRetryTried = true
                    mainHandler.postDelayed({ startInternal() }, 500L)
                    return
                }

                if (
                    (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                    partialResults.isNotEmpty()
                ) {
                    onResult?.invoke(partialResults)
                    return
                }

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "مشکل در دریافت صدا؛ دوباره تلاش کن"
                    SpeechRecognizer.ERROR_CLIENT -> "شنیدن متوقف شد؛ دوباره دکمهٔ بگو را بزن"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازهٔ میکروفون لازم است"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "برای تشخیص گفتار فارسی، اتصال اینترنت/سرویس گفتار را بررسی کن"
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "صدا را واضح تشخیص ندادم؛ صدا را کمی کشیده‌تر بگو"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "سرویس شنیدن مشغول است؛ یک لحظه بعد دوباره تلاش کن"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "صدایی نشنیدم؛ بعد از دیدن «گوش می‌دهم» صدا را بگو"
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                        "سرویس تشخیص گفتار پاسخ نداد؛ دوباره تلاش کن"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        "تشخیص گفتار فارسی روی این گوشی آماده نیست"
                    else -> "تشخیص صدا انجام نشد؛ دوباره تلاش کن"
                }
                onFailure?.invoke(message)
            }
        })
    }

    fun start(
        ready: () -> Unit,
        result: (List<String>) -> Unit,
        failure: (String) -> Unit
    ) {
        if (recognizer == null) {
            failure("روی این دستگاه سرویس تشخیص گفتار پیدا نشد")
            return
        }

        onReady = ready
        onResult = result
        onFailure = failure
        partialResults = emptyList()
        currentLanguage = "fa-IR"
        languageFallbackTried = false
        quickRetryTried = false
        sessionStartedAt = SystemClock.elapsedRealtime()

        if (active) {
            recognizer.cancel()
            active = false
            mainHandler.postDelayed({ startInternal() }, 250L)
        } else {
            startInternal()
        }
    }

    private fun startInternal() {
        val service = recognizer ?: return
        sessionStartedAt = SystemClock.elapsedRealtime()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 7)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)

            // Give a child time to start speaking. Some recognizers may ignore these
            // optional hints, but when honored they prevent very short sessions.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                900L
            )
        }

        active = true
        service.startListening(intent)
    }

    fun stop() {
        if (active) recognizer?.stopListening()
        active = false
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
    }
}
