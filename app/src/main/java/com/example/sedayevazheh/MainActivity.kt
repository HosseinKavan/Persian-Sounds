package com.example.sedayevazheh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PersianSoundsApp()
                }
            }
        }
    }
}

@Composable
private fun PersianSoundsApp() {
    val context = LocalContext.current
    val words = remember { WordRepository.load(context) }
    val statsStore = remember { StatsStore(context) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(SoundPhase.FIRST) }
    var message by remember { mutableStateOf("به تصویر نگاه کن و صدای اول را بگو") }
    var listening by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var statsVersion by remember { mutableIntStateOf(0) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsStatus by remember { mutableStateOf("در حال آماده‌سازی صدای فارسی…") }

    val tts = remember {
        PersianTts(context) { ready, status ->
            ttsReady = ready
            ttsStatus = status
        }
    }
    val speech = remember { PersianSpeechRecognizer(context) }

    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
            speech.destroy()
        }
    }

    val word = words[currentIndex]

    LaunchedEffect(currentIndex, ttsReady) {
        if (ttsReady) {
            tts.speak(word.word)
        }
    }

    fun resetExercise(newMessage: String = "صدای اول واژه را بگو") {
        phase = SoundPhase.FIRST
        listening = false
        message = newMessage
    }

    fun nextWord() {
        currentIndex = (currentIndex + 1) % words.size
        resetExercise()
    }

    fun previousWord() {
        currentIndex = if (currentIndex == 0) words.lastIndex else currentIndex - 1
        resetExercise()
    }

    lateinit var startRecognition: () -> Unit

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecognition()
        } else {
            message = "برای تمرین گفتاری، اجازهٔ میکروفون لازم است"
        }
    }

    startRecognition = {
        listening = true
        message = "گوش می‌دهم… صدای حرف را بگو"
        speech.start(
            ready = {
                listening = true
                message = "گوش می‌دهم…"
            },
            result = { alternatives ->
                listening = false
                val target = if (phase == SoundPhase.FIRST) word.firstSound else word.lastSound
                val correct = SoundMatcher.isMatch(target, alternatives)
                statsStore.record(word.id, phase, correct)
                statsVersion++

                if (correct) {
                    if (phase == SoundPhase.FIRST) {
                        phase = SoundPhase.LAST
                        message = "آفرین! حالا صدای آخر واژه را بگو"
                    } else {
                        phase = SoundPhase.COMPLETE
                        message = "عالی بود! هر دو صدا درست بودند 🎉"
                    }
                } else {
                    message = "این صدا درست نبود. دوباره تلاش کن 🙂"
                }
            },
            failure = { error ->
                listening = false
                message = error
            }
        )
    }

    fun requestListening() {
        if (phase == SoundPhase.COMPLETE) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (showStats) {
        StatsScreen(
            words = words,
            statsStore = statsStore,
            statsVersion = statsVersion,
            onBack = { showStats = false },
            onClear = {
                statsStore.clear()
                statsVersion++
            }
        )
    } else {
        FlashCardScreen(
            word = word,
            index = currentIndex,
            total = words.size,
            phase = phase,
            message = message,
            listening = listening,
            ttsReady = ttsReady,
            ttsStatus = ttsStatus,
            onRepeat = {
                if (ttsReady) {
                    val started = tts.speak(word.word)
                    if (!started) {
                        message = "خواندن واژه شروع نشد؛ دوباره امتحان کن"
                    }
                } else {
                    tts.refresh()
                    message = "$ttsStatus — برای فعال‌کردن صدا، تنظیمات تبدیل متن به گفتار گوشی را بررسی کن"
                    try {
                        context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                    } catch (_: Exception) {
                        // Some manufacturers do not expose the standard TTS settings activity.
                    }
                }
            },
            onMic = { requestListening() },
            onRetry = {
                phase = SoundPhase.FIRST
                message = "صدای اول واژه را بگو"
            },
            onPrevious = { previousWord() },
            onNext = { nextWord() },
            onStats = { showStats = true },
            stats = statsStore.get(word.id)
        )
    }
}

@Composable
private fun FlashCardScreen(
    word: WordCard,
    index: Int,
    total: Int,
    phase: SoundPhase,
    message: String,
    listening: Boolean,
    ttsReady: Boolean,
    ttsStatus: String,
    onRepeat: () -> Unit,
    onMic: () -> Unit,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStats: () -> Unit,
    stats: WordStats,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onStats) { Text("📊 پیشرفت") }
                Text("${index + 1} / $total", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(word.imageEmoji, fontSize = 88.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = word.word,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = if (phase == SoundPhase.FIRST) "① صدای اول ← اکنون" else "① صدای اول ✓",
                            fontSize = 18.sp,
                            fontWeight = if (phase == SoundPhase.FIRST) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = when (phase) {
                                SoundPhase.FIRST -> "② صدای آخر"
                                SoundPhase.LAST -> "② صدای آخر ← اکنون"
                                SoundPhase.COMPLETE -> "② صدای آخر ✓"
                            },
                            fontSize = 18.sp,
                            fontWeight = if (phase == SoundPhase.LAST) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = message,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            if (!ttsReady) {
                Text(
                    text = "🔈 $ttsStatus",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRepeat) {
                    Text(if (ttsReady) "🔊 دوباره بخوان" else "🔊 فعال‌کردن صدا")
                }
                Button(
                    onClick = onMic,
                    enabled = !listening && phase != SoundPhase.COMPLETE
                ) {
                    Text(if (listening) "🎤 گوش می‌دهم" else "🎤 بگو")
                }
            }

            if (phase == SoundPhase.COMPLETE) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("↻ دوباره تمرین کن")
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "تلاش‌ها: ${stats.attempts}   درست: ${stats.correct}   نادرست: ${stats.wrong}",
                fontSize = 14.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onPrevious) { Text("قبلی") }
            Button(onClick = onNext) { Text("واژه بعدی") }
        }
    }
}

@Composable
private fun StatsScreen(
    words: List<WordCard>,
    statsStore: StatsStore,
    statsVersion: Int,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val refresh = statsVersion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("بازگشت") }
            Text("گزارش تمرین", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClear) { Text("پاک کردن") }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(words, key = { it.id }) { item ->
                val s = statsStore.get(item.id)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(item.word, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("صدای اول: ${s.firstCorrect}/${s.firstAttempts}   صدای آخر: ${s.lastCorrect}/${s.lastAttempts}")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${s.accuracy}٪", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${s.attempts} تلاش")
                        }
                    }
                }
            }
        }
    }
}
