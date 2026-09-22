package com.enil.logez.fakes

import com.enil.logez.core.domain.WidgetRefresher

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2) — counts refreshes so a trigger can be asserted. */
class FakeWidgetRefresher : WidgetRefresher {
    var refreshCount = 0
        private set

    override suspend fun refresh() {
        refreshCount++
    }
}
