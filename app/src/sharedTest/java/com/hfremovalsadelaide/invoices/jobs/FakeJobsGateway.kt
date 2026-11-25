package com.hfremovalsadelaide.invoices.jobs

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory stand-in for the backend, used only by on-device end-to-end tests. It never touches the network,
 * the session cookie or any real booking: every job here is a clearly labelled test fixture.
 */
class FakeJobsGateway(initial: List<Job>) : JobsGateway {
    private val jobs = initial.toMutableList()
    private val events = mutableMapOf<String, MutableList<Pair<String, Instant>>>()
    private var clock = Instant.parse("2026-11-24T00:00:00Z")

    val calls = mutableListOf<String>()
    val savedDrafts = mutableListOf<JSONObject>()
    var lastSignature: String? = null
    var lastSignoffName: String? = null
    var lastSkipReason: String? = null
    var lastCrewEvent: CrewEventItem? = null

    private fun tick(): Instant { clock = clock.plusSeconds(1800); return clock }

    override suspend fun snapshot(): JobsResult<JobsSnapshot> {
        val ids = jobs.map { it.id }.toSet()
        return JobsResult.Success(
            JobsSnapshot(
                jobs = jobs.toList(),
                todayConfirmed = ids, upcoming = emptySet(),
                inProgress = jobs.filter { it.state == JobState.IN_PROGRESS || it.state == JobState.PAUSED }.map { it.id }.toSet(),
                completed = jobs.filter { it.state == JobState.COMPLETED }.map { it.id }.toSet(),
                awaitingSignoff = jobs.filter { it.state == JobState.COMPLETED && it.signoff == SignoffStatus.NONE }.map { it.id }.toSet(),
                unconfirmed = emptySet(), conflicts = emptyList(), calendarConnected = true, lastSyncedAt = null, lastError = null
            )
        )
    }

    override suspend fun detail(id: String): JobsResult<Job> =
        jobs.firstOrNull { it.id == id }?.let { JobsResult.Success(it) } ?: JobsResult.Failure("Not found")

    override suspend fun sync(): JobsResult<String> { calls += "sync"; return JobsResult.Success("{}") }

    override suspend fun act(id: String, action: String): JobsResult<String> {
        calls += "act:$action"
        val job = jobs.first { it.id == id }
        if (action == "travelling" || action == "arrived") {
            val travellingAt = if (action == "travelling") tick() else job.milestones?.travellingAt
            val arrivedAt = if (action == "arrived") tick() else null
            val travelMinutes = if (travellingAt != null && arrivedAt != null) {
                Duration.between(travellingAt, arrivedAt).toMinutes().toInt()
            } else null
            update(job.copy(milestones = JobMilestones(travellingAt, arrivedAt, travelMinutes)))
            return JobsResult.Success("{}")
        }
        val log = events.getOrPut(id) { mutableListOf() }
        val state = when (action) {
            "start" -> JobState.IN_PROGRESS
            "pause" -> JobState.PAUSED
            "resume" -> JobState.IN_PROGRESS
            "finish" -> JobState.COMPLETED
            else -> return JobsResult.Failure("Unknown action")
        }
        log += action to tick()
        update(job.copy(state = state))
        return JobsResult.Success("{}")
    }

    override suspend fun stopEvent(id: String, stopId: String, event: String): JobsResult<String> {
        val job = jobs.first { it.id == id }
        val stop = job.stops.first { it.id == stopId }
        val updatedStop = when (event) {
            "arrived" -> stop.copy(arrivedAt = tick())
            "completed" -> stop.copy(completedAt = tick())
            else -> return JobsResult.Failure("Unknown stop event")
        }
        calls += "stop:$stopId:$event"
        update(job.copy(stops = job.stops.map { if (it.id == stopId) updatedStop else it }))
        return JobsResult.Success("{}")
    }

    override suspend fun recordCrewEvent(
        id: String, memberName: String, eventType: String, customerAcknowledged: Boolean,
        acknowledgmentName: String, notes: String, at: Instant?
    ): JobsResult<String> {
        val job = jobs.first { it.id == id }
        val event = CrewEventItem("fixture-crew-event", memberName, eventType, at ?: tick(), customerAcknowledged, acknowledgmentName, notes)
        lastCrewEvent = event
        update(job.copy(crewEvents = job.crewEvents + event))
        return JobsResult.Success("{}")
    }

    /** Recomputes the recorded times the way the server does: working time is elapsed minus pauses. */
    private fun update(job: Job) {
        val log = events[job.id].orEmpty()
        val start = log.firstOrNull { it.first == "start" }?.second
        val finish = log.firstOrNull { it.first == "finish" }?.second
        val end = finish ?: log.lastOrNull()?.second
        var paused = 0L
        var pauseAt: Instant? = null
        for ((type, at) in log) {
            if (type == "pause") pauseAt = at
            if ((type == "resume" || type == "finish") && pauseAt != null) { paused += at.epochSecond - pauseAt.epochSecond; pauseAt = null }
        }
        val elapsed = if (start != null && end != null) ((end.epochSecond - start.epochSecond) / 60).toInt() else 0
        val breaks = (paused / 60).toInt()
        val next = job.copy(actualStart = start, actualFinish = finish, elapsedMinutes = elapsed, breakMinutes = breaks, workingMinutes = elapsed - breaks)
        jobs[jobs.indexOfFirst { it.id == job.id }] = next
    }

    override suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String> {
        calls += "correct:$field:$reason"
        return JobsResult.Success("{}")
    }

    override suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String> {
        calls += "signoff"
        lastSignoffName = customerName
        lastSignature = pngBase64
        val job = jobs.first { it.id == id }
        jobs[jobs.indexOf(job)] = job.copy(signoff = SignoffStatus.SIGNED)
        return JobsResult.Success("{}")
    }

    override suspend fun skipSignoff(id: String, reason: String): JobsResult<String> {
        calls += "skip"
        lastSkipReason = reason
        val job = jobs.first { it.id == id }
        jobs[jobs.indexOf(job)] = job.copy(signoff = SignoffStatus.SKIPPED)
        return JobsResult.Success("{}")
    }

    override suspend fun invoiceDraft(id: String): JobsResult<JSONObject> {
        calls += "draft"
        val job = jobs.first { it.id == id }
        return JobsResult.Success(
            JSONObject()
                .put("status", "draft")
                .put("workingMinutes", job.workingMinutes)
                .put("warnings", JSONArray().put("No matching truck package, so the hourly rate is 0. Enter the agreed rate before issuing."))
                .put("newCustomer", JSONObject().put("name", job.customerName ?: "Test Customer"))
                .put("input", JSONObject().put("serviceFrom", LocalDate.now().toString()).put("pickup", job.pickup ?: "")
                    .put("delivery", job.destination ?: "")
                    .put("days", JSONArray().put(JSONObject().put("start", "09:00").put("finish", "13:00").put("breakMinutes", job.breakMinutes).put("rate", "0").put("movers", 2))))
        )
    }

    override suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>> =
        JobsResult.Success(NotificationCategory.entries.associateWith { true })

    override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String> { calls += "prefs"; return JobsResult.Success("{}") }

    override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?): JobsResult<String> {
        calls += "saveDraft"
        savedDrafts += input
        return JobsResult.Success("{}")
    }

    companion object {
        fun fixture(state: JobState = JobState.SCHEDULED, id: String = "fixture-1") = Job(
            id = id, title = "TEST FIXTURE - Removal (not a customer)",
            scheduledStart = Instant.parse("2026-11-23T22:30:00Z"), scheduledEnd = Instant.parse("2026-11-24T02:30:00Z"),
            dateOnly = false, serviceDate = null, bookingStatus = BookingStatus.CONFIRMED, cancelled = false, state = state,
            signoff = SignoffStatus.NONE, reference = "TEST-0001", customerName = "Test Customer", customerPhone = "0400 000 000",
            pickup = "1 Test Street, Testville", destination = "2 Sample Road, Sampleton", truck = "MR Truck", crewSize = 2, notes = "Fixture only",
            assignees = emptyList(), actualStart = null, actualFinish = null, elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0
        )
    }
}
