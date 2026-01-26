package com.pantrywise.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.MealPlanEntryWithRecipe
import com.pantrywise.data.local.entity.*
import com.pantrywise.data.repository.MealPlanRepository
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.domain.model.Unit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class MealPlanUiState(
    val isLoading: Boolean = false,
    val selectedDate: Long = System.currentTimeMillis(),
    val weekStartDate: Long = MealPlanRepository.getWeekStartDate(System.currentTimeMillis()),
    val weekDays: List<Long> = emptyList(),
    val currentMealPlan: MealPlanEntity? = null,
    val entriesForWeek: List<MealPlanEntryWithRecipe> = emptyList(),
    val recipes: List<RecipeEntity> = emptyList(),
    val shoppingIngredients: List<RecipeIngredient> = emptyList(),
    val error: String? = null,
    val generatedListId: String? = null
)

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    private val dateFormatter = SimpleDateFormat("d", Locale.getDefault())
    private val dayFormatter = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateRangeFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

    init {
        loadCurrentWeek()
    }

    // Load current week data
    fun loadCurrentWeek() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val weekStart = _uiState.value.weekStartDate
            val weekEnd = MealPlanRepository.getWeekEndDate(weekStart)
            val weekDays = generateWeekDays(weekStart)

            // Load or create meal plan for this week
            val mealPlan = mealPlanRepository.getOrCreateMealPlanForWeek(weekStart)

            _uiState.update {
                it.copy(
                    weekDays = weekDays,
                    currentMealPlan = mealPlan
                )
            }

            // Observe entries for the week
            mealPlanRepository.getEntriesWithRecipeForDateRange(weekStart, weekEnd)
                .collect { entries ->
                    _uiState.update {
                        it.copy(
                            entriesForWeek = entries,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // Load all recipes for selection
    fun loadRecipes() {
        viewModelScope.launch {
            mealPlanRepository.getAllRecipes()
                .collect { recipes ->
                    _uiState.update { it.copy(recipes = recipes) }
                }
        }
    }

    // Navigate to previous week
    fun previousWeek() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.weekStartDate
            add(Calendar.WEEK_OF_YEAR, -1)
        }
        val newWeekStart = MealPlanRepository.getWeekStartDate(calendar.timeInMillis)
        _uiState.update { it.copy(weekStartDate = newWeekStart) }
        loadCurrentWeek()
    }

    // Navigate to next week
    fun nextWeek() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.weekStartDate
            add(Calendar.WEEK_OF_YEAR, 1)
        }
        val newWeekStart = MealPlanRepository.getWeekStartDate(calendar.timeInMillis)
        _uiState.update { it.copy(weekStartDate = newWeekStart) }
        loadCurrentWeek()
    }

    // Select a day
    fun selectDate(date: Long) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    // Get entries for selected date
    fun entriesForSelectedDate(): List<MealPlanEntryWithRecipe> {
        val selectedDate = MealPlanRepository.getDayStartDate(_uiState.value.selectedDate)
        return _uiState.value.entriesForWeek.filter {
            MealPlanRepository.getDayStartDate(it.date) == selectedDate
        }
    }

    // Get entries for a specific date
    fun entriesForDate(date: Long): List<MealPlanEntryWithRecipe> {
        val normalizedDate = MealPlanRepository.getDayStartDate(date)
        return _uiState.value.entriesForWeek.filter {
            MealPlanRepository.getDayStartDate(it.date) == normalizedDate
        }
    }

    // Add a new meal entry
    fun addMealEntry(
        date: Long,
        mealType: MealType,
        recipeId: String? = null,
        customMealName: String? = null,
        servings: Int = 2,
        notes: String? = null
    ) {
        viewModelScope.launch {
            val mealPlan = _uiState.value.currentMealPlan ?: return@launch
            mealPlanRepository.createEntry(
                mealPlanId = mealPlan.id,
                date = MealPlanRepository.getDayStartDate(date),
                mealType = mealType,
                recipeId = recipeId,
                customMealName = customMealName,
                servings = servings,
                notes = notes
            )
        }
    }

    // Delete a meal entry
    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            mealPlanRepository.deleteEntryById(entryId)
        }
    }

    // Generate shopping list from week's meals
    fun generateShoppingList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val weekStart = _uiState.value.weekStartDate
            val weekEnd = MealPlanRepository.getWeekEndDate(weekStart)

            val ingredients = mealPlanRepository.getAggregatedIngredientsForDateRange(weekStart, weekEnd)
            _uiState.update {
                it.copy(
                    shoppingIngredients = ingredients,
                    isLoading = false
                )
            }
        }
    }

    // Create shopping list from ingredients
    fun createShoppingListFromIngredients(listName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val ingredients = _uiState.value.shoppingIngredients
            if (ingredients.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = "No ingredients to add") }
                return@launch
            }

            // Create a new shopping list
            val list = shoppingRepository.createShoppingList(listName)

            // Add each ingredient
            for (ingredient in ingredients) {
                val unit = try {
                    Unit.valueOf(ingredient.unit.uppercase())
                } catch (e: Exception) {
                    Unit.EACH
                }

                shoppingRepository.addItemToList(
                    listId = list.id,
                    productId = ingredient.productId,
                    quantity = ingredient.quantity,
                    unit = unit,
                    reason = "From meal plan: $listName"
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    generatedListId = list.id
                )
            }
        }
    }

    // Get recipe by ID
    fun getRecipe(recipeId: String?): RecipeEntity? {
        if (recipeId == null) return null
        return _uiState.value.recipes.find { it.id == recipeId }
    }

    // Date formatting helpers
    fun dayName(date: Long): String = dayFormatter.format(Date(date))
    fun dayNumber(date: Long): String = dateFormatter.format(Date(date))

    fun isToday(date: Long): Boolean {
        val today = MealPlanRepository.getDayStartDate(System.currentTimeMillis())
        return MealPlanRepository.getDayStartDate(date) == today
    }

    fun isSelected(date: Long): Boolean {
        val selected = MealPlanRepository.getDayStartDate(_uiState.value.selectedDate)
        return MealPlanRepository.getDayStartDate(date) == selected
    }

    fun getCurrentWeekDateRange(): String {
        val weekStart = _uiState.value.weekStartDate
        val weekEnd = MealPlanRepository.getWeekEndDate(weekStart)
        return "${dateRangeFormatter.format(Date(weekStart))} - ${dateRangeFormatter.format(Date(weekEnd))}"
    }

    private fun generateWeekDays(weekStart: Long): List<Long> {
        val days = mutableListOf<Long>()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = weekStart
        }
        for (i in 0..6) {
            days.add(calendar.timeInMillis)
            calendar.add(Calendar.DAY_OF_WEEK, 1)
        }
        return days
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearGeneratedListId() {
        _uiState.update { it.copy(generatedListId = null) }
    }
}
