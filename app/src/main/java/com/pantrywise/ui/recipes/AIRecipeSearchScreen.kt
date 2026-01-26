package com.pantrywise.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.R
import com.pantrywise.data.remote.model.AIRecipeResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIRecipeSearchScreen(
    viewModel: RecipeDiscoveryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onShoppingListCreated: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    // Navigate to shopping when list is created
    LaunchedEffect(state.createdShoppingListId) {
        state.createdShoppingListId?.let { listId ->
            onShoppingListCreated(listId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_recipe_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ai_recipe_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ai_recipe_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Search input
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_recipe_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.searchRecipe()
                    }
                ),
                singleLine = true
            )
        }

        // Search button
        item {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.searchRecipe()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && state.searchQuery.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.find_recipe))
            }
        }

        // Suggestion chips
        if (state.discoveredRecipe == null && !state.isLoading) {
            item {
                Text(
                    text = "Try searching for:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = {
                                viewModel.updateSearchQuery(suggestion)
                                viewModel.searchRecipe()
                            },
                            label = { Text(suggestion) }
                        )
                    }
                }
            }
        }

        // Error message
        state.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearError) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }
        }

        // Recipe result
        state.discoveredRecipe?.let { recipe ->
            item {
                RecipeResultCard(
                    recipe = recipe,
                    onCreateShoppingList = viewModel::createShoppingList,
                    onSaveRecipe = viewModel::saveRecipeToLibrary,
                    onNewSearch = viewModel::clearRecipe,
                    shoppingListCreated = state.shoppingListCreated,
                    recipeSaved = state.recipeSaved
                )
            }
        }
        }
    }

    // API Key prompt dialog
    if (state.showApiKeyPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissApiKeyPrompt,
            title = { Text("API Key Required") },
            text = {
                Text("To use AI Recipe Discovery, please configure your OpenAI API key in Settings.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissApiKeyPrompt()
                    onNavigateToSettings()
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissApiKeyPrompt) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecipeResultCard(
    recipe: AIRecipeResult,
    onCreateShoppingList: () -> Unit,
    onSaveRecipe: () -> Unit,
    onNewSearch: () -> Unit,
    shoppingListCreated: Boolean,
    recipeSaved: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Recipe name
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Description
            Text(
                text = recipe.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Time and servings info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                recipe.prepTimeMinutes?.let { prep ->
                    InfoChip(
                        icon = Icons.Default.Timer,
                        label = "Prep: ${prep}m"
                    )
                }
                recipe.cookTimeMinutes?.let { cook ->
                    InfoChip(
                        icon = Icons.Default.LocalFireDepartment,
                        label = "Cook: ${cook}m"
                    )
                }
                InfoChip(
                    icon = Icons.Default.People,
                    label = "Serves ${recipe.servings}"
                )
            }

            // Cuisine and tags
            if (recipe.cuisine != null || recipe.tags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recipe.cuisine?.let { cuisine ->
                        item {
                            AssistChip(
                                onClick = {},
                                label = { Text(cuisine) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Restaurant,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                    items(recipe.tags) { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Ingredients preview
            Text(
                text = "Ingredients (${recipe.ingredients.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                recipe.ingredients.take(5).forEach { ingredient ->
                    val quantity = if (ingredient.quantity == ingredient.quantity.toLong().toDouble()) {
                        ingredient.quantity.toLong().toString()
                    } else {
                        ingredient.quantity.toString()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$quantity ${ingredient.unit} ${ingredient.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (recipe.ingredients.size > 5) {
                    Text(
                        text = "... and ${recipe.ingredients.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary action - Create shopping list
                Button(
                    onClick = onCreateShoppingList,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !shoppingListCreated
                ) {
                    if (shoppingListCreated) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Shopping List Created!")
                    } else {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Shopping List: ${recipe.name}")
                    }
                }

                // Secondary actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveRecipe,
                        modifier = Modifier.weight(1f),
                        enabled = !recipeSaved
                    ) {
                        if (recipeSaved) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Saved")
                        } else {
                            Icon(Icons.Default.Bookmark, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Recipe")
                        }
                    }

                    OutlinedButton(
                        onClick = onNewSearch,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Search")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
