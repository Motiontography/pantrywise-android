package com.pantrywise.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pantrywise.ui.pantry.PantryScreen
import com.pantrywise.ui.shopping.ShoppingListScreen
import com.pantrywise.ui.shopping.ShoppingSessionScreen
import com.pantrywise.ui.shopping.ReconciliationScreen
import com.pantrywise.ui.scanner.BarcodeScannerScreen
import com.pantrywise.ui.recipes.RecipeInputScreen
import com.pantrywise.ui.recipes.AIRecipeSearchScreen
import com.pantrywise.ui.finance.SpendingSummaryScreen
import com.pantrywise.ui.settings.SettingsScreen
import com.pantrywise.ui.staples.StaplesScreen
import com.pantrywise.ui.household.HouseholdScreen
import com.pantrywise.ui.nfc.NfcScanScreen
import com.pantrywise.ui.nfc.NfcWriteScreen
import com.pantrywise.ui.mealplan.MealPlanScreen

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Pantry : Screen(
        route = "pantry",
        title = "Pantry",
        selectedIcon = Icons.Filled.Kitchen,
        unselectedIcon = Icons.Outlined.Kitchen
    )

    data object Shopping : Screen(
        route = "shopping",
        title = "Shopping",
        selectedIcon = Icons.Filled.ShoppingCart,
        unselectedIcon = Icons.Outlined.ShoppingCart
    )

    data object Recipes : Screen(
        route = "recipes",
        title = "Recipes",
        selectedIcon = Icons.Filled.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook
    )

    data object Spending : Screen(
        route = "spending",
        title = "Spending",
        selectedIcon = Icons.Filled.AttachMoney,
        unselectedIcon = Icons.Outlined.AttachMoney
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

object Routes {
    const val PANTRY = "pantry"
    const val SHOPPING = "shopping"
    const val SHOPPING_SESSION = "shopping_session/{sessionId}"
    const val RECONCILIATION = "reconciliation/{sessionId}"
    const val SCANNER = "scanner?context={context}"
    const val RECIPES = "recipes"
    const val AI_RECIPE_SEARCH = "ai_recipe_search"
    const val MEAL_PLAN = "meal_plan"
    const val SPENDING = "spending"
    const val SETTINGS = "settings"
    const val STAPLES = "staples"
    const val HOUSEHOLD = "household"
    const val NFC_SCAN = "nfc_scan"
    const val NFC_WRITE = "nfc_write/{productId}?productName={productName}&barcode={barcode}"

    fun shoppingSession(sessionId: String) = "shopping_session/$sessionId"
    fun reconciliation(sessionId: String) = "reconciliation/$sessionId"
    fun scanner(context: String? = null) = "scanner?context=${context ?: ""}"
    fun nfcWrite(productId: String, productName: String? = null, barcode: String? = null): String {
        val base = "nfc_write/$productId"
        val params = mutableListOf<String>()
        productName?.let { params.add("productName=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        barcode?.let { params.add("barcode=$it") }
        return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
    }
}

val bottomNavItems = listOf(
    Screen.Pantry,
    Screen.Shopping,
    Screen.Recipes,
    Screen.Spending,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryNavHost(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine if we should show the bottom bar
    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PANTRY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.PANTRY) {
                PantryScreen(
                    onNavigateToScanner = { navController.navigate(Routes.scanner("pantry")) },
                    onNavigateToStaples = { navController.navigate(Routes.STAPLES) }
                )
            }

            composable(Routes.SHOPPING) {
                ShoppingListScreen(
                    onStartSession = { sessionId ->
                        navController.navigate(Routes.shoppingSession(sessionId))
                    },
                    onNavigateToScanner = { navController.navigate(Routes.scanner("shopping")) }
                )
            }

            composable(
                route = Routes.SHOPPING_SESSION,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                ShoppingSessionScreen(
                    sessionId = sessionId,
                    onNavigateToReconciliation = {
                        navController.navigate(Routes.reconciliation(sessionId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.RECONCILIATION,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                ReconciliationScreen(
                    sessionId = sessionId,
                    onSessionComplete = {
                        navController.popBackStack(Routes.SHOPPING, inclusive = false)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SCANNER,
                arguments = listOf(
                    navArgument("context") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val context = backStackEntry.arguments?.getString("context")
                BarcodeScannerScreen(
                    scanContext = context,
                    onProductScanned = { productId ->
                        // Handle scanned product based on context
                        navController.popBackStack()
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RECIPES) {
                RecipeInputScreen(
                    onIngredientsMatched = { /* Navigate to ingredient match */ },
                    onNavigateToMealPlan = { navController.navigate(Routes.MEAL_PLAN) }
                )
            }

            composable(Routes.SPENDING) {
                SpendingSummaryScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToHousehold = { navController.navigate(Routes.HOUSEHOLD) },
                    onNavigateToApiKeySettings = { /* TODO: Navigate to API key settings */ }
                )
            }

            composable(Routes.STAPLES) {
                StaplesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.AI_RECIPE_SEARCH) {
                AIRecipeSearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onShoppingListCreated = { listId ->
                        navController.popBackStack(Routes.SHOPPING, inclusive = false)
                    }
                )
            }

            composable(Routes.MEAL_PLAN) {
                MealPlanScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRecipeLibrary = { /* TODO: Navigate to recipe library */ },
                    onNavigateToShoppingList = { listId ->
                        navController.popBackStack(Routes.SHOPPING, inclusive = false)
                    }
                )
            }

            composable(Routes.HOUSEHOLD) {
                HouseholdScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.NFC_SCAN) {
                NfcScanScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onProductFound = { productId ->
                        // Navigate to product detail or pantry item
                        navController.popBackStack()
                    },
                    onBarcodeFound = { barcode ->
                        // Navigate to scanner with barcode
                        navController.navigate(Routes.scanner("pantry"))
                    }
                )
            }

            composable(
                route = Routes.NFC_WRITE,
                arguments = listOf(
                    navArgument("productId") { type = NavType.StringType },
                    navArgument("productName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("barcode") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId") ?: return@composable
                val productName = backStackEntry.arguments?.getString("productName")
                val barcode = backStackEntry.arguments?.getString("barcode")

                NfcWriteScreen(
                    productId = productId,
                    productName = productName,
                    barcode = barcode,
                    onNavigateBack = { navController.popBackStack() },
                    onWriteSuccess = { navController.popBackStack() }
                )
            }
        }
    }
}
