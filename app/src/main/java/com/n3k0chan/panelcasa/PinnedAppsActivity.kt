package com.n3k0chan.panelcasa

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Elegir qué apps van en el panel lateral y en qué orden (botones de subir y bajar, sin arrastrar). */
class PinnedAppsActivity : Activity() {
    private lateinit var app: PanelApp
    private lateinit var list: LinearLayout
    private var all: List<LaunchableApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PanelApp
        val root = Ui.column(this)
        root.addView(Ui.title(this, getString(R.string.pinned_apps)))
        root.addView(Ui.text(this, "Marca las apps que quieres tener a mano. Las fijadas aparecen primero, en el orden del panel."))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(ScrollView(this).apply { addView(root) })
        all = Apps(this).all()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val pinned = app.prefs.pinnedApps.filter { k -> all.any { it.key == k } }
        val ordered = pinned.mapNotNull { k -> all.firstOrNull { it.key == k } } + all.filter { it.key !in pinned }
        for (a in ordered) {
            val isPinned = a.key in pinned
            val idx = pinned.indexOf(a.key)
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(this@PinnedAppsActivity, 56)
                addView(ImageView(this@PinnedAppsActivity).apply { setImageDrawable(a.icon) },
                    LinearLayout.LayoutParams(Ui.dp(this@PinnedAppsActivity, 40), Ui.dp(this@PinnedAppsActivity, 40)))
                addView(CheckBox(this@PinnedAppsActivity).apply {
                    text = a.label
                    textSize = 18f
                    isChecked = isPinned
                    setOnCheckedChangeListener { _, checked ->
                        app.prefs.pinnedApps = if (checked) pinned + a.key else pinned - a.key
                        render()
                    }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (isPinned) {
                    addView(Ui.button(this@PinnedAppsActivity, "▲") { move(pinned, idx, -1) }.apply { isEnabled = idx > 0 })
                    addView(Ui.button(this@PinnedAppsActivity, "▼") { move(pinned, idx, 1) }.apply { isEnabled = idx < pinned.size - 1 })
                }
            })
        }
        if (all.isEmpty()) list.addView(TextView(this).apply { text = "No se han encontrado apps." })
    }

    private fun move(pinned: List<String>, idx: Int, delta: Int) {
        val m = pinned.toMutableList()
        val j = idx + delta
        if (j !in m.indices) return
        m[idx] = m[j].also { m[j] = m[idx] }
        app.prefs.pinnedApps = m
        render()
    }
}
