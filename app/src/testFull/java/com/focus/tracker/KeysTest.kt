package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeysTest {
    @Test fun reelKeyIsAuthorAndCaptionPrefix() {
        assertEquals("anna|hello", Detect.reelKey(" anna ", "hello "))
        assertEquals("anna", Detect.reelKey("anna", null))
        assertEquals("hello", Detect.reelKey(null, "hello"))
        assertNull(Detect.reelKey(null, null))
        assertEquals(48, Detect.reelKey(null, "x".repeat(100))!!.length)
    }

    @Test fun shortKeyStripsChromeAndWaitsForContent() {
        assertEquals("Cat jumps over the fence @cats", Detect.shortKey("Go to channel Cat jumps over the fence @cats"))
        assertNull(Detect.shortKey("Go to channel short"))     // not loaded yet
        assertNull(Detect.shortKey("PostPostPostlike something long enough"))
    }
}
