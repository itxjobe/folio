package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test fun families() {
        assertEquals(WidgetSize.SMALL, widgetSizeFor(150f, 150f))
        assertEquals(WidgetSize.MEDIUM, widgetSizeFor(320f, 150f))
        assertEquals(WidgetSize.LARGE, widgetSizeFor(320f, 320f))
        assertEquals(WidgetSize.SMALL, widgetSizeFor(0f, 0f))
    }
}
