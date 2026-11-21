package com.example

import com.example.data.model.NotificationType
import com.example.data.model.ThemeMode
import com.example.data.model.TrackingPriority
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingSourceType
import com.example.data.model.TrackingStatus
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTrackerModelTest {

    @Test
    fun `enum deserialization falls back safely on unknown raw values`() {
        assertEquals(UserRole.USER, UserRole.fromRaw("UNKNOWN_ROLE"))
        assertEquals(TrackingStatus.PENDING, TrackingStatus.fromRaw("LEGACY_STATUS"))
        assertEquals(TrackingPriority.MEDIUM, TrackingPriority.fromRaw(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromRaw("SEPIA"))
        assertEquals(TrackingSourceType.MANUAL, TrackingSourceType.fromRaw("OTHER"))
        assertEquals(NotificationType.SYSTEM, NotificationType.fromRaw("BAD"))
    }

    @Test
    fun `terminal status flags identify completed and cancelled states`() {
        assertTrue(TrackingStatus.COMPLETED.isTerminal)
        assertTrue(TrackingStatus.CANCELLED.isTerminal)
        assertFalse(TrackingStatus.PENDING.isTerminal)
        assertFalse(TrackingStatus.IN_PROGRESS.isTerminal)
        assertFalse(TrackingStatus.ON_HOLD.isTerminal)
        assertFalse(TrackingStatus.OVERDUE.isTerminal)
    }

    @Test
    fun `tracking record defaults are safe for Firestore deserialization`() {
        val rec = TrackingRecord()
        assertEquals("", rec.id)
        assertEquals(TrackingStatus.PENDING, rec.parsedStatus)
        assertEquals(TrackingPriority.MEDIUM, rec.parsedPriority)
        assertEquals(0L, rec.progressPercent)
    }
}
