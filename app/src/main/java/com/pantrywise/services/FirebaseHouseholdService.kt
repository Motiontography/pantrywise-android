package com.pantrywise.services

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pantrywise.data.remote.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed class HouseholdResult<T> {
    data class Success<T>(val data: T) : HouseholdResult<T>()
    data class Error<T>(val message: String) : HouseholdResult<T>()
}

@Singleton
class FirebaseHouseholdService @Inject constructor(
    private val authService: FirebaseAuthService
) {
    private val db: FirebaseFirestore = Firebase.firestore

    // Collection references
    private val householdsCollection = db.collection("households")
    private val membersCollection = db.collection("householdMembers")
    private val invitesCollection = db.collection("householdInvites")
    private val activityCollection = db.collection("householdActivity")
    private val shoppingListsCollection = db.collection("sharedShoppingLists")
    private val shoppingItemsCollection = db.collection("sharedShoppingItems")
    private val pantryItemsCollection = db.collection("sharedPantryItems")

    // ============ HOUSEHOLD OPERATIONS ============

    /**
     * Create a new household
     */
    suspend fun createHousehold(name: String): HouseholdResult<FirestoreHousehold> {
        val user = authService.currentUserSync
            ?: return HouseholdResult.Error("Not signed in")

        return try {
            val inviteCode = generateInviteCode()
            val householdId = UUID.randomUUID().toString()

            val household = FirestoreHousehold(
                id = householdId,
                name = name,
                ownerUserId = user.uid,
                inviteCode = inviteCode
            )

            householdsCollection.document(householdId).set(household).await()

            // Add owner as first member
            val member = FirestoreHouseholdMember(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                userId = user.uid,
                displayName = user.displayName ?: "Unknown",
                email = user.email,
                photoUrl = user.photoUrl,
                role = MemberRole.OWNER.name
            )
            membersCollection.document(member.id).set(member).await()

            HouseholdResult.Success(household)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to create household: ${e.localizedMessage}")
        }
    }

    /**
     * Get household by ID
     */
    suspend fun getHousehold(householdId: String): HouseholdResult<FirestoreHousehold> {
        return try {
            val snapshot = householdsCollection.document(householdId).get().await()
            val household = snapshot.toObject(FirestoreHousehold::class.java)
            if (household != null) {
                HouseholdResult.Success(household)
            } else {
                HouseholdResult.Error("Household not found")
            }
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to get household: ${e.localizedMessage}")
        }
    }

    /**
     * Get households for current user
     */
    fun getMyHouseholds(): Flow<List<FirestoreHousehold>> = callbackFlow {
        val user = authService.currentUserSync
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // First get member documents for this user
        val memberListener = membersCollection
            .whereEqualTo("userId", user.uid)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { memberSnapshot, memberError ->
                if (memberError != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val householdIds = memberSnapshot?.documents?.mapNotNull {
                    it.toObject(FirestoreHouseholdMember::class.java)?.householdId
                } ?: emptyList()

                if (householdIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                // Get households
                householdsCollection
                    .whereIn("id", householdIds.take(10)) // Firestore limits whereIn to 10
                    .get()
                    .addOnSuccessListener { householdSnapshot ->
                        val households = householdSnapshot.documents.mapNotNull {
                            it.toObject(FirestoreHousehold::class.java)
                        }
                        trySend(households)
                    }
            }

        awaitClose { memberListener.remove() }
    }

    /**
     * Update household name
     */
    suspend fun updateHousehold(householdId: String, name: String): HouseholdResult<Unit> {
        return try {
            householdsCollection.document(householdId).update(
                mapOf(
                    "name" to name,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            HouseholdResult.Success(Unit)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to update household: ${e.localizedMessage}")
        }
    }

    /**
     * Delete household
     */
    suspend fun deleteHousehold(householdId: String): HouseholdResult<Unit> {
        return try {
            // Delete all members
            val members = membersCollection.whereEqualTo("householdId", householdId).get().await()
            members.documents.forEach { it.reference.delete().await() }

            // Delete all invites
            val invites = invitesCollection.whereEqualTo("householdId", householdId).get().await()
            invites.documents.forEach { it.reference.delete().await() }

            // Delete household
            householdsCollection.document(householdId).delete().await()

            HouseholdResult.Success(Unit)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to delete household: ${e.localizedMessage}")
        }
    }

    // ============ MEMBER OPERATIONS ============

    /**
     * Get members of a household as Flow
     */
    fun getHouseholdMembers(householdId: String): Flow<List<FirestoreHouseholdMember>> = callbackFlow {
        val listener = membersCollection
            .whereEqualTo("householdId", householdId)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val members = snapshot?.documents?.mapNotNull {
                    it.toObject(FirestoreHouseholdMember::class.java)
                } ?: emptyList()

                trySend(members)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Join household with invite code
     */
    suspend fun joinHousehold(inviteCode: String): HouseholdResult<FirestoreHousehold> {
        val user = authService.currentUserSync
            ?: return HouseholdResult.Error("Not signed in")

        return try {
            // Find household with invite code
            val householdSnapshot = householdsCollection
                .whereEqualTo("inviteCode", inviteCode.uppercase())
                .get()
                .await()

            val household = householdSnapshot.documents.firstOrNull()
                ?.toObject(FirestoreHousehold::class.java)
                ?: return HouseholdResult.Error("Invalid invite code")

            // Check if already a member
            val existingMember = membersCollection
                .whereEqualTo("householdId", household.id)
                .whereEqualTo("userId", user.uid)
                .get()
                .await()

            if (!existingMember.isEmpty) {
                // Reactivate if inactive
                val member = existingMember.documents.first()
                member.reference.update("isActive", true).await()
                return HouseholdResult.Success(household)
            }

            // Add as new member
            val member = FirestoreHouseholdMember(
                id = UUID.randomUUID().toString(),
                householdId = household.id,
                userId = user.uid,
                displayName = user.displayName ?: "Unknown",
                email = user.email,
                photoUrl = user.photoUrl,
                role = MemberRole.MEMBER.name
            )
            membersCollection.document(member.id).set(member).await()

            // Log activity
            logActivity(
                householdId = household.id,
                action = HouseholdActionType.MEMBER_JOINED,
                description = "${user.displayName ?: "Someone"} joined the household"
            )

            HouseholdResult.Success(household)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to join household: ${e.localizedMessage}")
        }
    }

    /**
     * Leave household
     */
    suspend fun leaveHousehold(householdId: String): HouseholdResult<Unit> {
        val user = authService.currentUserSync
            ?: return HouseholdResult.Error("Not signed in")

        return try {
            val memberSnapshot = membersCollection
                .whereEqualTo("householdId", householdId)
                .whereEqualTo("userId", user.uid)
                .get()
                .await()

            val member = memberSnapshot.documents.firstOrNull()
                ?: return HouseholdResult.Error("Not a member of this household")

            // Check if owner
            val memberData = member.toObject(FirestoreHouseholdMember::class.java)
            if (memberData?.role == MemberRole.OWNER.name) {
                return HouseholdResult.Error("Owners cannot leave. Transfer ownership or delete the household.")
            }

            // Mark as inactive
            member.reference.update("isActive", false).await()

            // Log activity
            logActivity(
                householdId = householdId,
                action = HouseholdActionType.MEMBER_LEFT,
                description = "${user.displayName ?: "Someone"} left the household"
            )

            HouseholdResult.Success(Unit)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to leave household: ${e.localizedMessage}")
        }
    }

    /**
     * Remove member from household (admin/owner only)
     */
    suspend fun removeMember(householdId: String, memberId: String): HouseholdResult<Unit> {
        return try {
            val member = membersCollection.document(memberId).get().await()
                .toObject(FirestoreHouseholdMember::class.java)
                ?: return HouseholdResult.Error("Member not found")

            if (member.role == MemberRole.OWNER.name) {
                return HouseholdResult.Error("Cannot remove the owner")
            }

            membersCollection.document(memberId).update("isActive", false).await()

            // Log activity
            logActivity(
                householdId = householdId,
                action = HouseholdActionType.MEMBER_REMOVED,
                description = "${member.displayName} was removed from the household"
            )

            HouseholdResult.Success(Unit)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to remove member: ${e.localizedMessage}")
        }
    }

    /**
     * Update member role (owner only)
     */
    suspend fun updateMemberRole(memberId: String, role: MemberRole): HouseholdResult<Unit> {
        return try {
            membersCollection.document(memberId).update("role", role.name).await()
            HouseholdResult.Success(Unit)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to update role: ${e.localizedMessage}")
        }
    }

    // ============ INVITE OPERATIONS ============

    /**
     * Generate a new invite code for household
     */
    suspend fun regenerateInviteCode(householdId: String): HouseholdResult<String> {
        return try {
            val newCode = generateInviteCode()
            householdsCollection.document(householdId).update(
                mapOf(
                    "inviteCode" to newCode,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            HouseholdResult.Success(newCode)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to regenerate invite code: ${e.localizedMessage}")
        }
    }

    // ============ ACTIVITY LOG ============

    /**
     * Log activity to household
     */
    suspend fun logActivity(
        householdId: String,
        action: HouseholdActionType,
        description: String,
        itemType: String? = null,
        itemId: String? = null,
        itemName: String? = null
    ) {
        val user = authService.currentUserSync ?: return

        try {
            val activity = HouseholdActivity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                userId = user.uid,
                userDisplayName = user.displayName ?: "Unknown",
                action = action.name,
                description = description,
                itemType = itemType,
                itemId = itemId,
                itemName = itemName
            )
            activityCollection.document(activity.id).set(activity).await()
        } catch (e: Exception) {
            // Silently fail - activity logging shouldn't break operations
        }
    }

    /**
     * Get activity log for household
     */
    fun getActivityLog(householdId: String, limit: Int = 50): Flow<List<HouseholdActivity>> = callbackFlow {
        val listener = activityCollection
            .whereEqualTo("householdId", householdId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val activities = snapshot?.documents?.mapNotNull {
                    it.toObject(HouseholdActivity::class.java)
                } ?: emptyList()

                trySend(activities)
            }

        awaitClose { listener.remove() }
    }

    // ============ SHARED SHOPPING LISTS ============

    /**
     * Create shared shopping list
     */
    suspend fun createSharedList(householdId: String, name: String): HouseholdResult<FirestoreShoppingList> {
        val user = authService.currentUserSync
            ?: return HouseholdResult.Error("Not signed in")

        return try {
            val list = FirestoreShoppingList(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                name = name,
                createdByUserId = user.uid,
                createdByName = user.displayName ?: "Unknown"
            )
            shoppingListsCollection.document(list.id).set(list).await()

            logActivity(
                householdId = householdId,
                action = HouseholdActionType.LIST_CREATED,
                description = "${user.displayName} created list \"$name\"",
                itemType = "shopping_list",
                itemId = list.id,
                itemName = name
            )

            HouseholdResult.Success(list)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to create list: ${e.localizedMessage}")
        }
    }

    /**
     * Get shared shopping lists for household
     */
    fun getSharedLists(householdId: String): Flow<List<FirestoreShoppingList>> = callbackFlow {
        val listener = shoppingListsCollection
            .whereEqualTo("householdId", householdId)
            .whereEqualTo("isCompleted", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val lists = snapshot?.documents?.mapNotNull {
                    it.toObject(FirestoreShoppingList::class.java)
                } ?: emptyList()

                trySend(lists)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Add item to shared list
     */
    suspend fun addItemToSharedList(
        listId: String,
        name: String,
        quantity: Double,
        unit: String,
        category: String,
        productId: String? = null,
        notes: String? = null
    ): HouseholdResult<FirestoreShoppingListItem> {
        val user = authService.currentUserSync
            ?: return HouseholdResult.Error("Not signed in")

        return try {
            val item = FirestoreShoppingListItem(
                id = UUID.randomUUID().toString(),
                listId = listId,
                productId = productId,
                name = name,
                quantity = quantity,
                unit = unit,
                category = category,
                addedByUserId = user.uid,
                addedByName = user.displayName ?: "Unknown",
                notes = notes
            )
            shoppingItemsCollection.document(item.id).set(item).await()
            HouseholdResult.Success(item)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to add item: ${e.localizedMessage}")
        }
    }

    /**
     * Get items for shared list
     */
    fun getSharedListItems(listId: String): Flow<List<FirestoreShoppingListItem>> = callbackFlow {
        val listener = shoppingItemsCollection
            .whereEqualTo("listId", listId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull {
                    it.toObject(FirestoreShoppingListItem::class.java)
                } ?: emptyList()

                trySend(items)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Mark item as purchased
     */
    suspend fun markItemPurchased(itemId: String, purchased: Boolean): HouseholdResult<Unit> {
        val user = authService.currentUserSync
            ?: return HouseholdResult.Error("Not signed in")

        return try {
            shoppingItemsCollection.document(itemId).update(
                mapOf(
                    "isPurchased" to purchased,
                    "purchasedByUserId" to if (purchased) user.uid else null,
                    "purchasedByName" to if (purchased) (user.displayName ?: "Unknown") else null,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            HouseholdResult.Success(Unit)
        } catch (e: Exception) {
            HouseholdResult.Error("Failed to update item: ${e.localizedMessage}")
        }
    }

    // ============ HELPER FUNCTIONS ============

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed confusing chars like I, O, 0, 1
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
