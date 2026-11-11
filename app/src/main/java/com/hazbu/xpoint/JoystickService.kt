package com.hazbu.xpoint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.hazbu.xpoint.Constants.AUTHORITY
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.KEY_IS_ENABLED
import com.hazbu.xpoint.Constants.KEY_LAT
import com.hazbu.xpoint.Constants.KEY_LONG
import com.hazbu.xpoint.Constants.PREFS_NAME
import java.util.Locale
import kotlin.math.cos

class JoystickService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: GridLayout
    private var stepMeters: Double = 1.0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedStep = intent?.getDoubleExtra("STEP_METERS", 1.0) ?: 1.0
        stepMeters = if (requestedStep < 1.0) 1.0 else requestedStep
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupFloatingJoystick()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "xpoint_joystick_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "xPoint Joystick", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("xPoint Pro")
            .setContentText("الزر العائم نشط الآن")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1001, notification)
    }

package com.hazbu.xpoint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.hazbu.xpoint.Constants.AUTHORITY
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.KEY_IS_ENABLED
import com.hazbu.xpoint.Constants.KEY_LAT
import com.hazbu.xpoint.Constants.KEY_LONG
import com.hazbu.xpoint.Constants.PREFS_NAME
import kotlin.math.cos

class JoystickService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: GridLayout
    private var stepMeters: Double = 1.0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedStep = intent?.getDoubleExtra("STEP_METERS", 1.0) ?: 1.0
        stepMeters = if (requestedStep < 1.0) 1.0 else requestedStep
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupFloatingJoystick()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "xpoint_joystick_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "xPoint Joystick",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("xPoint Pro")
            .setContentText("الزر العائم للتحكم بالموقع نشط الآن")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1001, notification)
    }
    private fun setupFloatingJoystick() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#DD1E1E1E"))
            cornerRadius = 36f
            setStroke(2, Color.parseColor("#FF6200EE"))
        }

        floatingView = GridLayout(this).apply {
            rowCount = 3
            columnCount = 3
            background = bg
            setPadding(12, 12, 12, 12)
        }

        val btnUp = createDirButton("⬆️") { moveLocation(stepMeters, 0.0) }
        val btnDown = createDirButton("⬇️") { moveLocation(-stepMeters, 0.0) }
        val btnLeft = createDirButton("⬅️") { moveLocation(0.0, -stepMeters) }
        val btnRight = createDirButton("➡️") { moveLocation(0.0, stepMeters) }
        val btnClose = createDirButton("❌") { stopSelf() }

        val dragHandle = TextView(this).apply {
            text = "✥"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        floatingView.addView(createEmptySpace())
        floatingView.addView(btnUp)
        floatingView.addView(btnClose)
        floatingView.addView(btnLeft)
        floatingView.addView(dragHandle)
        floatingView.addView(btnRight)
        floatingView.addView(createEmptySpace())
        floatingView.addView(btnDown)
        floatingView.addView(createEmptySpace())

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    private fun createDirButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            minimumWidth = 0
            minimumHeight = 0
            setPadding(14, 14, 14, 14)
            setOnClickListener { onClick() }
        }
    }

    private fun createEmptySpace(): View {
        return View(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 10
                height = 10
            }
        }
    }

    private fun moveLocation(northMeters: Double, eastMeters: Double) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentLat = prefs.getString(KEY_LAT, DEFAULT_LAT.toString())?.toDoubleOrNull() ?: DEFAULT_LAT
        val currentLon = prefs.getString(KEY_LONG, DEFAULT_LONG.toString())?.toDoubleOrNull() ?: DEFAULT_LONG

        val latOffset = northMeters / 111111.0
        val newLat = (currentLat + latOffset).coerceIn(-90.0, 90.0)
        val lonOffset = eastMeters / (111111.0 * cos(Math.toRadians(newLat)))
        val newLon = (currentLon + lonOffset).coerceIn(-180.0, 180.0)

        prefs.edit().apply {
            putString(KEY_LAT, "%.6f".format(java.util.Locale.US, newLat))
            putString(KEY_LONG, "%.6f".format(java.util.Locale.US, newLon))
            putBoolean(KEY_IS_ENABLED, true)
            apply()
        }

        val uri = "content://$AUTHORITY".toUri()
        contentResolver.notifyChange(uri, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
