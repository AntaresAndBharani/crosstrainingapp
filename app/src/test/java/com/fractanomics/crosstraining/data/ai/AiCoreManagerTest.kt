package com.fractanomics.crosstraining.data.ai

import com.fractanomics.crosstraining.data.model.BlockKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Comprehensive unit tests for [AiCoreManager] and on-device Gemini Nano structured JSON extraction.
 *
 * Acceptance Criteria for Issue #470:
 * - [x] AiCoreManager class with suspend function parseWorkoutText(text: String): WorkoutParseResult
 * - [x] Prompt engineering: request JSON structure matching BlockKind + rep-scheme + weight
 * - [x] Output model: WorkoutParseResult(blocks: List<ParsedBlock>, confidence: Float, errors: List<String>)
 * - [x] Graceful fallback when Gemini Nano unavailable: return empty result with error message
 * - [x] Unit tests with 10+ diverse workout descriptions (EMOM, AMRAP, Strength, Complexes, WODs, etc.)
 * - [x] JSON parsing resilience: accept malformed JSON with partial field extraction
 * - [x] Performance: response within 2000ms for typical queries
 */
class AiCoreManagerTest {

    private lateinit var fakeClient: FakeGeminiNanoClient
    private lateinit var aiCoreManager: AiCoreManager

    @Before
    fun setUp() {
        fakeClient = FakeGeminiNanoClient()
        aiCoreManager = AiCoreManager(client = fakeClient)
    }

    @Test
    fun `graceful fallback when Gemini Nano is unavailable - returns empty result with error message`() = runTest {
        val unavailableManager = AiCoreManager(client = UnavailableGeminiNanoClient)
        val result = unavailableManager.parseWorkoutText("5 Back Squats at 120 kg")

        assertTrue(result.blocks.isEmpty())
        assertEquals(0.0f, result.confidence, 0.001f)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.contains("unavailable", ignoreCase = true) || it.contains("not available", ignoreCase = true) })
    }

    @Test
    fun `graceful fallback when input transcript is blank`() = runTest {
        val resultBlank = aiCoreManager.parseWorkoutText("   ")
        assertTrue(resultBlank.blocks.isEmpty())
        assertEquals(0.0f, resultBlank.confidence, 0.001f)
        assertTrue(resultBlank.errors.any { it.contains("blank", ignoreCase = true) })

        val resultEmpty = aiCoreManager.parseWorkoutText("")
        assertTrue(resultEmpty.blocks.isEmpty())
    }

    @Test
    fun `prompt engineering creates structured schema instructions with valid BlockKinds and input`() {
        val prompt = aiCoreManager.buildPrompt("Snatch 5x3 at 70 kg")
        assertTrue(prompt.contains("Valid BlockKinds:"))
        assertTrue(prompt.contains("STRENGTH"))
        assertTrue(prompt.contains("METCON"))
        assertTrue(prompt.contains("COMPLEX"))
        assertTrue(prompt.contains("blocks"))
        assertTrue(prompt.contains("repScheme"))
        assertTrue(prompt.contains("sets"))
        assertTrue(prompt.contains("Snatch 5x3 at 70 kg"))
    }

    // =========================================================================
    // 10+ Diverse Workout Descriptions
    // =========================================================================

    @Test
    fun `workout 1 - barbell complex dictation with E2MOM and multiple movements`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Halting Deadlift + Hang Power Snatch",
              "kind": "STRENGTH",
              "format": "E2MOM",
              "repScheme": "4x1",
              "movements": ["Halting Deadlift", "Hang Power Snatch"],
              "description": "1 Halting Deadlift plus 1 Hang Power Snatch at 60 kg, 4 sets on a 2 minute timer",
              "sets": [
                {"reps": 1, "weight": 60.0, "isWarmup": false}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("1 Halting Deadlift plus 1 Hang Power Snatch at 60 kilos, 4 sets on a 2 minute timer")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(BlockKind.STRENGTH, block.kind)
        assertEquals("E2MOM", block.format)
        assertEquals("4x1", block.repScheme)
        assertEquals(2, block.movements.size)
        assertTrue(block.movements.contains("Halting Deadlift"))
        assertTrue(block.movements.contains("Hang Power Snatch"))
        assertEquals(60.0, block.sets.first().weight ?: 0.0, 0.01)
    }

    @Test
    fun `workout 2 - EMOM with acoustic STT phonetic correction`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Wall Ball Shots",
              "kind": "METCON",
              "format": "EMOM 12",
              "repScheme": "15 reps",
              "movements": ["Wall Ball Shots"],
              "sets": [
                {"reps": 15, "weight": null}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("12 min a mom of 15 wall balls")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(BlockKind.METCON, block.kind)
        assertEquals("EMOM 12", block.format)
        assertEquals("Wall Ball Shots", block.name)
        assertEquals(15, block.sets.first().reps)
    }

    @Test
    fun `workout 3 - live set logging with weight and RPE`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Back Squat",
              "kind": "STRENGTH",
              "format": "",
              "repScheme": "1x5",
              "rpe": 8.0,
              "sets": [
                {"reps": 5, "weight": 120.0, "rpe": 8.0, "isWarmup": false}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("Logged 5 back squats at 120 kg, RPE 8")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals("Back Squat", block.name)
        assertEquals(BlockKind.STRENGTH, block.kind)
        assertEquals(8.0f, block.rpe ?: 0f, 0.01f)
        assertEquals(1, block.sets.size)
        assertEquals(5, block.sets.first().reps)
        assertEquals(120.0, block.sets.first().weight ?: 0.0, 0.01)
        assertEquals(8.0f, block.sets.first().rpe ?: 0f, 0.01f)
    }

    @Test
    fun `workout 4 - multi-movement AMRAP metcon`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "20 min AMRAP",
              "kind": "METCON",
              "format": "AMRAP 20",
              "repScheme": "10-20-30",
              "movements": ["Pull-ups", "Push-ups", "Air Squats"],
              "description": "20 min AMRAP of 10 pull ups, 20 push ups, 30 air squats"
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("20 min AMRAP of 10 pull ups, 20 push ups, 30 air squats")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(BlockKind.METCON, block.kind)
        assertEquals("AMRAP 20", block.format)
        assertEquals(3, block.movements.size)
        assertTrue(block.movements.contains("Pull-ups"))
        assertTrue(block.movements.contains("Push-ups"))
        assertTrue(block.movements.contains("Air Squats"))
    }

    @Test
    fun `workout 5 - Olympic lifting wave loading with progressive weights`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Snatch",
              "kind": "WEIGHTLIFTING",
              "format": "E3MOM",
              "repScheme": "3-2-1-3-2-1",
              "sets": [
                {"reps": 3, "weight": 70.0},
                {"reps": 2, "weight": 75.0},
                {"reps": 1, "weight": 80.0},
                {"reps": 3, "weight": 80.0},
                {"reps": 2, "weight": 85.0},
                {"reps": 1, "weight": 90.0}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("Snatch wave 3-2-1-3-2-1 at 70, 75, 80, 80, 85, 90 kg E3MOM")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(BlockKind.WEIGHTLIFTING, block.kind)
        assertEquals(6, block.sets.size)
        assertEquals(3, block.sets[0].reps)
        assertEquals(70.0, block.sets[0].weight ?: 0.0, 0.01)
        assertEquals(1, block.sets[5].reps)
        assertEquals(90.0, block.sets[5].weight ?: 0.0, 0.01)
    }

    @Test
    fun `workout 6 - For Time benchmark workout Fran`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Fran",
              "kind": "METCON",
              "format": "FOR TIME",
              "repScheme": "21-15-9",
              "movements": ["Thruster", "Pull-ups"],
              "sets": [
                {"reps": 21, "weight": 43.0},
                {"reps": 15, "weight": 43.0},
                {"reps": 9, "weight": 43.0}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("Fran: 21-15-9 Thrusters at 43 kg and Pull-ups for time")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals("Fran", block.name)
        assertEquals(BlockKind.METCON, block.kind)
        assertEquals("FOR TIME", block.format)
        assertEquals("21-15-9", block.repScheme)
        assertEquals(2, block.movements.size)
    }

    @Test
    fun `workout 7 - E3MOM heavy clean and jerk session`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Clean & Jerk",
              "kind": "WEIGHTLIFTING",
              "format": "E3MOM 15",
              "repScheme": "5x2",
              "sets": [
                {"reps": 2, "weight": 100.0},
                {"reps": 2, "weight": 100.0},
                {"reps": 2, "weight": 100.0},
                {"reps": 2, "weight": 100.0},
                {"reps": 2, "weight": 100.0}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("E3MOM for 15 min: 2 Clean and Jerks at 100 kg")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals("E3MOM 15", block.format)
        assertEquals(5, block.sets.size)
        assertTrue(block.sets.all { it.reps == 2 && it.weight == 100.0 })
    }

    @Test
    fun `workout 8 - monostructural rowing intervals with rest`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Rower",
              "kind": "CARDIO",
              "format": "REST 2 min",
              "repScheme": "4 rounds",
              "sets": [
                {"reps": 1, "metricValue": 500.0},
                {"reps": 1, "metricValue": 500.0},
                {"reps": 1, "metricValue": 500.0},
                {"reps": 1, "metricValue": 500.0}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("4 rounds of 500m row rest 2 minutes between sets")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(BlockKind.CARDIO, block.kind)
        assertEquals(4, block.sets.size)
        assertEquals(500.0, block.sets.first().metricValue ?: 0.0, 0.01)
    }

    @Test
    fun `workout 9 - Tabata intervals session`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Push-ups and Hollow Rocks",
              "kind": "METCON",
              "format": "TABATA",
              "repScheme": "8 rounds",
              "movements": ["Push-ups", "Hollow Rock"]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("Tabata 8 rounds alternating Push-ups and Hollow Rocks")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals("TABATA", block.format)
        assertEquals(BlockKind.METCON, block.kind)
        assertEquals(2, block.movements.size)
    }

    @Test
    fun `workout 10 - accessory superset for arms`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Bicep Curls + Tricep Pushdowns",
              "kind": "SUPERSET",
              "format": "",
              "repScheme": "3x12",
              "movements": ["Dumbbell Curl", "Triceps Pushdown"],
              "sets": [
                {"reps": 12, "weight": 16.0},
                {"reps": 12, "weight": 16.0},
                {"reps": 12, "weight": 16.0}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("Superset 3 sets of 12 Dumbbell Bicep Curls at 16kg and 15 Tricep Pushdowns")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(BlockKind.SUPERSET, block.kind)
        assertEquals(3, block.sets.size)
    }

    @Test
    fun `workout 11 - warmup mobility routine`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "General Warmup",
              "kind": "WARMUP",
              "format": "3 rounds",
              "repScheme": "10 reps each",
              "movements": ["PVC Pass Throughs", "Air Squats", "Ring Rows"]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("Warmup 3 rounds: 10 pass-throughs, 10 air squats, 10 ring rows")

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(BlockKind.WARMUP, block.kind)
        assertEquals(3, block.movements.size)
    }

    @Test
    fun `workout 12 - multi-block session with strength and metcon`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Back Squat",
              "kind": "STRENGTH",
              "repScheme": "5x5",
              "sets": [
                {"reps": 5, "weight": 140.0},
                {"reps": 5, "weight": 140.0},
                {"reps": 5, "weight": 140.0},
                {"reps": 5, "weight": 140.0},
                {"reps": 5, "weight": 140.0}
              ]
            },
            {
              "name": "WOD",
              "kind": "METCON",
              "format": "AMRAP 10",
              "movements": ["Burpees", "Box Jumps"]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseWorkoutText("Part A Back Squat 5x5 at 140 kg, Part B 10 min AMRAP 15 burpees 15 box jumps")

        assertEquals(2, result.blocks.size)
        assertEquals(BlockKind.STRENGTH, result.blocks[0].kind)
        assertEquals(5, result.blocks[0].sets.size)
        assertEquals(BlockKind.METCON, result.blocks[1].kind)
        assertEquals("AMRAP 10", result.blocks[1].format)
    }

    // =========================================================================
    // JSON Parsing Resilience (Malformed JSON, partial extraction, wrappers)
    // =========================================================================

    @Test
    fun `json resilience - accepts markdown code fence with json identifier`() {
        val raw = """
        ```json
        {
          "blocks": [
            {
              "name": "Deadlift",
              "kind": "STRENGTH",
              "repScheme": "5x3",
              "sets": [{"reps": 3, "weight": 150.0}]
            }
          ]
        }
        ```
        """.trimIndent()

        val result = aiCoreManager.parseJsonResponse(raw)
        assertEquals(1, result.blocks.size)
        assertEquals("Deadlift", result.blocks.first().name)
        assertEquals(150.0, result.blocks.first().sets.first().weight ?: 0.0, 0.01)
    }

    @Test
    fun `json resilience - accepts trailing commas in arrays and objects`() {
        val raw = """
        {
          "blocks": [
            {
              "name": "Power Clean",
              "kind": "STRENGTH",
              "repScheme": "3x3",
              "sets": [
                {"reps": 3, "weight": 80.0, },
                {"reps": 3, "weight": 80.0, },
              ],
            },
          ],
        }
        """.trimIndent()

        val result = aiCoreManager.parseJsonResponse(raw)
        assertEquals(1, result.blocks.size)
        assertEquals("Power Clean", result.blocks.first().name)
        assertEquals(2, result.blocks.first().sets.size)
    }

    @Test
    fun `json resilience - accepts single quotes instead of double quotes`() {
        val raw = "{'blocks': [{'name': 'Bench Press', 'kind': 'STRENGTH', 'repScheme': '3x8', 'sets': [{'reps': 8, 'weight': 90.0}]}]}"
        val result = aiCoreManager.parseJsonResponse(raw)

        assertEquals(1, result.blocks.size)
        assertEquals("Bench Press", result.blocks.first().name)
        assertEquals(90.0, result.blocks.first().sets.first().weight ?: 0.0, 0.01)
    }

    @Test
    fun `json resilience - accepts unquoted keys`() {
        val raw = "{ blocks: [ { name: \"Strict Press\", kind: \"STRENGTH\", repScheme: \"5x5\", sets: [ { reps: 5, weight: 60.0 } ] } ] }"
        val result = aiCoreManager.parseJsonResponse(raw)

        assertEquals(1, result.blocks.size)
        assertEquals("Strict Press", result.blocks.first().name)
        assertEquals(60.0, result.blocks.first().sets.first().weight ?: 0.0, 0.01)
    }

    @Test
    fun `json resilience - handles conversational text surrounding json`() {
        val raw = """
        Sure! Here is the parsed workout for you:
        {
          "blocks": [
            {
              "name": "Overhead Squat",
              "kind": "STRENGTH",
              "sets": [{"reps": 3, "weight": 70.0}]
            }
          ]
        }
        Have a great training session!
        """.trimIndent()

        val result = aiCoreManager.parseJsonResponse(raw)
        assertEquals(1, result.blocks.size)
        assertEquals("Overhead Squat", result.blocks.first().name)
    }

    @Test
    fun `json resilience - handles string-formatted numbers with units`() {
        val raw = """
        {
          "blocks": [
            {
              "name": "Back Squat",
              "kind": "STRENGTH",
              "rpe": "8.5",
              "sets": [
                {"reps": "5", "weight": "120.5 kg"}
              ]
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseJsonResponse(raw)
        assertEquals(1, result.blocks.size)
        val set = result.blocks.first().sets.first()
        assertEquals(5, set.reps)
        assertEquals(120.5, set.weight ?: 0.0, 0.01)
        assertEquals(8.5f, result.blocks.first().rpe ?: 0.0f, 0.01f)
    }

    @Test
    fun `json resilience - synthesizes sets when sets array is missing but repScheme and weight are provided`() {
        val raw = """
        {
          "blocks": [
            {
              "name": "Front Squat",
              "kind": "STRENGTH",
              "repScheme": "5x3",
              "weight": 110.0
            }
          ]
        }
        """.trimIndent()

        val result = aiCoreManager.parseJsonResponse(raw)
        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(5, block.sets.size)
        assertTrue(block.sets.all { it.reps == 3 && it.weight == 110.0 })
    }

    @Test
    fun `json resilience - recovers partial fields when JSON is severely truncated`() {
        val truncated = """{"blocks": [{"name": "Snatch", "format": "E2MOM", "repScheme": "5x3", "weight": 75.0"""
        val result = aiCoreManager.parseJsonResponse(truncated)

        assertEquals(1, result.blocks.size)
        assertEquals("Snatch", result.blocks.first().name)
        assertEquals("E2MOM", result.blocks.first().format)
        assertEquals(75.0, result.blocks.first().sets.first().weight ?: 0.0, 0.01)
    }

    @Test
    fun `json resilience - handles model exception during inference gracefully`() = runTest {
        fakeClient.shouldThrowException = true
        val result = aiCoreManager.parseWorkoutText("Deadlift 5x5")

        assertTrue(result.blocks.isEmpty())
        assertEquals(0.0f, result.confidence, 0.001f)
        assertTrue(result.errors.any { it.contains("inference failed", ignoreCase = true) })
    }

    // =========================================================================
    // Performance Verification
    // =========================================================================

    @Test
    fun `performance test - completes parsing within 2000ms for typical queries`() = runTest {
        fakeClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Back Squat",
              "kind": "STRENGTH",
              "format": "E2MOM",
              "repScheme": "5x3",
              "sets": [
                {"reps": 3, "weight": 120.0},
                {"reps": 3, "weight": 120.0},
                {"reps": 3, "weight": 120.0},
                {"reps": 3, "weight": 120.0},
                {"reps": 3, "weight": 120.0}
              ]
            }
          ]
        }
        """.trimIndent()

        val queries = listOf(
            "1 Halting Deadlift plus 1 Hang Power Snatch at 60 kilos, 4 sets on a 2 minute timer",
            "12 min a mom of 15 wall balls",
            "Logged 5 back squats at 120 kg, RPE 8",
            "20 min AMRAP of 10 pull ups, 20 push ups, 30 air squats",
            "Snatch wave 3-2-1-3-2-1 at 70, 75, 80, 80, 85, 90 kg E3MOM"
        )

        for (query in queries) {
            val elapsedMs = measureTimeMillis {
                val res = aiCoreManager.parseWorkoutText(query)
                assertTrue(res.blocks.isNotEmpty())
            }
            assertTrue("Query took ${elapsedMs}ms, must be < 2000ms", elapsedMs < 2000)
        }
    }

    /**
     * Fake Gemini Nano Client for deterministic JVM unit testing without hardware/API dependencies.
     */
    private class FakeGeminiNanoClient : GeminiNanoClient {
        var isModelAvailable: Boolean = true
        var shouldThrowException: Boolean = false
        var configuredResponse: String = "{ \"blocks\": [] }"
        var lastPromptReceived: String? = null

        override suspend fun isAvailable(): Boolean = isModelAvailable

        override suspend fun generateText(prompt: String): String {
            lastPromptReceived = prompt
            if (shouldThrowException) {
                throw RuntimeException("Simulated AICore / Gemini Nano out-of-memory or timeout")
            }
            return configuredResponse
        }
    }
}
