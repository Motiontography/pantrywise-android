package com.pantrywise.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.pantrywise.wear.presentation.expiring.ExpiringItemsScreen
import com.pantrywise.wear.presentation.home.HomeScreen
import com.pantrywise.wear.presentation.quickadd.QuickAddScreen
import com.pantrywise.wear.presentation.shopping.ShoppingListScreen
import com.pantrywise.wear.theme.PantryWiseWearTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    PantryWiseWearTheme {
        val navController = rememberSwipeDismissableNavController()

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = WearScreen.Home.route
        ) {
            composable(WearScreen.Home.route) {
                HomeScreen(
                    onNavigateToShopping = { navController.navigate(WearScreen.Shopping.route) },
                    onNavigateToExpiring = { navController.navigate(WearScreen.Expiring.route) },
                    onNavigateToQuickAdd = { navController.navigate(WearScreen.QuickAdd.route) }
                )
            }

            composable(WearScreen.Shopping.route) {
                ShoppingListScreen()
            }

            composable(WearScreen.Expiring.route) {
                ExpiringItemsScreen()
            }

            composable(WearScreen.QuickAdd.route) {
                QuickAddScreen()
            }
        }
    }
}

sealed class WearScreen(val route: String) {
    object Home : WearScreen("home")
    object Shopping : WearScreen("shopping")
    object Expiring : WearScreen("expiring")
    object QuickAdd : WearScreen("quick_add")
}
