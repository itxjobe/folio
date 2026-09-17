package com.mccal.folio.market

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * ADR 0005: the published JSON Schemas are the contract, so Folio's parsers must agree with them.
 *
 * - Schema says valid → the parser reads it with nothing skipped.
 * - Schema says invalid → the parser rejects it, says it needs a newer Folio, or reports what it skipped
 *   (newer fields and blocks are skipped on purpose; see "Versioning this format").
 *
 * Known places where the parser is stricter than the schemas can express, kept out of the generated samples:
 * language tags that differ only in case, and a trailing newline where Java's `$` would match.
 */
class SchemaConformanceTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val schemaDir = File(root, "docs/sdk/schema/v1")
    private val mapper = ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    private val manifestSchema = schema("manifest")
    private val depictionSchema = schema("depiction")

    private fun schema(name: String): JsonSchema = factory.getSchema(File(schemaDir, "$name.schema.json").readText())
    private fun resource(name: String) = javaClass.getResource("/conformance/$name")!!.readText()

    private fun schemaErrors(schema: JsonSchema, text: String): List<String> = try {
        schema.validate(mapper.readTree(text)).map { it.message }
    } catch (e: Exception) {
        listOf("unparseable: ${e.message}")
    }

    private fun agree(schema: JsonSchema, text: String, parse: (String) -> ParseResult<*>): String? {
        val errors = schemaErrors(schema, text)
        val result = parse(text)
        val clean = result is ParseResult.Ok && result.ignored.isEmpty()
        return when {
            errors.isEmpty() && !clean -> "schema accepts, parser says $result"
            errors.isNotEmpty() && clean -> "schema rejects ($errors), parser accepts"
            else -> null
        }
    }

    @Test fun `the samples are valid for both`() {
        val samples = listOf(
            manifestSchema to File(root, "docs/sdk/examples/cabinet/manifest.json").readText(),
            manifestSchema to resource("manifest-full.json"),
            depictionSchema to File(root, "docs/sdk/examples/cabinet/depiction.json").readText(),
            depictionSchema to resource("depiction-full.json"),
        )
        for ((schema, text) in samples) {
            assertEquals(emptyList<String>(), schemaErrors(schema, text))
            assertEquals(null, agree(schema, text) { if (schema === manifestSchema) PackageManifest.parse(it) else Depiction.parse(it) })
        }
    }

    @Test fun `Kotlin enums list exactly the schema's values`() {
        val props = JSONObject(File(schemaDir, "manifest.schema.json").readText()).getJSONObject("properties")
        fun JSONArray.strings() = (0 until length()).map(::getString).toSet()
        fun items(name: String) = props.getJSONObject(name).getJSONObject("items").getJSONArray("enum").strings()
        assertEquals(items("kind"), PackageKind.entries.map { it.id }.toSet())
        assertEquals(items("permissions"), PackagePermission.entries.map { it.id }.toSet())
        assertEquals(items("screens"), Screen.entries.map { it.id }.toSet())
        assertEquals(items("provides"), Provides.entries.map { it.id }.toSet())
        assertEquals(props.getJSONObject("section").getJSONArray("enum").strings(), Section.entries.map { it.id }.toSet())
        assertEquals(
            props.getJSONObject("requires").getJSONObject("properties").getJSONObject("features").getJSONObject("items").getJSONArray("enum").strings(),
            Capability.entries.map { it.id }.toSet(),
        )
        assertEquals(
            props.getJSONObject("via").getJSONObject("items").getJSONObject("properties").getJSONObject("store").getJSONArray("enum").strings(),
            ExternalSource.Store.entries.map { it.id }.toSet(),
        )
        val blockTypes = JSONObject(File(schemaDir, "depiction.schema.json").readText()).getJSONObject("properties")
            .getJSONObject("blocks").getJSONObject("items").getJSONArray("oneOf")
            .let { a -> (0 until a.length()).map { a.getJSONObject(it).getJSONObject("properties").getJSONObject("type").getString("const") } }
        val samples = listOf(
            DepictionBlock.Hero("a.png"), DepictionBlock.Screenshots(listOf("a.png")), DepictionBlock.Markdown(LocalizedText.of("x")),
            DepictionBlock.FeatureList(listOf(LocalizedText.of("x"))), DepictionBlock.Compatibility(emptySet(), null),
            DepictionBlock.Changelog(emptyList()), DepictionBlock.Link(LocalizedText.of("x"), "https://a.b"), DepictionBlock.Donation("https://a.b"),
        )
        assertEquals(blockTypes.toSet(), samples.map(::typeOf).toSet())
    }

    // Exhaustive: a new block class won't compile until it's listed here and in the schema.
    private fun typeOf(block: DepictionBlock) = when (block) {
        is DepictionBlock.Hero -> "hero"
        is DepictionBlock.Screenshots -> "screenshots"
        is DepictionBlock.Markdown -> "markdown"
        is DepictionBlock.FeatureList -> "featureList"
        is DepictionBlock.Compatibility -> "compatibility"
        is DepictionBlock.Changelog -> "changelog"
        is DepictionBlock.Link -> "link"
        is DepictionBlock.Donation -> "donation"
    }

    @Test fun `mutated manifests get the same answer from the schema and the parser`() {
        differential(manifestSchema, listOf(File(root, "docs/sdk/examples/cabinet/manifest.json").readText(), resource("manifest-full.json")), seed = 7) {
            PackageManifest.parse(it)
        }
    }

    @Test fun `mutated pages get the same answer from the schema and the parser`() {
        differential(depictionSchema, listOf(File(root, "docs/sdk/examples/cabinet/depiction.json").readText(), resource("depiction-full.json")), seed = 11) {
            Depiction.parse(it)
        }
    }

    private fun differential(schema: JsonSchema, seeds: List<String>, seed: Int, parse: (String) -> ParseResult<*>) {
        val random = Random(seed)
        val disagreements = LinkedHashMap<String, String>()
        var clean = 0
        repeat(ROUNDS) {
            val doc = JSONObject(seeds.random(random))
            repeat(1 + random.nextInt(3)) { mutate(doc, random) }
            val text = doc.toString()
            agree(schema, text, parse)?.let { disagreements.putIfAbsent(it.take(300), text.take(600)) }
            val result = parse(text)
            if (result is ParseResult.Ok && result.ignored.isEmpty()) clean++
        }
        assertTrue(disagreements.entries.take(5).joinToString("\n\n") { "${it.key}\n  ${it.value}" }, disagreements.isEmpty())
        // The generator must still produce a good share of valid files, or the check proves little.
        assertTrue("only $clean of $ROUNDS mutants were valid", clean > ROUNDS / 20)
    }

    private fun containers(node: Any, out: MutableList<Any>) {
        when (node) {
            is JSONObject -> { out += node; node.keys().asSequence().toList().forEach { containers(node.get(it), out) } }
            is JSONArray -> { out += node; for (i in 0 until node.length()) containers(node.get(i), out) }
        }
    }

    private fun mutate(doc: JSONObject, random: Random) {
        val all = mutableListOf<Any>()
        containers(doc, all)
        when (val target = all.random(random)) {
            is JSONObject -> {
                val keys = target.keys().asSequence().toList()
                when (random.nextInt(4)) {
                    0 -> if (keys.isNotEmpty()) target.remove(keys.random(random))
                    1 -> target.put(if (random.nextInt(8) == 0) "extra" else FIELD_NAMES.random(random), value(random))
                    else -> if (keys.isNotEmpty()) target.put(keys.random(random), value(random))
                }
            }
            is JSONArray -> {
                val n = target.length()
                when (random.nextInt(4)) {
                    0 -> if (n > 0) target.remove(random.nextInt(n))
                    1 -> if (n > 0) target.put(target.get(random.nextInt(n)))
                    2 -> if (n > 0) target.put(random.nextInt(n), value(random))
                    else -> target.put(value(random))
                }
            }
        }
    }

    private fun value(random: Random): Any = when (val v = POOL.random(random)) {
        is String -> if (v.startsWith("[") || v.startsWith("{")) (if (v.startsWith("[")) JSONArray(v) else JSONObject(v)) else v
        else -> v
    }

    private companion object {
        const val ROUNDS = 4000
        val FIELD_NAMES = listOf(
            "format", "id", "name", "version", "author", "minFolio", "section", "kind", "permissions", "screens", "depends", "conflicts",
            "icon", "depiction", "license", "description", "via", "provides", "requires", "features", "url", "store", "repoUrl",
            "tint", "blocks", "type", "image", "images", "text", "items", "notes", "entries", "date", "title", "\$schema", "en", "es",
        )
        val POOL: List<Any> = listOf(
            JSONObject.NULL, true, 0, 1, 2, -1, 1.5, "", "a", "x".repeat(40), "x".repeat(41), "x".repeat(400), "x".repeat(401),
            "x".repeat(4001), "https://example.com", "http://example.com", "https://", "https://a b", "assets/a.png", "/abs.png",
            "a/../b.png", "#12345G", "#0A84FF", "1.0", "1:2.0~b-1", "0.7", "0.7.0", "0.07.0", "1234567890.0.0", "01.0", "1.0-", "x1",
            "dev.maya.icons", "dev.maya.icons (>= 1.2)", "dev.maya.icons (> 1.2)", "dev.maya.icons (= ${"1".repeat(70)})", "Dev.Maya",
            "com.example.app", "com.example.app-2", "tweaks", "tweakBundle", "theme", "externalApp", "hologram", "playStore", "fdroid",
            "obtainium", "aurora", "cover", "inner", "rear", "tweaks.appPanels", "icons", "iconPack", "markdown", "hero", "link", "donation",
            "future", "2026-09-17", "2026-9-17", "MIT", "[]", "[\"tweaks\"]", "[\"tweaks\",\"tweaks\"]", "[\"cover\",\"inner\"]",
            "[\"cover\",\"cover\"]", "[\"a.png\"]", "[1]", "{}", "{\"en\":\"x\"}", "{\"es\":\"x\"}", "{\"en\":\"x\",\"pt-BR\":\"y\"}",
            "{\"en\":\"\"}", "{\"EN\":\"x\"}", "{\"en\":\"x\",\"e\":\"y\"}", "{\"store\":\"playStore\"}",
            "{\"store\":\"playStore\",\"id\":\"com.example.app\"}", "{\"store\":\"obtainium\",\"repoUrl\":\"https://github.com/a/b\"}",
            "{\"type\":\"hero\",\"image\":\"a.png\"}", "{\"type\":\"future\"}", "{\"type\":\"markdown\"}", "{\"features\":[\"icons\"]}",
            "{\"version\":\"1.0\",\"notes\":\"x\"}", "{\"name\":\"x\"}",
        )
    }
}
