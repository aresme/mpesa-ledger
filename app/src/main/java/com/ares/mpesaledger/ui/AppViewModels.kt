package com.ares.mpesaledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.MpesaLedgerApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as MpesaLedgerApp).container

/** Manual DI for ViewModels: builds [VM] from the AppContainer (and the nav entry's saved state). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}
