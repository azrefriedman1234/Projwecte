package com.pasiflonet.mobile.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

object TranslationManager {
    suspend fun translateToHebrew(text: String): String = withContext(Dispatchers.IO) {
        try {
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=iw&dt=t&q=${URLEncoder.encode(text, "UTF-8")}"
            val url = URL(urlStr)
            val con = url.openConnection() as HttpURLConnection
            con.requestMethod = "GET"
            
            val response = con.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)
            val parts = jsonArray.getJSONArray(0)
            val result = StringBuilder()
            
            for (i in 0 until parts.length()) {
                result.append(parts.getJSONArray(i).getString(0))
            }
            result.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
