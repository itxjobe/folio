package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoParseTest {
    private val sample = javaClass.classLoader!!.getResource("open-meteo-sample.json")!!.readText()

    @Test fun parsesCurrentHoursAndDays() {
        val w = parseOpenMeteo(sample)
        assertNotNull(w); w!!
        assertEquals(6, w.hours.size)
        assertEquals(6, w.days.size)
        assertTrue(w.highC >= w.lowC)
    }

    @Test fun oldShapeWithoutHourlyStillParses() {
        val w = parseOpenMeteo("""{"current":{"time":"2026-10-10T14:45","temperature_2m":20.5,"weather_code":1},
            "daily":{"temperature_2m_max":[24.0],"temperature_2m_min":[8.0]}}""")
        assertNotNull(w); assertEquals(0, w!!.hours.size); assertEquals(0, w.days.size)
    }
}
