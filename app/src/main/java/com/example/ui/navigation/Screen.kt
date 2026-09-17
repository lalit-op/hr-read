package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Home : Screen("home", "Home", Icons.Filled.Folder)
    data object Recent : Screen("recent", "Recent", Icons.Filled.History)
    data object Favorites : Screen("favorites", "Favorites", Icons.Filled.Bookmark)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val bottomNavTabs: List<Screen>
            get() = listOf(Home, Recent, Favorites, Settings)
    }
}
