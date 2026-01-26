package com.pantrywise.di

import android.content.Context
import com.pantrywise.data.local.PantryDatabase
import com.pantrywise.data.local.dao.*
import com.pantrywise.data.remote.OpenAIApi
import com.pantrywise.data.remote.OpenFoodFactsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Database
    @Provides
    @Singleton
    fun providePantryDatabase(@ApplicationContext context: Context): PantryDatabase {
        return PantryDatabase.getInstance(context)
    }

    // DAOs
    @Provides
    @Singleton
    fun provideProductDao(database: PantryDatabase): ProductDao {
        return database.productDao()
    }

    @Provides
    @Singleton
    fun provideInventoryDao(database: PantryDatabase): InventoryDao {
        return database.inventoryDao()
    }

    @Provides
    @Singleton
    fun provideShoppingListDao(database: PantryDatabase): ShoppingListDao {
        return database.shoppingListDao()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: PantryDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(database: PantryDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    @Singleton
    fun providePreferencesDao(database: PantryDatabase): PreferencesDao {
        return database.preferencesDao()
    }

    @Provides
    @Singleton
    fun provideMinimumStockRuleDao(database: PantryDatabase): MinimumStockRuleDao {
        return database.minimumStockRuleDao()
    }

    @Provides
    @Singleton
    fun provideMealPlanDao(database: PantryDatabase): MealPlanDao {
        return database.mealPlanDao()
    }

    @Provides
    @Singleton
    fun provideBudgetTargetDao(database: PantryDatabase): BudgetTargetDao {
        return database.budgetTargetDao()
    }

    // Phase 1: New DAOs
    @Provides
    @Singleton
    fun provideStoreDao(database: PantryDatabase): StoreDao {
        return database.storeDao()
    }

    @Provides
    @Singleton
    fun providePriceDao(database: PantryDatabase): PriceDao {
        return database.priceDao()
    }

    @Provides
    @Singleton
    fun provideWasteDao(database: PantryDatabase): WasteDao {
        return database.wasteDao()
    }

    @Provides
    @Singleton
    fun provideAuditDao(database: PantryDatabase): AuditDao {
        return database.auditDao()
    }

    @Provides
    @Singleton
    fun provideNutritionDao(database: PantryDatabase): NutritionDao {
        return database.nutritionDao()
    }

    @Provides
    @Singleton
    fun provideReceiptDao(database: PantryDatabase): ReceiptDao {
        return database.receiptDao()
    }

    @Provides
    @Singleton
    fun provideExpirationPatternDao(database: PantryDatabase): ExpirationPatternDao {
        return database.expirationPatternDao()
    }

    @Provides
    @Singleton
    fun provideCalendarEventDao(database: PantryDatabase): CalendarEventDao {
        return database.calendarEventDao()
    }

    // Network
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "MyPantryBuddy/1.0 (Android)")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("openFoodFacts")
    fun provideOpenFoodFactsRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OpenFoodFactsApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("openAI")
    fun provideOpenAIRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OpenAIApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(@Named("openFoodFacts") retrofit: Retrofit): OpenFoodFactsApi {
        return retrofit.create(OpenFoodFactsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenAIApi(@Named("openAI") retrofit: Retrofit): OpenAIApi {
        return retrofit.create(OpenAIApi::class.java)
    }
}
