package com.ares.mpesaledger.ui.budgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.EmptyState

/** Placeholder until per-category budgets land (phase 5). */
@Composable
fun BudgetsScreen() {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Budgets")
        EmptyState(
            icon = AppIcons.PieChart,
            title = "Budgets are coming",
            message = "Soon you'll set a monthly limit per category and get a heads-up at 80% and 100%.",
        )
    }
}
