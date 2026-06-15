package com.lyvox.vault

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lyvox.vault.ui.navigation.LyvoxNavHost
import com.lyvox.vault.ui.theme.LyvoxTheme

/**
 * Main activity — single-activity architecture with Compose Navigation.
 *
 * Handles:
 * - Theme (dark/light/system)
 * - Auto-lock on background
 * - Clipboard auto-clear via lifecycle
 * - Secure window (disable screenshots)
 */
class MainActivity : FragmentActivity() {

    private val app: LyvoxApp get() = LyvoxApp.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Secure window — prevent screenshots and screen recording
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setAttributes(
                WindowManager.LayoutParams().apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            )
        }
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // Set status bar icons based on theme
        updateStatusBarIcons()

        setContent {
            // Reactive config from shared state — updates instantly when Settings changes it
            val config = app.themeConfig.value
            val isDark = when (config.theme) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }

            LyvoxTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LyvoxNavHost()
                }
            }
        }

        // Auto-lock when app goes to background
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP && app.sessionManager.isUnlocked) {
                    // Record when the app went to the background
                    app.sessionManager.recordActivity()
                } else if (event == Lifecycle.Event.ON_START && app.sessionManager.isUnlocked) {
                    val timeoutMinutes = app.settingsRepository.getAutoLockMinutes()
                    if (timeoutMinutes > 0) {
                        val elapsed = System.currentTimeMillis() - app.sessionManager.getLastActivityMs()
                        if (elapsed >= timeoutMinutes * 60 * 1000L) {
                            app.sessionManager.lock()
                            recreate()
                        }
                    }
                }
            }
        })

        // Periodic auto-lock check for idle timeout (every 30 seconds)
        val autoLockHandler = Handler(Looper.getMainLooper())
        val autoLockRunnable = object : Runnable {
            override fun run() {
                if (app.sessionManager.isUnlocked) {
                    val timeoutMinutes = app.settingsRepository.getAutoLockMinutes()
                    if (app.sessionManager.shouldAutoLock(timeoutMinutes)) {
                        app.sessionManager.lock()
                    }
                }
                autoLockHandler.postDelayed(this, 30_000L)
            }
        }
        autoLockHandler.postDelayed(autoLockRunnable, 30_000L)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        app.sessionManager.recordActivity()
    }

    /**
     * Updates status bar icon color (light/dark) based on current theme.
     * Uses deprecated API for older Android, modern API for API 35+.
     */
    private fun updateStatusBarIcons() {
        val config = app.settingsRepository.loadConfig()
        val isDark = when (config.theme) {
            "light" -> false
            "system" -> (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            else -> true
        }

        val window = this.window
        val decorView = window.decorView

        if (Build.VERSION.SDK_INT >= 35) { // API 35+ (VANILLA_ICE_CREAM)
            // Modern API
            WindowCompat.getInsetsController(window, decorView).isAppearanceLightStatusBars = !isDark
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23+
            // Deprecated but works on older versions
            val flags = decorView.systemUiVisibility
            decorView.systemUiVisibility = if (isDark) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    @Composable
    private fun isSystemInDarkTheme(): Boolean {
        return androidx.compose.foundation.isSystemInDarkTheme()
    }
}
