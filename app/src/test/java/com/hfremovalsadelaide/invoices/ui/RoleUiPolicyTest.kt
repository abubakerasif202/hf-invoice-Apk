package com.hfremovalsadelaide.invoices.ui

import com.hfremovalsadelaide.invoices.auth.SessionUser
import com.hfremovalsadelaide.invoices.auth.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleUiPolicyTest {

    private fun user(role: UserRole, perms: Set<String>) = SessionUser(
        id = "u1",
        name = "Test",
        email = "test@hfremovalsadelaide.com.au",
        role = role,
        isLegacyAdmin = false,
        permissions = perms
    )

    @Test
    fun `owner and admin see all five primary tabs and can dispatch and invoice`() {
        val owner = user(UserRole.OWNER, setOf("invoices.read", "invoices.manage", "crm.read", "jobs.assign"))
        assertEquals(
            listOf(AppDestination.DASHBOARD, AppDestination.JOBS, AppDestination.INVOICES, AppDestination.CUSTOMERS, AppDestination.MORE),
            RoleUiPolicy.visibleDestinations(owner)
        )
        assertTrue(RoleUiPolicy.canDispatch(owner))
        assertTrue(RoleUiPolicy.canWriteInvoices(owner))
    }

    @Test
    fun `dispatcher sees dispatch and crm without restricted invoice management`() {
        val dispatcher = user(UserRole.DISPATCHER, setOf("crm.read", "jobs.assign", "fleet.read"))
        assertEquals(
            listOf(AppDestination.DASHBOARD, AppDestination.JOBS, AppDestination.CUSTOMERS, AppDestination.MORE),
            RoleUiPolicy.visibleDestinations(dispatcher)
        )
        assertTrue(RoleUiPolicy.canDispatch(dispatcher))
        assertFalse(RoleUiPolicy.canWriteInvoices(dispatcher))
    }

    @Test
    fun `driver mover and subcontractor do not see invoices or customer financials`() {
        listOf(UserRole.DRIVER, UserRole.MOVER, UserRole.SUBCONTRACTOR).forEach { role ->
            val worker = user(role, setOf("jobs.read", "jobs.act", "inventory.write"))
            assertEquals(
                listOf(AppDestination.DASHBOARD, AppDestination.JOBS, AppDestination.MORE),
                RoleUiPolicy.visibleDestinations(worker)
            )
            assertFalse(RoleUiPolicy.canDispatch(worker))
            assertFalse(RoleUiPolicy.canWriteInvoices(worker))
        }
    }

    @Test
    fun `unauthenticated user defaults strictly to non-financial shell tabs`() {
        assertEquals(
            listOf(AppDestination.DASHBOARD, AppDestination.JOBS, AppDestination.MORE),
            RoleUiPolicy.visibleDestinations(null)
        )
    }
}
