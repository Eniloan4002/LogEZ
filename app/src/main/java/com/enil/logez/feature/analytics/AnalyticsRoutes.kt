package com.enil.logez.feature.analytics

/** Route strings for the Profile tab's analytics sub-screens (PHASE2_PLAN.md §4, §5.2). */
object AnalyticsRoutes {
    const val DASHBOARD = "analytics_dashboard?focus={focus}"
    const val MONTHLY_REPORT = "monthly_report"

    /** [focus] pre-selects a training-chart metric — the Profile quick-chart tap-through (§5.2). */
    fun dashboard(focus: String? = null) = "analytics_dashboard" + (focus?.let { "?focus=$it" } ?: "")
}
