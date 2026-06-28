package com.fractanomics.crosstraining.data.model

import androidx.room.Embedded
import androidx.room.Relation

/** A session together with all of its sets, ordered by [SessionSet.position]. */
data class SessionWithSets(
    @Embedded val session: Session,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SessionSet>
)
