package com.discipline.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt 

data class Habit(
    var id: Long,
    var name: String,
    var target: String = "",
    var active: Boolean = true
)

class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("discipline", Context.MODE_PRIVATE)
    private val habitsKey = "habits"
    private val logsKey = "logs"
    private val onboardingKey = "onboarding"

    fun onboardingDone() = prefs.getBoolean(onboardingKey, false)
    fun setOnboardingDone() = prefs.edit().putBoolean(onboardingKey, true).apply()

    fun habits(): MutableList<Habit> {
        val out = mutableListOf<Habit>()
        val a = JSONArray(prefs.getString(habitsKey, "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out += Habit(o.getLong("id"), o.getString("name"), o.optString("target"), o.optBoolean("active", true))
        }
        return out
    }

    fun saveHabits(list: List<Habit>) {
        val a = JSONArray()
        list.forEach { h ->
            a.put(JSONObject().apply {
                put("id", h.id); put("name", h.name); put("target", h.target); put("active", h.active)
            })
        }
        prefs.edit().putString(habitsKey, a.toString()).apply()
    }

    fun checked(date: String, id: Long) = prefs.getBoolean("log_${date}_$id", false)
    fun setChecked(date: String, id: Long, value: Boolean) =
        prefs.edit().putBoolean("log_${date}_$id", value).apply()

    fun score(date: String): Int? {
        val hs = habits().filter { it.active }
        if (hs.isEmpty()) return null
        return ((hs.count { checked(date, it.id) } * 100.0) / hs.size).roundToInt()
    }
}

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var tab = 0
    private val green = Color.rgb(16,185,129)
    private val dark = Color.rgb(11,18,32)
    private val card = Color.rgb(24,34,52)
    private val muted = Color.rgb(148,163,184)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        if (!store.onboardingDone()) showOnboarding() else buildUi()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float = 16f, color: Int = white): TextView =
        TextView(this).apply {
            this.text = s
            textSize = size
            setTextColor(color)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

    private fun button(s: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = s; setTextColor(white); setOnClickListener { onClick() }
        }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(8))
        }
        top.addView(text("Discipline", 28f, white))
        top.addView(text("See your discipline, not just your habits.", 13f, muted))
        root.addView(top)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(10))
            setBackgroundColor(Color.rgb(15,23,42))
        }
        listOf("Today", "Calendar", "Stats", "Settings").forEachIndexed { i, label ->
            val b = button(label) { tab = i; render() }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        root.addView(nav)
        setContentView(root)
        render()
    }

    private fun cardLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(card)
        }
        val p = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(0, 0, 0, dp(10))
        layoutParams = p
    }

    private fun render() {
        content.removeAllViews()
        when(tab) {
            0 -> today()
            1 -> calendar()
            2 -> stats()
            3 -> settings()
        }
    }

    private fun today() {
        val date = todayKey()
        val score = store.score(date)
        val header = cardLayout()
        header.addView(text(SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date()), 18f))
        header.addView(text(if (score == null) "No habits scheduled" else "$score% completed today", 30f, if ((score ?: 0) >= 80) green else white))
        content.addView(header)
        val add = button("+ Add habit") { addHabit() }
        content.addView(add)

        store.habits().filter { it.active }.forEach { h ->
            val c = cardLayout()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val cb = CheckBox(this).apply {
                isChecked = store.checked(date, h.id)
                setOnCheckedChangeListener { _, v -> store.setChecked(date, h.id, v); render() }
            }
            row.addView(cb)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(h.name, 17f))
            if (h.target.isNotBlank()) col.addView(text(h.target, 13f, muted))
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            val edit = button("⋮") { editHabit(h) }
            row.addView(edit)
            c.addView(row)
            content.addView(c)
        }
    }

    private fun calendar() {
        content.addView(text("Calendar", 24f))
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (d in 1..max) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val s = store.score(key)
            val c = cardLayout()
            c.setPadding(dp(12), dp(7), dp(12), dp(7))
            c.addView(text(SimpleDateFormat("EEE, MMM d", Locale.US).format(cal.time), 15f))
            c.addView(text(if (s == null) "—  No scheduled habits" else "$s% completed", 14f, scoreColor(s)))
            content.addView(c)
        }
    }

    private fun stats() {
        content.addView(text("Statistics", 24f))
        val hs = store.habits().filter { it.active }
        val today = store.score(todayKey())
        val streak = calculateStreak()
        val c = cardLayout()
        c.addView(text("Today", 14f, muted))
        c.addView(text(if (today == null) "No scheduled habits" else "$today%", 32f))
        c.addView(text("Current streak: $streak days", 18f, green))
        c.addView(text("Active habits: ${hs.size}", 16f, muted))
        content.addView(c)
    }

    private fun settings() {
        content.addView(text("Settings", 24f))
        val c = cardLayout()
        c.addView(text("Discipline", 20f))
        c.addView(text("Default streak threshold: 80%", 15f, muted))
        c.addView(text("Your data is stored locally on this device.", 15f, muted))
        content.addView(c)
        content.addView(button("Add habit") { addHabit() })
        content.addView(button("Reset onboarding") {
            store.setOnboardingDone()
            Toast.makeText(this, "Onboarding will show next time you open the app.", Toast.LENGTH_SHORT).show()
        })
    }

    private fun scoreColor(s: Int?): Int = when {
        s == null -> Color.rgb(107,114,128)
        s == 100 -> Color.rgb(6,78,59)
        s >= 80 -> Color.rgb(16,185,129)
        s >= 60 -> Color.rgb(132,204,22)
        s >= 40 -> Color.rgb(245,158,11)
        s >= 20 -> Color.rgb(249,115,22)
        s > 0 -> Color.rgb(239,68,68)
        else -> Color.rgb(127,29,29)
    }

    private fun addHabit() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }
        val name = EditText(this).apply { hint = "Habit name" }
        val target = EditText(this).apply { hint = "Target (optional), e.g. 10 pages" }
        box.addView(name); box.addView(target)
        AlertDialog.Builder(this).setTitle("New habit").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isNotEmpty()) {
                    val hs = store.habits()
                    hs += Habit(System.currentTimeMillis(), n, target.text.toString().trim())
                    store.saveHabits(hs); render()
                }
            }.show()
    }

    private fun editHabit(h: Habit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }
        val name = EditText(this).apply { setText(h.name) }
        val target = EditText(this).apply { setText(h.target) }
        box.addView(name); box.addView(target)
        AlertDialog.Builder(this).setTitle("Edit habit").setView(box)
            .setNegativeButton("Delete") { _, _ ->
                store.saveHabits(store.habits().filterNot { it.id == h.id }); render()
            }
            .setPositiveButton("Save") { _, _ ->
                h.name = name.text.toString().trim().ifBlank { h.name }
                h.target = target.text.toString().trim()
                store.saveHabits(store.habits()); render()
            }.show()
    }

    private fun calculateStreak(): Int {
        var streak = 0
        val cal = Calendar.getInstance()
        repeat(365) {
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val s = store.score(key)
            if (s != null && s >= 80) streak++ else if (s != null && s < 80) return streak
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    private fun todayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun showOnboarding() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(50), dp(28), dp(20))
            setBackgroundColor(dark)
        }
        box.addView(text("Discipline", 38f))
        box.addView(text("See your discipline, not just your habits.", 18f, muted))
        box.addView(text("\nBuild consistency by tracking what you actually do every day.\n\nYour data stays on your device.", 17f))
        val b = button("Get started") { store.setOnboardingDone(); buildUi() }
        box.addView(b)
        setContentView(box)
    }
}
