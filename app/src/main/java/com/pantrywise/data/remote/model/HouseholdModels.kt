package com.pantrywise.data.remote.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a household in Firestore
 */
data class FirestoreHousehold(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val ownerUserId: String = "",
    val inviteCode: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    // No-argument constructor for Firestore
    constructor() : this("", "", "", "")
}

/**
 * Represents a household member in Firestore
 */
data class FirestoreHouseholdMember(
    @DocumentId
    val id: String = "",
    val householdId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val role: String = MemberRole.MEMBER.name,
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    @ServerTimestamp
    val joinedAt: Timestamp? = null
) {
    constructor() : this("", "", "", "", null, null, MemberRole.MEMBER.name, true, null)
}

/**
 * Role types for household members
 */
enum class MemberRole {
    OWNER,      // Can delete household, manage all members
    ADMIN,      // Can invite and remove members
    MEMBER      // Regular member
}

/**
 * Invite for joining a household
 */
data class HouseholdInvite(
    @DocumentId
    val id: String = "",
    val householdId: String = "",
    val householdName: String = "",
    val code: String = "",
    val createdByUserId: String = "",
    val createdByName: String = "",
    val expiresAt: Timestamp? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val maxUses: Int = -1,  // -1 means unlimited
    val usedCount: Int = 0
) {
    constructor() : this("", "", "", "", "", "", null, null, -1, 0)

    val isExpired: Boolean
        get() = expiresAt?.let { it.toDate().time < System.currentTimeMillis() } ?: false

    val isMaxedOut: Boolean
        get() = maxUses > 0 && usedCount >= maxUses

    val isValid: Boolean
        get() = !isExpired && !isMaxedOut
}

/**
 * Activity log entry for household
 */
data class HouseholdActivity(
    @DocumentId
    val id: String = "",
    val householdId: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val action: String = "",
    val description: String = "",
    val itemType: String? = null,
    val itemId: String? = null,
    val itemName: String? = null,
    @ServerTimestamp
    val timestamp: Timestamp? = null
) {
    constructor() : this("", "", "", "", "", "", null, null, null, null)
}

/**
 * Action types for activity log
 */
enum class HouseholdActionType {
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_REMOVED,
    ITEM_ADDED,
    ITEM_REMOVED,
    ITEM_UPDATED,
    LIST_CREATED,
    LIST_COMPLETED,
    SHOPPING_STARTED,
    SHOPPING_COMPLETED,
    HOUSEHOLD_UPDATED
}

/**
 * Shared shopping list in Firestore
 */
data class FirestoreShoppingList(
    @DocumentId
    val id: String = "",
    val householdId: String = "",
    val name: String = "",
    val createdByUserId: String = "",
    val createdByName: String = "",
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    constructor() : this("", "", "", "", "", false, null, null)
}

/**
 * Shared shopping list item in Firestore
 */
data class FirestoreShoppingListItem(
    @DocumentId
    val id: String = "",
    val listId: String = "",
    val productId: String? = null,
    val name: String = "",
    val quantity: Double = 1.0,
    val unit: String = "EACH",
    val category: String = "Other",
    @get:PropertyName("isPurchased")
    @set:PropertyName("isPurchased")
    var isPurchased: Boolean = false,
    val purchasedByUserId: String? = null,
    val purchasedByName: String? = null,
    val addedByUserId: String = "",
    val addedByName: String = "",
    val notes: String? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    constructor() : this("", "", null, "", 1.0, "EACH", "Other", false, null, null, "", "", null, null, null)
}

/**
 * Shared pantry item in Firestore
 */
data class FirestorePantryItem(
    @DocumentId
    val id: String = "",
    val householdId: String = "",
    val productId: String? = null,
    val name: String = "",
    val brand: String? = null,
    val category: String = "Other",
    val quantity: Double = 1.0,
    val unit: String = "EACH",
    val location: String = "PANTRY",
    val expirationDate: Timestamp? = null,
    val addedByUserId: String = "",
    val addedByName: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    constructor() : this("", "", null, "", null, "Other", 1.0, "EACH", "PANTRY", null, "", "", null, null)
}
