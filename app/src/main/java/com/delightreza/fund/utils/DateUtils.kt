package com.delightreza.fund.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

object DateUtils {

    fun formatToLocal(isoString: String): String {
        return try {
            val cleanString = isoString.replace("Z", "")
            val parsed = LocalDateTime.parse(cleanString)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            parsed.format(formatter)
        } catch (e: Exception) {
            isoString.replace("T", " ").take(16)
        }
    }

    fun formatToLocalDateOnly(isoString: String): String {
        return try {
            val cleanString = isoString.replace("Z", "")
            val parsed = LocalDateTime.parse(cleanString)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            parsed.format(formatter)
        } catch (e: Exception) {
            isoString.split("T")[0]
        }
    }

    fun getCurrentTime(): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return now.format(formatter)
    }

    fun getStringFromLocal(calendar: Calendar): String {
        val y = calendar.get(Calendar.YEAR)
        val m = calendar.get(Calendar.MONTH) + 1
        val d = calendar.get(Calendar.DAY_OF_MONTH)
        val h = calendar.get(Calendar.HOUR_OF_DAY)
        val min = calendar.get(Calendar.MINUTE)
        
        return String.format(
            Locale.US, 
            "%04d-%02d-%02dT%02d:%02d:%02d", 
            y, m, d, h, min, 0
        )
    }

    fun generateTransactionId(prefix: String = "tx"): String {
        val random = (1..5).map { "0123456789abcdefghijklmnopqrstuvwxyz".random() }.joinToString("")
        return "${prefix}_${System.currentTimeMillis()}_$random"
    }
}
