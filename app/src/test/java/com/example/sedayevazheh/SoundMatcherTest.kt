package com.example.sedayevazheh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundMatcherTest {
    @Test fun recognizesLetterName() {
        assertTrue(SoundMatcher.isMatch("د", listOf("دال")))
        assertTrue(SoundMatcher.isMatch("ر", listOf("راء")))
    }

    @Test fun recognizesShortPersianForms() {
        assertTrue(SoundMatcher.isMatch("ب", listOf("به")))
        assertTrue(SoundMatcher.isMatch("ک", listOf("که")))
    }

    @Test fun ignoresPromptWords() {
        assertTrue(SoundMatcher.isMatch("م", listOf("صدای اول میم")))
    }

    @Test fun rejectsWrongSound() {
        assertFalse(SoundMatcher.isMatch("د", listOf("میم")))
    }
}
