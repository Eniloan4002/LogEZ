package com.enil.logez.core.domain.model

/** Which body the Muscle body diagram (`core/designsystem/BodyDiagram.kt`) renders -- a global
 * display preference (Settings tree), not owned by any one feature screen, since the diagram is
 * rendered on 4 unrelated screens (Analytics, Profile, Workout recap/Share). */
enum class MuscleDiagramVariant {
    MALE,
    FEMALE,
}
