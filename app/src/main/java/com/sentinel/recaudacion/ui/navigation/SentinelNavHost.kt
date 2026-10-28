package com.sentinel.recaudacion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sentinel.recaudacion.ui.alarm.AlarmTestScreen
import com.sentinel.recaudacion.ui.alarm.SimulatedTextTestScreen
import com.sentinel.recaudacion.ui.blacklist.BlacklistScreen
import com.sentinel.recaudacion.ui.diagnostics.ScreenScanViewerScreen
import com.sentinel.recaudacion.ui.history.HistoryScreen
import com.sentinel.recaudacion.ui.home.HomeScreen
import com.sentinel.recaudacion.ui.importcsv.ImportScreen
import com.sentinel.recaudacion.ui.telegram.TelegramScreen

@Composable
fun SentinelNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpen = { route -> nav.navigate(route) }
            )
        }

        composable(Routes.BLACKLIST) {
            BlacklistScreen(
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.IMPORT) {
            ImportScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.TELEGRAM) {
            TelegramScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ALARM) {
            AlarmTestScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.TEXT_TEST) {
            SimulatedTextTestScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.KEYBOARD) {
            com.sentinel.recaudacion.ui.input.KeyboardSetupScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ACCESSIBILITY) {
            com.sentinel.recaudacion.ui.input.AccessibilitySetupScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DIAGNOSTICS) {
            ScreenScanViewerScreen(onBack = { nav.popBackStack() })
        }
    }
}
