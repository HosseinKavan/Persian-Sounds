package com.example.sedayevazheh

enum class RecognitionVerdict {
    CORRECT,
    WRONG,
    UNCERTAIN
}

data class RecognitionAssessment(
    val verdict: RecognitionVerdict,
    val heardToken: String?,
    val targetScore: Double,
    val competitorScore: Double,
    val margin: Double,
)

object ChildPhonemeScorer {
    fun assess(expected: String?, decision: PhonemeDecision): RecognitionAssessment {
        if (expected.isNullOrBlank() || decision.tokenScores.isEmpty()) {
            return RecognitionAssessment(
                verdict = RecognitionVerdict.UNCERTAIN,
                heardToken = decision.tokens.firstOrNull(),
                targetScore = 0.0,
                competitorScore = 0.0,
                margin = 0.0,
            )
        }

        val targetScore = decision.tokenScores[expected] ?: 0.0
        val competitor = decision.tokenScores
            .filterKeys { it != expected }
            .maxByOrNull { it.value }

        val competitorScore = competitor?.value ?: 0.0
        val margin = targetScore - competitorScore
        val targetWasDecoded = decision.tokens.take(4).contains(expected)

        val verdict = when {
            // Strong target evidence, or target survives decoding with a small
            // positive/near-tie margin. This helps quiet children's stop sounds.
            targetScore >= 0.50 && margin >= -0.03 ->
                RecognitionVerdict.CORRECT

            targetScore >= 0.30 && targetWasDecoded && margin >= 0.02 ->
                RecognitionVerdict.CORRECT

            targetScore >= 0.24 && targetWasDecoded && margin >= -0.01 ->
                RecognitionVerdict.CORRECT

            // If the model is weak or two phonemes are acoustically close
            // (for example b/d), never force a wrong answer.
            maxOf(targetScore, competitorScore) < 0.38 ->
                RecognitionVerdict.UNCERTAIN

            kotlin.math.abs(margin) < 0.12 ->
                RecognitionVerdict.UNCERTAIN

            targetScore >= 0.13 ->
                RecognitionVerdict.UNCERTAIN

            else ->
                RecognitionVerdict.WRONG
        }

        return RecognitionAssessment(
            verdict = verdict,
            heardToken = competitor?.key ?: decision.tokens.firstOrNull(),
            targetScore = targetScore,
            competitorScore = competitorScore,
            margin = margin,
        )
    }
}
