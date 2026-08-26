package com.example.adblock.filtering

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private val engine = FilterEngine(setOf("ads.example.com", "tracker.example.com"))
    @Test fun `blocks exact domains`() { assertTrue(engine.isBlocked("ads.example.com")); assertTrue(engine.isBlocked("tracker.example.com")) }
    @Test fun `blocks subdomains`() { assertTrue(engine.isBlocked("img.ads.example.com")) }
    @Test fun `allows ordinary domains`() { assertFalse(engine.isBlocked("youtube.com")); assertFalse(engine.isBlocked("google.com")) }
    @Test fun `normalizes case and final dot`() { assertTrue(engine.isBlocked("ADS.Example.COM.")) }
    @Test fun `deduplicates repeated rules`() { assertTrue(FilterEngine(listOf("ads.example.com", "ADS.EXAMPLE.COM")).isBlocked("ads.example.com")) }
    @Test fun `ignores invalid input`() { assertFalse(engine.isBlocked("")); assertFalse(engine.isBlocked("bad..domain")); assertFalse(engine.isBlocked(null)) }
    @Test fun `whitelist wins`() { assertFalse(FilterEngine(setOf("example.com"), setOf("safe.example.com")).isBlocked("safe.example.com")) }
}
