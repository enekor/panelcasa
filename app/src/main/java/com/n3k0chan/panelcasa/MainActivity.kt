package com.n3k0chan.panelcasa

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Launcher: panel lateral de apps a la izquierda, columna de Google Home solo con Android 10 o posterior,
 * y el panel web de meteo y Renfe en el resto.
 */
class MainActivity : Activity() {
    private lateinit var app: PanelApp
    private lateinit var apps: Apps
    private lateinit var sidebar: Sidebar
    private lateinit var webContainer: FrameLayout
    private lateinit var errorView: TextView
    private lateinit var nightOverlay: View
    private var webView: WebView? = null
    private var pageReady = false
    private var stationPrompted = false
    private var nightDismissedUntil = 0L
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshSidebar()
    }

    private val nightTick = object : Runnable {
        override fun run() {
            applyScreenState()
            handler.postDelayed(this, 30_000)
        }
    }

    private val retryLoad = Runnable { webView?.loadUrl(PANEL_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PanelApp
        apps = Apps(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sidebar = Sidebar(
            this,
            onApp = { apps.launch(it) },
            onAllApps = { startActivity(Intent(this, AllAppsActivity::class.java)) },
            onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
        )
        row.addView(sidebar, LinearLayout.LayoutParams(Ui.dp(this, Sidebar.WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT))

        val homeEnabled = VersionGate.homeColumnVisible()
        if (homeEnabled) {
            // Solo aquí se crea la columna de casa: por debajo de Android 10 no existe ni deja hueco.
            row.addView(HomeColumn(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        webContainer = FrameLayout(this).apply { tag = "web-container" }
        row.addView(webContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (homeEnabled) 2f else 1f))
        root.addView(row)

        errorView = TextView(this).apply {
            setTextColor(Ui.DIM)
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0F1317.toInt())
            visibility = View.GONE
        }
        createWebView()
        webContainer.addView(errorView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        nightOverlay = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            visibility = View.GONE
            isClickable = true
            tag = "night-overlay"
            setOnClickListener {
                // Despertar al tocar: 2 minutos con el panel visible.
                nightDismissedUntil = System.currentTimeMillis() + 2 * 60_000L
                applyScreenState()
            }
        }
        root.addView(nightOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        if (!app.prefs.pinnedInitialized) {
            app.prefs.pinnedApps = apps.defaultPinned()
            app.prefs.pinnedInitialized = true
        }
        refreshSidebar()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // Son difusiones del sistema: llegan aunque el receptor no se exporte.
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }

        app.diagnostics.webViewVersion = webViewVersion()
        app.diagnostics.event("Launcher creado · columna de casa: ${if (homeEnabled) "sí" else "no"} · WebView ${app.diagnostics.webViewVersion}")

        scope.launch { app.engine.panel.collect { push(it) } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        pageReady = false
        val wv = WebView(this).apply {
            tag = "panel-webview"
            setBackgroundColor(0xFF0F1317.toInt())
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isLongClickable = false
            setOnLongClickListener { true }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                setGeolocationEnabled(false)
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = true
                allowContentAccess = false
            }
            webViewClient = PanelClient()
        }
        webView = wv
        webContainer.addView(wv, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        wv.loadUrl(PANEL_URL)
    }

    private inner class PanelClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            pageReady = true
            errorView.visibility = View.GONE
            push(app.engine.panel.value)
        }

        // El panel no navega a ningún sitio.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

        @Deprecated("API < 24")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            pageReady = false
            showError("No se pudo cargar el panel (error ${error.errorCode}). Reintento en 30 s.")
            handler.removeCallbacks(retryLoad)
            handler.postDelayed(retryLoad, 30_000)
        }

        /** Android 8+: si el proceso del WebView muere, se crea uno nuevo en vez de cerrar el launcher. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            app.diagnostics.event("El WebView se cerró inesperadamente; se vuelve a crear")
            webContainer.removeView(view)
            view.destroy()
            webView = null
            createWebView()
            return true
        }
    }

    private fun showError(text: String) {
        errorView.text = text
        errorView.visibility = View.VISIBLE
        app.diagnostics.event(text)
    }

    private fun push(json: String) {
        val wv = webView ?: return
        if (!pageReady || json.isEmpty()) return
        wv.evaluateJavascript("window.panel&&window.panel.update($json)", null)
        app.diagnostics.markPanelShown()
    }

    private fun refreshSidebar() {
        sidebar.setApps(apps.pinned(app.prefs.pinnedApps))
    }

    override fun onStart() {
        super.onStart()
        app.engine.onForeground()
        refreshSidebar()
        handler.post(nightTick)
        if (!app.engine.hasStation && !stationPrompted) {
            stationPrompted = true
            startActivity(Intent(this, StationPickerActivity::class.java).putExtra(StationPickerActivity.EXTRA_FIRST_RUN, true))
        }
    }

    override fun onStop() {
        super.onStop()
        // Otra app en primer plano: se pausan las descargas.
        app.engine.onBackground()
        handler.removeCallbacks(nightTick)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        applyScreenState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Launcher: el botón de atrás no hace nada")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Es la pantalla de inicio: no hay a dónde volver.
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(packageReceiver) }
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    /** Horario de pantalla y brillo. De noche: capa negra con el brillo al mínimo, que se aparta al tocar. */
    private fun applyScreenState() {
        val p = app.prefs
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = ScreenSchedule.isNight(p.nightEnabled, p.nightFrom, p.nightTo, hour) &&
            System.currentTimeMillis() > nightDismissedUntil
        val target = if (night) View.VISIBLE else View.GONE
        if (nightOverlay.visibility != target) nightOverlay.visibility = target
        val lp = window.attributes
        val brightness = when {
            night -> 0.01f
            p.brightness in 0..100 -> (p.brightness.coerceAtLeast(1)) / 100f
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (lp.screenBrightness != brightness) {
            lp.screenBrightness = brightness
            window.attributes = lp
        }
    }

    private fun webViewVersion(): String {
        val ua = runCatching { WebSettings.getDefaultUserAgent(this) }.getOrNull() ?: return "?"
        return Regex("Chrome/([0-9.]+)").find(ua)?.groupValues?.get(1) ?: ua
    }

    companion object {
        const val PANEL_URL = "file:///android_asset/panel/index.html"
    }
}
