package com.fractanomics.crosstraining.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying phonetic correction and speech-to-text (STT) artifact mapping
 * in [FitnessSpeechLexicon].
 *
 * Covers Acceptance Criteria for Issue #468:
 * - Hardcoded dictionary entries for common fitness jargon
 * - Phonetic correction function: "12 min a mom of 15 wall balls" -> "EMOM 12 of 15 wall balls"
 * - 15+ common speech artifacts (min, amrap, rpe, exmom, jargon variants)
 * - Zero platform dependencies (pure JVM unit test)
 * - Immutable lexicon entries & deterministic pure functions
 */
class FitnessSpeechLexiconTest {

    private val lexicon = FitnessSpeechLexicon()

    @Test
    fun `acceptance criterion scenario - corrects 12 min a mom of 15 wall balls`() {
        val input = "12 min a mom of 15 wall balls"
        val expected = "EMOM 12 of 15 wall balls"
        val actual = lexicon.correct(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `acceptance criterion companion object - static correct function works`() {
        val input = "12 min a mom of 15 wall balls"
        val expected = "EMOM 12 of 15 wall balls"
        assertEquals(expected, FitnessSpeechLexicon.correct(input))
    }

    @Test
    fun `corrects EMOM STT phonetic variants with minute prefixes`() {
        assertEquals("EMOM 10 of 5 burpees", lexicon.correct("10 minute a mom of 5 burpees"))
        assertEquals("EMOM 15 of 20 kettlebell swings", lexicon.correct("15 min e-mom of 20 kettlebell swings"))
        assertEquals("EMOM 20 of 10 pull ups", lexicon.correct("20 mins ee mom of 10 pull ups"))
        assertEquals("EMOM 8 of 15 thrusters", lexicon.correct("8 min imam of 15 thrusters"))
        assertEquals("EMOM 12 of 30 double unders", lexicon.correct("12 minutes emam of 30 double unders"))
        assertEquals("EMOM 16", lexicon.correct("16 min e mum"))
    }

    @Test
    fun `corrects standalone EMOM phonetic variants`() {
        assertEquals("EMOM", lexicon.correct("a mom"))
        assertEquals("EMOM", lexicon.correct("an mom"))
        assertEquals("EMOM", lexicon.correct("e mom"))
        assertEquals("EMOM", lexicon.correct("e-mom"))
        assertEquals("EMOM", lexicon.correct("ee mom"))
        assertEquals("EMOM", lexicon.correct("imam"))
        assertEquals("EMOM", lexicon.correct("e mum"))
        assertEquals("EMOM 14", lexicon.correct("a mom 14 min"))
        assertEquals("EMOM 10", lexicon.correct("e mom 10 minutes"))
    }

    @Test
    fun `corrects AMRAP STT phonetic variants with time prefixes`() {
        assertEquals("AMRAP 20 of 10 pull ups and 20 push ups", lexicon.correct("20 min am rap of 10 pull ups and 20 push ups"))
        assertEquals("AMRAP 15 of 5 cleans", lexicon.correct("15 min i'm rap of 5 cleans"))
        assertEquals("AMRAP 12 of 15 wall balls", lexicon.correct("12 minute um rap of 15 wall balls"))
        assertEquals("AMRAP 10 of 8 snatches", lexicon.correct("10 min a m r a p of 8 snatches"))
        assertEquals("AMRAP 20", lexicon.correct("20 min as many rounds as possible"))
        assertEquals("AMRAP 15", lexicon.correct("15 minute as many reps as possible"))
    }

    @Test
    fun `corrects standalone AMRAP phonetic variants`() {
        assertEquals("AMRAP", lexicon.correct("am rap"))
        assertEquals("AMRAP", lexicon.correct("am-rap"))
        assertEquals("AMRAP", lexicon.correct("i'm rap"))
        assertEquals("AMRAP", lexicon.correct("um rap"))
        assertEquals("AMRAP", lexicon.correct("a m r a p"))
        assertEquals("AMRAP 20", lexicon.correct("am rap 20 min"))
        assertEquals("AMRAP 15", lexicon.correct("amrap 15"))
    }

    @Test
    fun `corrects RPE STT artifacts`() {
        assertEquals("Back Squat 5 reps at 120 kg RPE 8", lexicon.correct("Back Squat 5 reps at 120 kg r pay 8"))
        assertEquals("Deadlift 3 reps at 150 kg RPE 9", lexicon.correct("Deadlift 3 reps at 150 kg r p e 9"))
        assertEquals("Bench Press RPE 7.5", lexicon.correct("Bench Press r-p-e 7.5"))
        assertEquals("Squat RPE 8.5", lexicon.correct("Squat r.p.e. 8.5"))
        assertEquals("Press RPE 8", lexicon.correct("Press rate of perceived exertion 8"))
        assertEquals("RPE 9", lexicon.correct("rpm 9"))
    }

    @Test
    fun `corrects Every X Minutes on the Minute (EXMOM) variants`() {
        assertEquals("EMOM", lexicon.correct("every minute on the minute"))
        assertEquals("EMOM", lexicon.correct("every min on the min"))
        assertEquals("E2MOM", lexicon.correct("every 2 minutes on the minute"))
        assertEquals("E2MOM", lexicon.correct("every two minutes on the minute"))
        assertEquals("E2MOM", lexicon.correct("every 2 mins on the min"))
        assertEquals("E3MOM", lexicon.correct("every 3 minutes on the minute"))
        assertEquals("E3MOM", lexicon.correct("every three minutes on the minute"))
        assertEquals("E4MOM", lexicon.correct("every 4 minutes on the minute"))
        assertEquals("E5MOM", lexicon.correct("every 5 minutes on the minute"))
        assertEquals("E2MOM", lexicon.correct("e 2 mom"))
        assertEquals("E2MOM", lexicon.correct("e-2-mom"))
        assertEquals("E3MOM", lexicon.correct("e 3 mom"))
        assertEquals("E4MOM", lexicon.correct("e four mom"))
        assertEquals("E5MOM", lexicon.correct("e 5 mom"))
    }

    @Test
    fun `corrects WOD and Metcon artifacts`() {
        assertEquals("WOD", lexicon.correct("w o d"))
        assertEquals("WOD", lexicon.correct("w-o-d"))
        assertEquals("WOD", lexicon.correct("what of the day"))
        assertEquals("WOD", lexicon.correct("workout of the day"))
        assertEquals("METCON", lexicon.correct("met con"))
        assertEquals("METCON", lexicon.correct("met-con"))
        assertEquals("METCON", lexicon.correct("metabolic conditioning"))
    }

    @Test
    fun `corrects common exercise acronyms and movements`() {
        assertEquals("C&J at 80 kg", lexicon.correct("c and j at 80 kilos"))
        assertEquals("C&J", lexicon.correct("c & j"))
        assertEquals("C&J", lexicon.correct("c plus j"))
        assertEquals("OHS 5x3", lexicon.correct("o h s 5x3"))
        assertEquals("T2B 15 reps", lexicon.correct("t to b 15 reps"))
        assertEquals("T2B 20 reps", lexicon.correct("toes 2 bar 20 reps"))
        assertEquals("T2B 10 reps", lexicon.correct("toast to bar 10 reps"))
        assertEquals("C2B 12 reps", lexicon.correct("c to b 12 reps"))
        assertEquals("C2B 15 reps", lexicon.correct("chest 2 bar 15 reps"))
        assertEquals("BMU 5 reps", lexicon.correct("b m u 5 reps"))
        assertEquals("RMU 3 reps", lexicon.correct("r m u 3 reps"))
        assertEquals("HSPU 10 reps", lexicon.correct("h s p u 10 reps"))
        assertEquals("HSPU 15 reps", lexicon.correct("hand stand push up 15 reps"))
        assertEquals("HSW 20m", lexicon.correct("h s w 20m"))
        assertEquals("GHD 25 reps", lexicon.correct("g h d 25 reps"))
        assertEquals("Double Unders 50 reps", lexicon.correct("double-unders 50 reps"))
        assertEquals("Double Unders 50 reps", lexicon.correct("dubs 50 reps"))
        assertEquals("KB Swings 20 reps", lexicon.correct("k b s 20 reps"))
        assertEquals("BJO 10 reps", lexicon.correct("b j o 10 reps"))
    }

    @Test
    fun `corrects complex barbell dictation syntax`() {
        val input = "Strength: 1 Halting Deadlift plus 1 Hang Power Snatch at 60 kilos, 4 sets on a 2 minute timer"
        val expected = "Strength: 1 Halting Deadlift + 1 Hang Power Snatch at 60 kg, 4 sets on a 2 minute timer"
        assertEquals(expected, lexicon.correct(input))
    }

    @Test
    fun `handles units and weight normalization`() {
        assertEquals("Deadlift 5x5 at 140 kg", lexicon.correct("Deadlift 5x5 at 140 kilograms"))
        assertEquals("Back Squat 3x8 at 100 kg", lexicon.correct("Back Squat 3x8 at 100 kgs"))
        assertEquals("Bench 5x5 at 225 lbs", lexicon.correct("Bench 5x5 at 225 pounds"))
    }

    @Test
    fun `lexicon dictionary provides immutable and queryable entries`() {
        val dict = lexicon.canonicalDictionary
        assertTrue("Dictionary must contain 20+ entries", dict.size >= 20)
        assertNotNull(dict["a mom"])
        assertEquals("EMOM", dict["a mom"])
        assertEquals("AMRAP", dict["am rap"])
        assertEquals("RPE", dict["r pay"])
        assertEquals("WOD", dict["w o d"])
    }

    @Test
    fun `lookup function retrieves canonical term`() {
        assertEquals("EMOM", lexicon.lookup("a mom"))
        assertEquals("AMRAP", lexicon.lookup("i'm rap"))
        assertEquals("RPE", lexicon.lookup("r pay"))
        assertEquals("WOD", lexicon.lookup("what of the day"))
        assertEquals(null, lexicon.lookup("unknown term xyz"))
    }

    @Test
    fun `containsArtifact detects presence of speech artifacts`() {
        assertTrue(lexicon.containsArtifact("12 min a mom of 15 wall balls"))
        assertTrue(lexicon.containsArtifact("20 min am rap"))
        assertTrue(lexicon.containsArtifact("r pay 8"))
        assertTrue(!lexicon.containsArtifact("EMOM 12 of 15 Wall Balls"))
    }

    @Test
    fun `supports custom lexicon entries override`() {
        val custom = FitnessSpeechLexicon(
            customMappings = mapOf("my special wod" to "SUPER_WOD")
        )
        assertEquals("SUPER_WOD", custom.lookup("my special wod"))
        assertEquals("Doing SUPER_WOD today", custom.correct("Doing my special wod today"))
    }

    @Test
    fun `handles blank and empty input safely`() {
        assertEquals("", lexicon.correct(""))
        assertEquals("   ", lexicon.correct("   "))
    }

    @Test
    fun `corrects dictionary artifacts for reps, squat, and kg units`() {
        assertEquals("Back Squat 5 reps", lexicon.correct("Back Squat 5 wreps"))
        assertEquals("Squat 3 reps at 100 kg", lexicon.correct("Squad 3 wreps at 100 K"))
        assertEquals("Front Squats 5x5", lexicon.correct("Front Squads 5x5"))
    }

    @Test
    fun `phoneticDistance computes standard Levenshtein edit distance`() {
        assertEquals(0, FitnessSpeechLexicon.phoneticDistance("squat", "squat"))
        assertEquals(1, FitnessSpeechLexicon.phoneticDistance("squat", "squad"))
        assertEquals(1, FitnessSpeechLexicon.phoneticDistance("reps", "wreps"))
        assertEquals(3, FitnessSpeechLexicon.phoneticDistance("kitten", "sitting"))
        assertEquals(5, FitnessSpeechLexicon.phoneticDistance("", "kettlebell".take(5)))
    }

    @Test
    fun `rankCandidates ranks fitness vocabulary by phonetic distance ascending`() {
        val results = lexicon.rankCandidates("wreps", topN = 3)
        assertTrue(results.isNotEmpty())
        assertEquals("reps", results.first().term)
        assertEquals(1, results.first().distance)
        assertTrue(results.zipWithNext().all { (a, b) -> a.distance <= b.distance })
    }

    @Test
    fun `rankCandidates surfaces squat as closest match for the squad artifact`() {
        val results = lexicon.rankCandidates("squad", topN = 3)
        assertEquals("Squat", results.first().term)
        assertEquals(1, results.first().distance)
    }

    @Test
    fun `rankCandidates surfaces kg as closest match for the K unit artifact`() {
        val results = lexicon.rankCandidates("K", topN = 3)
        assertEquals("kg", results.first().term)
    }

    @Test
    fun `bestMatch returns null when nothing is within the distance threshold`() {
        assertEquals("reps", lexicon.bestMatch("wreps", maxDistance = 2))
        assertEquals(null, lexicon.bestMatch("xyzxyzxyzxyzxyz", maxDistance = 1))
    }

    @Test
    fun `bestMatch respects a custom maxDistance threshold`() {
        assertEquals(null, lexicon.bestMatch("wreps", maxDistance = 0))
        assertEquals("reps", lexicon.bestMatch("wreps", maxDistance = 1))
    }

    @Test
    fun `offline fitness vocabulary contains at least 500 distinct terms`() {
        val vocabulary = FitnessSpeechLexicon.CANONICAL_FITNESS_TERMS
        assertTrue(
            "Expected at least 500 fitness terms, found ${vocabulary.size}",
            vocabulary.size >= 500
        )
        assertEquals("Vocabulary must contain no duplicates", vocabulary.size, vocabulary.distinct().size)
        assertTrue(vocabulary.contains("Squat"))
        assertTrue(vocabulary.contains("kg"))
        assertTrue(vocabulary.contains("reps"))
    }
}
