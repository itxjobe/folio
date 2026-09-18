package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Settings scene draws Folio's real Settings, from `docs/mockups/lab/data/settings.json`. That file is generated
 * from the app, so this checks it still matches: a row added, renamed or moved in Settings has to reach the lab, or
 * the lab stops being a reference and becomes a nice drawing.
 *
 * It skips where the lab isn't checked out, since that folder isn't in git.
 */
class LabSettingsParityTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val data = File(root, "docs/mockups/lab/data/settings.json")
    private val sheet = File(root, "app/src/main/java/com/mccal/folio/CustomizationSheet.kt")
    private val checklist = File(root, "app/src/main/java/com/mccal/folio/SetupChecklist.kt")

    private fun json(): JSONObject {
        assumeTrue("the Mockup Lab isn't on this machine", data.isFile)
        return JSONObject(data.readText())
    }

    /** The Settings rows the app draws, in order, as (title, tag). */
    private fun appRows(): List<Pair<String, String>> {
        val body = sheet.readText().let { it.substring(it.indexOf("fun CustomizationSheet("), it.indexOf("@Composable private fun SwitchRow(")) }
        return Regex("""TweakRow\(Icons\.Rounded\.\w+, 0x[0-9A-Fa-f]{8}, "([^"]+)", "([^"]+)"""")
            .findAll(body).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private fun labRows(): List<Pair<String, String>> {
        val groups = json().getJSONArray("groups")
        return (0 until groups.length()).flatMap { g ->
            val group = groups.getJSONArray(g)
            (0 until group.length()).map { i ->
                group.getJSONObject(i).let { it.getString("title") to it.getString("tag") }
            }
        }
    }

    @Test fun `the lab shows the same Settings rows, in the same order`() {
        val lab = labRows()
        val app = appRows().take(lab.size)
        assertEquals("the lab's rows have drifted from CustomizationSheet.kt", app, lab)
        assertTrue("the lab shows the whole overview", lab.size >= 20)
        // The Market row is the one this release adds; if it ever disappears, the scene is out of date.
        assertTrue("customization-market" in lab.map { it.second })
    }

    @Test fun `the setup steps and permissions are the app's own`() {
        val data = json()
        val steps = data.getJSONArray("setupSteps").titles()
        val appSteps = Regex("""SetupStep\(Icons\.Rounded\.\w+, "([^"]+)"""").findAll(checklist.readText()).map { it.groupValues[1] }.toList()
        assertEquals(appSteps, steps)

        val perms = data.getJSONArray("permissions").titles()
        val appPerms = Regex("""Perm\("([^"]+)"""").findAll(sheet.readText()).map { it.groupValues[1] }.toList()
        assertEquals(appPerms, perms)
    }

    @Test fun `the scene and the app say the same thing about refreshing`() {
        assumeTrue("the Mockup Lab isn't on this machine", data.isFile)
        val scene = File(root, "docs/mockups/lab/scenes/settings.js")
        assumeTrue(scene.isFile)
        val text = scene.readText()
        // Wording that appears in both places, so a change in one shows up as a failure rather than a surprise.
        for (line in listOf("Refresh in the background", "Only on Wi-Fi", "Once a day", "Never uses mobile data")) {
            assertTrue("the scene is missing \"$line\"", line in text)
            assertTrue("Settings is missing \"$line\"", line in sheet.readText())
        }
    }

    private fun JSONArray.titles() = (0 until length()).map { getJSONObject(it).getString("title") }
}
