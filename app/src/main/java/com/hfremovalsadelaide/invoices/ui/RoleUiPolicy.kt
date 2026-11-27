package com.hfremovalsadelaide.invoices.ui

import com.hfremovalsadelaide.invoices.auth.SessionUser

/** Pure role-based navigation and action visibility rules matching backend RBAC. */
object RoleUiPolicy {
    fun visibleDestinations(user: SessionUser?): List<AppDestination> {
        if (user == null) {
            return listOf(AppDestination.DASHBOARD, AppDestination.JOBS, AppDestination.MORE)
        }
        return buildList {
            add(AppDestination.DASHBOARD)
            add(AppDestination.JOBS)
            if (user.can("invoices.read")) add(AppDestination.INVOICES)
            if (user.can("crm.read")) add(AppDestination.CUSTOMERS)
            add(AppDestination.MORE)
        }
    }

    fun canDispatch(user: SessionUser?): Boolean = user?.can("jobs.assign") == true
    fun canWriteInvoices(user: SessionUser?): Boolean = user?.can("invoices.manage") == true
    fun canViewPricingAdmin(user: SessionUser?): Boolean =
        user != null && user.can("settings.manage") && user.can("invoices.read")
    fun canApprovePricing(user: SessionUser?): Boolean =
        canViewPricingAdmin(user) && user?.role == com.hfremovalsadelaide.invoices.auth.UserRole.OWNER
}
