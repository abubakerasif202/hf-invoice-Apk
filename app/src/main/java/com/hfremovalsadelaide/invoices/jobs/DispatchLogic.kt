package com.hfremovalsadelaide.invoices.jobs

import java.time.Duration
import java.time.Instant

enum class ClashKind { CREW, TRUCK }

/** A crew member or truck that is already committed to another job during the same time window. */
data class AssignmentClash(val kind: ClashKind, val label: String, val otherJobId: String, val otherTitle: String)

enum class AttentionLevel { NONE, MEDIUM, HIGH }

/**
 * Derived from live data on every render; it is NOT a stored priority. HF has no priority field, so nothing here
 * is edited by hand or sent to the server.
 */
data class Attention(val level: AttentionLevel, val reasons: List<String>)

object DispatchLogic {
    private val URGENT_WINDOW: Duration = Duration.ofHours(24)

    private fun operational(job: Job) = !job.cancelled && !job.notice

    /** Real start and end times that intersect. Date-only entries never overlap, matching the backend detector. */
    fun overlaps(a: Job, b: Job): Boolean {
        if (a.dateOnly || b.dateOnly) return false
        val aStart = a.effectiveStart ?: return false
        val aEnd = a.effectiveEnd ?: return false
        val bStart = b.effectiveStart ?: return false
        val bEnd = b.effectiveEnd ?: return false
        return aStart < bEnd && bStart < aEnd
    }

    private fun norm(text: String?) = text?.trim()?.lowercase().orEmpty()

    /**
     * Resources in the proposed assignment that are already committed elsewhere during [job]'s window.
     * Completed, cancelled and notice entries do not hold a resource, and a job never clashes with itself.
     */
    fun clashes(
        job: Job,
        others: List<Job>,
        staffIds: Set<String>,
        crewNames: Set<String>,
        vehicleId: String?,
        truck: String?
    ): List<AssignmentClash> {
        val wantedNames = crewNames.map(::norm).filter { it.isNotEmpty() }.toSet()
        val wantedTruck = norm(truck)
        return others
            .filter { it.id != job.id && operational(it) && it.state != JobState.COMPLETED && overlaps(job, it) }
            .flatMap { other ->
                buildList {
                    val sharedStaff = staffIds.intersect(other.staffIds.toSet())
                    val sharedNames = other.assignees.filter { norm(it) in wantedNames }
                    (sharedStaff.firstOrNull() ?: sharedNames.firstOrNull())?.let { label ->
                        val shown = sharedNames.firstOrNull() ?: label
                        add(AssignmentClash(ClashKind.CREW, shown, other.id, other.title))
                    }
                    val sameVehicle = vehicleId != null && other.vehicleId == vehicleId
                    val sameTruckText = wantedTruck.isNotEmpty() && norm(other.truck) == wantedTruck
                    if (sameVehicle || sameTruckText) {
                        add(AssignmentClash(ClashKind.TRUCK, other.truck?.ifBlank { null } ?: truck.orEmpty(), other.id, other.title))
                    }
                }
            }
    }

    /** How urgently a scheduled job needs a dispatcher's attention. [conflictJobIds] comes from the server's detector. */
    fun attention(job: Job, conflictJobIds: Set<String>, now: Instant): Attention {
        if (!operational(job) || job.state != JobState.SCHEDULED) return Attention(AttentionLevel.NONE, emptyList())

        val missingCrew = job.assignees.isEmpty() && job.staffIds.isEmpty()
        val missingTruck = job.truck.isNullOrBlank() && job.vehicleId == null
        val start = job.effectiveStart
        val imminent = start != null && start >= now.minus(Duration.ofHours(1)) && start <= now.plus(URGENT_WINDOW)

        val high = buildList {
            if (job.id in conflictJobIds) add("Crew or truck conflict")
            if (imminent && missingCrew) add("Starts within 24h with no crew")
            if (imminent && missingTruck) add("Starts within 24h with no truck")
        }
        if (high.isNotEmpty()) return Attention(AttentionLevel.HIGH, high)

        val medium = buildList {
            if (missingCrew) add("No crew assigned")
            if (missingTruck) add("No truck assigned")
            if (job.bookingStatus != BookingStatus.CONFIRMED) add("Booking not confirmed")
            if (job.missingInfo.isNotEmpty()) add("Missing: ${job.missingInfo.joinToString()}")
        }
        return if (medium.isEmpty()) Attention(AttentionLevel.NONE, emptyList()) else Attention(AttentionLevel.MEDIUM, medium)
    }
}
