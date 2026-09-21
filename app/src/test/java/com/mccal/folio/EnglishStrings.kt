package com.mccal.folio

/**
 * The English text of strings.xml for plain unit tests, which have no Android resources. Resolves an R.string id to
 * its name, then formats that string the way Resources.getString would.
 */
object EnglishStrings {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val byName: Map<String, String> by lazy {
        val xml = java.io.File(root, "app/src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { m ->
            m.groupValues[1] to m.groupValues[2].replace("\\'", "'").replace("\\\"", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        }
    }
    private val names: Map<Int, String> by lazy { R.string::class.java.fields.associate { it.getInt(null) to it.name } }

    fun get(id: Int, vararg args: Any): String = byName.getValue(names.getValue(id)).let { if (args.isEmpty()) it else it.format(*args) }
    val text: (Int, Array<out Any>) -> String = { id, args -> get(id, *args) }
}
