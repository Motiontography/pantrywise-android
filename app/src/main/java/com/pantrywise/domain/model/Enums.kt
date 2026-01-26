package com.pantrywise.domain.model

enum class StockStatus(val displayName: String, val badgeColor: String) {
    IN_STOCK("In Stock", "#4CAF50"),
    LOW("Low", "#FFC107"),
    OUT_OF_STOCK("Out of Stock", "#9E9E9E"),
    EXPIRED("Expired", "#F44336"),
    EXPIRING_SOON("Expiring Soon", "#FF9800"),
    UNKNOWN("Unknown", "#9E9E9E")
}

enum class Unit(val displayName: String) {
    EACH("each"),
    PACK("pack"),
    BOX("box"),
    BAG("bag"),
    CAN("can"),
    JAR("jar"),
    BOTTLE("bottle"),
    CUP("cup"),
    TABLESPOON("tbsp"),
    TEASPOON("tsp"),
    OUNCE("oz"),
    POUND("lb"),
    GRAM("g"),
    KILOGRAM("kg"),
    MILLILITER("ml"),
    LITER("L"),
    FLUID_OUNCE("fl oz"),
    GALLON("gal"),
    BUNCH("bunch"),
    UNKNOWN("unit");

    // Aliases for backward compatibility
    companion object {
        val OZ = OUNCE
        val LB = POUND
        val G = GRAM
        val KG = KILOGRAM
        val ML = MILLILITER
        val L = LITER
        val FL_OZ = FLUID_OUNCE
        val GAL = GALLON
    }
}

enum class SourceType {
    USER_MANUAL,
    BARCODE_SCAN,
    OPEN_FOOD_FACTS,
    SUGGESTION,
    RECIPE_PARSE,
    AI_SUGGESTION,
    AI_VISION,
    RECEIPT_SCAN,
    SMART_SHELF_SNAP
}

enum class LocationType(val displayName: String, val icon: String) {
    PANTRY("Pantry", "kitchen"),
    FRIDGE("Fridge", "kitchen"),
    FREEZER("Freezer", "ac_unit"),
    GARAGE("Garage", "garage"),
    OTHER("Other", "more_horiz")
}

enum class ActionType {
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    INVENTORY_ADDED,
    INVENTORY_REMOVED,
    INVENTORY_MOVED,
    SHOPPING_SESSION_STARTED,
    SHOPPING_SESSION_COMPLETED,
    SHOPPING_LIST_ITEM_ADDED,
    SHOPPING_LIST_ITEM_REMOVED,
    PREFERENCE_UPDATED,
    EXPORT_COMPLETED
}

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED
}

enum class CartMatchType(val displayName: String, val color: String) {
    PLANNED("Planned", "#4CAF50"),
    EXTRA("Extra", "#2196F3"),
    ALREADY_STOCKED("Already Stocked", "#FF9800")
}
