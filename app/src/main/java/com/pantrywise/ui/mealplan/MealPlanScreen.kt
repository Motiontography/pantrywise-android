package com.pantrywise.ui.mealplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.dao.MealPlanEntryWithRecipe
import com.pantrywise.data.local.entity.MealType
import com.pantrywise.data.local.entity.RecipeEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    viewModel: MealPlanViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToRecipeLibrary: () -> Unit,
    onNavigateToShoppingList: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddMealSheet by remember { mutableStateOf(false) }
    var showGenerateListSheet by remember { mutableStateOf(false) }
    var selectedMealType by remember { mutableStateOf(MealType.DINNER) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadRecipes()
    }

    // Navigate to shopping list when generated
    LaunchedEffect(uiState.generatedListId) {
        uiState.generatedListId?.let { listId ->
            viewModel.clearGeneratedListId()
            onNavigateToShoppingList(listId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.getCurrentWeekDateRange()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Meal") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    showMoreMenu = false
                                    showAddMealSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recipe Library") },
                                leadingIcon = { Icon(Icons.Default.MenuBook, null) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToRecipeLibrary()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Generate Shopping List") },
                                leadingIcon = { Icon(Icons.Default.ShoppingCart, null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.generateShoppingList()
                                    showGenerateListSheet = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Week Navigation Header
            WeekNavigationHeader(
                weekRange = viewModel.getCurrentWeekDateRange(),
                onPreviousWeek = { viewModel.previousWeek() },
                onNextWeek = { viewModel.nextWeek() }
            )

            // Day Selector
            DaySelector(
                weekDays = uiState.weekDays,
                viewModel = viewModel
            )

            HorizontalDivider()

            // Meals for Selected Day
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                MealsForSelectedDay(
                    entries = viewModel.entriesForSelectedDate(),
                    onAddMeal = { mealType ->
                        selectedMealType = mealType
                        showAddMealSheet = true
                    },
                    onDeleteEntry = { entryId ->
                        viewModel.deleteEntry(entryId)
                    }
                )
            }
        }
    }

    // Add Meal Bottom Sheet
    if (showAddMealSheet) {
        AddMealSheet(
            mealType = selectedMealType,
            recipes = uiState.recipes,
            onDismiss = { showAddMealSheet = false },
            onAddCustomMeal = { name, servings ->
                viewModel.addMealEntry(
                    date = uiState.selectedDate,
                    mealType = selectedMealType,
                    customMealName = name,
                    servings = servings
                )
                showAddMealSheet = false
            },
            onAddRecipe = { recipe, servings ->
                viewModel.addMealEntry(
                    date = uiState.selectedDate,
                    mealType = selectedMealType,
                    recipeId = recipe.id,
                    servings = servings
                )
                showAddMealSheet = false
            }
        )
    }

    // Generate Shopping List Sheet
    if (showGenerateListSheet) {
        GenerateShoppingListSheet(
            ingredients = uiState.shoppingIngredients,
            weekRange = viewModel.getCurrentWeekDateRange(),
            isLoading = uiState.isLoading,
            onDismiss = { showGenerateListSheet = false },
            onCreateList = { listName ->
                viewModel.createShoppingListFromIngredients(listName)
                showGenerateListSheet = false
            }
        )
    }
}

@Composable
private fun WeekNavigationHeader(
    weekRange: String,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousWeek) {
            Icon(Icons.Default.ChevronLeft, "Previous week")
        }

        Text(
            text = weekRange,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = onNextWeek) {
            Icon(Icons.Default.ChevronRight, "Next week")
        }
    }
}

@Composable
private fun DaySelector(
    weekDays: List<Long>,
    viewModel: MealPlanViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weekDays.forEach { date ->
            DayButton(
                dayName = viewModel.dayName(date),
                dayNumber = viewModel.dayNumber(date),
                isToday = viewModel.isToday(date),
                isSelected = viewModel.isSelected(date),
                mealCount = viewModel.entriesForDate(date).size,
                onClick = { viewModel.selectDate(date) }
            )
        }
    }
}

@Composable
private fun DayButton(
    dayName: String,
    dayNumber: String,
    isToday: Boolean,
    isSelected: Boolean,
    mealCount: Int,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val dotColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .size(width = 48.dp, height = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f)
        )

        Text(
            text = dayNumber,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(8.dp)
        ) {
            repeat(minOf(mealCount, 3)) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(dotColor, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun MealsForSelectedDay(
    entries: List<MealPlanEntryWithRecipe>,
    onAddMeal: (MealType) -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(MealType.entries) { mealType ->
            MealTypeSection(
                mealType = mealType,
                entries = entries.filter { it.mealType == mealType },
                onAddTap = { onAddMeal(mealType) },
                onDeleteEntry = onDeleteEntry
            )
        }
    }
}

@Composable
private fun MealTypeSection(
    mealType: MealType,
    entries: List<MealPlanEntryWithRecipe>,
    onAddTap: () -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    val mealColor = when (mealType) {
        MealType.BREAKFAST -> Color(0xFFFF9800)
        MealType.LUNCH -> Color(0xFFFFC107)
        MealType.DINNER -> Color(0xFF9C27B0)
        MealType.SNACK -> Color(0xFF4CAF50)
    }

    val mealIcon = when (mealType) {
        MealType.BREAKFAST -> Icons.Default.FreeBreakfast
        MealType.LUNCH -> Icons.Default.LunchDining
        MealType.DINNER -> Icons.Default.DinnerDining
        MealType.SNACK -> Icons.Default.Cookie
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = mealIcon,
                        contentDescription = null,
                        tint = mealColor
                    )
                    Text(
                        text = mealType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onAddTap) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add ${mealType.displayName}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Entries
            if (entries.isEmpty()) {
                Text(
                    text = "No meals planned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                entries.forEach { entry ->
                    MealEntryRow(
                        entry = entry,
                        onDelete = { onDeleteEntry(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MealEntryRow(
    entry: MealPlanEntryWithRecipe,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${entry.servings} servings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMealSheet(
    mealType: MealType,
    recipes: List<RecipeEntity>,
    onDismiss: () -> Unit,
    onAddCustomMeal: (name: String, servings: Int) -> Unit,
    onAddRecipe: (recipe: RecipeEntity, servings: Int) -> Unit
) {
    var customMealName by remember { mutableStateOf("") }
    var servings by remember { mutableIntStateOf(2) }
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var showRecipeList by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Add ${mealType.displayName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Quick add custom meal
            OutlinedTextField(
                value = customMealName,
                onValueChange = { customMealName = it },
                label = { Text("Quick add meal name") },
                placeholder = { Text("e.g., Leftover pasta") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Servings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Servings")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (servings > 1) servings-- },
                        enabled = servings > 1
                    ) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text(
                        text = servings.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { servings++ }) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add custom meal button
            Button(
                onClick = { onAddCustomMeal(customMealName, servings) },
                enabled = customMealName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Custom Meal")
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Or select from recipes
            Text(
                text = "Or select from recipes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (recipes.isEmpty()) {
                Text(
                    text = "No recipes saved yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(recipes) { recipe ->
                        ListItem(
                            headlineContent = { Text(recipe.name) },
                            supportingContent = recipe.description?.let { { Text(it, maxLines = 1) } },
                            leadingContent = {
                                if (recipe.isFavorite) {
                                    Icon(Icons.Default.Favorite, null, tint = Color.Red)
                                } else {
                                    Icon(Icons.Default.Restaurant, null)
                                }
                            },
                            modifier = Modifier.clickable {
                                onAddRecipe(recipe, servings)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateShoppingListSheet(
    ingredients: List<com.pantrywise.data.local.entity.RecipeIngredient>,
    weekRange: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreateList: (listName: String) -> Unit
) {
    var listName by remember { mutableStateOf("Meal Plan - $weekRange") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Generate Shopping List",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = listName,
                onValueChange = { listName = it },
                label = { Text("List Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (ingredients.isEmpty()) {
                Text(
                    text = "No ingredients found for this week's meals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Text(
                    text = "${ingredients.size} ingredients will be added:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(ingredients) { ingredient ->
                        Text(
                            text = "• ${ingredient.displayString}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onCreateList(listName) },
                enabled = ingredients.isNotEmpty() && listName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ShoppingCart, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Shopping List")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
