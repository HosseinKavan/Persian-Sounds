package com.example.sedayevazheh

import android.content.Context

class StatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("word_stats", Context.MODE_PRIVATE)

    fun record(wordId: Int, phase: SoundPhase, correct: Boolean) {
        if (phase == SoundPhase.COMPLETE) return
        val prefix = if (phase == SoundPhase.FIRST) "first" else "last"
        val attemptsKey = "$wordId.$prefix.attempts"
        val correctKey = "$wordId.$prefix.correct"
        val attempts = prefs.getInt(attemptsKey, 0) + 1
        val correctCount = prefs.getInt(correctKey, 0) + if (correct) 1 else 0
        prefs.edit()
            .putInt(attemptsKey, attempts)
            .putInt(correctKey, correctCount)
            .apply()
    }

    fun get(wordId: Int): WordStats = WordStats(
        firstAttempts = prefs.getInt("$wordId.first.attempts", 0),
        firstCorrect = prefs.getInt("$wordId.first.correct", 0),
        lastAttempts = prefs.getInt("$wordId.last.attempts", 0),
        lastCorrect = prefs.getInt("$wordId.last.correct", 0),
    )

    fun clear() = prefs.edit().clear().apply()
}
