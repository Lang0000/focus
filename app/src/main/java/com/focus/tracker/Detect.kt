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

    fun reel(root: AccessibilityNodeInfo): Detected? {
        var chrome = false
        var author: String? = null
        var caption: String? = null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            node.viewIdResourceName?.let { id ->
                when {
                    id.endsWith("reel_viewer_root") -> return null // stories
                    id.endsWith("clips_ufi_component") ->
                        if (node.isVisibleToUser) chrome = true
                    id.endsWith("clips_author_username") ->
                        if (node.isVisibleToUser) {
                            chrome = true
                            if (author == null) author = text(node)
                        }
                    id.endsWith("clips_captions_component") ->
                        if (node.isVisibleToUser) {
                            chrome = true
                            if (caption == null) caption = text(node)
                        }
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        if (!chrome) return null
        val key = buildString {
            author?.trim()?.let { append(it) }
            append('|')
            caption?.trim()?.take(48)?.let { append(it) }
        }.trim('|').ifBlank { null }
        return Detected(key)
    }

    fun short(root: AccessibilityNodeInfo): Detected? {
        var recycler: AccessibilityNodeInfo? = null
        var content: AccessibilityNodeInfo? = null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            node.viewIdResourceName?.let { id ->
                when {
                    id.endsWith("reel_recycler") ->
                        if (recycler == null && node.isVisibleToUser) recycler = node
                    id.endsWith("reel_player_page_content") ->
                        if (content == null && node.isVisibleToUser) content = node
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        val player = recycler ?: return null
        return Detected(shortKey(content ?: player))
    }

    private fun shortKey(node: AccessibilityNodeInfo): String? {
        val raw = StringBuilder()
        collectText(node, raw, 0)
        val s = raw.toString()
            .replace("Video Progress", "")
            .replace("Tap to watch live", "")
            .replace("Go to channel", "")
            .trim()
        if (s.contains("PostPostPostlike")) return null
        if (s.length <= 15) return null // not loaded yet
        return s
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
        lastKey?.let { if (overlap(key, it) >= 0.9f) { lastKey = key; return false } }
        lastKey = key
        if (recent.any { overlap(key, it) >= 0.9f }) return false
        recent.addFirst(key)
        while (recent.size > 80) recent.removeLast()
        return true
    }

    private fun overlap(a: String, b: String): Float {
        val wa = a.lowercase(Locale.ROOT).split(' ', '|').filterTo(HashSet()) { it.isNotBlank() }
        val wb = b.lowercase(Locale.ROOT).split(' ', '|').filterTo(HashSet()) { it.isNotBlank() }
        if (wa.isEmpty() || wb.isEmpty()) return if (a == b) 1f else 0f
        return wa.count { it in wb }.toFloat() / minOf(wa.size, wb.size)
    }
}
