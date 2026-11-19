package com.n3k0chan.panelcasa

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Pequeños ayudantes para construir pantallas nativas sin XML ni bibliotecas de interfaz. */
object Ui {
    const val BG = 0xFF141A1F.toInt()
    const val SIDEBAR = 0xFF0B0E11.toInt()
    const val TEXT = 0xFFE8ECEF.toInt()
    const val DIM = 0xFFA9B4BD.toInt()
    const val ACCENT = 0xFF57C785.toInt()
    const val DIVIDER = 0xFF232B32.toInt()
    const val PRESSED = 0xFF232B32.toInt()

    fun dp(context: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    /** Fondo con estado pulsado, sin animaciones de onda. */
    fun pressable(base: Int = Color.TRANSPARENT): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(PRESSED))
        addState(intArrayOf(), ColorDrawable(base))
    }

    fun column(context: Context, padding: Int = 24): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(context, padding)
        setPadding(p, p, p, p)
    }

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setPadding(0, dp(context, 18), 0, dp(context, 6))
    }

    fun text(context: Context, text: String, sizeSp: Float = 17f, color: Int = DIM): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    /** Fila táctil de al menos 56 dp con título y subtítulo. */
    fun row(context: Context, title: String, subtitle: String?, onClick: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = dp(context, 56)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
        background = pressable()
        isClickable = true
        setOnClickListener { onClick() }
        addView(text(context, title, 19f, TEXT).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        if (subtitle != null) addView(text(context, subtitle, 15f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
    }

    fun button(context: Context, label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        minHeight = dp(context, 48)
        setOnClickListener { onClick() }
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(DIVIDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1))
    }
}
