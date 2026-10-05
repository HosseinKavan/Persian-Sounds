package com.example.sedayevazheh

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

data class VoskChoice(
    val text: String,
    val confidence: Double,
)

data class VoskDecision(
    val choices: List<VoskChoice>,
) {
    val best: VoskChoice? get() = choices.maxByOrNull { it.confidence }
    val second: VoskChoice? get() = choices.sortedByDescending { it.confidence }.drop(1).firstOrNull()
}

class VoskPersianRecognizer(private val context: Context) : RecognitionListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    private var onReadyState: ((Boolean, String) -> Unit)? = null
    private var onListening: (() -> Unit)? = null
    private var onResultCallback: ((VoskDecision) -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null

    private var lastHypothesis: String = ""
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
        onListeningStarted: () -> Unit,
        onResult: (VoskDecision) -> Unit,
        onError: (String) -> Unit
    ) {
        val loadedModel = model ?: run {
            onError("مدل شنیدن فارسی هنوز آماده نشده")
            return
        }

        stop()
        finished = false
        lastHypothesis = ""
        onListening = onListeningStarted
        onResultCallback = onResult
        onFailure = onError

        try {
            val grammar = buildFullAlphabetGrammar()
            recognizer = Recognizer(loadedModel, 16000.0f, grammar).also {
                it.setMaxAlternatives(5)
                it.setWords(true)
            }
            speechService = SpeechService(recognizer, 16000.0f)
            onListening?.invoke()
            speechService?.startListening(this, 5500)
        } catch (e: IOException) {
            cleanupRecognition()
            onError("میکروفون آماده نشد: ${e.message ?: "خطا"}")
        } catch (e: Exception) {
            cleanupRecognition()
            onError("شنیدن شروع نشد: ${e.message ?: "خطا"}")
        }
    }

    private fun buildFullAlphabetGrammar(): String {
        val phrases = SoundMatcher.allRecognitionPhrases().toMutableList()
        phrases += "[unk]"
        return phrases.joinToString(prefix = "[", postfix = "]") { JSONObject.quote(it) }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (!hypothesis.isNullOrBlank()) lastHypothesis = hypothesis
    }

    override fun onResult(hypothesis: String?) {
        if (!hypothesis.isNullOrBlank()) lastHypothesis = hypothesis
    }

    override fun onFinalResult(hypothesis: String?) {
        val json = hypothesis?.takeIf { it.isNotBlank() } ?: lastHypothesis
        finishWith(json)
    }

    override fun onTimeout() {
        finishWith(lastHypothesis)
    }

    override fun onError(exception: Exception?) {
        if (finished) return
        finished = true
        cleanupRecognition()
        onFailure?.invoke("شنیدن صدا با خطا روبه‌رو شد؛ دوباره تلاش کن")
    }

    private fun finishWith(json: String) {
        if (finished) return
        finished = true

        val decision = parseDecision(json)
        cleanupRecognition()

        if (decision.choices.isEmpty()) {
            onFailure?.invoke("صدات رو خوب نشنیدم؛ یک بار دیگه، آروم و واضح بگو")
        } else {
            onResultCallback?.invoke(decision)
        }
    }

    private fun parseDecision(json: String): VoskDecision {
        if (json.isBlank()) return VoskDecision(emptyList())
        return try {
            val obj = JSONObject(json)
            val choices = mutableListOf<VoskChoice>()

            val alternatives = obj.optJSONArray("alternatives")
            if (alternatives != null) {
                for (i in 0 until alternatives.length()) {
                    val item = alternatives.optJSONObject(i) ?: continue
                    val text = item.optString("text", "").trim()
                    val confidence = item.optDouble("confidence", 0.0)
                    if (text.isNotBlank() && text != "[unk]") {
                        choices += VoskChoice(text, confidence)
                    }
                }
            } else {
                val text = obj.optString("text", "").trim()
                if (text.isNotBlank() && text != "[unk]") {
                    val words = obj.optJSONArray("result")
                    val confidence = if (words != null && words.length() > 0) {
                        var sum = 0.0
                        var count = 0
                        for (i in 0 until words.length()) {
                            val w = words.optJSONObject(i) ?: continue
                            sum += w.optDouble("conf", 0.0)
                            count++
                        }
                        if (count > 0) sum / count else 0.0
                    } else 0.0
                    choices += VoskChoice(text, confidence)
                }
            }

            VoskDecision(choices.distinctBy { SoundMatcher.normalize(it.text) })
        } catch (_: Exception) {
            VoskDecision(emptyList())
        }
    }

    fun stop() {
        try { speechService?.stop() } catch (_: Exception) {}
        cleanupRecognition()
    }

    private fun cleanupRecognition() {
        try { speechService?.shutdown() } catch (_: Exception) {}
        speechService = null
        try { recognizer?.close() } catch (_: Exception) {}
        recognizer = null
    }

    fun destroy() {
        stop()
        try { model?.close() } catch (_: Exception) {}
        model = null
    }
}
