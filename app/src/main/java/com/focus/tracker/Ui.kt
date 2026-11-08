package com.focus.tracker

import android.content.Context
import android.util.TypedValue

/** [v] density-independent pixels in real pixels. */
fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
