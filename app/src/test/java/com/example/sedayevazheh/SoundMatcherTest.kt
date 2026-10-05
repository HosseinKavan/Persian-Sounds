package com.example.sedayevazheh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundMatcherTest {
    @Test fun recognizesLetterName() {
        assertTrue(SoundMatcher.isTopMatch("د", "دال"))
        assertTrue(SoundMatcher.isTopMatch("ر", "راء"))
    }

    @Test fun recognizesShortPersianForms() {
        assertTrue(SoundMatcher.isTopMatch("ب", "به"))
        assertTrue(SoundMatcher.isTopMatch("ک", "که"))
    }

    @Test fun acceptsOnlySingleUsefulAnswer() {
        assertTrue(SoundMatcher.isTopMatch("م", "صدای میم"))
        assertFalse(SoundMatcher.isTopMatch("م", "میم نون"))
    }

    @Test fun rejectsWrongSound() {
        assertFalse(SoundMatcher.isTopMatch("د", "میم"))
    }

    @Test fun fullGrammarContainsWrongAlternativesToo() {
        val grammar = SoundMatcher.allRecognitionPhrases()
        assertTrue(grammar.contains("دال"))
        assertTrue(grammar.contains("میم"))
        assertTrue(grammar.contains("نون"))
    }
}
