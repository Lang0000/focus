package com.focus.tracker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }
    private val uk = Locale("uk", "UA")
    private val labelCache = HashMap<String, String>()
    private val icons by lazy { AppIcons(this) }
    private val iconCache = HashMap<String, android.graphics.drawable.Drawable?>()

    /** The system's per-app time for the last 7 days, oldest first; re-read every [DAYS_FRESH_MS]. */
    private var days: List<Map<String, AppUse>> = List(7) { emptyMap() }
    private var daysReadAt = 0L
    /** The day the dashboard shows: 6 is today, 0 is six days ago. */
    private var pick = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.enable).setOnClickListener {
            val step = nextStep() ?: return@setOnClickListener
            if (step.intents.none { runCatching { startActivity(it) }.isSuccess }) {
                Toast.makeText(this, "Не вдалося відкрити налаштування", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.appInfo).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        findViewById<View>(R.id.pick).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        findViewById<Switch>(R.id.overlay).apply {
            isChecked = Store.overlayOn(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Store.setOverlayOn(this@MainActivity, on)
                render()
            }
        }
        findViewById<Switch>(R.id.nowBar).apply {
            isChecked = Store.nowBarOn(this@MainActivity)
            visibility = if (NowBar.SUPPORTED) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, on ->
                Store.setNowBarOn(this@MainActivity, on)
                render()
            }
        }
        findViewById<Switch>(R.id.countReels).apply {
            isChecked = Store.countReels(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Store.setCountReels(this@MainActivity, on) }
        }
        if (!Setup.REELS) findViewById<View>(R.id.countReels).visibility = View.GONE
        // Edge to edge (target 35): keep the header below the status bar.
        findViewById<View>(R.id.scroll).setOnApplyWindowInsetsListener { v, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).top
                else @Suppress("DEPRECATION") insets.systemWindowInsetTop
            v.setPadding(0, top, 0, 0)
            insets
        }
        findViewById<View>(R.id.resetPos).setOnClickListener {
            Store.resetPillPos(this)
            Toast.makeText(this, "Віконце повернеться вгору по центру", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Setup.start(this)
        daysReadAt = 0L   // the selection or a permission may have changed while away
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun render() {
        renderSetup()

        val date = SimpleDateFormat("EEEE, d MMMM", uk).format(Calendar.getInstance().time)
        findViewById<TextView>(R.id.date).text = date.replaceFirstChar { it.titlecase(uk) }

        val now = SystemClock.elapsedRealtime()
        if (daysReadAt == 0L || now - daysReadAt >= DAYS_FRESH_MS) {
            days = SystemUsage.days(this).map { day -> day.filterKeys { it in launchable } }
            daysReadAt = now
            shown = null
        }
        if (shown == pick) return   // nothing new to draw
        shown = pick

        val day = days[pick]
        val top = Breakdown.top(days, TOP_APPS)
        val colours = top.zip(Palette.pick(top.map { icons.accent(it) })).toMap()

        findViewById<TextView>(R.id.totalLabel).text = if (pick == 6) "Сьогодні" else
            SimpleDateFormat("EEEE, d MMMM", uk).format(dayCal(pick).time).replaceFirstChar { it.titlecase(uk) }
        findViewById<TextView>(R.id.total).text = Format.words(day.values.sumOf { it.seconds })
        findViewById<TextView>(R.id.totalSub).text = Format.opens(day.values.sumOf { it.opens })

        renderWeek(top, colours)
        renderLegend(top, colours)
        renderApps(Breakdown.rows(day), colours)
    }

    /** Apps a person opens from the launcher - not Focus itself, the home screen or system services. */
    private val launchable: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet() - packageName
    }

    /** The day last drawn; null when the data changed. */
    private var shown: Int? = null

    /** The next thing to switch on: the tracker's own permissions first, then the Now Bar's. */
    private fun nextStep(): Step? = Setup.next(this) ?: NowBar.step(this)

    private var shownStep: String? = null

    private fun renderSetup() {
        val step = nextStep()
        findViewById<View>(R.id.setup).visibility = if (step == null) View.GONE else View.VISIBLE
        if (step == null) {
            if (shownStep != null) Setup.start(this) // a permission was just granted
            shownStep = null
            return
        }
        if (shownStep == step.title) return
        shownStep = step.title
        findViewById<TextView>(R.id.setupTitle).text = step.title
        findViewById<TextView>(R.id.setupBody).text = step.body
        findViewById<View>(R.id.appInfo).visibility = if (step.appInfo) View.VISIBLE else View.GONE
    }

    /** Calendar on the day at bar [i] (6 = today). */
    private fun dayCal(i: Int) = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(6 - i)) }

    /** Seven stacked bars: each top app in its colour, the rest in grey. Tap a day to show it. */
    private fun renderWeek(top: List<String>, colours: Map<String, Int>) {
        val week = findViewById<LinearLayout>(R.id.week)
        val max = (days.maxOfOrNull { d -> d.values.sumOf { it.seconds } } ?: 0).coerceAtLeast(60)
        val dayFmt = SimpleDateFormat("EE", uk)
        val maxBar = dp(108)
        week.removeAllViews()
        for (i in 0 until 7) {
            val parts = Breakdown.bar(days[i], top)
            val stack = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(Color.parseColor("#262A31")) }
                clipToOutline = true
                alpha = if (i == pick) 1f else 0.55f
            }
            // Biggest app at the bottom: add from the top down.
            for ((pkg, sec) in parts.asReversed()) {
                stack.addView(View(this).apply { setBackgroundColor(pkg?.let { colours[it] } ?: Palette.REST) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (maxBar.toLong() * sec / max).toInt()))
            }
            val total = parts.sumOf { it.second }
            val h = (maxBar.toLong() * total / max).toInt().coerceAtLeast(dp(4))
            val lbl = TextView(this).apply {
                text = dayFmt.format(dayCal(i).time)
                setTextColor(getColor(if (i == pick) R.color.text else R.color.dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                addView(stack, LinearLayout.LayoutParams(dp(22), h))
                addView(lbl, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) })
                setOnClickListener { pick = i; render() }
            }
            week.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /** Which colour is which app. */
    private fun renderLegend(top: List<String>, colours: Map<String, Int>) {
        val text = SpannableStringBuilder()
        for (pkg in top + listOf<String?>(null)) {
            if (text.isNotEmpty()) text.append("   ")
            val dot = text.length
            text.append("● ")   // the dot stays with its name
            text.setSpan(ForegroundColorSpan(pkg?.let { colours[it] } ?: Palette.REST), dot, dot + 1, 0)
            text.append((pkg?.let { label(it) } ?: "інші").replace(" ", "\u00A0"))
        }
        findViewById<TextView>(R.id.legend).text = text
    }

    private fun renderApps(rows: List<Pair<String, AppUse>>, colours: Map<String, Int>) {
        val list = findViewById<LinearLayout>(R.id.appList)
        list.removeAllViews()
        if (rows.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Цього дня додатками не користувались"
                setTextColor(getColor(R.color.dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(8), 0, dp(4))
            })
            return
        }
        val max = rows.first().second.seconds.coerceAtLeast(1)
        for ((pkg, use) in rows) {
            val colour = colours[pkg] ?: icons.accent(pkg) ?: getColor(R.color.accent)
            list.addView(appRow(pkg, use, max, colour))
        }
    }

    /** What the day's counter saw in this app: «12 Reels за 9 хв», or null. */
    private fun clips(pkg: String): String? {
        if (!Setup.REELS) return null
        val d = Store.day(6 - pick)
        return when (pkg) {
            Store.INSTAGRAM -> clips(Store.reels(this, d), "Reels", Store.reelSec(this, d))
            Store.YOUTUBE -> clips(Store.shorts(this, d), "Shorts", Store.shortSec(this, d))
            else -> null
        }
    }

    private fun clips(n: Int, kind: String, sec: Int): String? = when {
        n == 0 -> null
        sec < 60 -> "$n $kind"
        else -> "$n $kind за ${Format.words(sec)}"
    }

    /** One app: name, opens and Reels/Shorts under it, the time on the right, a bar in its colour. */
    private fun appRow(pkg: String, use: AppUse, max: Int, colour: Int): View {
        val sec = use.seconds
        val icon = ImageView(this).apply {
            setImageDrawable(iconCache.getOrPut(pkg) {
                runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
            })
        }
        val name = TextView(this).apply {
            text = label(pkg)
            setTextColor(getColor(R.color.text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val sub = TextView(this).apply {
            text = listOfNotNull(Format.opens(use.opens), clips(pkg)).joinToString("  ·  ")
            setTextColor(getColor(R.color.dim))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
        }
        val time = TextView(this).apply {
            text = Format.words(sec)
            setTextColor(getColor(R.color.text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            fontFeatureSettings = "tnum"
            gravity = Gravity.END
        }
        val track = LinearLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat(); setColor(Color.parseColor("#262A31"))
            }
        }
        val fill = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(colour) }
        }
        val frac = sec.toFloat() / max
        track.addView(fill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, frac))
        track.addView(View(this), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - frac))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(sub)
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(time, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(12)
            })
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(line)
            addView(track, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
                topMargin = dp(8)
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(14) })
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    companion object {
        /** Apps with their own colour on the week chart. */
        private const val TOP_APPS = 5
        /** How often the open dashboard re-reads the system's usage log. */
        private const val DAYS_FRESH_MS = 30_000L
    }
}
