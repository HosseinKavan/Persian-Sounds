package com.example.sedayevazheh

object SoundMatcher {
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670]")
    private val punctuation = Regex("[،؛؟!,.\\-_:؛\\[\\]{}()«»\\\"']")
    private val fillerWords = setOf("صدا", "صدای", "حرف", "اول", "آخر", "است", "هست", "میشه", "می‌شود")

    private val aliases = linkedMapOf(
        "آ" to setOf("آ", "ا", "الف"),
        "ا" to setOf("ا", "الف"),
        "ب" to setOf("ب", "به"),
        "پ" to setOf("پ", "په"),
        "ت" to setOf("ت", "ته"),
        "ث" to setOf("ث", "ثه"),
        "ج" to setOf("ج", "جیم"),
        "چ" to setOf("چ", "چه"),
        "ح" to setOf("ح", "حا"),
        "خ" to setOf("خ", "خا", "خه"),
        "د" to setOf("د", "دال", "ده"),
        "ذ" to setOf("ذ", "ذال"),
        "ر" to setOf("ر", "را", "راء"),
        "ز" to setOf("ز", "زا"),
        "ژ" to setOf("ژ", "ژه"),
        "س" to setOf("س", "سین", "سه"),
        "ش" to setOf("ش", "شین", "شه"),
        "ص" to setOf("ص", "صاد"),
        "ض" to setOf("ض", "ضاد"),
        "ط" to setOf("ط", "طا"),
        "ظ" to setOf("ظ", "ظا"),
        "ع" to setOf("ع", "عین"),
        "غ" to setOf("غ", "غین"),
        "ف" to setOf("ف", "فا", "فه"),
        "ق" to setOf("ق", "قاف"),
        "ک" to setOf("ک", "کاف", "که"),
        "گ" to setOf("گ", "گاف", "گه"),
        "ل" to setOf("ل", "لام", "له"),
        "م" to setOf("م", "میم", "مه"),
        "ن" to setOf("ن", "نون", "نه"),
        "و" to setOf("و", "واو"),
        "ه" to setOf("ه", "ها", "هاء", "هه"),
        "ی" to setOf("ی", "یا", "یاء"),
    )

    fun normalize(raw: String): String = raw
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace("ۀ", "ه")
        .replace("ة", "ه")
        .replace("‌", " ")
        .replace(diacritics, "")
        .replace(punctuation, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun acceptedPhrases(target: String): List<String> {
        val key = normalize(target)
        return (aliases[key].orEmpty() + key)
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun allRecognitionPhrases(): List<String> =
        aliases.values
            .flatten()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()

    fun isTopMatch(target: String, recognized: String): Boolean {
        val accepted = acceptedPhrases(target).toSet()
        val normalized = normalize(recognized)
        if (normalized in accepted) return true

        val usefulTokens = normalized.split(" ")
            .filter { it.isNotBlank() && it !in fillerWords }

        return usefulTokens.size == 1 && usefulTokens.first() in accepted
    }
}
