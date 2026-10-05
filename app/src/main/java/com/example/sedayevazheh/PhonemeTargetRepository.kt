package com.example.sedayevazheh

import android.content.Context
import org.json.JSONObject

data class PhonemeTarget(
    val phones: String,
    val first: String,
    val last: String,
)

class PhonemeTargetRepository(context: Context) {
    private val targets: Map<Int, PhonemeTarget>

    init {
        val json = context.assets.open("phoneme_targets.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(json)
        val map = mutableMapOf<Int, PhonemeTarget>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = root.getJSONObject(key)
            map[key.toInt()] = PhonemeTarget(
                phones = obj.getString("phones"),
                first = obj.getString("first"),
                last = obj.getString("last"),
            )
        }
        targets = map
    }

    fun forWord(wordId: Int): PhonemeTarget? = targets[wordId]
}
