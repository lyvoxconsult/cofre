package com.lyvox.vault

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.lyvox.vault.data.database.DatabaseHelper
import com.lyvox.vault.data.model.AppConfig
import com.lyvox.vault.data.repository.SettingsRepository
import com.lyvox.vault.security.SessionManager

/**
 * Application class — initializes shared dependencies.
 *
 * Lazy initialization ensures resources are only created when needed.
 */
class LyvoxApp : Application() {

    /** In-memory session state (master key held only while unlocked). */
    val sessionManager: SessionManager by lazy { SessionManager() }

    /** SQLite database helper. */
    val databaseHelper: DatabaseHelper by lazy { DatabaseHelper(this) }

    /** Settings repository (config.json). */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    /**
     * Shared reactive config state.
     * MainActivity observes this to apply theme; SettingsScreen updates this when theme changes.
     * Initialized from file on app start.
     */
    val themeConfig: MutableState<AppConfig> = mutableStateOf(AppConfig())

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Load saved config into reactive state
        themeConfig.value = settingsRepository.loadConfig()
    }

    companion object {
        lateinit var instance: LyvoxApp
            private set
    }
}
