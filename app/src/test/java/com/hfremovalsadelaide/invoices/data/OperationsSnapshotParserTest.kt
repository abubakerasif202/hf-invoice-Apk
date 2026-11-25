package com.hfremovalsadelaide.invoices.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Shape mirrors the backend's computeOperations() output (lib/ops-snapshot.ts). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OperationsSnapshotParserTest {
    private val payload = """
        {
          "confirmedToday": 3, "tentativeToday": 1,
          "confirmedTomorrow": 2, "tentativeTomorrow": 0,
          "activeJobs": 1,
          "unassignedBookings": 4, "unassignedCrew": 3, "unassignedTruck": 2,
          "trucksTotal": 5, "trucksUnavailable": 1, "trucksOnJobsToday": 2, "trucksAvailableToday": 2,
          "workingStaffToday": 6,
          "crewConflicts": [{"jobA": "a", "jobB": "b", "reasons": ["crew", "truck"]}],
          "maintenanceDue": [
            {"vehicleId": "v1", "vehicleName": "T1", "kind": "rego", "dueDate": "2026-12-01", "message": "T1 rego due 2026-12-01"}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses every operations field the dashboard shows`() {
        val ops = parseOperationsSnapshot(JSONObject().put("operations", JSONObject(payload)))
        assertNotNull(ops)
        ops!!
        assertEquals(3, ops.confirmedToday)
        assertEquals(1, ops.tentativeToday)
        assertEquals(2, ops.confirmedTomorrow)
        assertEquals(1, ops.activeJobs)
        assertEquals(4, ops.unassignedBookings)
        assertEquals(3, ops.unassignedCrew)
        assertEquals(2, ops.unassignedTruck)
        assertEquals(5, ops.trucksTotal)
        assertEquals(2, ops.trucksAvailableToday)
        assertEquals(6, ops.workingStaffToday)
        assertEquals(listOf("crew", "truck"), ops.crewConflicts.single().reasons)
        assertEquals("a", ops.crewConflicts.single().jobA)
        assertEquals("rego", ops.maintenanceDue.single().kind)
        assertEquals("2026-12-01", ops.maintenanceDue.single().dueDate)
    }

    @Test
    fun `an older backend without the operations block yields null instead of fabricated zeros`() {
        assertNull(parseOperationsSnapshot(JSONObject("""{"kpis": {}}""")))
    }

    @Test
    fun `a service item with no due date keeps a null date`() {
        val json = JSONObject(payload)
        json.getJSONArray("maintenanceDue").put(
            JSONObject().put("vehicleId", "v2").put("vehicleName", "T2").put("kind", "service").put("message", "T2 service due")
        )
        val ops = parseOperationsSnapshot(JSONObject().put("operations", json))!!
        assertNull(ops.maintenanceDue.last().dueDate)
    }
}
