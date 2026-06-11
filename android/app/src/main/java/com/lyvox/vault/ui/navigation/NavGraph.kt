package com.lyvox.vault.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.ui.components.BottomNavBar
import com.lyvox.vault.ui.screens.*

// ─── Route Constants ────────────────────────────────────────

object Routes {
    const val UNLOCK = "unlock"
    const val RECOVERY = "recovery"
    const val RECOVERY_SETUP = "recovery_setup"
    const val VAULT = "vault"
    const val ENTRY_DETAIL = "entry_detail/{entryId}"
    const val ENTRY_FORM = "entry_form?entryId={entryId}"
    const val NOTES = "notes"
    const val NOTE_FORM = "note_form?noteId={noteId}"
    const val GENERATOR = "generator"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"

    fun entryDetail(entryId: Long) = "entry_detail/$entryId"
    fun entryForm(entryId: Long? = null) = if (entryId != null) "entry_form?entryId=$entryId" else "entry_form"
    fun noteForm(noteId: Long? = null) = if (noteId != null) "note_form?noteId=$noteId" else "note_form"
}

/**
 * Bottom-nav destinations (show bottom bar when on these routes).
 */
private val bottomNavRoutes = setOf(Routes.VAULT, Routes.GENERATOR, Routes.NOTES, Routes.SETTINGS)

/**
 * Root navigation host — manages the full screen flow.
 */
@Composable
fun LyvoxNavHost() {
    val navController = rememberNavController()
    val app = LyvoxApp.instance
    val session = app.sessionManager
    val settings = app.settingsRepository

    // Determine start destination
    val startDest = when {
        settings.isFirstRun() -> Routes.UNLOCK
        !session.isUnlocked -> Routes.UNLOCK
        else -> Routes.VAULT
    }

    val isUnlockedFlowState by session.isUnlockedFlow.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route?.split("?")?.get(0) ?: startDest
    val showBottomBar = currentRoute in bottomNavRoutes

    LaunchedEffect(isUnlockedFlowState, currentRoute) {
        if (!isUnlockedFlowState && !settings.isFirstRun() && currentRoute != Routes.UNLOCK && currentRoute != Routes.RECOVERY) {
            navController.navigate(Routes.UNLOCK) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(Routes.VAULT) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn(initialAlpha = 0.3f) },
            exitTransition = { fadeOut(targetAlpha = 0.3f) }
        ) {
            // ── Auth Flow ─────────────────────────────────
            composable(Routes.UNLOCK) {
                UnlockScreen(
                    onUnlocked = {
                        navController.navigate(Routes.VAULT) {
                            popUpTo(Routes.UNLOCK) { inclusive = true }
                        }
                    },
                    onRecovery = {
                        navController.navigate(Routes.RECOVERY)
                    }
                )
            }

            composable(Routes.RECOVERY) {
                RecoveryScreen(
                    onRecovered = {
                        navController.navigate(Routes.VAULT) {
                            popUpTo(Routes.UNLOCK) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RECOVERY_SETUP) {
                RecoverySetupScreen(
                    onConfigured = { navController.popBackStack() }
                )
            }

            // ── Vault ─────────────────────────────────────
            composable(Routes.VAULT) {
                VaultScreen(
                    onEntryClick = { entryId ->
                        navController.navigate(Routes.entryDetail(entryId))
                    },
                    onAddEntry = {
                        navController.navigate(Routes.entryForm())
                    }
                )
            }

            composable(
                route = Routes.ENTRY_DETAIL,
                arguments = listOf(navArgument("entryId") { type = NavType.LongType })
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
                EntryDetailScreen(
                    entryId = entryId,
                    onEdit = { navController.navigate(Routes.entryForm(entryId)) },
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ENTRY_FORM,
                arguments = listOf(navArgument("entryId") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getLong("entryId") ?: -1L
                EntryFormScreen(
                    entryId = if (entryId > 0) entryId else null,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Notes ─────────────────────────────────────
            composable(Routes.NOTES) {
                NotesScreen(
                    onNoteClick = { noteId ->
                        navController.navigate(Routes.noteForm(noteId))
                    },
                    onAddNote = {
                        navController.navigate(Routes.noteForm())
                    }
                )
            }

            composable(
                route = Routes.NOTE_FORM,
                arguments = listOf(navArgument("noteId") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong("noteId") ?: -1L
                NoteFormScreen(
                    noteId = if (noteId > 0) noteId else null,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Generator ────────────────────────────────
            composable(Routes.GENERATOR) {
                GeneratorScreen()
            }

            // ── Settings ────────────────────────────────
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToRecoverySetup = {
                        navController.navigate(Routes.RECOVERY_SETUP)
                    },
                    onNavigateToBackup = {
                        navController.navigate(Routes.BACKUP)
                    }
                )
            }

            composable(Routes.BACKUP) {
                BackupScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
