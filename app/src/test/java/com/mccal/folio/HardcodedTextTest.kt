package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only text in strings.xml can be translated. This counts English sentences and labels still written into Kotlin and
 * fails if the count goes up, so new screens start translatable while the old ones are moved over. When you move some,
 * lower [LIMIT] to the new count the failure message prints, so it can't creep back.
 *
 * It's a heuristic: a capitalized, quoted phrase outside logs, keys and patterns. Brand and app names it flags count
 * too; they're few, and moving them keeps every visible word in one place.
 */
class HardcodedTextTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val phrase = Regex("""(?<![\w$])"([A-Z][A-Za-z'’]*(?: [^"\\]*?)?[a-z.!?…)])"""")
    /** Text that starts with a value, like "$count steps left": a template and at least one real word after a space. */
    private val templated = Regex(""""((?:[^"\\$]|\$\{[^{}"]*\}|\$\w+)*\$(?:[^"\\$]|\$\{[^{}"]*\}|\$\w+)*)"""")
    private val template = Regex("""\$\{[^{}"]*\}|\$\w+""")
    private val words = Regex("""\s[a-z]{3,}""")
    private val notText = Regex("""Log\.|TAG|const val|Regex|require\(|error\(|check\(|throw |Exception\(|\.put(Extra|String|Boolean|Int)|getString\(|optString|prefs|key =|Intent\(|action|@Preview|println|\.startsWith|\.equals|when \(|".*" ->|[Tt]race|section\(|json|JSON""")

    private fun found(): List<String> = java.io.File(root, "app/src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
        file.readLines().withIndex().flatMap { (i, line) ->
            val s = line.trim()
            if (s.startsWith("//") || s.startsWith("*") || s.startsWith("/*") || notText.containsMatchIn(line)) emptyList()
            else (phrase.findAll(line).map { it.groupValues[1] }.filter { ' ' in it || it.length >= 4 } +
                templated.findAll(line).map { it.groupValues[1] }.filter { !it.first().isUpperCase() && words.containsMatchIn(it.replace(template, "#")) })
                .map { "${file.name}:${i + 1} $it" }.toList()
        }
    }.toList()

    @Test fun `no new hard-coded text`() {
        val hits = found()
        if (System.getenv("LIST_TEXT") != null) hits.forEach(::println)
        val byFile = hits.groupingBy { it.substringBefore(':') }.eachCount().entries.sortedByDescending { it.value }.take(8)
        assertTrue("${hits.size} hard-coded phrases, limit $LIMIT. Put new text in strings.xml. Most in: $byFile",
            hits.size <= LIMIT)
        if (hits.size < LIMIT) println("HardcodedTextTest: ${hits.size} now; lower LIMIT from $LIMIT to ${hits.size}.")
    }

    private companion object { const val LIMIT = 386 }
}
