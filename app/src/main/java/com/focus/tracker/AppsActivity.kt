package com.focus.tracker

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Pick which apps are timed. Saves on every tap. */
class AppsActivity : Activity() {

    private class App(val pkg: String, val label: String, val icon: Drawable?)

    private var all: List<App> = emptyList()
    private var shown: List<App> = emptyList()
    private val selected = HashSet<String>()
    private lateinit var adapter: Adapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(Store.apps(this))

        val title = TextView(this).apply {
            text = "Додатки для відліку"
            setTextColor(getColor(R.color.text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val search = EditText(this).apply {
            hint = "Пошук"
            setHintTextColor(getColor(R.color.dim))
            setTextColor(getColor(R.color.text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = getDrawable(R.drawable.button)
            setPadding(dp(14), 0, dp(14), 0)
            isSingleLine = true
        }
        val list = ListView(this).apply {
            divider = null
            setSelector(android.R.color.transparent)
        }
        adapter = Adapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val app = shown[pos]
            if (!selected.remove(app.pkg)) selected.add(app.pkg)
            Store.setApps(this, selected)
            adapter.notifyDataSetChanged()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            setPadding(dp(18), dp(20), dp(18), 0)
            addView(title)
            addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
                topMargin = dp(14); bottomMargin = dp(8)
            })
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
        })

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != packageName }
                .mapNotNull { pkg ->
                    // An app can be uninstalled while we list; skip it rather than crash.
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        App(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
                    }.getOrNull()
                }
                // Picked apps first, then by name.
                .sortedWith(compareBy<App>({ it.pkg !in selected }, { it.label.lowercase() }))
            runOnUiThread {
                all = apps
                filter(search.text.toString())
            }
        }.start()
    }

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        shown = if (query.isEmpty()) all else all.filter {
            it.label.lowercase().contains(query) || it.pkg.contains(query)
        }
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: makeRow()
            val app = shown[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
            (row.getChildAt(2) as CheckBox).isChecked = app.pkg in selected
            return row
        }

        private fun makeRow() = LinearLayout(this@AppsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(14) })
            addView(TextView(context).apply {
                setTextColor(getColor(R.color.text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(CheckBox(context).apply {
                isClickable = false
                isFocusable = false
            })
        }
    }
}
