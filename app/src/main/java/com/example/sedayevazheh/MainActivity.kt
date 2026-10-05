package com.example.sedayevazheh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Cream = Color(0xFFFFFAF2)
private val Purple = Color(0xFF6C4AB6)
private val PurpleSoft = Color(0xFFEDE4FF)
private val Sky = Color(0xFF5EC8F2)
private val Yellow = Color(0xFFFFD166)
private val Coral = Color(0xFFFF8A75)
private val Mint = Color(0xFF7ED6A7)
private val Ink = Color(0xFF2B2440)
private val SoftInk = Color(0xFF6B647A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PersianSoundsApp()
            }
        }
    }
}

@Composable
private fun PersianSoundsApp() {
    val context = LocalContext.current
    val words = remember { WordRepository.load(context) }
    val statsStore = remember { StatsStore(context) }
    val audio = remember { OfflineWordAudio(context) }
    val recognizer = remember { VoskPersianRecognizer(context) }

    var currentIndex by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(SoundPhase.FIRST) }
    var message by remember { mutableStateOf("اول صدای واژه را خوب گوش کن 🌟") }
    var listening by remember { mutableStateOf(false) }
    var recognizerReady by remember { mutableStateOf(false) }
    var recognizerStatus by remember { mutableStateOf("در حال آماده‌سازی شنیدن فارسی…") }
    var showStats by remember { mutableStateOf(false) }
    var statsVersion by remember { mutableIntStateOf(0) }
    var celebration by remember { mutableStateOf(false) }

    val word = words[currentIndex]

    DisposableEffect(Unit) {
        recognizer.initialize { ready, status ->
            recognizerReady = ready
            recognizerStatus = status
        }
        onDispose {
            audio.release()
            recognizer.destroy()
        }
    }

    LaunchedEffect(currentIndex) {
        delay(350)
        audio.play(word.id) {
            message = "فایل صدای این واژه پیدا نشد"
        }
    }

    fun resetExercise() {
        recognizer.stop()
        listening = false
        phase = SoundPhase.FIRST
        celebration = false
        message = "صدای اول واژه را بگو"
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
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecognition()
        else message = "برای تمرین گفتاری، اجازهٔ میکروفون لازم است"
    }

    startRecognition = {
        if (!recognizerReady) {
            message = recognizerStatus
        } else if (phase != SoundPhase.COMPLETE) {
            val target = if (phase == SoundPhase.FIRST) word.firstSound else word.lastSound
            listening = true
            message = "آماده شو…"
            recognizer.start(
                target = target,
                onListeningStarted = {
                    listening = true
                    message = "🎤 گوش می‌دهم… حالا بگو"
                },
                onResult = { alternatives ->
                    listening = false
                    val correct = SoundMatcher.isMatch(target, alternatives)
                    statsStore.record(word.id, phase, correct)
                    statsVersion++

                    if (correct && phase == SoundPhase.FIRST) {
                        phase = SoundPhase.LAST
                        celebration = true
                        message = "آفرین! ⭐ حالا صدای آخر را بگو"
                    } else if (correct && phase == SoundPhase.LAST) {
                        phase = SoundPhase.COMPLETE
                        celebration = true
                        message = "عالی بود! هر دو صدا درست بود 🎉"
                    } else {
                        celebration = false
                        message = "نزدیک بود! یک بار دیگر امتحان کن 🌈"
                    }
                },
                onError = { error ->
                    listening = false
                    celebration = false
                    message = error
                }
            )
        }
    }

    fun requestListening() {
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
        KidFlashCardScreen(
            word = word,
            index = currentIndex,
            total = words.size,
            phase = phase,
            message = message,
            listening = listening,
            recognizerReady = recognizerReady,
            recognizerStatus = recognizerStatus,
            celebration = celebration,
            stats = statsStore.get(word.id),
            onRepeat = {
                audio.play(word.id) { message = "فایل صدای این واژه پیدا نشد" }
            },
            onMic = { requestListening() },
            onRetry = { resetExercise() },
            onPrevious = { previousWord() },
            onNext = { nextWord() },
            onStats = { showStats = true }
        )
    }
}

@Composable
private fun KidFlashCardScreen(
    word: WordCard,
    index: Int,
    total: Int,
    phase: SoundPhase,
    message: String,
    listening: Boolean,
    recognizerReady: Boolean,
    recognizerStatus: String,
    celebration: Boolean,
    stats: WordStats,
    onRepeat: () -> Unit,
    onMic: () -> Unit,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStats: () -> Unit,
) {
    val background = Brush.verticalGradient(
        listOf(Color(0xFFFFF6E8), Color(0xFFF8F0FF), Color(0xFFEAF8FF))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onStats,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = PurpleSoft)
            ) {
                Text("📊 پیشرفت", color = Purple, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = "${index + 1} / $total",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = Purple,
            trackColor = PurpleSoft
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    illustrationColor(word.id).copy(alpha = 0.28f),
                                    illustrationColor(word.id).copy(alpha = 0.08f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(word.imageEmoji, fontSize = 88.sp)
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = word.word,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SoundStepChip(
                        title = "صدای اول",
                        value = word.firstSound,
                        active = phase == SoundPhase.FIRST,
                        done = phase != SoundPhase.FIRST,
                        modifier = Modifier.weight(1f),
                        activeColor = Sky
                    )
                    SoundStepChip(
                        title = "صدای آخر",
                        value = word.lastSound,
                        active = phase == SoundPhase.LAST,
                        done = phase == SoundPhase.COMPLETE,
                        modifier = Modifier.weight(1f),
                        activeColor = Coral
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (celebration) Color(0xFFE9FFF1) else Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                textAlign = TextAlign.Center,
                color = if (celebration) Color(0xFF1B7F4B) else Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 28.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        if (!recognizerReady) {
            Surface(
                color = Yellow.copy(alpha = 0.30f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "🧠 $recognizerStatus",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = SoftInk,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRepeat,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("🔊 گوش کن", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onMic,
                enabled = !listening && phase != SoundPhase.COMPLETE && recognizerReady,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text(
                    if (listening) "🎤 گوش می‌دهم" else "🎤 بگو",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (phase == SoundPhase.COMPLETE) {
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onRetry,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE9FFF1))
            ) {
                Text("↻ دوباره تمرین کن", color = Color(0xFF1B7F4B), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBadge("⭐", stats.correct.toString(), "درست")
            StatBadge("🌱", stats.wrong.toString(), "تمرین بیشتر")
            StatBadge("🎯", "${stats.accuracy}٪", "دقت")
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onPrevious,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("قبلی")
            }
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text("واژه بعدی  ←", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SoundStepChip(
    title: String,
    value: String,
    active: Boolean,
    done: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color
) {
    val background = when {
        done -> Mint.copy(alpha = 0.25f)
        active -> activeColor.copy(alpha = 0.22f)
        else -> Color(0xFFF3F1F5)
    }
    val border = when {
        done -> Mint
        active -> activeColor
        else -> Color(0xFFD8D2DE)
    }

    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    done -> "✓ $title"
                    active -> "● $title"
                    else -> title
                },
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = value,
                color = Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
        }
    }
}

@Composable
private fun StatBadge(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 21.sp)
        Text(value, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = SoftInk)
    }
}

private fun illustrationColor(id: Int): Color = when (id % 5) {
    0 -> Purple
    1 -> Sky
    2 -> Yellow
    3 -> Coral
    else -> Mint
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
            .background(Brush.verticalGradient(listOf(Cream, Color(0xFFF4EEFF))))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("بازگشت") }
            Text("گزارش پیشرفت 🌟", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            TextButton(onClick = onClear) { Text("پاک کردن") }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(words, key = { it.id }) { item ->
                val s = statsStore.get(item.id)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(illustrationColor(item.id).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.imageEmoji, fontSize = 29.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.word, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                                Text(
                                    "اول: ${s.firstCorrect}/${s.firstAttempts}   آخر: ${s.lastCorrect}/${s.lastAttempts}",
                                    color = SoftInk,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Surface(
                            color = if (s.accuracy >= 80) Mint.copy(alpha = 0.24f) else PurpleSoft,
                            shape = CircleShape
                        ) {
                            Text(
                                "${s.accuracy}٪",
                                modifier = Modifier.padding(11.dp),
                                color = Ink,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
