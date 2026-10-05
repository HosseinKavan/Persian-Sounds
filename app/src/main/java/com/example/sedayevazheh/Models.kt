package com.example.sedayevazheh

data class WordCard(
    val id: Int,
    val word: String,
    val pronunciation: String,
    val english: String,
    val category: String,
    val imageEmoji: String,
    val firstSound: String,
    val lastSound: String,
)

enum class SoundPhase { FIRST, LAST, COMPLETE }

data class WordStats(
    val firstAttempts: Int = 0,
    val firstCorrect: Int = 0,
    val lastAttempts: Int = 0,
    val lastCorrect: Int = 0,
) {
    val attempts: Int get() = firstAttempts + lastAttempts
    val correct: Int get() = firstCorrect + lastCorrect
    val wrong: Int get() = attempts - correct
    val accuracy: Int get() = if (attempts == 0) 0 else ((correct * 100f) / attempts).toInt()
}
