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
import com.pantrywise.domain.model.Unit as MeasurementUnit
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
        BudgetTargetEntity::class,
        // Phase 1: New entities
        StoreEntity::class,
        PriceRecordEntity::class,
        StoreAisleMapEntity::class,
        PriceAlertEntity::class,
        WasteEventEntity::class,
        AuditSessionEntity::class,
        AuditItemEntity::class,
        NutritionEntity::class,
        NutritionLogEntry::class,
        NutritionGoalsEntity::class,
        ReceiptEntity::class,
        ReceiptImageEntity::class,
        ExpirationPatternEntity::class,
        CalendarEventEntity::class,
        CalendarSettingsEntity::class
    ],
    version = 5,
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

    // Phase 1: New DAOs
    abstract fun storeDao(): StoreDao
    abstract fun priceDao(): PriceDao
    abstract fun wasteDao(): WasteDao
    abstract fun auditDao(): AuditDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun expirationPatternDao(): ExpirationPatternDao
    abstract fun calendarEventDao(): CalendarEventDao

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

        // Migration from version 4 to 5: Add Phase 1 tables (price, waste, audit, nutrition, receipt, calendar)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create stores table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS stores (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        address TEXT,
                        latitude REAL,
                        longitude REAL,
                        phone TEXT,
                        website TEXT,
                        notes TEXT,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        lastVisited INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create price_records table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS price_records (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL,
                        storeId TEXT NOT NULL,
                        price REAL NOT NULL,
                        currency TEXT NOT NULL DEFAULT 'USD',
                        unitSize REAL,
                        unitType TEXT,
                        pricePerUnit REAL,
                        isOnSale INTEGER NOT NULL DEFAULT 0,
                        saleEndDate INTEGER,
                        notes TEXT,
                        recordedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (productId) REFERENCES products(id) ON DELETE CASCADE,
                        FOREIGN KEY (storeId) REFERENCES stores(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_price_records_productId ON price_records(productId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_price_records_storeId ON price_records(storeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_price_records_recordedAt ON price_records(recordedAt)")

                // Create store_aisle_maps table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS store_aisle_maps (
                        id TEXT NOT NULL PRIMARY KEY,
                        storeId TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        aisle TEXT NOT NULL,
                        section TEXT,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (storeId) REFERENCES stores(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_store_aisle_maps_storeId ON store_aisle_maps(storeId)")

                // Create price_alerts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS price_alerts (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL,
                        targetPrice REAL NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        triggeredAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (productId) REFERENCES products(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_price_alerts_productId ON price_alerts(productId)")

                // Create waste_events table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS waste_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT,
                        inventoryItemId TEXT,
                        productName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        unit TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        estimatedCost REAL,
                        currency TEXT NOT NULL DEFAULT 'USD',
                        daysBeforeExpiration INTEGER,
                        notes TEXT,
                        imageUrl TEXT,
                        wastedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (productId) REFERENCES products(id) ON DELETE SET NULL,
                        FOREIGN KEY (inventoryItemId) REFERENCES inventory_items(id) ON DELETE SET NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_waste_events_productId ON waste_events(productId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_waste_events_inventoryItemId ON waste_events(inventoryItemId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_waste_events_wastedAt ON waste_events(wastedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_waste_events_reason ON waste_events(reason)")

                // Create audit_sessions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS audit_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
                        totalItems INTEGER NOT NULL DEFAULT 0,
                        auditedItems INTEGER NOT NULL DEFAULT 0,
                        adjustedItems INTEGER NOT NULL DEFAULT 0,
                        removedItems INTEGER NOT NULL DEFAULT 0,
                        notes TEXT,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create audit_items table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS audit_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        inventoryItemId TEXT,
                        productName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        location TEXT NOT NULL,
                        expectedQuantity REAL NOT NULL,
                        actualQuantity REAL,
                        unit TEXT NOT NULL,
                        action TEXT,
                        notes TEXT,
                        auditedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (sessionId) REFERENCES audit_sessions(id) ON DELETE CASCADE,
                        FOREIGN KEY (inventoryItemId) REFERENCES inventory_items(id) ON DELETE SET NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_items_sessionId ON audit_items(sessionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_items_inventoryItemId ON audit_items(inventoryItemId)")

                // Create nutrition_data table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS nutrition_data (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL,
                        servingSize REAL,
                        servingSizeUnit TEXT,
                        servingsPerContainer REAL,
                        calories REAL,
                        totalFat REAL,
                        saturatedFat REAL,
                        transFat REAL,
                        polyunsaturatedFat REAL,
                        monounsaturatedFat REAL,
                        cholesterol REAL,
                        sodium REAL,
                        totalCarbohydrates REAL,
                        dietaryFiber REAL,
                        totalSugars REAL,
                        addedSugars REAL,
                        sugarAlcohols REAL,
                        protein REAL,
                        vitaminA REAL,
                        vitaminC REAL,
                        vitaminD REAL,
                        vitaminE REAL,
                        vitaminK REAL,
                        vitaminB1 REAL,
                        vitaminB2 REAL,
                        vitaminB3 REAL,
                        vitaminB6 REAL,
                        vitaminB12 REAL,
                        folate REAL,
                        biotin REAL,
                        pantothenicAcid REAL,
                        calcium REAL,
                        iron REAL,
                        potassium REAL,
                        magnesium REAL,
                        zinc REAL,
                        phosphorus REAL,
                        copper REAL,
                        manganese REAL,
                        selenium REAL,
                        chromium REAL,
                        molybdenum REAL,
                        iodine REAL,
                        caffeine REAL,
                        water REAL,
                        alcohol REAL,
                        omega3 REAL,
                        omega6 REAL,
                        sourceApi TEXT,
                        confidence REAL NOT NULL DEFAULT 1.0,
                        isUserEdited INTEGER NOT NULL DEFAULT 0,
                        labelFormat TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (productId) REFERENCES products(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_nutrition_data_productId ON nutrition_data(productId)")

                // Create nutrition_log_entries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS nutrition_log_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT,
                        productName TEXT NOT NULL,
                        servings REAL NOT NULL DEFAULT 1.0,
                        calories REAL,
                        protein REAL,
                        carbohydrates REAL,
                        fat REAL,
                        fiber REAL,
                        sugar REAL,
                        sodium REAL,
                        mealType TEXT,
                        notes TEXT,
                        loggedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_nutrition_log_entries_loggedAt ON nutrition_log_entries(loggedAt)")

                // Create nutrition_goals table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS nutrition_goals (
                        id TEXT NOT NULL PRIMARY KEY,
                        caloriesGoal REAL NOT NULL DEFAULT 2000.0,
                        proteinGoal REAL NOT NULL DEFAULT 50.0,
                        carbsGoal REAL NOT NULL DEFAULT 275.0,
                        fatGoal REAL NOT NULL DEFAULT 78.0,
                        fiberGoal REAL NOT NULL DEFAULT 28.0,
                        sugarLimit REAL NOT NULL DEFAULT 50.0,
                        sodiumLimit REAL NOT NULL DEFAULT 2300.0,
                        waterGoal REAL NOT NULL DEFAULT 2000.0,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create receipts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS receipts (
                        id TEXT NOT NULL PRIMARY KEY,
                        imageUri TEXT NOT NULL,
                        thumbnailUri TEXT,
                        storeId TEXT,
                        transactionId TEXT,
                        storeName TEXT,
                        storeAddress TEXT,
                        subtotal REAL,
                        tax REAL,
                        total REAL,
                        currency TEXT NOT NULL DEFAULT 'USD',
                        paymentMethod TEXT,
                        receiptDate INTEGER,
                        receiptNumber TEXT,
                        itemsJson TEXT NOT NULL DEFAULT '[]',
                        rawText TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        processingError TEXT,
                        confidence REAL NOT NULL DEFAULT 0,
                        isVerified INTEGER NOT NULL DEFAULT 0,
                        scannedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (storeId) REFERENCES stores(id) ON DELETE SET NULL,
                        FOREIGN KEY (transactionId) REFERENCES purchase_transactions(id) ON DELETE SET NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_storeId ON receipts(storeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_transactionId ON receipts(transactionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_scannedAt ON receipts(scannedAt)")

                // Create receipt_images table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS receipt_images (
                        id TEXT NOT NULL PRIMARY KEY,
                        receiptId TEXT NOT NULL,
                        imageUri TEXT NOT NULL,
                        pageNumber INTEGER NOT NULL DEFAULT 1,
                        width INTEGER,
                        height INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (receiptId) REFERENCES receipts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_images_receiptId ON receipt_images(receiptId)")

                // Create expiration_patterns table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS expiration_patterns (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT,
                        productName TEXT,
                        category TEXT NOT NULL,
                        typicalDaysToExpiration INTEGER NOT NULL,
                        minDays INTEGER,
                        maxDays INTEGER,
                        storageLocation TEXT,
                        openedShelfLife INTEGER,
                        confidence REAL NOT NULL DEFAULT 1.0,
                        sampleSize INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (productId) REFERENCES products(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_expiration_patterns_productId ON expiration_patterns(productId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_expiration_patterns_category ON expiration_patterns(category)")

                // Create calendar_events table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS calendar_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        calendarEventId INTEGER NOT NULL,
                        calendarId INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        isAllDay INTEGER NOT NULL DEFAULT 1,
                        reminderMinutes INTEGER,
                        lastSyncedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_calendarEventId ON calendar_events(calendarEventId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_entityType ON calendar_events(entityType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_entityId ON calendar_events(entityId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_eventType ON calendar_events(eventType)")

                // Create calendar_settings table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS calendar_settings (
                        id TEXT NOT NULL PRIMARY KEY,
                        isEnabled INTEGER NOT NULL DEFAULT 0,
                        calendarId INTEGER,
                        calendarName TEXT,
                        syncExpirations INTEGER NOT NULL DEFAULT 1,
                        syncMealPlans INTEGER NOT NULL DEFAULT 1,
                        syncShoppingTrips INTEGER NOT NULL DEFAULT 0,
                        syncRestockReminders INTEGER NOT NULL DEFAULT 1,
                        expirationReminderDays INTEGER NOT NULL DEFAULT 3,
                        mealPlanReminderMinutes INTEGER NOT NULL DEFAULT 60,
                        defaultReminderMinutes INTEGER NOT NULL DEFAULT 30,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
    fun fromUnit(value: MeasurementUnit): String = value.name

    @TypeConverter
    fun toUnit(value: String): MeasurementUnit = MeasurementUnit.valueOf(value)

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

    // Phase 1: New converters
    @TypeConverter
    fun fromWasteReason(value: WasteReason): String = value.name

    @TypeConverter
    fun toWasteReason(value: String): WasteReason = WasteReason.valueOf(value)

    @TypeConverter
    fun fromAuditStatus(value: AuditStatus): String = value.name

    @TypeConverter
    fun toAuditStatus(value: String): AuditStatus = AuditStatus.valueOf(value)

    @TypeConverter
    fun fromAuditAction(value: AuditAction?): String? = value?.name

    @TypeConverter
    fun toAuditAction(value: String?): AuditAction? = value?.let { AuditAction.valueOf(it) }

    @TypeConverter
    fun fromReceiptStatus(value: ReceiptStatus): String = value.name

    @TypeConverter
    fun toReceiptStatus(value: String): ReceiptStatus = ReceiptStatus.valueOf(value)

    @TypeConverter
    fun fromCalendarEventType(value: CalendarEventType): String = value.name

    @TypeConverter
    fun toCalendarEventType(value: String): CalendarEventType = CalendarEventType.valueOf(value)

    @TypeConverter
    fun fromReceiptLineItemList(value: List<ReceiptLineItem>): String = gson.toJson(value)

    @TypeConverter
    fun toReceiptLineItemList(value: String): List<ReceiptLineItem> {
        val type = object : TypeToken<List<ReceiptLineItem>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
