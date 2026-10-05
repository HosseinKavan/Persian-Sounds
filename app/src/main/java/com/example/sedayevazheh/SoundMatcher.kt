package com.example.sedayevazheh

object SoundMatcher {
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670]")
    private val punctuation = Regex("[،؛؟!,.\\-_:؛\\[\\]{}()«»\\\"']")
    private val fillerWords = setOf("صدا", "صدای", "حرف", "اول", "آخر", "است", "هست", "میشه", "می‌شود")

    private val aliases = mapOf(
        "آ" to setOf("آ", "ا", "الف", "آلف"),
        "ا" to setOf("ا", "الف"),
        "ب" to setOf("ب", "به", "بِ"),
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
        "ز" to setOf("ز", "زا", "زِ"),
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
        "ی" to setOf("ی", "یا", "یِ", "یاء"),
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

    fun isMatch(target: String, recognitionResults: List<String>): Boolean {
        val normalizedTarget = normalize(target)
        val accepted = (aliases[normalizedTarget].orEmpty() + normalizedTarget)
            .map(::normalize)
            .toSet()

        return recognitionResults.any { raw ->
            val normalized = normalize(raw)
            if (normalized in accepted) return@any true

            val usefulTokens = normalized.split(" ")
                .filter { it.isNotBlank() && it !in fillerWords }

            usefulTokens.any { it in accepted } || usefulTokens.joinToString("") in accepted
        }
    }
}
