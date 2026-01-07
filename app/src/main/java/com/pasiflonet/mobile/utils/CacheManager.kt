package com.pasiflonet.mobile.utils

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {

    // פונקציה לחישוב גודל המטמון הנוכחי (ב-MB)
    fun getCacheSize(context: Context): String {
        val cacheDir = context.cacheDir
        val sizeBytes = getDirSize(cacheDir)
        val sizeMB = sizeBytes / (1024.0 * 1024.0)
        return String.format("%.2f MB", sizeMB)
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                size += file.length()
            } else if (file.isDirectory) {
                size += getDirSize(file)
            }
        }
        return size
    }

    // הפונקציה הראשית לניקוי
    suspend fun clearAppCache(context: Context, showToast: Boolean = true) {
        withContext(Dispatchers.IO) {
            val cacheDir = context.cacheDir
            val filesDir = context.filesDir
            
            var deletedCount = 0
            var deletedSize: Long = 0

            // 1. ניקוי תיקיית המטמון הראשית (קבצים זמניים, תמונות, וידאו מעובד)
            // מוחקים הכל כי זה Cache
            cacheDir.listFiles()?.forEach { file ->
                val size = getDirSize(file)
                if (file.deleteRecursively()) {
                    deletedCount++
                    deletedSize += size
                }
            }

            // 2. ניקוי סלקטיבי בתיקיית הקבצים (קבצים שירדו מטלגרם)
            // נזהר לא למחוק את תיקיית "tdlib" שמחזיקה את ההתחברות!
            filesDir.listFiles()?.forEach { file ->
                // מוחקים רק קבצים שהם לא מסד הנתונים של טלגרם
                if (file.name != "tdlib" && file.name != "tdlib_files" && file.name != "datastore") {
                    val size = getDirSize(file)
                    if (file.deleteRecursively()) {
                        deletedCount++
                        deletedSize += size
                    }
                }
            }

            val sizeMB = deletedSize / (1024.0 * 1024.0)
            
            withContext(Dispatchers.Main) {
                if (showToast) {
                    val msg = String.format("🧹 Cleaned %.2f MB (%d files)", sizeMB, deletedCount)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
