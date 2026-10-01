package com.enil.logez.feature.routines

import android.net.Uri

/** Route strings for the Workout tab's sub-screens (PHASE2_PLAN.md §4). */
object RoutineRoutes {
    const val DETAIL = "routine_detail/{routineId}"
    const val BUILDER = "routine_builder?routineId={routineId}&folderId={folderId}"
    /** P-205 "See all" — the full Recent list; no args, it's a global feed, not folder-scoped. */
    const val RECENT = "recent_workouts"

    /** Recent workout detail (R-1, 2026-10-01): a finished workout opened like a routine, with Start on top. */
    const val RECENT_DETAIL = "recent_workout/{workoutId}"

    fun recentDetail(workoutId: String) = "recent_workout/$workoutId"

    fun detail(routineId: String) = "routine_detail/$routineId"

    fun builder(routineId: String? = null, folderId: String? = null): String {
        val params = buildList {
            routineId?.let { add("routineId=${Uri.encode(it)}") }
            folderId?.let { add("folderId=${Uri.encode(it)}") }
        }
        return "routine_builder" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }
}
