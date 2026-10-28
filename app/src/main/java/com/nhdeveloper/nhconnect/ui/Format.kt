package com.nhdeveloper.nhconnect.ui

import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

object Format {
    private fun day(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

    fun time(ts: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))

    fun listTime(ts: Long): String {
        if (ts <= 0) return ""
        val today = LocalDate.now()
        return when (day(ts)) {
            today -> time(ts)
            today.minusDays(1) -> "Yesterday"
            else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts))
        }
    }

    fun dateLabel(ts: Long): String {
        val today = LocalDate.now()
        return when (day(ts)) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
        }
    }

    fun sameDay(a: Long, b: Long) = day(a) == day(b)

    fun lastSeen(ts: Long): String = if (ts <= 0) "" else "last seen ${listTime(ts).lowercase()}"

    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
