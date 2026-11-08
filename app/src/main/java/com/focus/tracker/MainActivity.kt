package com.focus.tracker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
    private val iconCache = HashMap<String, android.graphics.drawable.Drawable?>()

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
        if (!Setup.REELS) {
            findViewById<View>(R.id.reelsRow).visibility = View.GONE
            findViewById<View>(R.id.countReels).visibility = View.GONE
        }
        findViewById<View>(R.id.resetPos).setOnClickListener {
            Store.resetPillPos(this)
            Toast.makeText(this, "Віконце повернеться вгору по центру", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Setup.start(this)
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

        val selected = Store.apps(this)
        val apps = Store.dayApps(this).filter { it.first in selected }
        val total = apps.sumOf { it.second }
        findViewById<TextView>(R.id.total).text = Format.words(total)

        findViewById<TextView>(R.id.reels).text = Store.reels(this).toString()
        findViewById<TextView>(R.id.reelsTime).text = Format.words(Store.reelSec(this))
        findViewById<TextView>(R.id.shorts).text = Store.shorts(this).toString()
        findViewById<TextView>(R.id.shortsTime).text = Format.words(Store.shortSec(this))

        renderWeek()
        renderApps(apps, selected)
    }

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

    private fun renderWeek() {
        val week = findViewById<LinearLayout>(R.id.week)
        val totals = (6 downTo 0).map { off ->
            val d = Store.day(off)
            Store.dayTotal(this, d)
        }
        val max = (totals.maxOrNull() ?: 0).coerceAtLeast(60)
        val dayFmt = SimpleDateFormat("EE", uk)
        if (week.childCount != 7) {
            week.removeAllViews()
            repeat(7) {
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                val bar = View(this)
                val lbl = TextView(this).apply {
                    setTextColor(getColor(R.color.dim))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    gravity = Gravity.CENTER
                }
                col.addView(bar, LinearLayout.LayoutParams(dp(18), dp(4)))
                col.addView(lbl, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) })
                week.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        val maxBar = dp(66)
        for (i in 0 until 7) {
            val col = week.getChildAt(i) as LinearLayout
            val bar = col.getChildAt(0)
            val lbl = col.getChildAt(1) as TextView
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(6 - i)) }
            lbl.text = dayFmt.format(cal.time)
            val h = (maxBar * totals[i] / max).coerceAtLeast(dp(4))
            val lp = bar.layoutParams
            if (lp.height != h) { lp.height = h; bar.layoutParams = lp }
            bar.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(if (i == 6) getColor(R.color.accent) else Color.parseColor("#2E333C"))
            }
        }
    }

    private fun renderApps(apps: List<Pair<String, Int>>, selected: Set<String>) {
        val list = findViewById<LinearLayout>(R.id.appList)
        list.removeAllViews()
        // Selected apps not used yet today still get a row, at zero.
        val shown = apps.map { it.first }.toSet()
        val rows = apps + selected.filter { it !in shown && installed(it) }
            .sortedBy { label(it).lowercase() }.map { it to 0 }
        if (rows.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Ще не вибрано жодного додатка"
                setTextColor(getColor(R.color.dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(8), 0, dp(4))
            })
            return
        }
        val max = (rows.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        for ((pkg, sec) in rows) list.addView(appRow(pkg, sec, max))
    }

    private fun installed(pkg: String) =
        runCatching { packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    private fun appRow(pkg: String, sec: Int, max: Int): View {
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
        val time = TextView(this).apply {
            text = if (sec > 0) Format.words(sec) else "—"
            setTextColor(getColor(if (sec > 0) R.color.text else R.color.dim))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            fontFeatureSettings = "tnum"
        }
        val track = LinearLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat(); setColor(Color.parseColor("#262A31"))
            }
        }
        val fill = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat(); setColor(getColor(R.color.accent))
            }
        }
        val frac = sec.toFloat() / max
        track.addView(fill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, frac))
        track.addView(View(this), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - frac))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(time)
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(top)
            addView(track, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5)).apply {
                topMargin = dp(7)
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
            addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}
