package com.hfremovalsadelaide.invoices.jobs

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Serializes and deserializes [JobsSnapshot] for encrypted offline read caching inside [EncryptedStore].
 * Keeps cached records strictly inside app-private encrypted storage and wipes them on logout.
 */
object JobSnapshotCodec {

    fun encode(snapshot: JobsSnapshot): String {
        val jobsArr = JSONArray()
        snapshot.jobs.forEach { j ->
            jobsArr.put(
                JSONObject()
                    .put("id", j.id)
                    .put("title", j.title)
                    .put("scheduledStart", j.scheduledStart?.toString() ?: JSONObject.NULL)
                    .put("scheduledEnd", j.scheduledEnd?.toString() ?: JSONObject.NULL)
                    .put("dateOnly", j.dateOnly)
                    .put("serviceDate", j.serviceDate ?: JSONObject.NULL)
                    .put("bookingStatus", j.bookingStatus.name)
                    .put("cancelled", j.cancelled)
                    .put("state", j.state.server)
                    .put("signoff", j.signoff.name)
                    .put("reference", j.reference ?: JSONObject.NULL)
                    .put("customerName", j.customerName ?: JSONObject.NULL)
                    .put("customerPhone", j.customerPhone ?: JSONObject.NULL)
                    .put("pickup", j.pickup ?: JSONObject.NULL)
                    .put("destination", j.destination ?: JSONObject.NULL)
                    .put("truck", j.truck ?: JSONObject.NULL)
                    .put("crewSize", j.crewSize ?: JSONObject.NULL)
                    .put("notes", j.notes ?: JSONObject.NULL)
                    .put("assignees", JSONArray(j.assignees))
                    .put("actualStart", j.actualStart?.toString() ?: JSONObject.NULL)
                    .put("actualFinish", j.actualFinish?.toString() ?: JSONObject.NULL)
                    .put("elapsedMinutes", j.elapsedMinutes)
                    .put("breakMinutes", j.breakMinutes)
                    .put("workingMinutes", j.workingMinutes)
                    .put("notice", j.notice)
                    .put("appScheduledStart", j.appScheduledStart?.toString() ?: JSONObject.NULL)
                    .put("appScheduledEnd", j.appScheduledEnd?.toString() ?: JSONObject.NULL)
                    .put("vehicleId", j.vehicleId ?: JSONObject.NULL)
                    .put("staffIds", JSONArray(j.staffIds))
                    .put("travelNotes", j.travelNotes)
                    .put("dispatchNotes", j.dispatchNotes)
                    .put("missingInfo", JSONArray(j.missingInfo))
                    .put("waitingMinutes", j.waitingMinutes)
                    .put("overtimeMinutes", j.overtimeMinutes)
                    .put("waitActive", j.waitActive)
                    .put("updatedAt", j.updatedAt ?: JSONObject.NULL)
            )
        }
        val conflictsArr = JSONArray()
        snapshot.conflicts.forEach { (a, b) -> conflictsArr.put(JSONArray().put(a).put(b)) }

        return JSONObject()
            .put("jobs", jobsArr)
            .put("todayConfirmed", JSONArray(snapshot.todayConfirmed.toList()))
            .put("upcoming", JSONArray(snapshot.upcoming.toList()))
            .put("inProgress", JSONArray(snapshot.inProgress.toList()))
            .put("completed", JSONArray(snapshot.completed.toList()))
            .put("awaitingSignoff", JSONArray(snapshot.awaitingSignoff.toList()))
            .put("unconfirmed", JSONArray(snapshot.unconfirmed.toList()))
            .put("conflicts", conflictsArr)
            .put("calendarConnected", snapshot.calendarConnected)
            .put("lastSyncedAt", snapshot.lastSyncedAt ?: JSONObject.NULL)
            .put("lastError", snapshot.lastError ?: JSONObject.NULL)
            .put("missingInfoIds", JSONArray(snapshot.missingInfoIds.toList()))
            .put("twoWaySyncEnabled", snapshot.twoWaySyncEnabled)
            .toString()
    }

    fun decode(raw: String?): JobsSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val jArr = root.getJSONArray("jobs")
            val jobs = List(jArr.length()) { idx ->
                val o = jArr.getJSONObject(idx)
                Job(
                    id = o.getString("id"),
                    title = o.optString("title", ""),
                    scheduledStart = instant(o.str("scheduledStart")),
                    scheduledEnd = instant(o.str("scheduledEnd")),
                    dateOnly = o.optBoolean("dateOnly", false),
                    serviceDate = o.str("serviceDate"),
                    bookingStatus = runCatching { BookingStatus.valueOf(o.optString("bookingStatus")) }.getOrDefault(BookingStatus.UNKNOWN),
                    cancelled = o.optBoolean("cancelled", false),
                    state = JobState.from(o.str("state")),
                    signoff = runCatching { SignoffStatus.valueOf(o.optString("signoff")) }.getOrDefault(SignoffStatus.NONE),
                    reference = o.str("reference"),
                    customerName = o.str("customerName"),
                    customerPhone = o.str("customerPhone"),
                    pickup = o.str("pickup"),
                    destination = o.str("destination"),
                    truck = o.str("truck"),
                    crewSize = if (o.isNull("crewSize")) null else o.optInt("crewSize"),
                    notes = o.str("notes"),
                    assignees = o.optJSONArray("assignees").toStrings(),
                    actualStart = instant(o.str("actualStart")),
                    actualFinish = instant(o.str("actualFinish")),
                    elapsedMinutes = o.optInt("elapsedMinutes", 0),
                    breakMinutes = o.optInt("breakMinutes", 0),
                    workingMinutes = o.optInt("workingMinutes", 0),
                    notice = o.optBoolean("notice", false),
                    appScheduledStart = instant(o.str("appScheduledStart")),
                    appScheduledEnd = instant(o.str("appScheduledEnd")),
                    vehicleId = o.str("vehicleId"),
                    staffIds = o.optJSONArray("staffIds").toStrings(),
                    travelNotes = o.optString("travelNotes", ""),
                    dispatchNotes = o.optString("dispatchNotes", ""),
                    missingInfo = o.optJSONArray("missingInfo").toStrings(),
                    waitingMinutes = o.optInt("waitingMinutes", 0),
                    overtimeMinutes = o.optInt("overtimeMinutes", 0),
                    waitActive = o.optBoolean("waitActive", false),
                    updatedAt = o.str("updatedAt")
                )
            }
            val cArr = root.optJSONArray("conflicts") ?: JSONArray()
            val conflicts = List(cArr.length()) { idx ->
                val pair = cArr.getJSONArray(idx)
                pair.optString(0) to pair.optString(1)
            }
            JobsSnapshot(
                jobs = jobs,
                todayConfirmed = root.optJSONArray("todayConfirmed").toStrings().toSet(),
                upcoming = root.optJSONArray("upcoming").toStrings().toSet(),
                inProgress = root.optJSONArray("inProgress").toStrings().toSet(),
                completed = root.optJSONArray("completed").toStrings().toSet(),
                awaitingSignoff = root.optJSONArray("awaitingSignoff").toStrings().toSet(),
                unconfirmed = root.optJSONArray("unconfirmed").toStrings().toSet(),
                conflicts = conflicts,
                calendarConnected = root.optBoolean("calendarConnected", false),
                lastSyncedAt = root.str("lastSyncedAt"),
                lastError = root.str("lastError"),
                missingInfoIds = root.optJSONArray("missingInfoIds").toStrings().toSet(),
                twoWaySyncEnabled = root.optBoolean("twoWaySyncEnabled", false)
            )
        }.getOrNull()
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() }

    private fun instant(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun JSONArray?.toStrings(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it, "") }.filter { it.isNotBlank() }
}
