package com.focus.tracker

import android.content.Context

/** What the Reels/Shorts counter knows about an app right now. */
data class ReelCount(val count: Int, val onPlayer: Boolean)

/**
 * Where the tracker shows the app in front: the floating pill and the Now Bar
 * card, each as the user switched it on. The services only say which app is in
 * front (or that none is); this is the one place that decides where it shows.
 *
 * [canDraw] says whether the pill's window may be added right now (the lite
 * build needs "display over other apps"). [reels] gives the counter, or null
 * for an app without one.
 */
class Screen(
    private val ctx: Context,
    windowType: Int,
    private val canDraw: () -> Boolean = { true },
    reels: (String) -> ReelCount? = { null },
) {
    private val overlay = Overlay(ctx, windowType) { pkg ->
        reels(pkg)?.let { Pill.badge(pkg, it.count, it.onPlayer) }
    }
    private val nowBar = NowBar(ctx) { pkg -> reels(pkg)?.count }

    fun show(pkg: String) {
        overlay.applyReset()
        if (Store.overlayOn(ctx) && canDraw()) overlay.show(pkg) else overlay.hide()
        if (Store.nowBarOn(ctx)) nowBar.show(pkg) else nowBar.hide()
    }

    /** Redraw for the app already shown, e.g. after a reel was counted. */
    fun update() {
        overlay.update()
        nowBar.update()
    }

    fun hide() {
        overlay.hide()
        nowBar.hide()
    }

    fun remove() {
        overlay.remove()
        nowBar.hide()
    }
}
