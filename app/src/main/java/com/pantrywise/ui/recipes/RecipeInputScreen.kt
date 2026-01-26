package com.pantrywise.ui.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.domain.usecase.ParsedIngredient
import com.pantrywise.ui.theme.PantryGreen

enum class RecipeTab(val title: String) {
    MEAL_PLAN("Meal Plan"),
    AI_SEARCH("AI Search"),
    PARSE("Parse")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeInputScreen(
    onIngredientsMatched: () -> Unit,
    onNavigateToMealPlan: () -> Unit = {},
    viewModel: RecipeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var ingredientText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(RecipeTab.MEAL_PLAN) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Recipes") }
                )
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    RecipeTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.title) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        RecipeTab.MEAL_PLAN -> Icons.Default.CalendarMonth
                                        RecipeTab.AI_SEARCH -> Icons.Default.AutoAwesome
                                        RecipeTab.PARSE -> Icons.Default.TextFields
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            RecipeTab.MEAL_PLAN -> {
                MealPlanTabContent(
                    onNavigateToFullMealPlan = onNavigateToMealPlan,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            RecipeTab.AI_SEARCH -> {
                AIRecipeSearchContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            RecipeTab.PARSE -> {
                ParseIngredientsContent(
                    uiState = uiState,
                    ingredientText = ingredientText,
                    onIngredientTextChange = { ingredientText = it },
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun MealPlanTabContent(
    onNavigateToFullMealPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Meal Planning",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Plan your meals for the week and automatically generate shopping lists from your recipes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToFullMealPlan,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Icon(Icons.Default.CalendarMonth, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Meal Planner")
        }
    }
}

@Composable
private fun AIRecipeSearchContent(modifier: Modifier = Modifier) {
    AIRecipeSearchScreen(
        onNavigateBack = { /* No-op - embedded in tab */ }
    )
}

@Composable
private fun ParseIngredientsContent(
    uiState: RecipeUiState,
    ingredientText: String,
    onIngredientTextChange: (String) -> Unit,
    viewModel: RecipeViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Input area
        if (!uiState.hasParsedIngredients) {
            OutlinedTextField(
                value = ingredientText,
                onValueChange = onIngredientTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    label = { Text("Paste ingredient list") },
                    placeholder = {
                        Text(
                            "Example:\n2 cups flour\n1 tsp salt\n3 eggs\n1/2 cup sugar"
                        )
                    },
                    supportingText = {
                        Text("Paste or type your recipe ingredients, one per line")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* TODO: Photo capture */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo")
                    }

                    Button(
                        onClick = { viewModel.parseIngredients(ingredientText) },
                        modifier = Modifier.weight(1f),
                        enabled = ingredientText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Parse")
                    }
                }
            } else {
                // Parsed results
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Parsed Ingredients",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${uiState.matchedCount} matched, ${uiState.unmatchedCount} need attention",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { viewModel.reset() }) {
                        Text("Start Over")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.parsedIngredients) { ingredient ->
                        ParsedIngredientCard(
                            ingredient = ingredient,
                            onAddToList = { viewModel.addToShoppingList(ingredient) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.addAllUnmatchedToList() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.unmatchedCount > 0
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add ${uiState.unmatchedCount} Items to Shopping List")
                }
            }
        }
    }
}

@Composable
private fun ParsedIngredientCard(
    ingredient: ParsedIngredient,
    onAddToList: () -> Unit
) {
    val isMatched = ingredient.matchedProduct != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMatched)
                PantryGreen.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isMatched) Icons.Default.CheckCircle else Icons.Default.HelpOutline,
                contentDescription = null,
                tint = if (isMatched) PantryGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ingredient.originalText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isMatched) {
                    Text(
                        text = "Matched: ${ingredient.matchedProduct?.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PantryGreen
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ingredient.quantity?.let {
                            Text(
                                text = "Qty: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "\"${ingredient.ingredientName}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Confidence indicator
                LinearProgressIndicator(
                    progress = { ingredient.confidence.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = if (ingredient.confidence > 0.5)
                        PantryGreen
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            if (!isMatched) {
                IconButton(onClick = onAddToList) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add to list",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
