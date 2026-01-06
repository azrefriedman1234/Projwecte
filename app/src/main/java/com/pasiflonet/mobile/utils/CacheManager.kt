package com.pasiflonet.mobile.utils

import android.content.Context
import java.io.File

object CacheManager {
    fun clearAppCache(context: Context): Long {
        var bytesDeleted: Long = 0
        
        // 1. ניקוי תיקיית המטמון של אנדרואיד (תמונות ערוכות וזמניות)
        context.cacheDir.listFiles()?.forEach {
            bytesDeleted += it.length()
            it.deleteRecursively()
        }

        // 2. ניקוי תיקיית המדיה של TDLib (קבצים שירדו מטלגרם)
        // שים לב: אנחנו לא נוגעים בתיקיית ה-database שנקראת "tdlib"
        val tdlibFiles = File(context.filesDir, "tdlib_files")
        if (tdlibFiles.exists()) {
            tdlibFiles.listFiles()?.forEach {
                bytesDeleted += it.length()
                it.deleteRecursively()
            }
        }
        
        return bytesDeleted // מחזיר כמה מגה-בייט נמחקו
    }
}
