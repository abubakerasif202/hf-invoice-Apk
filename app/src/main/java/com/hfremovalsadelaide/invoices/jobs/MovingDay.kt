package com.hfremovalsadelaide.invoices.jobs

import java.time.Instant

enum class StopKind(val server: String, val label: String) {
    PICKUP("pickup", "Pickup"),
    DELIVERY("delivery", "Delivery");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.server == value } ?: PICKUP
    }
}

/** Travelling and arrived are recorded apart from the work clock, so they never change charged hours. */
data class JobMilestones(val travellingAt: Instant?, val arrivedAt: Instant?, val travelMinutes: Int?)

data class JobStop(
    val id: String,
    val seq: Int,
    val kind: StopKind,
    val address: String,
    val contactName: String,
    val contactPhone: String,
    val notes: String,
    val arrivedAt: Instant?,
    val completedAt: Instant?
)

/** A stop being edited. [visited] stops keep their place and cannot be removed. */
data class StopDraft(
    val id: String?,
    val kind: StopKind,
    val address: String,
    val contactName: String = "",
    val contactPhone: String = "",
    val notes: String = "",
    val visited: Boolean = false
)

enum class JourneyStatus { NOT_STARTED, TRAVELLING, ARRIVED, WORKING, DONE }
enum class StopStatus { PENDING, ARRIVED, COMPLETED }

object MovingDay {
    const val MAX_STOPS = 20

    private fun actionable(job: Job) = !job.cancelled && !job.notice

    fun canMarkTravelling(job: Job, canAct: Boolean): Boolean =
        canAct && actionable(job) && job.state == JobState.SCHEDULED &&
            job.milestones?.travellingAt == null && job.milestones?.arrivedAt == null

    /** Arrival can be recorded even when travelling was forgotten. */
    fun canMarkArrived(job: Job, canAct: Boolean): Boolean =
        canAct && actionable(job) && job.state == JobState.SCHEDULED && job.milestones?.arrivedAt == null

    fun journeyStatus(job: Job): JourneyStatus = when {
        job.state == JobState.COMPLETED -> JourneyStatus.DONE
        job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED -> JourneyStatus.WORKING
        job.milestones?.arrivedAt != null -> JourneyStatus.ARRIVED
        job.milestones?.travellingAt != null -> JourneyStatus.TRAVELLING
        else -> JourneyStatus.NOT_STARTED
    }

    fun stopStatus(stop: JobStop): StopStatus = when {
        stop.completedAt != null -> StopStatus.COMPLETED
        stop.arrivedAt != null -> StopStatus.ARRIVED
        else -> StopStatus.PENDING
    }

    private fun running(job: Job) = actionable(job) && (job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED)

    fun canArriveAtStop(job: Job, stop: JobStop, canAct: Boolean) = canAct && running(job) && stop.arrivedAt == null

    fun canCompleteStop(job: Job, stop: JobStop, canAct: Boolean) =
        canAct && running(job) && stop.arrivedAt != null && stop.completedAt == null

    /** The first stop not yet completed, in dispatch order. */
    fun nextStop(job: Job): JobStop? = job.stops.sortedBy { it.seq }.firstOrNull { it.completedAt == null }

    /** Null for a simple job (no stops, or a single one), so the screen only adds detail when there is some. */
    fun summary(job: Job): String? {
        if (job.stops.size < 2) return null
        val pickups = job.stops.count { it.kind == StopKind.PICKUP }
        val deliveries = job.stops.count { it.kind == StopKind.DELIVERY }
        fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
        return "${plural(pickups, "pickup", "pickups")} · ${plural(deliveries, "delivery", "deliveries")}"
    }

    // --- Stop editor (dispatch) -------------------------------------------------------------------------------

    fun toDrafts(stops: List<JobStop>): List<StopDraft> = stops.sortedBy { it.seq }.map {
        StopDraft(it.id, it.kind, it.address, it.contactName, it.contactPhone, it.notes, visited = it.arrivedAt != null)
    }

    fun add(list: List<StopDraft>, kind: StopKind): List<StopDraft> =
        if (list.size >= MAX_STOPS) list else list + StopDraft(null, kind, "")

    /** A stop never moves above one that has already been visited: the visit order is history. */
    fun moveUp(list: List<StopDraft>, index: Int): List<StopDraft> {
        if (index <= 0 || index >= list.size || list[index].visited || list[index - 1].visited) return list
        return list.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }
    }

    fun moveDown(list: List<StopDraft>, index: Int): List<StopDraft> {
        if (index < 0 || index >= list.size - 1 || list[index].visited || list[index + 1].visited) return list
        return list.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }
    }

    fun remove(list: List<StopDraft>, index: Int): List<StopDraft> =
        if (index !in list.indices || list[index].visited) list else list.filterIndexed { i, _ -> i != index }

    /** Null when the list can be saved, otherwise the reason it can not. */
    fun validate(list: List<StopDraft>): String? = when {
        list.size > MAX_STOPS -> "A job can have at most $MAX_STOPS stops."
        list.any { it.address.isBlank() } -> "Each stop needs an address."
        else -> null
    }
}
