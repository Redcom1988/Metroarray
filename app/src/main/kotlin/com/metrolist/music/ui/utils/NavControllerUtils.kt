/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import com.metrolist.music.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}

/**
 * Navigate to a route with automatic launchSingleTop to prevent duplicate navigation stacking.
 * This prevents the issue where rapid clicks cause multiple instances of the same destination
 * to be added to the back stack.
 *
 * @param route The destination route
 * @param builder Optional NavOptionsBuilder for additional navigation options
 */
fun NavController.navigateSafe(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}
