package com.example.sedayevazheh

import android.content.Context
import org.json.JSONArray

object WordRepository {
    fun load(context: Context): List<WordCard> {
        val json = context.assets.open("words.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    WordCard(
                        id = item.getInt("id"),
                        word = item.getString("word"),
                        pronunciation = item.getString("pronunciation"),
                        english = item.getString("english"),
                        category = item.getString("category"),
                        imageEmoji = item.getString("imageEmoji"),
                        firstSound = item.getString("firstSound"),
                        lastSound = item.getString("lastSound"),
                    )
                )
            }
        }
    }
}
