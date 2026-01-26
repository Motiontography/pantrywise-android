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
import com.pantrywise.ui.settings.CalendarSettingsScreen
import com.pantrywise.ui.staples.StaplesScreen
import com.pantrywise.ui.household.HouseholdScreen
import com.pantrywise.ui.nfc.NfcScanScreen
import com.pantrywise.ui.nfc.NfcWriteScreen
import com.pantrywise.ui.mealplan.MealPlanScreen
import com.pantrywise.ui.price.PriceHistoryScreen
import com.pantrywise.ui.price.PriceBookScreen
import com.pantrywise.ui.waste.WasteDashboardScreen
import com.pantrywise.ui.health.NutritionDashboardScreen
import com.pantrywise.ui.health.FoodLogScreen
import com.pantrywise.ui.health.DailyGoalsSettingsScreen
import com.pantrywise.ui.audit.AuditSessionScreen
import com.pantrywise.ui.stores.StoreManagementScreen
import com.pantrywise.ui.stores.AisleMappingScreen

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

    // Phase 5-6: New feature routes
    const val PRICE_HISTORY = "price_history/{productId}?productName={productName}"
    const val PRICE_BOOK = "price_book"
    const val WASTE_DASHBOARD = "waste_dashboard"
    const val NUTRITION_DASHBOARD = "nutrition_dashboard"
    const val FOOD_LOG = "food_log"
    const val DAILY_GOALS = "daily_goals"
    const val CALENDAR_SETTINGS = "calendar_settings"
    const val AUDIT_SESSION = "audit_session"
    const val STORE_MANAGEMENT = "store_management"
    const val AISLE_MAPPING = "aisle_mapping/{storeId}"

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

    fun priceHistory(productId: String, productName: String? = null): String {
        val encodedName = productName?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
        return "price_history/$productId?productName=$encodedName"
    }

    fun aisleMapping(storeId: String) = "aisle_mapping/$storeId"
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
                    onNavigateToApiKeySettings = { /* TODO: Navigate to API key settings */ },
                    onNavigateToPriceBook = { navController.navigate(Routes.PRICE_BOOK) },
                    onNavigateToWasteDashboard = { navController.navigate(Routes.WASTE_DASHBOARD) },
                    onNavigateToNutrition = { navController.navigate(Routes.NUTRITION_DASHBOARD) },
                    onNavigateToCalendarSettings = { navController.navigate(Routes.CALENDAR_SETTINGS) },
                    onNavigateToAudit = { navController.navigate(Routes.AUDIT_SESSION) },
                    onNavigateToStoreManagement = { navController.navigate(Routes.STORE_MANAGEMENT) }
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

            // Phase 5-6: New feature screens
            composable(
                route = Routes.PRICE_HISTORY,
                arguments = listOf(
                    navArgument("productId") { type = NavType.StringType },
                    navArgument("productName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId") ?: return@composable
                val productName = backStackEntry.arguments?.getString("productName") ?: "Product"

                PriceHistoryScreen(
                    productId = productId,
                    productName = productName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PRICE_BOOK) {
                PriceBookScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onProductClick = { productId, productName ->
                        navController.navigate(Routes.priceHistory(productId, productName))
                    }
                )
            }

            composable(Routes.WASTE_DASHBOARD) {
                WasteDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLogWaste = { /* Handle waste logging */ }
                )
            }

            composable(Routes.NUTRITION_DASHBOARD) {
                NutritionDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFoodLog = { navController.navigate(Routes.FOOD_LOG) },
                    onNavigateToGoals = { navController.navigate(Routes.DAILY_GOALS) }
                )
            }

            composable(Routes.FOOD_LOG) {
                FoodLogScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProductSearch = { /* Navigate to product search */ }
                )
            }

            composable(Routes.DAILY_GOALS) {
                DailyGoalsSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CALENDAR_SETTINGS) {
                CalendarSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.AUDIT_SESSION) {
                AuditSessionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.STORE_MANAGEMENT) {
                StoreManagementScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAisleMapping = { storeId ->
                        navController.navigate(Routes.aisleMapping(storeId))
                    }
                )
            }

            composable(
                route = Routes.AISLE_MAPPING,
                arguments = listOf(navArgument("storeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val storeId = backStackEntry.arguments?.getString("storeId") ?: return@composable
                AisleMappingScreen(
                    storeId = storeId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
