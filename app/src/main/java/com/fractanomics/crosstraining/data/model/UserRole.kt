package com.fractanomics.crosstraining.data.model

/**
 * Defines the active application role and workflow perspective.
 *
 * - [ATHLETE]: Focuses on daily workout execution, logging sessions, timers, personal PRs, and history.
 * - [COACH]: Focuses on programming, creating training cycles & periodization goals, building routine templates, and analyzing progression.
 */
enum class UserRole(
    val title: String,
    val subtitle: String,
    val emoji: String
) {
    ATHLETE(
        title = "Athlete Mode",
        subtitle = "Daily workout logging, history & PR tracking",
        emoji = "🏃"
    ),
    COACH(
        title = "Coach Mode",
        subtitle = "Cycle programming, routine builder & analytics",
        emoji = "🏋️"
    );

    val isCoach: Boolean get() = this == COACH
    val isAthlete: Boolean get() = this == ATHLETE
}
