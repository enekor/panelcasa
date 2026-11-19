package com.n3k0chan.panelcasa

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Cuadrícula con todas las apps instaladas que tienen icono de inicio. */
class AllAppsActivity : Activity() {
    private lateinit var apps: Apps
    private var items: List<LaunchableApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = Apps(this)
        items = apps.all()
        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = Ui.dp(this@AllAppsActivity, 112)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = Ui.dp(this@AllAppsActivity, 8)
            val p = Ui.dp(this@AllAppsActivity, 16)
            setPadding(p, p, p, p)
            adapter = Adapter()
            setOnItemClickListener { _, _, pos, _ ->
                apps.launch(items[pos].key)
                finish()
            }
        }
        setContentView(grid)
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val a = items[position]
            val c = this@AllAppsActivity
            val cell = (convertView as? LinearLayout) ?: LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 104))
                setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 4))
                addView(ImageView(c), LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)))
                addView(TextView(c).apply {
                    setTextColor(Ui.TEXT)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            (cell.getChildAt(0) as ImageView).setImageDrawable(a.icon)
            (cell.getChildAt(1) as TextView).text = a.label
            return cell
        }
    }
}
