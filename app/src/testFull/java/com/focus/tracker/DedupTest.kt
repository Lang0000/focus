package com.focus.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupTest {
    private val day = "2026-11-08"

    @Test fun countsANewItemOnce() {
        val d = Dedup()
        assertTrue(d.isNew("anna|sunset at the beach", day))
        assertFalse(d.isNew("anna|sunset at the beach", day))
    }

    @Test fun captionFinishingLoadingIsTheSameItem() {
        val d = Dedup()
        assertTrue(d.isNew("anna", day))
        assertFalse(d.isNew("anna|sunset at the beach", day))
    }

    @Test fun differentItemsCountSeparately() {
        val d = Dedup()
        assertTrue(d.isNew("anna|sunset at the beach", day))
        assertTrue(d.isNew("bob|my new car review", day))
    }

    @Test fun scrollingBackDoesNotRecount() {
        val d = Dedup()
        d.isNew("anna|sunset at the beach", day)
        d.isNew("bob|my new car review", day)
        assertFalse(d.isNew("anna|sunset at the beach", day))
    }

    @Test fun aNewDayCountsAgain() {
        val d = Dedup()
        d.isNew("anna|sunset at the beach", day)
        assertTrue(d.isNew("anna|sunset at the beach", "2026-11-09"))
    }

    @Test fun anAuthorOnlyKeyDoesNotSwallowThatAuthorsNextReel() {
        val d = Dedup()
        assertTrue(d.isNew("anna", day))                       // caption still loading
        assertFalse(d.isNew("anna|sunset at the beach", day))  // same reel, now loaded
        assertTrue(d.isNew("anna|my new car review", day))     // her next reel
    }

    @Test fun remembersTheLast80ItemsOnly() {
        val d = Dedup()
        for (i in 0..80) assertTrue(d.isNew("author$i|caption number $i", day))
        assertFalse(d.isNew("author80|caption number 80", day)) // recent: still known
        assertTrue(d.isNew("author0|caption number 0", day))    // 81 back: forgotten
    }
}
