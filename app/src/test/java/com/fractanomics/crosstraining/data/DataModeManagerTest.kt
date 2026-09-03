package com.fractanomics.crosstraining.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying in-memory session-scoped DataModeManager behaviour (Issue #482 / Parent #481).
 *
 * Acceptance Criteria (Scenario 1):
 * - Given an athlete or guest launches the application
 * - When the application initializes and navigation mounts
 * - Then DataModeManager initializes with demoMode == false in-memory
 * - And no demoMode preference is read from or written to SharedPreferences
 * - And the active database is crosstraining.db (realRepository)
 */
class DataModeManagerTest {

    @Test
    fun coldStart_initializesWithDemoModeFalseInMemory() {
        val manager = DataModeManager(context = null)
        assertFalse("DataModeManager must default to demoMode == false on launch", manager.demoMode.value)
    }

    @Test
    fun coldStart_ignoresPersistedDemoModePreferenceAndNeverReadsIt() {
        // Given an existing user who previously had demoMode=true stored in SharedPreferences
        val fakePrefs = FakeTrackingSharedPreferences(mapOf("demoMode" to true))

        // When DataModeManager initializes
        val manager = DataModeManager(context = null, sharedPreferences = fakePrefs)

        // Then demoMode is false in-memory
        assertFalse("demoMode must be false in-memory regardless of stored preferences", manager.demoMode.value)
        // And "demoMode" was never read from SharedPreferences
        assertFalse("KEY_DEMO_MODE must not be read from SharedPreferences", fakePrefs.readKeys.contains("demoMode"))
    }

    @Test
    fun setDemoMode_togglesInMemoryStateOnly_andDoesNotWriteToSharedPreferences() = runTest {
        val fakePrefs = FakeTrackingSharedPreferences()
        val manager = DataModeManager(context = null, sharedPreferences = fakePrefs)

        // Initially false
        assertFalse(manager.demoMode.value)

        // When toggling to demo mode
        manager.setDemoMode(true)
        assertTrue("demoMode should be true in-memory", manager.demoMode.value)
        assertFalse("KEY_DEMO_MODE must not be written to SharedPreferences", fakePrefs.writtenKeys.contains("demoMode"))
        assertFalse("Preferences must not contain demoMode key", fakePrefs.contains("demoMode"))

        // When toggling back to real data
        manager.setDemoMode(false)
        assertFalse("demoMode should be false in-memory", manager.demoMode.value)
        assertFalse("KEY_DEMO_MODE must not be written to SharedPreferences", fakePrefs.writtenKeys.contains("demoMode"))
    }

    @Test
    fun realRepository_returnsTestRepositoryWhenProvided() {
        val fakeDb = FakeSampleAppDatabase()
        val fakeRepo = Repository(fakeDb, FakeTransactionRunner(fakeDb))
        val manager = DataModeManager(context = null)

        manager.setRepositoryForTesting(fakeRepo)

        assertEquals("realRepository must return testRepository when set", fakeRepo, manager.realRepository)
        assertEquals("current repository must return realRepository in default state", fakeRepo, manager.current)
    }

    @Test
    fun current_switchesBetweenRealAndDemo_whileRealRepositoryRemainsIsolated() = runTest {
        val fakeDbReal = FakeSampleAppDatabase()
        val fakeDbDemo = FakeSampleAppDatabase()
        val realRepo = Repository(fakeDbReal, FakeTransactionRunner(fakeDbReal))
        val demoRepo = Repository(fakeDbDemo, FakeTransactionRunner(fakeDbDemo))

        val manager = DataModeManager(context = null)
        manager.setRepositoryForTesting(repo = realRepo, demoRepo = demoRepo)

        // Real data active initially
        assertEquals(realRepo, manager.current)
        assertEquals(realRepo, manager.realRepository)
        assertEquals(realRepo, manager.repositoryFlow.first())

        // Toggle to demo mode
        manager.setDemoMode(true)
        assertEquals(demoRepo, manager.current)
        assertEquals(realRepo, manager.realRepository) // realRepository stays isolated!

        // Toggle back to real data
        manager.setDemoMode(false)
        assertEquals(realRepo, manager.current)
        assertEquals(realRepo, manager.realRepository)
    }

    /**
     * In-memory SharedPreferences fake to track read/write operations on preference keys.
     */
    class FakeTrackingSharedPreferences(initialValues: Map<String, Any> = emptyMap()) : SharedPreferences {
        val store = HashMap<String, Any?>(initialValues)
        val readKeys = mutableSetOf<String>()
        val writtenKeys = mutableSetOf<String>()

        override fun getAll(): MutableMap<String, *> = HashMap(store)

        override fun getString(key: String?, defValue: String?): String? {
            if (key != null) readKeys.add(key)
            return (store[key] as? String) ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            if (key != null) readKeys.add(key)
            @Suppress("UNCHECKED_CAST")
            return (store[key] as? MutableSet<String>) ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            if (key != null) readKeys.add(key)
            return (store[key] as? Int) ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            if (key != null) readKeys.add(key)
            return (store[key] as? Long) ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            if (key != null) readKeys.add(key)
            return (store[key] as? Float) ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            if (key != null) readKeys.add(key)
            return (store[key] as? Boolean) ?: defValue
        }

        override fun contains(key: String?): Boolean {
            return store.containsKey(key)
        }

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class FakeEditor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = values
                }
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) {
                    store.remove(key)
                }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                store.clear()
                return this
            }

            override fun commit(): Boolean = true

            override fun apply() {}
        }
    }
}
