package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.health.HealthConnectManager
import com.example.data.model.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read VitalPulse string and verify compact Health Connect prompt context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VitalPulse", appName)

        val hcManager = HealthConnectManager(context)
        val snapshot = hcManager.createPreviewSnapshot(TimeWindow.DAYS_7)
        val compactPrompt = snapshot.formatForGemmaPrompt()

        assertTrue(compactPrompt.contains("Readiness 88/100"))
        assertTrue(compactPrompt.lines().size <= 4)
    }
}
