package com.hfremovalsadelaide.invoices.jobs

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All display times use the business time zone, so DST changes are handled by java.time. */
object JobFormat {
    val zone: ZoneId = ZoneId.of("Australia/Adelaide")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(zone)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val longFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val editFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH).withZone(zone)

    fun time(i: Instant?) = i?.let { timeFmt.format(it) } ?: "-"
    fun day(d: LocalDate): String = dayFmt.format(d)
    fun longDay(d: LocalDate): String = longFmt.format(d)
    fun month(d: LocalDate): String = monthFmt.format(d)
    fun editable(i: Instant?) = i?.let { editFmt.format(it) }.orEmpty()

    /** Parses "yyyy-MM-dd HH:mm" in Adelaide time; null when malformed. */
    fun parseEditable(text: String): Instant? = runCatching {
        java.time.LocalDateTime.parse(text.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)).atZone(zone).toInstant()
    }.getOrNull()

    fun duration(minutes: Int) = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

    fun localDate(job: Job): LocalDate? =
        job.scheduledStart?.atZone(zone)?.toLocalDate() ?: job.serviceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun scheduleText(job: Job) = when {
        job.dateOnly -> "All day (no time set)"
        job.scheduledStart == null -> "No time set"
        job.scheduledEnd == null -> time(job.scheduledStart)
        else -> "${time(job.scheduledStart)} - ${time(job.scheduledEnd)}"
    }
}
