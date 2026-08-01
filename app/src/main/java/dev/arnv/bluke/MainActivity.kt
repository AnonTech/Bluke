package dev.arnv.bluke

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.sound.KeyboardSoundSynthesizer
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.HomeScreen

class MainActivity : ComponentActivity() {
    companion object {
        @android.annotation.SuppressLint("StaticFieldLeak")
        private var btManagerInstance: BluetoothKeyboardManager? = null
    }

    private lateinit var btManager: BluetoothKeyboardManager
    private lateinit var soundSynth: KeyboardSoundSynthesizer

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Notify Bluetooth service to re-check status after user interaction
        btManager.checkBluetoothCapabilities()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("has_seen_onboarding", false)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Initialize or reuse services safely against missing HID framework classes
        dev.arnv.bluke.utils.DeveloperLogManager.init(applicationContext)
        
        if (btManagerInstance == null) {
            try {
                btManagerInstance = BluetoothKeyboardManager(applicationContext)
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Failed to initialize BluetoothKeyboardManager", e)
            }
        }
        if (btManagerInstance != null) {
            btManager = btManagerInstance!!
        }

        soundSynth = KeyboardSoundSynthesizer(applicationContext)

        // Request Bluetooth and Location permissions dynamically
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HomeScreen(
                    btManager = btManager,
                    soundSynth = soundSynth
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::btManager.isInitialized) {
            btManager.checkBluetoothCapabilities()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundSynth.isInitialized) {
            soundSynth.release()
        }
        if (isFinishing) {
            if (::btManager.isInitialized) {
                btManager.close()
                btManagerInstance = null
            }
        }
    }
}
