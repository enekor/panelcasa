package com.n3k0chan.panelcasa

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Panel lateral izquierdo, nativo para que funcione aunque el WebView falle:
 * apps fijadas arriba (con desplazamiento si no caben) y, al pie, «Todas» y la rueda de ajustes.
 */
class Sidebar(
    context: Context,
    private val onApp: (String) -> Unit,
    private val onAllApps: () -> Unit,
    private val onSettings: () -> Unit,
) : LinearLayout(context) {
    private val list = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(Ui.SIDEBAR)
        tag = "sidebar"
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(list)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(Ui.divider(context))
        addView(item(context.getString(R.string.all_apps_short), null, R.drawable.ic_apps, "all-apps") { onAllApps() })
        addView(item(context.getString(R.string.settings), null, R.drawable.ic_settings, "settings") { onSettings() })
    }

    fun setApps(apps: List<LaunchableApp>) {
        list.removeAllViews()
        for (a in apps) list.addView(item(a.label, a, 0, "app:" + a.key) { onApp(a.key) })
    }

    private fun item(label: String, app: LaunchableApp?, iconRes: Int, viewTag: String, onClick: () -> Unit): LinearLayout {
        val c = context
        return LinearLayout(c).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            tag = viewTag
            minimumHeight = Ui.dp(c, 72)
            setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 6))
            background = Ui.pressable()
            isClickable = true
            contentDescription = label
            setOnClickListener { onClick() }
            val icon = ImageView(c).apply {
                if (app != null) setImageDrawable(app.icon) else setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val size = Ui.dp(c, if (app != null) 44 else 32)
            addView(FrameLayout(c).apply { addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER)) },
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 44)))
            addView(TextView(c).apply {
                text = label
                setTextColor(Ui.DIM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    companion object {
        const val WIDTH_DP = 96
    }
}
