package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.*

class MainActivity : Activity() {

    private lateinit var etX: EditText
    private lateinit var etY: EditText
    private lateinit var etDelay: EditText
    private lateinit var etCount: EditText
    private lateinit var etRepeat: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Buat UI secara programmatic
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        etX = EditText(this).apply {
            hint = "Koordinat X (pixel)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        etY = EditText(this).apply {
            hint = "Koordinat Y (pixel)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        etDelay = EditText(this).apply {
            hint = "Delay (ms)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1000")
        }
        etCount = EditText(this).apply {
            hint = "Klik per siklus"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        etRepeat = EditText(this).apply {
            hint = "Ulangi (0 = tak terbatas)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("0")
        }

        btnStart = Button(this).apply { text = "Mulai" }
        btnStop = Button(this).apply { text = "Berhenti" }

        layout.addView(etX)
        layout.addView(etY)
        layout.addView(etDelay)
        layout.addView(etCount)
        layout.addView(etRepeat)

        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnLayout.addView(btnStart, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnLayout.addView(btnStop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(btnLayout)

        setContentView(layout)

        btnStart.setOnClickListener { startAutoClicker() }
        btnStop.setOnClickListener { stopService(Intent(this, AutoClickerService::class.java)) }
    }

    private fun startAutoClicker() {
        // Periksa apakah layanan aksesibilitas sudah diaktifkan
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabledServices == null || !enabledServices.contains(packageName)) {
            Toast.makeText(this, "Aktifkan AutoClicker di Pengaturan → Aksesibilitas", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val x = etX.text.toString().toFloatOrNull() ?: 0f
        val y = etY.text.toString().toFloatOrNull() ?: 0f
        val delay = etDelay.text.toString().toLongOrNull() ?: 1000L
        val count = etCount.text.toString().toIntOrNull() ?: 1
        val repeat = etRepeat.text.toString().toIntOrNull() ?: 0

        Intent(this, AutoClickerService::class.java).apply {
            putExtra("x", x)
            putExtra("y", y)
            putExtra("delay", delay)
            putExtra("count", count)
            putExtra("repeat", repeat)
            startService(this)
        }
        Toast.makeText(this, "AutoClicker dimulai", Toast.LENGTH_SHORT).show()
    }

    // Accessibility Service sebagai nested class
    class AutoClickerService : AccessibilityService() {
        private val handler = Handler(Looper.getMainLooper())
        private var running = false
        private var delayMs = 1000L
        private var countPerCycle = 1
        private var repeatCycles = 0
        private var x = 0f
        private var y = 0f
        private var cycleDone = 0

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            intent?.let {
                x = it.getFloatExtra("x", 0f)
                y = it.getFloatExtra("y", 0f)
                delayMs = it.getLongExtra("delay", 1000L)
                countPerCycle = it.getIntExtra("count", 1)
                repeatCycles = it.getIntExtra("repeat", 0)
            }
            if (!running) {
                running = true
                cycleDone = 0
                startCycles()
            }
            return START_STICKY
        }

        private fun startCycles() {
            if (!running) return
            if (repeatCycles > 0 && cycleDone >= repeatCycles) {
                stopSelf()
                return
            }
            performMultiClick(0)
        }

        private fun performMultiClick(clickIdx: Int) {
            if (!running) return
            if (clickIdx >= countPerCycle) {
                cycleDone++
                handler.postDelayed({ startCycles() }, delayMs)
                return
            }
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(desc: GestureDescription?) { performMultiClick(clickIdx + 1) }
                override fun onCancelled(desc: GestureDescription?) { performMultiClick(clickIdx + 1) }
            }, null)
        }

        override fun onServiceConnected() {
            super.onServiceConnected()
            // Konfigurasi service info tanpa file XML
            serviceInfo = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.DEFAULT
                canPerformGestures = true
                notificationTimeout = 100
            }
        }

        override fun onDestroy() {
            running = false
            handler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }

        override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
        override fun onInterrupt() { running = false }
    }
}
