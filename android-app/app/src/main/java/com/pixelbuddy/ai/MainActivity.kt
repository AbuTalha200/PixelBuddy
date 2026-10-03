package com.pixelbuddy.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pixelbuddy.ai.capture.ScreenCaptureService
import com.pixelbuddy.ai.capture.VisionCredentialStore
import com.pixelbuddy.ai.overlay.PixelOverlayService
import com.pixelbuddy.ai.overlay.PixelSmileyView
import com.pixelbuddy.ai.overlay.SmileyGender
import com.pixelbuddy.ai.overlay.SmileyPreferences

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var overlayButton: Button
    private lateinit var shareButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var stopButton: Button
    private lateinit var previewSmiley: PixelSmileyView
    private lateinit var maleButton: Button
    private lateinit var femaleButton: Button
    private lateinit var reactButton: Button

    private val keyStore by lazy { VisionCredentialStore(this) }
    private val smileyPrefs by lazy { SmileyPreferences(this) }
    private var startupFresh = false
    private var pendingAnalyze = false
    private var projectionRequestInFlight = false
    private var requestProjectionAfterOverlay = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Granted or not, the overlay still works; continue the normal startup flow.
        runStartupFlow()
    }

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlay()
            if (requestProjectionAfterOverlay) requestProjection()
        } else {
            setStatus("Floating permission was not granted. You can enable it here later.")
        }
        requestProjectionAfterOverlay = false
        refreshButtons()
    }

    private val screenPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        projectionRequestInFlight = false
        val token = result.data
        if (result.resultCode == Activity.RESULT_OK && token != null) {
            val analyzeImmediately = pendingAnalyze && keyStore.hasKey()
            pendingAnalyze = false
            try {
                ContextCompat.startForegroundService(
                    this,
                    ScreenCaptureService.startIntent(this, result.resultCode, token, analyzeImmediately)
                )
                setStatus("Screen sharing is on. Open an app, then tap Analyze screen in the tasks menu.")
                if (analyzeImmediately) moveTaskToBack(true)
            } catch (_: RuntimeException) {
                setStatus("Screen sharing could not start. Please try again.")
            }
        } else {
            setStatus("Screen sharing was not allowed. You can request it again here.")
        }
        refreshButtons()
        refreshButtonsSoon()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        overlayButton = findViewById(R.id.overlayPermissionButton)
        shareButton = findViewById(R.id.shareScreenButton)
        analyzeButton = findViewById(R.id.analyzeButton)
        stopButton = findViewById(R.id.stopSharingButton)
        previewSmiley = findViewById(R.id.previewSmiley)
        maleButton = findViewById(R.id.maleButton)
        femaleButton = findViewById(R.id.femaleButton)
        reactButton = findViewById(R.id.reactButton)

        pendingAnalyze = savedInstanceState?.getBoolean(STATE_PENDING_ANALYZE)
            ?: (intent?.action == ACTION_ANALYZE_FROM_OVERLAY)

        findViewById<Button>(R.id.saveKeyButton).setOnClickListener {
            val newKey = apiKeyInput.text.toString().trim()
            if (newKey.isEmpty()) {
                setStatus("Enter a Gemini API key before saving.")
            } else {
                try {
                    keyStore.save(newKey)
                    apiKeyInput.text.clear()
                    setStatus("Gemini key saved securely on this device.")
                } catch (_: Exception) {
                    setStatus("The key could not be saved. Please try again.")
                    return@setOnClickListener
                }
                if (pendingAnalyze) analyzeOrRequestPermission()
            }
        }

        overlayButton.setOnClickListener { requestOverlayPermission(false) }
        shareButton.setOnClickListener { requestProjection() }
        analyzeButton.setOnClickListener {
            pendingAnalyze = true
            analyzeOrRequestPermission()
        }
        stopButton.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            setStatus("Screen sharing stopped. No frames are being captured.")
            refreshButtons()
            refreshButtonsSoon()
        }

        maleButton.setOnClickListener { selectGender(SmileyGender.MALE) }
        femaleButton.setOnClickListener { selectGender(SmileyGender.FEMALE) }
        reactButton.setOnClickListener {
            previewSmiley.playReaction()
            PixelOverlayService.playReaction()
        }
        previewSmiley.setOnClickListener { previewSmiley.playReaction() }
        syncGender()
        previewSmiley.postDelayed({ previewSmiley.playReaction() }, 600L)

        refreshButtons()
        startupFresh = savedInstanceState == null && intent?.action != ACTION_OPEN_SETTINGS
        if (startupFresh && needsNotificationPermission()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runStartupFlow()
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun runStartupFlow() {
        val freshLaunch = startupFresh
        startupFresh = false
        if (!Settings.canDrawOverlays(this) && freshLaunch) {
            requestOverlayPermission(true)
        } else if (Settings.canDrawOverlays(this)) {
            startOverlay()
            if (freshLaunch && !ScreenCaptureService.isSessionActive) {
                if (pendingAnalyze && !keyStore.hasKey()) {
                    setStatus("Save your Gemini API key before analyzing the screen.")
                } else {
                    requestProjection()
                }
            }
        }
    }

    private fun selectGender(gender: SmileyGender) {
        if (smileyPrefs.gender != gender) {
            smileyPrefs.gender = gender // the overlay listens for this and updates itself
            syncGender()
        }
        previewSmiley.playReaction()
    }

    private fun syncGender() {
        val gender = smileyPrefs.gender
        previewSmiley.gender = gender
        maleButton.isSelected = gender == SmileyGender.MALE
        femaleButton.isSelected = gender == SmileyGender.FEMALE
        reactButton.setText(
            if (gender == SmileyGender.FEMALE) R.string.react_female else R.string.react_male
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_ANALYZE_FROM_OVERLAY) {
            pendingAnalyze = true
            analyzeOrRequestPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::overlayButton.isInitialized) {
            refreshButtons()
            syncGender() // the overlay menu can change the smiley while the app is in the background
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_ANALYZE, pendingAnalyze)
        super.onSaveInstanceState(outState)
    }

    private fun requestOverlayPermission(askForScreenAfter: Boolean) {
        if (Settings.canDrawOverlays(this)) {
            startOverlay()
            if (askForScreenAfter) requestProjection()
            return
        }

        requestProjectionAfterOverlay = askForScreenAfter
        overlayPermission.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun startOverlay() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, PixelOverlayService::class.java))
        } catch (_: RuntimeException) {
            setStatus("The floating assistant could not start. Check the overlay permission.")
        }
        refreshButtons()
    }

    private fun analyzeOrRequestPermission() {
        if (!keyStore.hasKey()) {
            setStatus("Save a Gemini API key first, then tap Analyze screen again.")
            return
        }

        if (ScreenCaptureService.isSessionActive) {
            pendingAnalyze = false
            ScreenCaptureService.requestAnalysis(this)
            setStatus("Analyzing the visible screen. Advice will appear beside the smiley.")
        } else {
            requestProjection()
        }
    }

    private fun requestProjection() {
        if (projectionRequestInFlight) return
        if (ScreenCaptureService.isSessionActive) {
            if (pendingAnalyze) analyzeOrRequestPermission()
            return
        }

        projectionRequestInFlight = true
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            screenPermission.launch(manager.createScreenCaptureIntent())
        } catch (_: RuntimeException) {
            projectionRequestInFlight = false
            setStatus("This device could not open the screen sharing request.")
        }
    }

    /** The capture service flips isSessionActive a moment after it is started or stopped. */
    private fun refreshButtonsSoon() {
        statusText.postDelayed({ if (!isFinishing) refreshButtons() }, 600L)
    }

    private fun refreshButtons() {
        overlayButton.isEnabled = !Settings.canDrawOverlays(this)
        shareButton.isEnabled = !ScreenCaptureService.isSessionActive
        stopButton.isEnabled = ScreenCaptureService.isSessionActive
        analyzeButton.isEnabled = true
        listOf(overlayButton, shareButton, stopButton, analyzeButton).forEach {
            it.alpha = if (it.isEnabled) 1f else 0.45f
        }
    }

    private fun setStatus(message: String) {
        statusText.text = message
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.pixelbuddy.ai.OPEN_SETTINGS"
        const val ACTION_ANALYZE_FROM_OVERLAY = "com.pixelbuddy.ai.ANALYZE_FROM_OVERLAY"
        private const val STATE_PENDING_ANALYZE = "pending_analyze"
    }
}