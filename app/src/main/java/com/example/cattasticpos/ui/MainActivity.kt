package com.example.cattasticpos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.domain.model.AppThemeAccent
import com.example.cattasticpos.ui.dashboard.DashboardScreen
import com.example.cattasticpos.ui.dashboard.DashboardViewModel
import com.example.cattasticpos.ui.history.HistoryScreen
import com.example.cattasticpos.ui.history.HistoryViewModel
import com.example.cattasticpos.ui.inventory.InventoryScreen
import com.example.cattasticpos.ui.inventory.InventoryViewModel
import com.example.cattasticpos.ui.menu.MenuEditorScreen
import com.example.cattasticpos.ui.menu.MenuEditorViewModel
import com.example.cattasticpos.ui.components.PinScreen
import com.example.cattasticpos.ui.components.AppUpdateGate
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cattasticpos.ui.adaptive.AdaptiveTheme
import com.example.cattasticpos.ui.adaptive.BionicHaptic
import com.example.cattasticpos.ui.adaptive.FeedbackEvent
import com.example.cattasticpos.ui.adaptive.ParallaxNavHost
import com.example.cattasticpos.ui.adaptive.PosSound
import com.example.cattasticpos.ui.adaptive.ProvidePosFeedback
import com.example.cattasticpos.ui.adaptive.isPushNavigation
import com.example.cattasticpos.ui.adaptive.rememberPosFeedback
import com.example.cattasticpos.ui.theme.AdaptiveSystemBarStyle

@Composable
fun CattasticTheme(
    accent: AppThemeAccent = AppThemeAccent.EMERALD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AdaptiveSystemBarStyle(darkTheme = darkTheme)
    AdaptiveTheme(accent = accent, darkTheme = darkTheme, content = content)
}

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Only warn about Bluetooth — POST_NOTIFICATIONS is requested in the same batch but is
        // optional (it only gates the low-stock alert), so a refusal must not nag the cashier.
        val bluetoothAsked = permissions.keys.any {
            it == android.Manifest.permission.BLUETOOTH_CONNECT ||
                it == android.Manifest.permission.BLUETOOTH_SCAN
        }
        val connectGranted = permissions[android.Manifest.permission.BLUETOOTH_CONNECT] ?: true
        val scanGranted = permissions[android.Manifest.permission.BLUETOOTH_SCAN] ?: true
        if (bluetoothAsked && (!connectGranted || !scanGranted)) {
            android.widget.Toast.makeText(
                this,
                "Bluetooth permissions are required for receipt printing.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val permissions = buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(android.Manifest.permission.BLUETOOTH_CONNECT)
                add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
            // Declared in the manifest but never requested, so LowStockCheckWorker's alert was
            // dropped by the system on every Android 13+ tablet.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missingPermissions = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }

        setContent {
            val app = application as CattasticPosApp
            val appConfig by app.container.appConfigRepository.getAppConfig()
                .collectAsStateWithLifecycle(initialValue = null)
            val accent = AppThemeAccent.fromId(appConfig?.themeAccentId)

            CattasticTheme(accent = accent) {
                ProvidePosFeedback(preferences = app.container.feedbackPreferences) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("dashboard") }
                    var menuEditorReturnScreen by remember { mutableStateOf("dashboard") }
                    var isPushing by remember { mutableStateOf(true) }
                    val performFeedback = rememberPosFeedback()

                    fun navigateTo(target: String) {
                        isPushing = isPushNavigation(currentScreen, target)
                        if (isPushing) {
                            performFeedback(FeedbackEvent(BionicHaptic.Light, PosSound.Tap))
                        } else {
                            performFeedback(FeedbackEvent(BionicHaptic.Light, PosSound.Tap))
                        }
                        currentScreen = target
                    }
                    
                    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
                    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
                    val inventoryViewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory)
                    val menuEditorViewModel: MenuEditorViewModel = viewModel(factory = MenuEditorViewModel.Factory)

                    ParallaxNavHost(
                        targetScreen = currentScreen,
                        isPushing = isPushing
                    ) { screen ->
                        when (screen) {
                            "dashboard" -> {
                                DashboardScreen(
                                    viewModel = dashboardViewModel,
                                    onNavigateToHistory = { navigateTo("pin_history") },
                                    onNavigateToInventory = { navigateTo("inventory") }
                                )
                            }
                            "pin_history" -> {
                                PinScreen(
                                    onPinSuccess = { navigateTo("history") },
                                    onCancel = { navigateTo("dashboard") }
                                )
                            }
                            "history" -> {
                                HistoryScreen(
                                    viewModel = historyViewModel,
                                    onNavigateBack = { navigateTo("dashboard") },
                                    onNavigateToMenuEditor = {
                                        menuEditorReturnScreen = "history"
                                        navigateTo("menu_editor")
                                    }
                                )
                            }
                            "inventory" -> {
                                InventoryScreen(
                                    viewModel = inventoryViewModel,
                                    onNavigateBack = { navigateTo("dashboard") },
                                    onNavigateToMenuEditor = {
                                        menuEditorReturnScreen = "inventory"
                                        navigateTo("pin_menu_editor")
                                    }
                                )
                            }
                            "pin_menu_editor" -> {
                                PinScreen(
                                    subtitle = "Enter your PIN to edit menu products & categories",
                                    onPinSuccess = { navigateTo("menu_editor") },
                                    onCancel = { navigateTo(menuEditorReturnScreen) }
                                )
                            }
                            "menu_editor" -> {
                                MenuEditorScreen(
                                    viewModel = menuEditorViewModel,
                                    onNavigateBack = { navigateTo(menuEditorReturnScreen) }
                                )
                            }
                        }
                    }

                    // Self-update prompt — checks the cloud for a newer build on launch.
                    AppUpdateGate()

                    // One-time store-account sign-in; cloud sync stays off until done.
                    com.example.cattasticpos.ui.components.SyncLoginGate()
                }
                }
            }
        }
    }
}
