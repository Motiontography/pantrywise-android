package com.pantrywise.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pantrywise.data.local.dao.*
import com.pantrywise.data.local.entity.*
import com.pantrywise.data.local.entity.MealType
import com.pantrywise.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        ProductEntity::class,
        InventoryItemEntity::class,
        ShoppingListEntity::class,
        ShoppingListItemEntity::class,
        ShoppingSessionEntity::class,
        PurchaseTransactionEntity::class,
        UserPreferencesEntity::class,
        CategoryEntity::class,
        ActionEventEntity::class,
        PendingLookupEntity::class,
        MinimumStockRuleEntity::class,
        RecipeEntity::class,
        MealPlanEntity::class,
        MealPlanEntryEntity::class,
        BudgetTargetEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun minimumStockRuleDao(): MinimumStockRuleDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun budgetTargetDao(): BudgetTargetDao

    companion object {
        @Volatile
        private var INSTANCE: PantryDatabase? = null

        // Migration from version 1 to 2: Add minimum_stock_rules table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS minimum_stock_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        minimumQuantity REAL NOT NULL DEFAULT 1.0,
                        reorderQuantity REAL NOT NULL DEFAULT 1.0,
                        autoAddToList INTEGER NOT NULL DEFAULT 1,
                        lastTriggeredAt INTEGER,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        isStaple INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Migration from version 2 to 3: Add meal planning tables
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create recipes table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recipes (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT,
                        instructions TEXT,
                        prepTimeMinutes INTEGER,
                        cookTimeMinutes INTEGER,
                        servings INTEGER NOT NULL DEFAULT 4,
                        ingredientsJson TEXT NOT NULL DEFAULT '[]',
                        imageUrl TEXT,
                        sourceUrl TEXT,
                        isAiGenerated INTEGER NOT NULL DEFAULT 0,
                        aiQuery TEXT,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create meal_plans table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS meal_plans (
                        id TEXT NOT NULL PRIMARY KEY,
                        weekStartDate INTEGER NOT NULL,
                        name TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create index on weekStartDate for efficient lookups
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_meal_plans_weekStartDate
                    ON meal_plans (weekStartDate)
                """.trimIndent())

                // Create meal_plan_entries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS meal_plan_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        mealPlanId TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        mealType TEXT NOT NULL,
                        recipeId TEXT,
                        customMealName TEXT,
                        servings INTEGER NOT NULL DEFAULT 2,
                        notes TEXT,
                        calendarEventId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (mealPlanId) REFERENCES meal_plans(id) ON DELETE CASCADE,
                        FOREIGN KEY (recipeId) REFERENCES recipes(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // Create indices for meal_plan_entries
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_meal_plan_entries_mealPlanId
                    ON meal_plan_entries (mealPlanId)
                """.trimIndent())

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_meal_plan_entries_recipeId
                    ON meal_plan_entries (recipeId)
                """.trimIndent())

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_meal_plan_entries_date
                    ON meal_plan_entries (date)
                """.trimIndent())
            }
        }

        // Migration from version 3 to 4: Add budget targets table
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS budget_targets (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT 'Grocery Budget',
                        amount REAL NOT NULL DEFAULT 0.0,
                        period TEXT NOT NULL DEFAULT 'WEEKLY',
                        alertThreshold REAL NOT NULL DEFAULT 0.8,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        startDate INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): PantryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PantryDatabase::class.java,
                    "pantrywise_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    seedDefaultCategories(database.categoryDao())
                }
            }
        }

        private suspend fun seedDefaultCategories(categoryDao: CategoryDao) {
            val defaultCategories = listOf(
                CategoryEntity(name = "Produce", icon = "eco", color = "#4CAF50", sortOrder = 0, isDefault = true),
                CategoryEntity(name = "Dairy & Eggs", icon = "egg", color = "#FFF9C4", sortOrder = 1, isDefault = true),
                CategoryEntity(name = "Meat & Seafood", icon = "set_meal", color = "#FFCDD2", sortOrder = 2, isDefault = true),
                CategoryEntity(name = "Frozen", icon = "ac_unit", color = "#B3E5FC", sortOrder = 3, isDefault = true),
                CategoryEntity(name = "Pantry Staples", icon = "kitchen", color = "#D7CCC8", sortOrder = 4, isDefault = true),
                CategoryEntity(name = "Snacks", icon = "cookie", color = "#FFE0B2", sortOrder = 5, isDefault = true),
                CategoryEntity(name = "Beverages", icon = "local_cafe", color = "#B2EBF2", sortOrder = 6, isDefault = true),
                CategoryEntity(name = "Bakery", icon = "bakery_dining", color = "#FFECB3", sortOrder = 7, isDefault = true),
                CategoryEntity(name = "Condiments & Sauces", icon = "liquor", color = "#FFCCBC", sortOrder = 8, isDefault = true),
                CategoryEntity(name = "Canned Goods", icon = "inventory_2", color = "#CFD8DC", sortOrder = 9, isDefault = true),
                CategoryEntity(name = "Cleaning Supplies", icon = "cleaning_services", color = "#E1BEE7", sortOrder = 10, isDefault = true),
                CategoryEntity(name = "Personal Care", icon = "spa", color = "#F8BBD9", sortOrder = 11, isDefault = true),
                CategoryEntity(name = "Baby & Kids", icon = "child_care", color = "#C5CAE9", sortOrder = 12, isDefault = true),
                CategoryEntity(name = "Pet Supplies", icon = "pets", color = "#A5D6A7", sortOrder = 13, isDefault = true),
                CategoryEntity(name = "Other", icon = "more_horiz", color = "#E0E0E0", sortOrder = 14, isDefault = true)
            )
            categoryDao.insertAll(defaultCategories)
        }
    }
}

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromUnit(value: Unit): String = value.name

    @TypeConverter
    fun toUnit(value: String): Unit = Unit.valueOf(value)

    @TypeConverter
    fun fromStockStatus(value: StockStatus): String = value.name

    @TypeConverter
    fun toStockStatus(value: String): StockStatus = StockStatus.valueOf(value)

    @TypeConverter
    fun fromSourceType(value: SourceType?): String? = value?.name

    @TypeConverter
    fun toSourceType(value: String?): SourceType? = value?.let { SourceType.valueOf(it) }

    @TypeConverter
    fun fromLocationType(value: LocationType): String = value.name

    @TypeConverter
    fun toLocationType(value: String): LocationType = LocationType.valueOf(value)

    @TypeConverter
    fun fromActionType(value: ActionType): String = value.name

    @TypeConverter
    fun toActionType(value: String): ActionType = ActionType.valueOf(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter
    fun fromCartMatchType(value: CartMatchType): String = value.name

    @TypeConverter
    fun toCartMatchType(value: String): CartMatchType = CartMatchType.valueOf(value)

    @TypeConverter
    fun fromMealType(value: MealType): String = value.name

    @TypeConverter
    fun toMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter
    fun fromBudgetPeriod(value: BudgetPeriod): String = value.name

    @TypeConverter
    fun toBudgetPeriod(value: String): BudgetPeriod = BudgetPeriod.valueOf(value)

    @TypeConverter
    fun fromCartItemList(value: List<CartItem>): String = gson.toJson(value)

    @TypeConverter
    fun toCartItemList(value: String): List<CartItem> {
        val type = object : TypeToken<List<CartItem>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromPurchaseItemList(value: List<PurchaseItem>): String = gson.toJson(value)

    @TypeConverter
    fun toPurchaseItemList(value: String): List<PurchaseItem> {
        val type = object : TypeToken<List<PurchaseItem>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
