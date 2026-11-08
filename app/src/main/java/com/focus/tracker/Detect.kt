package com.focus.tracker

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Reels / Shorts recognition, adapted from the open-source unhook project
 * (github.com/rogerantony-dev/unhook, MIT).
 *
 *  - Instagram Reels: the Reels player's own chrome — clips_ufi_component,
 *    clips_author_username, clips_captions_component. Stories (reel_viewer_root)
 *    are excluded. Author + caption prefix is the reel's identity.
 *  - YouTube Shorts: reel_recycler, the vertical pager that exists only in the
 *    Shorts player (not the home Shorts shelf). All text under
 *    reel_player_page_content is the short's identity.
 */
class Detected(val key: String?)

object Detect {
    private const val MAX_NODES = 3000
    private const val CAPTION_CHARS = 48
    private const val MIN_SHORT_TEXT = 16
    private val SHORT_CHROME = listOf("Video Progress", "Tap to watch live", "Go to channel")

    fun reel(root: AccessibilityNodeInfo): Detected? {
        var chrome = false
        var author: String? = null
        var caption: String? = null
        val stories = walk(root) { node, id ->
            if (id.endsWith("reel_viewer_root")) return@walk true // stories, never reels
            if (!node.isVisibleToUser) return@walk false
            when {
                id.endsWith("clips_ufi_component") -> chrome = true
                id.endsWith("clips_author_username") -> { chrome = true; author = author ?: text(node) }
                id.endsWith("clips_captions_component") -> { chrome = true; caption = caption ?: text(node) }
            }
            false
        }
        if (stories || !chrome) return null
        return Detected(reelKey(author, caption))
    }

    fun short(root: AccessibilityNodeInfo): Detected? {
        var recycler: AccessibilityNodeInfo? = null
        var content: AccessibilityNodeInfo? = null
        walk(root) { node, id ->
            if (node.isVisibleToUser) when {
                id.endsWith("reel_recycler") -> recycler = recycler ?: node
                id.endsWith("reel_player_page_content") -> content = content ?: node
            }
            false
        }
        val player = recycler ?: return null
        val raw = StringBuilder().also { collectText(content ?: player, it, 0) }
        return Detected(shortKey(raw.toString()))
    }

    /** Identity of a reel: author + the start of its caption. Null until either has loaded. */
    fun reelKey(author: String?, caption: String?): String? = buildString {
        author?.trim()?.let { append(it) }
        append('|')
        caption?.trim()?.take(CAPTION_CHARS)?.let { append(it) }
    }.trim('|').ifBlank { null }

    /** Identity of a short: its on-screen text without YouTube's fixed labels. Null until loaded. */
    fun shortKey(raw: String): String? {
        if (raw.contains("PostPostPostlike")) return null // a community post, not a Short
        val s = SHORT_CHROME.fold(raw) { acc, label -> acc.replace(label, "") }.trim()
        return s.takeIf { it.length >= MIN_SHORT_TEXT }
    }

    /**
     * Breadth-first over [root], calling [visit] for every node with a view id.
     * Stops early, returning true, as soon as [visit] returns true.
     */
    private inline fun walk(
        root: AccessibilityNodeInfo,
        visit: (AccessibilityNodeInfo, String) -> Boolean
    ): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (id != null && visit(node, id)) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 40) return
        node.text?.let { if (it.isNotBlank()) out.append(it).append(' ') }
        node.contentDescription?.let { if (it.isNotBlank()) out.append(it).append(' ') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it, out, depth + 1) }
    }

    private fun text(node: AccessibilityNodeInfo): String? =
        node.text?.toString()?.takeIf { it.isNotBlank() }
}

/**
 * Counts each distinct item once per day. Keys are compared by word overlap, so a
 * caption that finishes loading a moment later is still the same reel, and
 * scrolling back to one already seen doesn't count it again.
 */
class Dedup {
    private var date: String? = null
    private var lastKey: String? = null
    private val recent = ArrayDeque<String>()

    /** True when [key] is a new item that should be counted. */
    fun isNew(key: String, today: String): Boolean {
        if (date != today) { date = today; recent.clear(); lastKey = null }
        lastKey?.let { if (overlap(key, it) >= SAME) { lastKey = key; return false } }
        lastKey = key
        if (recent.any { overlap(key, it) >= SAME }) return false
        recent.addFirst(key)
        while (recent.size > MEMORY) recent.removeLast()
        return true
    }

    private fun words(s: String) =
        s.lowercase(Locale.ROOT).split(' ', '|').filterTo(HashSet()) { it.isNotBlank() }

    private fun overlap(a: String, b: String): Float {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return if (a == b) 1f else 0f
        return wa.count { it in wb }.toFloat() / minOf(wa.size, wb.size)
    }

    private companion object {
        const val SAME = 0.9f
        const val MEMORY = 80
    }
}
