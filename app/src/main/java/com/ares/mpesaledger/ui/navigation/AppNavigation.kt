package com.ares.mpesaledger.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ares.mpesaledger.ui.budgets.BudgetsScreen
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.dashboard.DashboardScreen
import com.ares.mpesaledger.ui.detail.DetailScreen
import com.ares.mpesaledger.ui.detail.DetailViewModel
import com.ares.mpesaledger.ui.onboarding.OnboardingScreen
import com.ares.mpesaledger.ui.rules.RulesScreen
import com.ares.mpesaledger.ui.settings.SettingsScreen
import com.ares.mpesaledger.ui.theme.AresBlack
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresGreenContainer
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.transactions.TransactionsScreen
import com.ares.mpesaledger.ui.transactions.TransactionsViewModel
import com.ares.mpesaledger.ui.unparsed.UnparsedScreen
import java.time.YearMonth

/** String routes keep the build free of the serialization plugin. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val BUDGETS = "budgets"
    const val SETTINGS = "settings"
    const val UNPARSED = "unparsed"
    const val RULES = "rules"
    private const val DETAIL = "detail"

    const val ACTIVITY_PATTERN =
        "$ACTIVITY?${TransactionsViewModel.ARG_CATEGORY}={${TransactionsViewModel.ARG_CATEGORY}}&${TransactionsViewModel.ARG_MONTH}={${TransactionsViewModel.ARG_MONTH}}"
    const val DETAIL_PATTERN = "$DETAIL/{${DetailViewModel.ARG_CODE}}/{${DetailViewModel.ARG_KIND}}"

    fun activity(category: String? = null, month: YearMonth? = null) =
        "$ACTIVITY?${TransactionsViewModel.ARG_CATEGORY}=${Uri.encode(category.orEmpty())}&${TransactionsViewModel.ARG_MONTH}=${month ?: ""}"

    fun detail(code: String, kind: String) = "$DETAIL/${Uri.encode(code)}/${Uri.encode(kind)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.ACTIVITY, "Activity", Icons.AutoMirrored.Filled.List),
    Tab(Routes.BUDGETS, "Budgets", AppIcons.PieChart),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppNavigation(
    showOnboarding: Boolean,
    demoToggleAvailable: Boolean,
    onToggleDemo: (Boolean) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentBase = entry?.destination?.route?.substringBefore('?')?.substringBefore('/')
    val showBar = currentBase in tabs.map { it.route }

    Scaffold(
        containerColor = AresBlack,
        bottomBar = { if (showBar) BottomBar(currentBase, nav) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (showOnboarding) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onDone = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                DashboardScreen(
                    onOpenCategory = { category, month -> nav.navigate(Routes.activity(category, month)) },
                    onOpenActivity = { nav.navigateTab(Routes.ACTIVITY_PATTERN) },
                )
            }
            composable(
                Routes.ACTIVITY_PATTERN,
                arguments = listOf(
                    navArgument(TransactionsViewModel.ARG_CATEGORY) { type = NavType.StringType; defaultValue = "" },
                    navArgument(TransactionsViewModel.ARG_MONTH) { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                TransactionsScreen(onOpen = { code, kind -> nav.navigate(Routes.detail(code, kind)) })
            }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenUnparsed = { nav.navigate(Routes.UNPARSED) },
                    onOpenRules = { nav.navigate(Routes.RULES) },
                    demoToggleAvailable = demoToggleAvailable,
                    onToggleDemo = onToggleDemo,
                )
            }
            composable(
                Routes.DETAIL_PATTERN,
                arguments = listOf(
                    navArgument(DetailViewModel.ARG_CODE) { type = NavType.StringType },
                    navArgument(DetailViewModel.ARG_KIND) { type = NavType.StringType },
                ),
            ) { DetailScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.UNPARSED) { UnparsedScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.RULES) { RulesScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun BottomBar(currentBase: String?, nav: NavHostController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentBase == tab.route,
                onClick = { nav.navigateTab(if (tab.route == Routes.ACTIVITY) Routes.ACTIVITY_PATTERN else tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AresGreen,
                    selectedTextColor = AresGreen,
                    indicatorColor = AresGreenContainer,
                    unselectedIconColor = AresMuted,
                    unselectedTextColor = AresMuted,
                ),
            )
        }
    }
}

/**
 * Standard bottom-nav behaviour: one copy of each tab, state saved and restored. Home is the
 * start destination, so it just pops back to it: "saving" the stack above Home and restoring
 * it into Home would immediately bring the other screen back (the tap would appear to do nothing).
 */
private fun NavHostController.navigateTab(route: String) {
    if (route == Routes.HOME) {
        popBackStack(graph.findStartDestination().id, inclusive = false)
        return
    }
    val target = if (route == Routes.ACTIVITY_PATTERN) Routes.activity() else route
    navigate(target) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
