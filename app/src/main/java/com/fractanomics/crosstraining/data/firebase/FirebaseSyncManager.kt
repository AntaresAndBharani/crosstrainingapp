package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class SharedBlockPayload(
    val name: String = "",
    val kind: String = "WEIGHTLIFTING",
    val format: String = "",
    val setsCount: Int = 1,
    val targetRepsScheme: String = "",
    val exerciseNamesCsv: String = "",
    val notes: String = ""
)

data class SharedWorkoutPayload(
    val shareCode: String = "",
    val routineName: String = "",
    val description: String = "",
    val defaultFormat: String = "",
    val creatorUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val blocks: List<SharedBlockPayload> = emptyList()
)

object FirebaseSyncManager {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun ensureAuthenticated() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                // If offline or auth failed, ignore gracefully
            }
        }
    }

    suspend fun publishRoutine(
        routine: Routine,
        blocks: List<RoutineBlock>,
        exerciseIdToNameMap: Map<Long, String>
    ): String {
        ensureAuthenticated()
        val code = generateShareCode()

        val blockPayloads = blocks.map { b ->
            val exerciseNames = b.exerciseIdsCsv.split(",")
                .mapNotNull { idStr -> idStr.trim().toLongOrNull() }
                .mapNotNull { id -> exerciseIdToNameMap[id] }
                .joinToString(",")

            SharedBlockPayload(
                name = b.name,
                kind = b.kind.name,
                format = b.format,
                setsCount = b.setsCount,
                targetRepsScheme = b.targetRepsScheme,
                exerciseNamesCsv = exerciseNames,
                notes = b.notes
            )
        }

        val payload = SharedWorkoutPayload(
            shareCode = code,
            routineName = routine.name,
            description = routine.description,
            defaultFormat = routine.defaultFormat,
            creatorUserId = currentUserId,
            createdAt = System.currentTimeMillis(),
            blocks = blockPayloads
        )

        try {
            firestore.collection("shared_workouts")
                .document(code)
                .set(payload)
                .await()
        } catch (e: Exception) {
            // Log or propagate exception
        }

        return code
    }

    suspend fun getSharedWorkout(shareCode: String): SharedWorkoutPayload? {
        ensureAuthenticated()
        val cleanCode = shareCode.trim().uppercase(Locale.ROOT)
        return try {
            val doc = firestore.collection("shared_workouts")
                .document(cleanCode)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(SharedWorkoutPayload::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchCommunityWorkouts(limit: Long = 20): List<SharedWorkoutPayload> {
        ensureAuthenticated()
        return try {
            val snapshot = firestore.collection("shared_workouts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            snapshot.toObjects(SharedWorkoutPayload::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
}
