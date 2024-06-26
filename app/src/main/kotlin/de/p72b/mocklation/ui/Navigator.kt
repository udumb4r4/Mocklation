package de.p72b.mocklation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class Navigator {

    private val _sharedFlow =
        MutableSharedFlow<NavTarget>(extraBufferCapacity = 1)
    val sharedFlow = _sharedFlow.asSharedFlow()

    fun navigateTo(navTarget: NavTarget) {
        _sharedFlow.tryEmit(navTarget)
    }

    enum class NavTarget(
        val label: String,
        val icon: ImageVector? = null,
        val selectedIcon: ImageVector? = null,
    ) {
        Simulation("simulation"),
        Requirements("requirements"),
        Dashboard("dashboard", Icons.Outlined.Home, Icons.Filled.Home),
        Collection("collection", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List)
    }
}