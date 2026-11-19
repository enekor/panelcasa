package com.n3k0chan.panelcasa

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.n3k0chan.panelcasa.renfe.Station
import com.n3k0chan.panelcasa.renfe.StationSearch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Buscador de estaciones de Cercanías. Se abre en el primer arranque y desde ajustes. */
class StationPickerActivity : Activity() {
    private val scope = MainScope()
    private lateinit var app: PanelApp
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var search: StationSearch? = null
    private var results: List<Station> = emptyList()
    private val adapter = ResultsAdapter()
    private var choosing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PanelApp
        val firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)

        val root = Ui.column(this)
        if (firstRun) {
            root.addView(Ui.title(this, "Bienvenido. Elige tu estación de Cercanías"))
            root.addView(Ui.text(this, "Se puede cambiar cuando quieras desde la rueda dentada del panel lateral."))
        }
        input = EditText(this).apply {
            hint = getString(R.string.search_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 22f
            isSingleLine = true
            isEnabled = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = runSearch()
            })
        }
        root.addView(input)
        status = Ui.text(this, "")
        root.addView(status)
        val list = ListView(this).apply {
            adapter = this@StationPickerActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> choose(results[pos]) }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        scope.launch { app.engine.busy.collect { if (it != null) status.text = it } }
        scope.launch {
            status.text = "Cargando la lista de estaciones…"
            val index = app.engine.ensureIndex()
            if (index == null) {
                status.text = "No se pudo descargar el horario de Renfe. Comprueba la conexión y vuelve a entrar."
                return@launch
            }
            search = StationSearch(index)
            status.text = "${index.stations.size} estaciones con servicio."
            input.isEnabled = true
            input.requestFocus()
            runSearch()
        }
    }

    private fun runSearch() {
        val s = search ?: return
        results = s.search(input.text.toString())
        adapter.notifyDataSetChanged()
    }

    private fun choose(station: Station) {
        if (choosing) return
        choosing = true
        input.isEnabled = false
        status.text = "Procesando el horario de ${station.name}…"
        app.engine.changeStation(station.id) { ok ->
            choosing = false
            if (ok) {
                finish()
            } else {
                input.isEnabled = true
                status.text = "No se pudo preparar el horario de ${station.name}. Inténtalo de nuevo."
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private inner class ResultsAdapter : BaseAdapter() {
        override fun getCount() = results.size
        override fun getItem(position: Int) = results[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val st = results[position]
            val subtitle = st.networks.joinToString(", ") + " · " + st.lines.joinToString(", ")
            return Ui.row(this@StationPickerActivity, st.name, subtitle) { choose(st) }
        }
    }

    companion object {
        const val EXTRA_FIRST_RUN = "first_run"
    }
}
