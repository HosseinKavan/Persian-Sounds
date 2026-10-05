package com.example.sedayevazheh

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.sqrt

data class PhonemeDecision(
    val decoded: String,
    val tokens: List<String>,
    val confidence: Double,
)

class AvaSanjPhonemeRecognizer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var idToToken: Map<Int, String> = emptyMap()
    private var blankId: Int = 0

    @Volatile
    private var stopRequested = false

    val isReady: Boolean get() = session != null && idToToken.isNotEmpty()

    fun initialize(onState: (Boolean, String) -> Unit) {
        onState(false, "در حال آماده‌سازی شنیدن صدای فارسی…")
        Thread {
            try {
                val modelFile = File(context.filesDir, "avasanj_int8.onnx")
                if (!modelFile.exists() || modelFile.length() < 10_000_000L) {
                    context.assets.open("avasanj/avasanj_int8.onnx").use { input ->
                        modelFile.outputStream().buffered(1024 * 1024).use { output ->
                            input.copyTo(output, 1024 * 1024)
                        }
                    }
                }

                val vocabText = context.assets.open("avasanj/vocabulary.json")
                    .bufferedReader()
                    .use { it.readText() }
                parseVocabulary(vocabText)

                val env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val sess = env.createSession(modelFile.absolutePath, options)
                environment = env
                session = sess

                handler.post { onState(true, "شنیدن صدای فارسی آماده است ✓") }
            } catch (e: Exception) {
                handler.post {
                    onState(false, "مدل تشخیص صدا آماده نشد: ${e.message ?: "خطای نامشخص"}")
                }
            }
        }.start()
    }

    private fun parseVocabulary(text: String) {
        val root = JSONObject(text)
        val parsed = mutableMapOf<Int, String>()

        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = root.get(key)
            if (value is Number) {
                parsed[value.toInt()] = key
            } else {
                key.toIntOrNull()?.let { parsed[it] = value.toString() }
            }
        }

        if (parsed.isEmpty()) throw IllegalStateException("AvaSanj vocabulary is empty")
        idToToken = parsed

        blankId = parsed.entries.firstOrNull {
            it.value == "<pad>" || it.value == "[PAD]" || it.value == "<blank>"
        }?.key ?: 0
    }

    fun start(
        onListeningStarted: () -> Unit,
        onResult: (PhonemeDecision) -> Unit,
        onError: (String) -> Unit
    ) {
        val sess = session
        val env = environment
        if (sess == null || env == null) {
            onError("مدل تشخیص صدا هنوز آماده نشده")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("اجازهٔ میکروفون لازم است")
            return
        }

        stopRequested = false
        onListeningStarted()

        Thread {
            var recorder: AudioRecord? = null
            try {
                val sampleRate = 16000
                val minBuffer = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = max(minBuffer, sampleRate / 2)

                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("میکروفون آماده نشد")
                }

                val maxSamples = sampleRate * 3
                val samples = ShortArray(maxSamples)
                var offset = 0

                recorder.startRecording()
                while (offset < maxSamples && !stopRequested) {
                    val read = recorder.read(samples, offset, maxSamples - offset)
                    if (read > 0) offset += read
                    else if (read < 0) break
                }
                try { recorder.stop() } catch (_: Exception) {}

                if (offset < sampleRate / 5) {
                    throw IllegalStateException("صدای کافی دریافت نشد")
                }

                val trimmed = trimSilence(samples.copyOf(offset), sampleRate)
                if (trimmed.size < sampleRate / 8) {
                    throw IllegalStateException("صدای واضحی پیدا نشد")
                }

                val normalized = normalize(trimmed)
                val tensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(normalized),
                    longArrayOf(1, normalized.size.toLong())
                )

                val result = tensor.use {
                    sess.run(mapOf("input_values" to it))
                }

                result.use {
                    val raw = it[0].value
                    @Suppress("UNCHECKED_CAST")
                    val logits = raw as Array<Array<FloatArray>>
                    val decision = decode(logits[0])
                    handler.post {
                        if (decision.tokens.isEmpty()) {
                            onError("صدات رو خوب نشنیدم؛ دوباره امتحان کن")
                        } else {
                            onResult(decision)
                        }
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    onError(e.message ?: "تشخیص صدا انجام نشد")
                }
            } finally {
                try { recorder?.release() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun trimSilence(input: ShortArray, sampleRate: Int): ShortArray {
        if (input.isEmpty()) return input
        var peak = 0
        for (s in input) peak = max(peak, kotlin.math.abs(s.toInt()))
        if (peak < 250) return ShortArray(0)

        val threshold = max(300, (peak * 0.08).toInt())
        var first = -1
        var last = -1
        for (i in input.indices) {
            if (kotlin.math.abs(input[i].toInt()) >= threshold) {
                if (first < 0) first = i
                last = i
            }
        }
        if (first < 0 || last < first) return ShortArray(0)

        val pad = sampleRate / 8
        val start = max(0, first - pad)
        val end = minOf(input.size, last + pad + 1)
        return input.copyOfRange(start, end)
    }

    private fun normalize(input: ShortArray): FloatArray {
        val floats = FloatArray(input.size) { input[it] / 32768.0f }
        var mean = 0.0
        for (v in floats) mean += v
        mean /= floats.size

        var variance = 0.0
        for (v in floats) {
            val d = v - mean
            variance += d * d
        }
        variance /= floats.size
        val std = sqrt(variance + 1e-7).toFloat()

        for (i in floats.indices) {
            floats[i] = ((floats[i] - mean) / std).toFloat()
        }
        return floats
    }

    private fun decode(frames: Array<FloatArray>): PhonemeDecision {
        val tokens = mutableListOf<String>()
        val confidences = mutableListOf<Double>()
        var previousId = -1

        for (frame in frames) {
            if (frame.isEmpty()) continue
            var bestId = 0
            var bestLogit = frame[0]
            for (i in 1 until frame.size) {
                if (frame[i] > bestLogit) {
                    bestLogit = frame[i]
                    bestId = i
                }
            }

            if (bestId == previousId) continue
            previousId = bestId
            if (bestId == blankId) continue

            val token = idToToken[bestId] ?: continue
            if (token in setOf("<pad>", "<s>", "</s>", "<unk>", "[UNK]", "|")) continue

            var denom = 0.0
            var maxLogit = Double.NEGATIVE_INFINITY
            for (v in frame) if (v.toDouble() > maxLogit) maxLogit = v.toDouble()
            for (v in frame) denom += kotlin.math.exp(v.toDouble() - maxLogit)
            val prob = 1.0 / denom

            tokens += token
            confidences += prob
        }

        val decoded = tokens.joinToString("")
        val conf = if (confidences.isEmpty()) 0.0 else confidences.average()
        return PhonemeDecision(decoded, tokens, conf)
    }

    fun stop() {
        stopRequested = true
    }

    fun destroy() {
        stop()
        try { session?.close() } catch (_: Exception) {}
        session = null
        environment = null
    }
}
