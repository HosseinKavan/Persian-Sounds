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
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class PhonemeDecision(
    val decoded: String,
    val tokens: List<String>,
    val confidence: Double,
    val tokenScores: Map<String, Double> = emptyMap(),
    val rms: Double = 0.0,
    val speechDurationMs: Int = 0,
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

        Thread {
            var recorder: AudioRecord? = null
            var agc: AutomaticGainControl? = null
            var ns: NoiseSuppressor? = null

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

                // Quiet children's speech benefits from AGC. Noise suppression is
                // enabled only when the device exposes it; unsupported devices simply skip it.
                if (AutomaticGainControl.isAvailable()) {
                    try {
                        agc = AutomaticGainControl.create(recorder.audioSessionId)
                        agc?.enabled = true
                    } catch (_: Exception) {}
                }
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        ns = NoiseSuppressor.create(recorder.audioSessionId)
                        ns?.enabled = true
                    } catch (_: Exception) {}
                }

                val maxSamples = sampleRate * 5
                val samples = ShortArray(maxSamples)
                var offset = 0

                recorder.startRecording()
                handler.post { onListeningStarted() }

                // First 300 ms acts as a room-noise calibration window.
                // We intentionally keep it in the buffer so an eager child is not lost.
                val calibrationSamples = (sampleRate * 0.30).toInt()

                while (offset < maxSamples && !stopRequested) {
                    val read = recorder.read(samples, offset, minOf(bufferSize, maxSamples - offset))
                    if (read > 0) {
                        offset += read

                        // Once we have enough audio, stop early after speech has ended.
                        if (offset > calibrationSamples + sampleRate) {
                            val captured = samples.copyOf(offset)
                            val region = detectSpeechRegion(captured, sampleRate)
                            if (region != null) {
                                val (_, end) = region
                                val trailing = offset - end
                                if (trailing >= (sampleRate * 0.85).toInt()) {
                                    break
                                }
                            }
                        }
                    } else if (read < 0) {
                        break
                    }
                }

                try { recorder.stop() } catch (_: Exception) {}

                if (offset < sampleRate / 3) {
                    throw IllegalStateException("صدای کافی دریافت نشد")
                }

                val captured = samples.copyOf(offset)
                val region = detectSpeechRegion(captured, sampleRate)
                    ?: throw IllegalStateException("صدای واضحی پیدا نشد")

                val (start, end) = region

                // 300 ms pre-roll protects short plosives such as ب / د / پ / ت.
                val preRoll = (sampleRate * 0.30).toInt()
                val postRoll = (sampleRate * 0.22).toInt()
                val paddedStart = max(0, start - preRoll)
                val paddedEnd = minOf(captured.size, end + postRoll)
                val trimmed = captured.copyOfRange(paddedStart, paddedEnd)

                if (trimmed.size < (sampleRate * 0.16).toInt()) {
                    throw IllegalStateException("صدا خیلی کوتاه بود؛ یک بار دیگه بگو")
                }

                val rms = calculateRms(trimmed)
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
                    val decision = decode(
                        frames = logits[0],
                        rms = rms,
                        speechDurationMs = ((end - start) * 1000 / sampleRate)
                    )
                    handler.post {
                        if (decision.tokens.isEmpty() && decision.tokenScores.isEmpty()) {
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
                try { agc?.release() } catch (_: Exception) {}
                try { ns?.release() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun detectSpeechRegion(input: ShortArray, sampleRate: Int): Pair<Int, Int>? {
        if (input.isEmpty()) return null

        val frameSize = max(1, sampleRate / 50) // 20 ms
        val calibrationFrames = max(3, (0.30 * sampleRate / frameSize).toInt())
        val rmsFrames = mutableListOf<Double>()

        var pos = 0
        while (pos < input.size) {
            val end = minOf(input.size, pos + frameSize)
            var energy = 0.0
            var count = 0
            for (i in pos until end) {
                val v = input[i].toDouble()
                energy += v * v
                count++
            }
            rmsFrames += if (count == 0) 0.0 else sqrt(energy / count)
            pos = end
        }

        if (rmsFrames.isEmpty()) return null

        val calibration = rmsFrames
            .take(minOf(calibrationFrames, rmsFrames.size))
            .sorted()

        val noiseFloor = if (calibration.isEmpty()) 20.0 else {
            calibration[(calibration.size * 0.65).toInt().coerceAtMost(calibration.lastIndex)]
        }

        // Adaptive threshold: quiet rooms and quiet children both get a low threshold.
        // The floor is intentionally far below the old fixed value of 300.
        val threshold = max(45.0, noiseFloor * 2.15 + 18.0)
        val softThreshold = max(32.0, noiseFloor * 1.55 + 12.0)

        var firstFrame = -1
        var lastFrame = -1
        var consecutive = 0

        for (i in rmsFrames.indices) {
            val active = rmsFrames[i] >= threshold ||
                (i > 0 && rmsFrames[i] >= softThreshold && rmsFrames[i - 1] >= softThreshold)

            if (active) {
                consecutive++
                if (firstFrame < 0 && consecutive >= 1) {
                    firstFrame = i
                }
                lastFrame = i
            } else if (firstFrame < 0) {
                consecutive = 0
            }
        }

        if (firstFrame < 0 || lastFrame < firstFrame) return null

        val start = (firstFrame * frameSize).coerceAtLeast(0)
        val end = minOf(input.size, (lastFrame + 1) * frameSize)
        return start to end
    }

    private fun calculateRms(input: ShortArray): Double {
        if (input.isEmpty()) return 0.0
        var energy = 0.0
        for (s in input) {
            val v = s.toDouble() / 32768.0
            energy += v * v
        }
        return sqrt(energy / input.size)
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
            floats[i] = ((floats[i] - mean) / std).coerceIn(-6f, 6f)
        }
        return floats
    }

    private fun decode(
        frames: Array<FloatArray>,
        rms: Double,
        speechDurationMs: Int,
    ): PhonemeDecision {
        val tokens = mutableListOf<String>()
        val selectedConfidences = mutableListOf<Double>()
        val tokenScores = mutableMapOf<String, Double>()
        var previousId = -1

        for (frame in frames) {
            if (frame.isEmpty()) continue

            var maxLogit = Double.NEGATIVE_INFINITY
            for (v in frame) {
                if (v.toDouble() > maxLogit) maxLogit = v.toDouble()
            }

            var denom = 0.0
            val exponentials = DoubleArray(frame.size)
            for (i in frame.indices) {
                val e = exp(frame[i].toDouble() - maxLogit)
                exponentials[i] = e
                denom += e
            }
            if (denom <= 0.0) continue

            var bestId = 0
            var bestProb = -1.0
            for (i in frame.indices) {
                val probability = exponentials[i] / denom
                if (probability > bestProb) {
                    bestProb = probability
                    bestId = i
                }

                if (i != blankId) {
                    val token = idToToken[i]
                    if (!token.isNullOrBlank() &&
                        token !in setOf("<pad>", "<s>", "</s>", "<unk>", "[UNK]", "|")) {
                        val old = tokenScores[token] ?: 0.0
                        if (probability > old) tokenScores[token] = probability
                    }
                }
            }

            if (bestId == previousId) continue
            previousId = bestId
            if (bestId == blankId) continue

            val token = idToToken[bestId] ?: continue
            if (token in setOf("<pad>", "<s>", "</s>", "<unk>", "[UNK]", "|")) continue

            tokens += token
            selectedConfidences += bestProb
        }

        val decoded = tokens.joinToString("")
        val confidence = if (selectedConfidences.isEmpty()) {
            tokenScores.values.maxOrNull() ?: 0.0
        } else {
            selectedConfidences.average()
        }

        return PhonemeDecision(
            decoded = decoded,
            tokens = tokens,
            confidence = confidence,
            tokenScores = tokenScores.toList()
                .sortedByDescending { it.second }
                .take(8)
                .toMap(),
            rms = rms,
            speechDurationMs = speechDurationMs,
        )
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
