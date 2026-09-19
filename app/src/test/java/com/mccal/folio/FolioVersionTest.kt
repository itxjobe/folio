package com.mccal.folio

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The release number in one file has to agree with two others, and for a while it didn't: the app said 0.6.1 while
 * the packages it ships said they need 0.7.0. Nothing broke, because a built-in package is exempt from `minFolio`,
 * which is exactly why nobody noticed. These are the checks that would have.
 */
class FolioVersionTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }

    private val version = Regex("""^val folioVersion = "([^"]+)"""", RegexOption.MULTILINE)
        .find(File(root, "app/build.gradle.kts").readText())?.groupValues?.get(1)

    @Test fun `the build declares a version`() {
        assertNotNull("app/build.gradle.kts should set folioVersion", version)
    }

    @Test fun `the changelog has a section for this version`() {
        // What's New matches the version name exactly, so a build with no section of its own shows nothing.
        val notes = WhatsNew.parse(File(root, "CHANGELOG.md").readText())
        assertTrue(
            "CHANGELOG.md has no section for $version - What's New would have nothing to show after the update",
            notes.any { it.version == version },
        )
    }

    @Test fun `nothing Folio ships needs a newer Folio than this`() {
        val build = requireNotNull(version).substringBefore('-').split('.').map { it.toInt() }
        val packages = File(root, "docs/sdk/source/packages").listFiles().orEmpty().sortedBy { it.name }
        assertTrue("no built-in packages found", packages.isNotEmpty())
        for (folder in packages) {
            val manifest = File(folder, "manifest.json").takeIf { it.isFile } ?: continue
            val needs = Regex(""""minFolio"\s*:\s*"([0-9.]+)"""").find(manifest.readText())
                ?.groupValues?.get(1)?.split('.')?.map { it.toInt() } ?: continue
            assertTrue(
                "${folder.name} needs Folio ${needs.joinToString(".")} but this build is $version",
                compareLists(needs, build) <= 0,
            )
        }
    }

    private fun compareLists(a: List<Int>, b: List<Int>): Int =
        a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: a.size.compareTo(b.size)
}
