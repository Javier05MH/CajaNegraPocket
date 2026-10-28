package es.kinetrace.cajanegrapocket

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity(), SensorEventListener, LocationListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private lateinit var speedView: TextView
    private lateinit var statusView: TextView
    private lateinit var sensorsView: TextView
    private lateinit var geoidOffsetInput: EditText
    private lateinit var geoidModelInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val sensors = mutableListOf<Sensor>()
    private val sensorValues = mutableMapOf<Sensor, FloatArray>()
    private val sensorColumns = mutableListOf<Pair<Sensor, List<String>>>()
    private var latestLocation: Location? = null
    private var isRecording = false
    private var writer: BufferedWriter? = null
    private var activeFile: File? = null
    private var sampleCount = 0L
    private var lastUiUpdate = 0L

    companion object {
        private const val REQUEST_LOCATION = 101
        private const val CREATE_CSV = 102
        private const val SAMPLE_PERIOD_MS = 100L // 10 Hz
        private val UTC_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(15, 23, 42)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        buildScreen()
        refreshSensors()
        updateButtons()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            setBackgroundColor(android.graphics.Color.rgb(248, 250, 252))
        }
        scroll.addView(root)

        val speedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(24), dp(12), dp(24))
            setBackgroundColor(android.graphics.Color.rgb(15, 23, 42))
        }
        speedView = TextView(this).apply {
            text = "— km/h"
            textSize = 48f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        }
        speedPanel.addView(speedView)
        speedPanel.addView(TextView(this).apply {
            text = "VELOCIDAD GPS"
            textSize = 12f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(203, 213, 225))
        })
        val speedParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        speedParams.setMargins(0, dp(22), 0, dp(15))
        root.addView(speedPanel, speedParams)

        // El pliego exige que la velocidad sea el primer dato visible al abrir la app.
        root.addView(TextView(this).apply {
            text = "CajaNegra Pocket · KineTrace Labs"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.rgb(13, 148, 136))
            setPadding(0, dp(4), 0, dp(3))
        })
        root.addView(TextView(this).apply {
            text = "Registro de ensayos dinámicos · Android"
            textSize = 12f
            setTextColor(android.graphics.Color.DKGRAY)
        })

        statusView = TextView(this).apply {
            text = "Preparada. Pulsa Iniciar para crear un registro."
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(51, 65, 85))
            setPadding(0, dp(8), 0, dp(10))
        }
        root.addView(statusView)
        sensorsView = TextView(this).apply {
            textSize = 13f
            setTextColor(android.graphics.Color.rgb(71, 85, 105))
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(sensorsView)

        root.addView(TextView(this).apply {
            text = "Corrección de geoide (opcional)"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.rgb(15, 23, 42))
        })
        geoidModelInput = EditText(this).apply {
            hint = "Modelo usado (p. ej., EGM96)"
            setSingleLine(true)
            textSize = 14f
        }
        root.addView(geoidModelInput)
        geoidOffsetInput = EditText(this).apply {
            hint = "N: ondulación del geoide en metros (si conocida)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            textSize = 14f
        }
        root.addView(geoidOffsetInput)
        root.addView(TextView(this).apply {
            text = "La altura ortométrica aproximada se calcula como H = h − N. No introduzcas un valor inventado: si no conoces N del modelo en la zona de ensayo, deja el campo vacío y esa columna quedará vacía."
            textSize = 12f
            setTextColor(android.graphics.Color.rgb(100, 116, 139))
            setPadding(0, dp(4), 0, dp(14))
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = Button(this).apply {
            text = "Iniciar"
            setOnClickListener { startFlow() }
        }
        stopButton = Button(this).apply {
            text = "Detener y exportar"
            setOnClickListener { stopAndExport() }
        }
        buttons.addView(startButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(stopButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)
        root.addView(TextView(this).apply {
            text = "Muestreo configurado: 10 Hz (cada 100 ms). Mantén el teléfono sujeto y deja que lo maneje una persona acompañante; quien conduce no debe manipularlo."
            textSize = 12f
            setTextColor(android.graphics.Color.rgb(100, 116, 139))
            setPadding(0, dp(15), 0, 0)
        })
        setContentView(scroll)
    }

    private fun refreshSensors() {
        sensors.clear()
        sensors.addAll(sensorManager.getSensorList(Sensor.TYPE_ALL).sortedWith(compareBy({ it.type }, { it.name })))
        sensorsView.text = buildString {
            append("Sensores detectados: ${sensors.size}. ")
            append("Acelerómetro: ${hasType(Sensor.TYPE_ACCELEROMETER)} · ")
            append("Magnetómetro: ${hasType(Sensor.TYPE_MAGNETIC_FIELD)} · ")
            append("Luz: ${hasType(Sensor.TYPE_LIGHT)}")
        }
    }

    private fun hasType(type: Int): String = if (sensors.any { it.type == type }) "sí" else "no disponible"

    private fun startFlow() {
        if (isRecording) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
            return
        }
        startRecording()
    }

    private fun startRecording() {
        try {
            refreshSensors()
            val now = System.currentTimeMillis()
            val fileName = "CajaNegraPocket_${DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(java.time.LocalDateTime.now())}.csv"
            activeFile = File(cacheDir, fileName)
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(activeFile!!), Charsets.UTF_8), 64 * 1024)
            sampleCount = 0L
            sensorValues.clear()
            sensorColumns.clear()
            sensors.forEachIndexed { index, sensor ->
                val label = safeName(sensor.name).ifBlank { "sensor" }
                val axes = when (sensor.type) {
                    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GYROSCOPE,
                    Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY, Sensor.TYPE_ROTATION_VECTOR,
                    Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                    Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> listOf("x", "y", "z", "v4", "v5", "v6")
                    Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_PROXIMITY,
                    Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_AMBIENT_TEMPERATURE -> listOf("value")
                    else -> listOf("v1", "v2", "v3", "v4", "v5", "v6")
                }
                val count = sensor.maxValueCountGuess()
                val names = (0 until count).map { i ->
                    val axis = when {
                        count == 1 -> axes.firstOrNull() ?: "v1"
                        i < axes.size -> axes[i]
                        else -> "v${i + 1}"
                    }
                    if (count == 1) "s${index + 1}_t${sensor.type}_${label}_${unitToken(sensor.type)}"
                    else "s${index + 1}_t${sensor.type}_${label}_${unitToken(sensor.type)}_$axis"
                }
                sensorColumns.add(sensor to names)
            }
            writer?.write("\uFEFF") // BOM para mejorar la detección de UTF-8 en hojas de cálculo.
            writer?.write((baseHeaders() + sensorColumns.flatMap { it.second }).joinToString(","))
            writer?.newLine()
            writer?.flush()

            var registered = 0
            sensors.forEach { sensor ->
                if (sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) registered++
            }
            try {
                val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val provider = if (fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    LocationManager.GPS_PROVIDER
                } else {
                    LocationManager.NETWORK_PROVIDER
                }
                locationManager.requestLocationUpdates(provider, 250L, 0f, this, Looper.getMainLooper())
            } catch (_: Exception) {
                // La captura de sensores puede continuar si el proveedor de posición no está disponible.
            }
            isRecording = true
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            statusView.text = "Grabando… ${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(now))} · sensores registrados: $registered"
            updateButtons()
            handler.post(sampleRunnable)
        } catch (e: Exception) {
            closeWriter()
            isRecording = false
            statusView.text = "No se pudo iniciar: ${e.message ?: "error de almacenamiento"}"
            updateButtons()
        }
    }

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            try {
                writeSample()
                sampleCount++
                if (SystemClock.elapsedRealtime() - lastUiUpdate > 500L) {
                    statusView.text = "Grabando · muestras: $sampleCount · ${if (latestLocation == null) "esperando GPS" else "GPS recibido"}"
                    lastUiUpdate = SystemClock.elapsedRealtime()
                }
                if (sampleCount % 50L == 0L) writer?.flush()
            } catch (e: Exception) {
                statusView.text = "Aviso de escritura: ${e.message ?: "error"}"
            }
            handler.postDelayed(this, SAMPLE_PERIOD_MS)
        }
    }

    private fun baseHeaders(): List<String> = listOf(
        "registro_tipo", "timestamp_utc", "tiempo_monotonico_ns", "velocidad_gps_m_s", "velocidad_gps_km_h",
        "latitud_deg", "longitud_deg", "zona_utm", "utm_este_m", "utm_norte_m",
        "altura_elipsoidal_m", "ondulacion_geoide_N_m", "altura_ortometrica_aprox_m",
        "precision_horizontal_m", "rumbo_deg", "proveedor_posicion"
    )

    private fun writeSample() {
        val loc = latestLocation
        val timestamp = Instant.now()
        val geoN = geoidOffsetInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val utm = if (loc != null) UtmConverter.fromLatLon(loc.latitude, loc.longitude) else null
        val h = if (loc != null && loc.hasAltitude()) loc.altitude else null
        val orthometric = if (h != null && geoN != null) h - geoN else null
        val speed = if (loc != null && loc.hasSpeed()) loc.speed.toDouble() else null
        val row = mutableListOf<String>()
        fun add(value: Any?) { row.add(value?.toString() ?: "") }
        add("CAPTURA_REAL")
        add(UTC_FORMAT.format(timestamp))
        add(SystemClock.elapsedRealtimeNanos())
        add(speed?.fmt())
        add(speed?.times(3.6)?.fmt())
        add(loc?.latitude?.fmt())
        add(loc?.longitude?.fmt())
        add(utm?.zoneLabel)
        add(utm?.eastingMeters?.fmt())
        add(utm?.northingMeters?.fmt())
        add(h?.fmt())
        add(geoN?.fmt())
        add(orthometric?.fmt())
        add(if (loc != null && loc.hasAccuracy()) loc.accuracy.toDouble().fmt() else null)
        add(if (loc != null && loc.hasBearing()) loc.bearing.toDouble().fmt() else null)
        add(loc?.provider)

        sensorColumns.forEach { (sensor, names) ->
            val values = sensorValues[sensor]
            names.indices.forEach { i -> add(values?.getOrNull(i)?.toDouble()?.fmt()) }
        }
        writer?.write(row.joinToString(",") { csvEscape(it) })
        writer?.newLine()
    }

    private fun stopAndExport() {
        if (!isRecording) return
        isRecording = false
        handler.removeCallbacks(sampleRunnable)
        sensorManager.unregisterListener(this)
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        closeWriter()
        updateButtons()
        val file = activeFile
        if (file == null || !file.exists()) {
            statusView.text = "Registro detenido, pero no se creó el fichero."
            return
        }
        statusView.text = "Registro detenido: $sampleCount muestras. Elige dónde guardar el CSV."
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivityForResult(intent, CREATE_CSV)
    }

    private fun closeWriter() {
        try { writer?.flush() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        writer = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_CSV && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            val file = activeFile
            if (uri == null || file == null || !file.exists()) {
                statusView.text = "No se pudo completar la exportación."
                return
            }
            try {
                contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
                statusView.text = "CSV exportado correctamente · $sampleCount muestras."
                Toast.makeText(this, "CSV guardado", Toast.LENGTH_SHORT).show()
                file.delete()
            } catch (e: Exception) {
                statusView.text = "Error al exportar: ${e.message ?: "error de escritura"}"
            }
        } else if (requestCode == CREATE_CSV) {
            statusView.text = "CSV conservado temporalmente en el dispositivo; no se eligió destino."
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startRecording()
            else statusView.text = "Permiso de ubicación denegado. Los sensores están disponibles, pero no habrá posición GPS."
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRecording) return
        sensorValues[event.sensor] = event.values.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    override fun onLocationChanged(location: Location) {
        latestLocation = location
        if (location.hasSpeed()) speedView.text = "${(location.speed * 3.6f).roundToInt()} km/h"
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 26")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
    override fun onProviderEnabled(provider: String) { }
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) speedView.text = "— km/h"
    }

    override fun onResume() {
        super.onResume()
        refreshSensors()
    }

    override fun onDestroy() {
        if (isRecording) {
            isRecording = false
            handler.removeCallbacks(sampleRunnable)
            sensorManager.unregisterListener(this)
            try { locationManager.removeUpdates(this) } catch (_: Exception) {}
            closeWriter()
        }
        super.onDestroy()
    }

    private fun updateButtons() {
        startButton.isEnabled = !isRecording
        stopButton.isEnabled = isRecording
    }

    private fun Sensor.maxValueCountGuess(): Int = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY -> 3
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> 6
        Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_PROXIMITY,
        Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_AMBIENT_TEMPERATURE -> 1
        else -> 3
    }

    private fun unitToken(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY -> "m_s2"
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "uT"
        Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad_s"
        Sensor.TYPE_LIGHT -> "lux"
        Sensor.TYPE_PRESSURE -> "hpa"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "percent"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "degc"
        else -> "raw"
    }

    private fun safeName(input: String): String = input.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "_").trim('_').take(36)

    private fun csvEscape(value: String): String = if (value.contains(',') || value.contains('"') || value.contains('\n'))
        "\"${value.replace("\"", "\"\"")}\"" else value

    private fun Double.fmt(): String = String.format(Locale.US, "%.6f", this)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
