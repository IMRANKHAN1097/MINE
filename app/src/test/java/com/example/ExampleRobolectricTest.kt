package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.GpsData
import com.example.model.SafetyStatus
import com.example.model.SensorData
import com.example.model.StatusData
import com.example.model.TelemetryData
import com.example.safety.AiSafetyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SKIMMS", appName)
    }

    @Test
    fun `ai engine identifies safe baseline`() {
        val engine = AiSafetyEngine()
        val data = TelemetryData(
            board_id = "TEST_NODE",
            sensors = SensorData(mq4 = 380, mq135 = 260, accel = 9.8, ir = 48000),
            status = StatusData()
        )
        val insight = engine.evaluate(data)
        assertEquals(SafetyStatus.SAFE, insight.status)
        assertTrue(insight.riskScore < 30)
    }

    @Test
    fun `ai engine debounce requires 3 consecutive samples for danger`() {
        val engine = AiSafetyEngine()
        val hazardData = TelemetryData(
            board_id = "TEST_DEBOUNCE",
            sensors = SensorData(mq4 = 2300, mq135 = 300, accel = 9.8, ir = 48000),
            status = StatusData(env_hazard = true)
        )

        // Sample 1: Should be CAUTION confirming reading
        val s1 = engine.evaluate(hazardData)
        assertEquals(SafetyStatus.CAUTION, s1.status)
        assertEquals("Confirming reading (1/3)", s1.debounceState)

        // Sample 2: Still CAUTION confirming reading
        val s2 = engine.evaluate(hazardData)
        assertEquals(SafetyStatus.CAUTION, s2.status)
        assertEquals("Confirming reading (2/3)", s2.debounceState)

        // Sample 3: Debounce satisfied -> DANGER
        val s3 = engine.evaluate(hazardData)
        assertEquals(SafetyStatus.DANGER, s3.status)
    }

    @Test
    fun `ai engine triggers immediate critical on dual hazard`() {
        val engine = AiSafetyEngine()
        val dualData = TelemetryData(
            board_id = "TEST_DUAL",
            sensors = SensorData(mq4 = 2400, mq135 = 2100, accel = 26.0, ir = 40000),
            status = StatusData(health_hazard = true, env_hazard = true)
        )
        // Dual hazard bypasses debounce
        val insight = engine.evaluate(dualData)
        assertEquals(SafetyStatus.CRITICAL, insight.status)
        assertTrue(insight.riskScore >= 95)
    }

    @Test
    fun `ai engine detects signal loss after 15 seconds`() {
        val engine = AiSafetyEngine()
        val data = TelemetryData(board_id = "TEST_LOSS")
        engine.evaluate(data, nowMs = 1000L)

        // After 16 seconds (17000ms):
        val lossInsight = engine.checkSignalLoss("TEST_LOSS", nowMs = 17000L)
        assertNotNull(lossInsight)
        assertEquals(SafetyStatus.DANGER, lossInsight?.status)
        assertTrue(lossInsight?.signalLost == true)
    }
}
