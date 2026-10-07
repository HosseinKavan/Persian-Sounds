package com.example.sedayevazheh

import org.junit.Assert.assertEquals
import org.junit.Test

class ChildPhonemeScorerTest {
    @Test
    fun strongTargetIsCorrect() {
        val decision = PhonemeDecision(
            decoded = "ب",
            tokens = listOf("ب"),
            confidence = 0.70,
            tokenScores = mapOf("ب" to 0.72, "د" to 0.21)
        )
        val result = ChildPhonemeScorer.assess("ب", decision)
        assertEquals(RecognitionVerdict.CORRECT, result.verdict)
    }

    @Test
    fun closeBDConfusionIsUncertainNotWrong() {
        val decision = PhonemeDecision(
            decoded = "د",
            tokens = listOf("د"),
            confidence = 0.49,
            tokenScores = mapOf("ب" to 0.39, "د" to 0.45)
        )
        val result = ChildPhonemeScorer.assess("ب", decision)
        assertEquals(RecognitionVerdict.UNCERTAIN, result.verdict)
    }

    @Test
    fun strongDifferentSoundIsWrong() {
        val decision = PhonemeDecision(
            decoded = "م",
            tokens = listOf("م"),
            confidence = 0.82,
            tokenScores = mapOf("ب" to 0.05, "م" to 0.84)
        )
        val result = ChildPhonemeScorer.assess("ب", decision)
        assertEquals(RecognitionVerdict.WRONG, result.verdict)
    }

    @Test
    fun weakSignalIsUncertain() {
        val decision = PhonemeDecision(
            decoded = "",
            tokens = emptyList(),
            confidence = 0.0,
            tokenScores = emptyMap()
        )
        val result = ChildPhonemeScorer.assess("ب", decision)
        assertEquals(RecognitionVerdict.UNCERTAIN, result.verdict)
    }
}
