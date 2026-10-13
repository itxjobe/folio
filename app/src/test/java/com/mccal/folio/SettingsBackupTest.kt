package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    @Test fun `settings ride along in a backup and come back out`() {
        val withSettings = withSettings("""{"version":3,"apps":[]}""", """{"schema":10,"island":false,"panelBlur":0.3}""")
        val settings = backupSettings(withSettings)!!
        assertTrue(settings.contains("\"island\":false"))
        assertNull(backupSettings("""{"version":3}"""))
    }

    @Test fun `a saved state file is told apart from a layout backup`() {
        assertTrue(isSavedStateFile("""{"schema":10,"homeSlots":[]}"""))
        assertFalse(isSavedStateFile("""{"version":3,"homeSlots":[]}"""))
        assertFalse(isSavedStateFile("not json"))
    }

    @Test fun `settings land on the current layout, which stays`() {
        val current = LauncherState(dock = listOf("a", null), island = true, haptics = true)
        val saved = LauncherState(dock = listOf("z"), island = false, haptics = false)
        val merged = settingsOnto(saved, current)
        assertEquals(listOf("a", null), merged.dock)
        assertFalse(merged.island)
        assertFalse(merged.haptics)
    }
}

class CcOrderTest {
    @Test fun `a control moves one step and stays put at either end`() {
        assertEquals(listOf("b", "a", "c"), moveItem(listOf("a", "b", "c"), 1, -1))
        assertEquals(listOf("a", "c", "b"), moveItem(listOf("a", "b", "c"), 1, 1))
        assertEquals(listOf("a", "b"), moveItem(listOf("a", "b"), 0, -1))
        assertEquals(listOf("a", "b"), moveItem(listOf("a", "b"), 1, 1))
    }
}
