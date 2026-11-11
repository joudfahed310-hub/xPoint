package com.hazbu.xpoint

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.hazbu.xpoint.Constants.AUTHORITY
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MIN
import com.hazbu.xpoint.Constants.DEFAULT_ALTITUDE
import com.hazbu.xpoint.Constants.DEFAULT_IS_ENABLED
import com.hazbu.xpoint.Constants.DEFAULT_JITTER_RADIUS
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.KEY_ACCURACY_MAX
import com.hazbu.xpoint.Constants.KEY_ACCURACY_MIN
import com.hazbu.xpoint.Constants.KEY_ALTITUDE
import com.hazbu.xpoint.Constants.KEY_IS_ENABLED
import com.hazbu.xpoint.Constants.KEY_JITTER_RADIUS
import com.hazbu.xpoint.Constants.KEY_LAT
import com.hazbu.xpoint.Constants.KEY_LONG
import com.hazbu.xpoint.Constants.KEY_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.KEY_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.PREFS_NAME
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnableSpoof: MaterialSwitch
    private lateinit var sliderAccuracyRange: RangeSlider
    private lateinit var sliderVAccuracyRange: RangeSlider
    private lateinit var sliderAltitude: Slider
    private lateinit var sliderJitterRadius: Slider
    private lateinit var etLatitude: TextInputEditText
    private lateinit var etLongitude: TextInputEditText
    private lateinit var etStepMeters: TextInputEditText
    private lateinit var mapView: MapView
    private lateinit var progressLongPress: ProgressBar
    private var currentMarker: Marker? = null
    private var isJoystickRunning = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)
        initViews()
        setupMap()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    private fun initViews() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        etLatitude = findViewById(R.id.et_latitude)
        etLongitude = findViewById(R.id.et_longitude)
        etStepMeters = findViewById(R.id.et_step_meters)
        switchEnableSpoof = findViewById(R.id.switch_enable_spoof)
        sliderAltitude = findViewById(R.id.slider_altitude)
        sliderJitterRadius = findViewById(R.id.slider_jitter_radius)
        sliderAccuracyRange = findViewById(R.id.slider_accuracy_range)
        sliderVAccuracyRange = findViewById(R.id.slider_v_accuracy_range)

        etLatitude.setText(prefs.getString(KEY_LAT, DEFAULT_LAT.toString()))
        etLongitude.setText(prefs.getString(KEY_LONG, DEFAULT_LONG.toString()))
        switchEnableSpoof.isChecked = prefs.getBoolean(KEY_IS_ENABLED, DEFAULT_IS_ENABLED)

        sliderAltitude.value = (prefs.getString(KEY_ALTITUDE, DEFAULT_ALTITUDE.toString())?.toFloatOrNull() ?: 25f).coerceIn(0f, 1000f)
        sliderJitterRadius.value = prefs.getFloat(KEY_JITTER_RADIUS, DEFAULT_JITTER_RADIUS).coerceIn(0f, 50f)
        sliderAccuracyRange.setValues(prefs.getFloat(KEY_ACCURACY_MIN, DEFAULT_ACCURACY_MIN).coerceIn(0f, 50f), prefs.getFloat(KEY_ACCURACY_MAX, DEFAULT_ACCURACY_MAX).coerceIn(0f, 50f))
        sliderVAccuracyRange.setValues(prefs.getFloat(KEY_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MIN).coerceIn(0f, 50f), prefs.getFloat(KEY_V_ACCURACY_MAX, DEFAULT_V_ACCURACY_MAX).coerceIn(0f, 50f))

        switchEnableSpoof.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_IS_ENABLED, isChecked).apply()
            contentResolver.notifyChange("content://$AUTHORITY".toUri(), null)
        }

        findViewById<Button>(R.id.btn_save_favorite).setOnClickListener {
            showSaveFavoriteDialog(etLatitude.text.toString(), etLongitude.text.toString())
        }

        findViewById<Button>(R.id.btn_show_favorites).setOnClickListener {
            showFavoritesListDialog()
        }

        findViewById<Button>(R.id.btn_toggle_joystick).setOnClickListener {
            toggleJoystick()
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            applyNewLocation(etLatitude.text.toString(), etLongitude.text.toString(), moveMap = true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        mapView = findViewById(R.id.map_view)
        progressLongPress = findViewById(R.id.progress_long_press)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val lat = etLatitude.text.toString().toDoubleOrNull() ?: 21.4225
        val lon = etLongitude.text.toString().toDoubleOrNull() ?: 39.8262
        val startPoint = GeoPoint(if (lat == 0.0) 21.4225 else lat, if (lon == 0.0) 39.8262 else lon)

        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(startPoint)
        updateMarker(startPoint)

        var downX = 0f
        var downY = 0f
        var startTime = 0L
        var holding = false

        val holdTask = object : Runnable {
            override fun run() {
                if (!holding) return
                val elapsed = System.currentTimeMillis() - startTime
                progressLongPress.progress = elapsed.toInt().coerceAtMost(3000)
                if (elapsed >= 3000L) {
                    holding = false
                    progressLongPress.visibility = View.GONE
                    val pt = mapView.projection.fromPixels(downX.toInt(), downY.toInt()) as GeoPoint
                    val latStr = "%.6f".format(Locale.US, pt.latitude)
                    val lonStr = "%.6f".format(Locale.US, pt.longitude)
                    applyNewLocation(latStr, lonStr, moveMap = false)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("تم تحديد الموقع")
                        .setMessage("هل تريد حفظ الموقع في المفضلة باسم من اختيارك؟")
                        .setPositiveButton("نعم") { _, _ -> showSaveFavoriteDialog(latStr, lonStr) }
                        .setNegativeButton("لا", null)
                        .show()
                } else {
                    handler.postDelayed(this, 50L)
                }
            }
        }

        mapView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> if (event.pointerCount == 1) {
                    downX = event.x; downY = event.y; startTime = System.currentTimeMillis(); holding = true
                    progressLongPress.progress = 0; progressLongPress.visibility = View.VISIBLE
                    handler.postDelayed(holdTask, 50L)
                }
                MotionEvent.ACTION_MOVE -> if (holding && (abs(event.x - downX) > 25f || abs(event.y - downY) > 25f || event.pointerCount > 1)) {
                    holding = false; progressLongPress.visibility = View.GONE; handler.removeCallbacks(holdTask)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holding = false; progressLongPress.visibility = View.GONE; handler.removeCallbacks(holdTask)
                }
            }
            false
        }
    }

    private fun applyNewLocation(latStr: String, lonStr: String, moveMap: Boolean) {
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            Toast.makeText(this, "إحداثيات غير صحيحة", Toast.LENGTH_SHORT).show()
            return
        }
        etLatitude.setText(latStr)
        etLongitude.setText(lonStr)
        switchEnableSpoof.isChecked = true

        val pt = GeoPoint(lat, lon)
        if (moveMap) mapView.controller.animateTo(pt)
        updateMarker(pt)

        val accValues = sliderAccuracyRange.values
        val vAccValues = sliderVAccuracyRange.values

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString(KEY_LAT, latStr)
            putString(KEY_LONG, lonStr)
            putString(KEY_ALTITUDE, sliderAltitude.value.toDouble().toString())
            putFloat(KEY_ACCURACY_MIN, accValues[0])
            putFloat(KEY_ACCURACY_MAX, accValues[1])
            putFloat(KEY_V_ACCURACY_MIN, vAccValues[0])
            putFloat(KEY_V_ACCURACY_MAX, vAccValues[1])
            putFloat(KEY_JITTER_RADIUS, sliderJitterRadius.value)
            putBoolean(KEY_IS_ENABLED, true)
            apply()
        }
        contentResolver.notifyChange("content://$AUTHORITY".toUri(), null)
        Toast.makeText(this, "تم تثبيت الموقع بنجاح", Toast.LENGTH_SHORT).show()
    }

    private fun updateMarker(pt: GeoPoint) {
        if (currentMarker == null) {
            currentMarker = Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
            mapView.overlays.add(currentMarker)
        }
        currentMarker?.position = pt
        mapView.invalidate()
    }

    private fun showSaveFavoriteDialog(latStr: String, lonStr: String) {
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        if (lat == null || lon == null) {
            Toast.makeText(this, "الرجاء تحديد إحداثيات صحيحة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = "اكتب اسم الموقع (مثلاً: العمل، المنزل)"
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("إضافة إلى المفضلة")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "موقع محفوظ" }
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val array = JSONArray(prefs.getString("favorites_list", "[]") ?: "[]")
                val obj = JSONObject().apply { put("name", name); put("lat", latStr); put("lon", lonStr) }
                array.put(obj)
                prefs.edit().putString("favorites_list", array.toString()).apply()
                Toast.makeText(this, "تم حفظ ($name) في المفضلة", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showFavoritesListDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val array = JSONArray(prefs.getString("favorites_list", "[]") ?: "[]")
        if (array.length() == 0) {
            Toast.makeText(this, "قائمة المفضلة فارغة حالياً", Toast.LENGTH_SHORT).show()
            return
        }
        val names = Array(array.length()) { i ->
            val obj = array.getJSONObject(i)
            "${obj.getString("name")} (${obj.getString("lat")}, ${obj.getString("lon")})"
        }
        AlertDialog.Builder(this)
            .setTitle("اختر موقعاً من المفضلة")
            .setItems(names) { _, which ->
                val selected = array.getJSONObject(which)
                applyNewLocation(selected.getString("lat"), selected.getString("lon"), moveMap = true)
            }
            .setNeutralButton("مسح الكل") { _, _ ->
                prefs.edit().remove("favorites_list").apply()
                Toast.makeText(this, "تم مسح المفضلة", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun toggleJoystick() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val btn = findViewById<Button>(R.id.btn_toggle_joystick)
        if (isJoystickRunning) {
            stopService(Intent(this, JoystickService::class.java))
            isJoystickRunning = false
            btn.text = "تشغيل الزر العائم"
        } else {
            val step = (etStepMeters.text.toString().toDoubleOrNull() ?: 1.0).coerceAtLeast(1.0)
            val intent = Intent(this, JoystickService::class.java).putExtra("STEP_METERS", step)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            isJoystickRunning = true
            btn.text = "إيقاف الزر العائم"
        }
    }

    fun checkSelfActive(): Boolean = false
}
