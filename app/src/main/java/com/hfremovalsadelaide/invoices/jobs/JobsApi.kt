package com.hfremovalsadelaide.invoices.jobs

import android.webkit.CookieManager
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

object JobsApi : JobsGateway {
    private const val BASE = TrustedOriginPolicy.PRODUCTION_URL
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 25_000

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() }

    private fun instant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it, "") }.filter { it.isNotBlank() }

    fun parseJob(o: JSONObject): Job {
        val details = o.optJSONObject("details") ?: JSONObject()
        val actual = o.optJSONObject("actual") ?: JSONObject()

        val crewEvents = o.optJSONArray("crewEvents")?.let { arr ->
            List(arr.length()) { idx ->
                val c = arr.getJSONObject(idx)
                CrewEventItem(
                    id = c.optString("id"),
                    memberName = c.optString("memberName"),
                    eventType = c.optString("eventType"),
                    at = instant(c.str("at")),
                    customerAcknowledged = c.optBoolean("customerAcknowledged", false),
                    acknowledgmentName = c.optString("acknowledgmentName", ""),
                    notes = c.optString("notes", "")
                )
            }
        } ?: emptyList()

        val timeline = o.optJSONArray("timeline")?.let { arr ->
            List(arr.length()) { idx ->
                val t = arr.getJSONObject(idx)
                TimelineEntry(
                    kind = t.optString("kind"),
                    at = t.optString("at"),
                    label = t.optString("label"),
                    actor = t.optString("actor"),
                    detail = t.optString("detail")
                )
            }
        } ?: emptyList()

        val conditionReports = o.optJSONArray("conditionReports")?.let { arr ->
            List(arr.length()) { idx ->
                val r = arr.getJSONObject(idx)
                val clObj = r.optJSONObject("checklist") ?: JSONObject()
                val checklist = clObj.keys().asSequence().associateWith { clObj.optBoolean(it, false) }
                ConditionReportItem(
                    id = r.optString("id"),
                    phase = r.optString("phase"),
                    accessConditions = r.optString("access_conditions", ""),
                    existingDamage = r.optString("existing_damage", ""),
                    delaysOrIssues = r.optString("delays_or_issues", ""),
                    additionalServices = r.optString("additional_services", ""),
                    checklist = checklist,
                    customerAcknowledged = r.optBoolean("customer_acknowledged", false),
                    acknowledgmentName = r.optString("acknowledgment_name", ""),
                    notes = r.optString("notes", "")
                )
            }
        } ?: emptyList()

        val photos = o.optJSONArray("photos")?.let { arr ->
            List(arr.length()) { idx ->
                val p = arr.getJSONObject(idx)
                JobPhotoMeta(
                    id = p.optString("id"),
                    phase = p.optString("phase"),
                    inventoryItemId = p.str("inventory_item_id"),
                    caption = p.optString("caption", ""),
                    mimeType = p.optString("mime_type", "image/jpeg"),
                    createdAt = p.optString("created_at", "")
                )
            }
        } ?: emptyList()

        return Job(
            id = o.getString("id"),
            title = o.optString("title", ""),
            scheduledStart = instant(o.str("scheduledStart")),
            scheduledEnd = instant(o.str("scheduledEnd")),
            dateOnly = o.optBoolean("dateOnly", false),
            serviceDate = o.str("serviceDate"),
            bookingStatus = BookingStatus.from(o.str("bookingStatus")),
            cancelled = o.optBoolean("cancelled", false),
            state = JobState.from(o.str("state")),
            signoff = SignoffStatus.from(o.str("signoffStatus")),
            reference = details.str("reference"),
            customerName = details.str("customerName"),
            customerPhone = details.str("customerPhone"),
            pickup = details.str("pickup"),
            destination = details.str("destination"),
            truck = o.str("truck") ?: details.str("truck"),
            crewSize = if (details.isNull("crewSize")) null else details.optInt("crewSize").takeIf { it > 0 },
            notes = details.str("notes"),
            assignees = o.optJSONArray("assignees").strings(),
            actualStart = instant(actual.str("start")),
            actualFinish = instant(actual.str("finish")),
            elapsedMinutes = actual.optInt("elapsedMinutes", 0),
            breakMinutes = actual.optInt("breakMinutes", 0),
            workingMinutes = actual.optInt("workingMinutes", 0),
            notice = o.optBoolean("notice", false),
            appScheduledStart = instant(o.str("appScheduledStart")),
            appScheduledEnd = instant(o.str("appScheduledEnd")),
            vehicleId = o.str("vehicleId"),
            staffIds = o.optJSONArray("staffIds").strings(),
            assignmentDetails = o.optJSONArray("assignmentDetails")?.let { assignments ->
                List(assignments.length()) { index ->
                    val assignment = assignments.getJSONObject(index)
                    JobAssignment(assignment.str("staffId"), assignment.getString("assignee"), assignment.getString("role"))
                }
            },
            travelNotes = o.optString("travelNotes", ""),
            dispatchNotes = o.optString("dispatchNotes", ""),
            priority = JobPriority.from(o.str("priority")),
            lifecycle = BookingLifecycle.fromJson(o.optJSONObject("lifecycle")),
            milestones = o.optJSONObject("milestones")?.let { m ->
                JobMilestones(
                    travellingAt = instant(m.str("travellingAt")),
                    arrivedAt = instant(m.str("arrivedAt")),
                    travelMinutes = if (m.isNull("travelMinutes")) null else m.optInt("travelMinutes")
                )
            },
            stops = o.optJSONArray("stops")?.let { arr ->
                List(arr.length()) { i ->
                    val s = arr.getJSONObject(i)
                    JobStop(
                        id = s.getString("id"),
                        seq = s.optInt("seq", i + 1),
                        kind = StopKind.from(s.str("kind")),
                        address = s.optString("address", ""),
                        contactName = s.optString("contactName", ""),
                        contactPhone = s.optString("contactPhone", ""),
                        notes = s.optString("notes", ""),
                        arrivedAt = instant(s.str("arrivedAt")),
                        completedAt = instant(s.str("completedAt"))
                    )
                }
            }.orEmpty(),
            missingInfo = o.optJSONArray("missingInfo").strings(),
            waitingMinutes = actual.optInt("waitingMinutes", 0),
            overtimeMinutes = actual.optInt("overtimeMinutes", 0),
            waitActive = actual.optBoolean("waitActive", false),
            updatedAt = o.str("updatedAt"),
            crewEvents = crewEvents,
            timeline = timeline,
            conditionReports = conditionReports,
            photos = photos
        )
    }

    fun parseSnapshot(text: String): JobsSnapshot {
        val root = JSONObject(text)
        val jobsArray = root.optJSONArray("jobs") ?: JSONArray()
        val jobs = List(jobsArray.length()) { parseJob(jobsArray.getJSONObject(it)) }
        val summary = root.optJSONObject("summary") ?: JSONObject()
        val conflicts = summary.optJSONArray("conflicts")?.let { arr ->
            List(arr.length()) { arr.optJSONArray(it) }.mapNotNull { p -> p?.let { p.optString(0) to p.optString(1) } }
        } ?: emptyList()
        val calendar = root.optJSONObject("calendar") ?: JSONObject()
        return JobsSnapshot(
            jobs = jobs,
            todayConfirmed = summary.optJSONArray("todayConfirmed").strings().toSet(),
            upcoming = summary.optJSONArray("upcoming").strings().toSet(),
            inProgress = summary.optJSONArray("inProgress").strings().toSet(),
            completed = summary.optJSONArray("completed").strings().toSet(),
            awaitingSignoff = summary.optJSONArray("awaitingSignoff").strings().toSet(),
            unconfirmed = summary.optJSONArray("unconfirmed").strings().toSet(),
            conflicts = conflicts,
            calendarConnected = calendar.optBoolean("connected", false),
            lastSyncedAt = calendar.str("lastSyncedAt"),
            lastError = calendar.str("lastError"),
            missingInfoIds = summary.optJSONArray("missingInfo").strings().toSet(),
            twoWaySyncEnabled = calendar.optBoolean("twoWaySyncEnabled", false)
        )
    }

    override suspend fun snapshot(): JobsResult<JobsSnapshot> = call("GET", "/api/jobs", null) { JobsResult.Success(parseSnapshot(it)) }
    override suspend fun detail(id: String): JobsResult<Job> = call("GET", "/api/jobs/$id", null) { JobsResult.Success(parseJob(JSONObject(it))) }
    override suspend fun sync(): JobsResult<String> = call("POST", "/api/jobs", JSONObject().put("action", "sync")) { JobsResult.Success(it) }
    override suspend fun act(id: String, action: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", action)) { JobsResult.Success(it) }

    override suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", "correct").put("field", field).put("at", atIso).put("reason", reason)) { JobsResult.Success(it) }

    override suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String> =
        signoff(id, customerName, pngBase64, false, "", "")

    override suspend fun signoff(
        id: String,
        customerName: String,
        pngBase64: String,
        chargesAcknowledged: Boolean,
        damageNotes: String,
        outstandingItems: String
    ): JobsResult<String> = call(
        "POST",
        "/api/jobs/$id",
        JSONObject()
            .put("action", "signoff")
            .put("outcome", "signed")
            .put("customerName", customerName)
            .put("signaturePngBase64", pngBase64)
            .put("chargesAcknowledged", chargesAcknowledged)
            .put("damageNotes", damageNotes)
            .put("outstandingItems", outstandingItems)
    ) { JobsResult.Success(it) }

    override suspend fun skipSignoff(id: String, reason: String): JobsResult<String> =
        skipSignoff(id, reason, "", "")

    override suspend fun skipSignoff(
        id: String,
        reason: String,
        damageNotes: String,
        outstandingItems: String
    ): JobsResult<String> = call(
        "POST",
        "/api/jobs/$id",
        JSONObject()
            .put("action", "signoff")
            .put("outcome", "skipped")
            .put("reason", reason)
            .put("damageNotes", damageNotes)
            .put("outstandingItems", outstandingItems)
    ) { JobsResult.Success(it) }

    override suspend fun invoiceDraft(id: String): JobsResult<JSONObject> =
        call("GET", "/api/jobs/$id/invoice-draft", null) { JobsResult.Success(JSONObject(it)) }

    override suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>> = call("GET", "/api/jobs/notifications", null) { text ->
        val prefs = JSONObject(text).optJSONObject("preferences") ?: JSONObject()
        JobsResult.Success(NotificationCategory.entries.associateWith { prefs.optBoolean(it.serverKey, true) })
    }

    override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String> {
        val body = JSONObject().put("action", "preferences").put("preferences", JSONObject().apply { prefs.forEach { (k, v) -> put(k.serverKey, v) } })
        return call("POST", "/api/jobs/notifications", body) { JobsResult.Success(it) }
    }

    override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?): JobsResult<String> {
        val data = JSONObject(input.toString())
        if (newCustomer != null) data.put("newCustomer", newCustomer)
        if (!data.has("customerId")) data.put("customerId", "")
        return call("POST", "/api/manage", JSONObject().put("action", "save").put("id", "").put("data", data)) { JobsResult.Success(it) }
    }

    override suspend fun assignJob(
        id: String,
        staffAssignments: List<Triple<String?, String, String>>,
        vehicleId: String?,
        truck: String?,
        travelNotes: String,
        dispatchNotes: String,
        overrideReason: String?
    ): JobsResult<String> {
        val arr = JSONArray()
        staffAssignments.forEach { (staffId, name, role) ->
            arr.put(JSONObject().put("staffId", staffId ?: JSONObject.NULL).put("assignee", name).put("role", role))
        }
        val body = JSONObject()
            .put("action", "assign")
            .put("assignments", arr)
            .put("vehicleId", vehicleId ?: JSONObject.NULL)
            .put("truck", truck ?: JSONObject.NULL)
            .put("travelNotes", travelNotes)
            .put("dispatchNotes", dispatchNotes)
        if (overrideReason != null) body.put("override", JSONObject().put("reason", overrideReason))
        return call("POST", "/api/jobs/$id", body) { JobsResult.Success(it) }
    }

    override suspend fun saveStops(id: String, stops: List<StopDraft>): JobsResult<String> {
        val arr = JSONArray()
        stops.forEach { s ->
            val o = JSONObject()
                .put("kind", s.kind.server)
                .put("address", s.address.trim())
                .put("contactName", s.contactName.trim())
                .put("contactPhone", s.contactPhone.trim())
                .put("notes", s.notes.trim())
            if (s.id != null) o.put("id", s.id)
            arr.put(o)
        }
        return call("POST", "/api/jobs/$id", JSONObject().put("action", "save_stops").put("stops", arr)) { JobsResult.Success(it) }
    }

    override suspend fun stopEvent(id: String, stopId: String, event: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", "stop_$event").put("stopId", stopId)) { JobsResult.Success(it) }

    override suspend fun setPriority(id: String, priority: JobPriority): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", "set_priority").put("priority", priority.server)) { JobsResult.Success(it) }

    override suspend fun cancelBooking(id: String, reason: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", "cancel").put("reason", reason)) { JobsResult.Success(it) }

    override suspend fun rescheduleJob(id: String, startIso: String, endIso: String, reason: String, overrideReason: String?): JobsResult<String> {
        val body = JSONObject().put("action", "reschedule").put("start", startIso).put("end", endIso).put("reason", reason)
        if (overrideReason != null) body.put("override", JSONObject().put("reason", overrideReason))
        return call("POST", "/api/jobs/$id", body) { JobsResult.Success(it) }
    }

    override suspend fun recordCrewEvent(
        id: String,
        memberName: String,
        eventType: String,
        customerAcknowledged: Boolean,
        acknowledgmentName: String,
        notes: String,
        at: Instant?
    ): JobsResult<String> = call(
        "POST",
        "/api/jobs/$id",
        JSONObject()
            .put("action", "crew_event")
            .put("memberName", memberName)
            .put("eventType", eventType)
            .put("customerAcknowledged", customerAcknowledged)
            .put("acknowledgmentName", acknowledgmentName)
            .put("notes", notes)
            .put("at", at?.toString())
    ) { JobsResult.Success(it) }

    override suspend fun fetchInventory(id: String): JobsResult<JobInventoryState> =
        call("GET", "/api/jobs/$id/inventory", null) { text ->
            val o = JSONObject(text)
            val roomsArr = o.optJSONArray("rooms") ?: JSONArray()
            val rooms = List(roomsArr.length()) { i ->
                val r = roomsArr.getJSONObject(i)
                InventoryRoom(r.getString("id"), r.getString("name"), r.optInt("sort_order", 0), r.optString("notes", ""))
            }
            val itemsArr = o.optJSONArray("items") ?: JSONArray()
            val items = List(itemsArr.length()) { i ->
                val it = itemsArr.getJSONObject(i)
                InventoryItem(
                    id = it.getString("id"),
                    roomId = it.str("room_id"),
                    name = it.getString("name"),
                    category = it.optString("category", "general"),
                    quantity = it.optInt("quantity", 1),
                    dimensions = it.optString("dimensions", ""),
                    isFragile = it.optBoolean("is_fragile", false),
                    isHeavy = it.optBoolean("is_heavy", false),
                    packingMethod = it.optString("packing_method", ""),
                    packingChargeRate = it.str("packing_charge_rate"),
                    barcode = it.optString("barcode", ""),
                    status = InventoryStatus.from(it.optString("status")),
                    conditionBefore = it.optString("condition_before", ""),
                    conditionAfter = it.optString("condition_after", ""),
                    exceptionNote = it.optString("exception_note", "")
                )
            }
            val rulesArr = o.optJSONArray("packingRules") ?: JSONArray()
            val rules = List(rulesArr.length()) { i ->
                val r = rulesArr.getJSONObject(i)
                PackingRule(r.getString("id"), r.getString("name"), r.optString("category", "general"), r.optString("unit_price", "0"), r.optString("tax", "exclusive"))
            }
            JobsResult.Success(JobInventoryState(rooms, items, rules))
        }

    override suspend fun addRoom(id: String, name: String): JobsResult<String> =
        call("POST", "/api/jobs/$id/inventory", JSONObject().put("action", "add_room").put("name", name)) { JobsResult.Success(it) }

    override suspend fun saveInventoryItem(id: String, item: InventoryItem): JobsResult<String> {
        val body = JSONObject()
            .put("action", "save_item")
            .put("id", item.id.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("roomId", item.roomId ?: JSONObject.NULL)
            .put("name", item.name)
            .put("category", item.category)
            .put("quantity", item.quantity)
            .put("dimensions", item.dimensions)
            .put("isFragile", item.isFragile)
            .put("isHeavy", item.isHeavy)
            .put("packingMethod", item.packingMethod)
            .put("packingChargeRate", item.packingChargeRate ?: JSONObject.NULL)
            .put("barcode", item.barcode)
            .put("status", item.status.serverKey)
            .put("conditionBefore", item.conditionBefore)
            .put("conditionAfter", item.conditionAfter)
            .put("exceptionNote", item.exceptionNote)
        return call("POST", "/api/jobs/$id/inventory", body) { JobsResult.Success(it) }
    }

    override suspend fun scanBarcode(id: String, barcode: String, status: InventoryStatus, note: String): JobsResult<String> =
        call(
            "POST",
            "/api/jobs/$id/inventory",
            JSONObject().put("action", "scan").put("barcode", barcode).put("status", status.serverKey).put("exceptionNote", note)
        ) { JobsResult.Success(it) }

    override suspend fun saveConditionReport(id: String, report: ConditionReportItem): JobsResult<String> {
        val cl = JSONObject().apply { report.checklist.forEach { (k, v) -> put(k, v) } }
        val body = JSONObject()
            .put("phase", report.phase)
            .put("accessConditions", report.accessConditions)
            .put("existingDamage", report.existingDamage)
            .put("delaysOrIssues", report.delaysOrIssues)
            .put("additionalServices", report.additionalServices)
            .put("checklist", cl)
            .put("customerAcknowledged", report.customerAcknowledged)
            .put("acknowledgmentName", report.acknowledgmentName)
            .put("notes", report.notes)
        return call("POST", "/api/jobs/$id/condition", body) { JobsResult.Success(it) }
    }

    override suspend fun uploadPhoto(
        id: String,
        phase: String,
        caption: String,
        imageBase64: String,
        inventoryItemId: String?,
        idempotencyKey: String?
    ): JobsResult<String> = call(
        "POST",
        "/api/jobs/$id/photos",
        JSONObject()
            .put("phase", phase)
            .put("caption", caption)
            .put("imageBase64", imageBase64)
            .put("inventoryItemId", inventoryItemId ?: JSONObject.NULL)
            .put("idempotencyKey", idempotencyKey ?: JSONObject.NULL)
    ) { JobsResult.Success(it) }

    private suspend fun <T> call(method: String, path: String, body: JSONObject?, parse: (String) -> JobsResult<T>): JobsResult<T> =
        withContext(Dispatchers.IO) {
            val endpoint = BASE + path
            if (!TrustedOriginPolicy.isTrustedOrigin(endpoint)) return@withContext JobsResult.Failure("Untrusted endpoint.")
            var connection: HttpsURLConnection? = null
            try {
                val cookie = CookieManager.getInstance().getCookie(endpoint)
                if (cookie.isNullOrBlank()) return@withContext JobsResult.AuthRequired
                val raw = URL(endpoint).openConnection() as? HttpsURLConnection ?: return@withContext JobsResult.Failure("Network error.")
                connection = raw.apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cookie", cookie)
                    if (body != null) {
                        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Origin", BASE)
                        setFixedLengthStreamingMode(bytes.size)
                        outputStream.use { it.write(bytes) }
                    }
                }
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_UNAUTHORIZED -> JobsResult.AuthRequired
                    code == HttpURLConnection.HTTP_FORBIDDEN && body == null -> {
                        val err = connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                        val msg = runCatching { JSONObject(err.orEmpty()).optString("error") }.getOrNull()?.ifBlank { null }
                        if (msg?.contains("permission", ignoreCase = true) == true) JobsResult.Failure(msg) else JobsResult.AuthRequired
                    }
                    code in 200..299 -> parse(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
                    else -> {
                        val err = connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                        val conflicts = if (code == HttpURLConnection.HTTP_CONFLICT) parseScheduleConflicts(err.orEmpty()) else emptyList()
                        JobsResult.Failure(
                            runCatching { JSONObject(err.orEmpty()).optString("error") }.getOrNull()?.ifBlank { null } ?: "Request failed ($code).",
                            conflicts = conflicts
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                JobsResult.Failure("No connection. Check your internet and try again.", connectionUnavailable = true)
            } catch (_: Exception) {
                JobsResult.Failure("The server returned an unexpected response.")
            } finally {
                connection?.disconnect()
            }
        }
}
