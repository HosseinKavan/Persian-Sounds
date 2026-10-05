package com.example.sedayevazheh

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoskPersianRecognizer(private val context: Context) : RecognitionListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    private var onReadyState: ((Boolean, String) -> Unit)? = null
    private var onListening: (() -> Unit)? = null
    private var onResultCallback: ((List<String>) -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null

    private var partialText: String = ""
    private var finished = false

    val isReady: Boolean get() = model != null

    fun initialize(onState: (Boolean, String) -> Unit) {
        onReadyState = onState
        onState(false, "در حال آماده‌سازی شنیدن فارسی…")
        StorageService.unpack(
            context,
            "model-fa",
            "model-fa",
            { loaded ->
                model = loaded
                onReadyState?.invoke(true, "شنیدن فارسی آماده است ✓")
            },
            { error ->
                onReadyState?.invoke(false, "مدل فارسی آماده نشد: ${error.message ?: "خطای نامشخص"}")
            }
        )
    }

    fun start(
        target: String,
        onListeningStarted: () -> Unit,
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val loadedModel = model ?: run {
            onError("مدل شنیدن فارسی هنوز آماده نشده")
            return
        }

        stop()
        finished = false
        partialText = ""
        onListening = onListeningStarted
        onResultCallback = onResult
        onFailure = onError

        try {
            val grammar = buildGrammar(target)
            recognizer = Recognizer(loadedModel, 16000.0f, grammar)
            speechService = SpeechService(recognizer, 16000.0f)
            onListening?.invoke()
            speechService?.startListening(this, 5000)
        } catch (e: IOException) {
            cleanupRecognition()
            onError("میکروفون آماده نشد: ${e.message ?: "خطا"}")
        } catch (e: Exception) {
            cleanupRecognition()
            onError("شنیدن شروع نشد: ${e.message ?: "خطا"}")
        }
    }

    private fun buildGrammar(target: String): String {
        val words = SoundMatcher.acceptedPhrases(target)
            .flatMap { phrase ->
                listOf(phrase, "حرف $phrase", "صدای $phrase")
            }
            .distinct()
            .toMutableList()
        words += "[unk]"
        return words.joinToString(prefix = "[", postfix = "]") { phrase ->
            JSONObject.quote(phrase)
        }
    }

    private fun extractText(json: String, key: String): String = try {
        JSONObject(json).optString(key, "").trim()
    } catch (_: Exception) {
        ""
    }

    override fun onPartialResult(hypothesis: String?) {
        val text = hypothesis?.let { extractText(it, "partial") }.orEmpty()
        if (text.isNotBlank()) partialText = text
    }

    override fun onResult(hypothesis: String?) {
        val text = hypothesis?.let { extractText(it, "text") }.orEmpty()
        if (text.isNotBlank()) partialText = text
    }

    override fun onFinalResult(hypothesis: String?) {
        val text = hypothesis?.let { extractText(it, "text") }.orEmpty()
            .ifBlank { partialText }
        finishWith(text)
    }

    override fun onTimeout() {
        finishWith(partialText)
    }

    override fun onError(exception: Exception?) {
        if (finished) return
        finished = true
        cleanupRecognition()
        onFailure?.invoke("شنیدن صدا با خطا روبه‌رو شد؛ دوباره تلاش کن")
    }

    private fun finishWith(text: String) {
        if (finished) return
        finished = true
        cleanupRecognition()

        if (text.isBlank()) {
            onFailure?.invoke("صدایی تشخیص ندادم؛ بعد از «گوش می‌دهم» کمی واضح‌تر بگو")
        } else {
            onResultCallback?.invoke(listOf(text))
        }
    }

    fun stop() {
        try {
            speechService?.stop()
        } catch (_: Exception) {
        }
        cleanupRecognition()
    }

    private fun cleanupRecognition() {
        try {
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null

        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    fun destroy() {
        stop()
        try {
            model?.close()
        } catch (_: Exception) {
        }
        model = null
    }
}
