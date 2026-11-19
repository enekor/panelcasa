package com.n3k0chan.panelcasa

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Métricas para comprobar los criterios de aceptación sin depender de adb.
 * Todo se escribe también en logcat con la etiqueta «PanelCasa».
 */
class Diagnostics(private val context: Context) {
    private val events = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale("es", "ES"))

    @Volatile var firstPanelSinceBootMs: Long = -1
    @Volatile var firstPanelSinceProcessMs: Long = -1
    @Volatile var lastScanMs: Long = -1
    @Volatile var lastScanPeakHeapMb: Long = -1
    @Volatile var lastScanAt: Long = 0
    @Volatile var realtimeOkAt: Long = 0
    @Volatile var meteoOkAt: Long = 0
    @Volatile var staticCheckedAt: Long = 0
    @Volatile var webViewVersion: String = "?"
    @Volatile var webFeatures: String = "?"

    // Memoria y CPU a lo largo del tiempo, para la prueba de 24 horas sin adb.
    private var firstPssMb = -1
    private var maxPssMb = -1
    private val hourly = ArrayDeque<String>()
    private var lastHourlyAt = 0L
    private var lastCpuMs = 0L
    private var lastCpuAt = 0L
    private var recentCpuPct = -1.0
    private val sampler = Handler(Looper.getMainLooper())
    private val sampleTask = object : Runnable {
        override fun run() {
            sample()
            sampler.postDelayed(this, SAMPLE_MS)
        }
    }

    /** Empieza a tomar muestras cada 10 minutos. */
    fun startSampling() {
        sampler.removeCallbacks(sampleTask)
        sampler.post(sampleTask)
    }

    @Synchronized
    private fun sample() {
        val pss = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024
        if (firstPssMb < 0) firstPssMb = pss
        maxPssMb = maxOf(maxPssMb, pss)
        val now = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        if (lastCpuAt > 0 && now > lastCpuAt) recentCpuPct = 100.0 * (cpu - lastCpuMs) / (now - lastCpuAt)
        lastCpuMs = cpu
        lastCpuAt = now
        if (now - lastHourlyAt >= 3600_000L || lastHourlyAt == 0L) {
            lastHourlyAt = now
            hourly.addLast(SimpleDateFormat("dd/MM HH:mm", Locale("es", "ES")).format(Date()) + "  $pss MB")
            while (hourly.size > 48) hourly.removeFirst()
        }
    }

    @Synchronized
    fun event(message: String) {
        Log.i(PanelApp.TAG, message)
        events.addFirst(fmt.format(Date()) + "  " + message)
        while (events.size > 40) events.removeLast()
    }

    fun error(where: String, e: Throwable) {
        val cert = generateSequence(e) { it.cause }.any { it is javax.net.ssl.SSLException || it is java.security.cert.CertificateException }
        event((if (cert) "ERROR DE CERTIFICADO en " else "Error en ") + where + ": " + (e.message ?: e.javaClass.simpleName))
    }

    fun markPanelShown() {
        if (firstPanelSinceBootMs >= 0) return
        firstPanelSinceBootMs = SystemClock.elapsedRealtime()
        firstPanelSinceProcessMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        event("Panel visible: ${firstPanelSinceBootMs / 1000} s desde el arranque del sistema, ${firstPanelSinceProcessMs} ms desde el inicio de la app")
    }

    fun report(): String {
        val rt = Runtime.getRuntime()
        val mem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val sys = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        fun ago(t: Long) = if (t <= 0) "nunca" else fmt.format(Date(t))
        return buildString {
            appendLine("Panel de casa — diagnóstico ${fmt.format(Date())}")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("WebView: $webViewVersion")
            appendLine("Motor web: $webFeatures")
            appendLine("Columna de Google Home: ${when { VersionGate.homeColumnVisible() -> "activa"; !VersionGate.homeEnabled() -> "ausente (Android < 10)"; else -> "oculta (integración aplazada)" }}")
            appendLine()
            appendLine("Memoria de la app (PSS total): ${mem.totalPss / 1024} MB")
            appendLine("Heap Java: ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} MB usados de ${rt.maxMemory() / 1_048_576} MB")
            appendLine("Heap nativo: ${Debug.getNativeHeapAllocatedSize() / 1_048_576} MB")
            appendLine("Sistema: ${sys.availMem / 1_048_576} MB libres de ${sys.totalMem / 1_048_576} MB")
            val upMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            appendLine("Proceso activo desde hace: ${upMs / 60000} min")
            synchronized(this@Diagnostics) {
                appendLine("Memoria (PSS): al inicio ${if (firstPssMb < 0) "?" else "$firstPssMb MB"}, máximo ${if (maxPssMb < 0) "?" else "$maxPssMb MB"}")
                val avgCpu = if (upMs > 0) 100.0 * Process.getElapsedCpuTime() / upMs else 0.0
                appendLine(
                    String.format(
                        Locale("es", "ES"), "CPU media: %.1f %% desde el inicio; %s en los últimos 10 min (100 %% = un núcleo)",
                        avgCpu, if (recentCpuPct < 0) "?" else String.format(Locale("es", "ES"), "%.1f %%", recentCpuPct),
                    ),
                )
                if (hourly.isNotEmpty()) appendLine("Memoria cada hora: " + hourly.joinToString(" · "))
            }
            appendLine()
            appendLine("Arranque: panel visible a los ${if (firstPanelSinceBootMs < 0) "?" else (firstPanelSinceBootMs / 1000).toString()} s del encendido; ${firstPanelSinceProcessMs} ms desde el inicio de la app")
            appendLine("Último procesado del horario: ${if (lastScanMs < 0) "?" else "$lastScanMs ms"}, pico de heap ${if (lastScanPeakHeapMb < 0) "?" else "$lastScanPeakHeapMb MB"} (${ago(lastScanAt)})")
            appendLine("Última comprobación del horario: ${ago(staticCheckedAt)}")
            appendLine("Último tiempo real de Renfe: ${ago(realtimeOkAt)}")
            appendLine("Última meteo: ${ago(meteoOkAt)}")
            appendLine()
            appendLine("Sucesos recientes:")
            synchronized(this@Diagnostics) { events.forEach { appendLine("  $it") } }
        }
    }

    /** Escribe el informe en la carpeta de la app accesible por USB: Android/data/<paquete>/files/. Sin permisos. */
    fun export(): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val f = File(dir, "diagnostico-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".txt")
        f.writeText(report())
        event("Informe exportado a ${f.absolutePath}")
        return f
    }

    private companion object {
        const val SAMPLE_MS = 10 * 60_000L
    }
}
