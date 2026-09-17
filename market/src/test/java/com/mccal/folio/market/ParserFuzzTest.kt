package com.mccal.folio.market

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import org.json.JSONObject
import kotlin.math.sign

/**
 * Jazzer fuzz targets for every parser. A normal test run replays the seeds in
 * `resources/com/mccal/folio/market/ParserFuzzTestInputs/<target>/`; fuzzing mode explores for 5 minutes per target:
 *
 *     JAZZER_FUZZ=1 ./gradlew :market:testDebugUnitTest --tests '*ParserFuzzTest.manifest'
 *
 * Any exception, hang or broken invariant is a finding, and Jazzer saves the input next to the seeds.
 */
class ParserFuzzTest {
    @FuzzTest(maxDuration = "5m")
    fun manifest(data: FuzzedDataProvider) {
        val result = PackageManifest.parse(data.consumeRemainingAsString())
        checkReport(result)
        if (result is ParseResult.Ok) {
            val m = result.value
            check(PackageManifest.ID.containsMatchIn(m.id) && m.kinds.isNotEmpty())
            check(m.name.english.codePointCount(0, m.name.english.length) <= PackageManifest.MAX_NAME)
            m.icon?.let { check(!it.startsWith("/") && ".." !in it) }
        }
    }

    @FuzzTest(maxDuration = "5m")
    fun depiction(data: FuzzedDataProvider) {
        val result = Depiction.parse(data.consumeRemainingAsString())
        checkReport(result)
        if (result is ParseResult.Ok) {
            check(result.value.blocks.size <= Depiction.MAX_BLOCKS)
            for (block in result.value.blocks) when (block) {
                is DepictionBlock.Link -> check(block.url.startsWith("https://"))
                is DepictionBlock.Donation -> check(block.url.startsWith("https://"))
                else -> Unit
            }
        }
    }

    /** Whatever JsonGuard accepts, org.json must read without an exception. */
    @FuzzTest(maxDuration = "5m")
    fun jsonGuard(data: FuzzedDataProvider) {
        val text = data.consumeRemainingAsString()
        if (JsonGuard.check(text, 64 * 1024) == null) JSONObject(text)
    }

    /** dpkg ordering stays a total order that agrees with equals and hashCode. */
    @FuzzTest(maxDuration = "5m")
    fun versions(data: FuzzedDataProvider) {
        val a = DebVersion.parse(data.consumeString(80)) ?: return
        val b = DebVersion.parse(data.consumeString(80)) ?: return
        val c = DebVersion.parse(data.consumeRemainingAsString()) ?: return
        check(a.compareTo(b).sign == -b.compareTo(a).sign)
        check((a.compareTo(b) == 0) == (a == b))
        if (a == b) check(a.hashCode() == b.hashCode())
        if (a <= b && b <= c) check(a <= c)
        PackageRelation.parse("x.y (>= $a)")?.let { check(it.matches(a)) }
    }

    @FuzzTest(maxDuration = "5m")
    fun localizedText(data: FuzzedDataProvider) {
        val text = data.consumeString(4096)
        val preferred = List(data.consumeInt(0, 4)) { data.consumeString(16) }
        if (JsonGuard.check(text, 8192) != null) return
        val value = LocalizedText.read(JSONObject(text), 400) {} ?: return
        check(value.resolve(preferred) in listOf(value.english) + value.languages.map { value.resolve(listOf(it)) })
    }

    private fun checkReport(result: ParseResult<*>) {
        when (result) {
            is ParseResult.Invalid -> check(result.errors.size in 1..Problems.MAX_REPORTED)
            is ParseResult.Unsupported -> check(result.needs.isNotEmpty())
            is ParseResult.Ok -> check(result.ignored.size <= Problems.MAX_REPORTED)
        }
    }
}
