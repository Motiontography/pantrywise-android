package com.pantrywise.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.remote.model.FirestoreHousehold
import com.pantrywise.data.remote.model.FirestoreHouseholdMember
import com.pantrywise.data.remote.model.HouseholdActivity
import com.pantrywise.data.remote.model.MemberRole
import com.pantrywise.services.AuthUser
import com.pantrywise.services.FirebaseAuthService
import com.pantrywise.services.FirebaseHouseholdService
import com.pantrywise.services.HouseholdResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val households: List<FirestoreHousehold> = emptyList(),
    val selectedHousehold: FirestoreHousehold? = null,
    val members: List<FirestoreHouseholdMember> = emptyList(),
    val activityLog: List<HouseholdActivity> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val showJoinDialog: Boolean = false,
    val showInviteDialog: Boolean = false,
    val showLeaveConfirmation: Boolean = false
)

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val authService: FirebaseAuthService,
    private val householdService: FirebaseHouseholdService
) : ViewModel() {

    private val _state = MutableStateFlow(HouseholdState())
    val state: StateFlow<HouseholdState> = _state.asStateFlow()

    init {
        loadUserAndHouseholds()
    }

    private fun loadUserAndHouseholds() {
        viewModelScope.launch {
            // Observe current user
            authService.currentUser.collect { user ->
                _state.update { it.copy(currentUser = user) }

                if (user != null) {
                    // Load households when user is available
                    householdService.getMyHouseholds().collect { households ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                households = households
                            )
                        }

                        // Auto-select first household if none selected
                        if (_state.value.selectedHousehold == null && households.isNotEmpty()) {
                            selectHousehold(households.first())
                        }
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            households = emptyList(),
                            selectedHousehold = null,
                            members = emptyList()
                        )
                    }
                }
            }
        }
    }

    fun selectHousehold(household: FirestoreHousehold) {
        _state.update { it.copy(selectedHousehold = household) }

        // Load members and activity for this household
        viewModelScope.launch {
            householdService.getHouseholdMembers(household.id).collect { members ->
                _state.update { it.copy(members = members) }
            }
        }

        viewModelScope.launch {
            householdService.getActivityLog(household.id).collect { activities ->
                _state.update { it.copy(activityLog = activities) }
            }
        }
    }

    fun createHousehold(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            when (val result = householdService.createHousehold(name)) {
                is HouseholdResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            showCreateDialog = false,
                            successMessage = "Household \"$name\" created!"
                        )
                    }
                    selectHousehold(result.data)
                }
                is HouseholdResult.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun joinHousehold(inviteCode: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            when (val result = householdService.joinHousehold(inviteCode)) {
                is HouseholdResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            showJoinDialog = false,
                            successMessage = "Joined \"${result.data.name}\"!"
                        )
                    }
                    selectHousehold(result.data)
                }
                is HouseholdResult.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun leaveHousehold() {
        val household = _state.value.selectedHousehold ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showLeaveConfirmation = false) }

            when (val result = householdService.leaveHousehold(household.id)) {
                is HouseholdResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selectedHousehold = null,
                            successMessage = "Left \"${household.name}\""
                        )
                    }
                }
                is HouseholdResult.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun removeMember(member: FirestoreHouseholdMember) {
        val household = _state.value.selectedHousehold ?: return

        viewModelScope.launch {
            when (val result = householdService.removeMember(household.id, member.id)) {
                is HouseholdResult.Success -> {
                    _state.update {
                        it.copy(successMessage = "Removed ${member.displayName}")
                    }
                }
                is HouseholdResult.Error -> {
                    _state.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun updateMemberRole(member: FirestoreHouseholdMember, role: MemberRole) {
        viewModelScope.launch {
            when (val result = householdService.updateMemberRole(member.id, role)) {
                is HouseholdResult.Success -> {
                    _state.update {
                        it.copy(successMessage = "${member.displayName} is now ${role.name.lowercase()}")
                    }
                }
                is HouseholdResult.Error -> {
                    _state.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun regenerateInviteCode() {
        val household = _state.value.selectedHousehold ?: return

        viewModelScope.launch {
            when (val result = householdService.regenerateInviteCode(household.id)) {
                is HouseholdResult.Success -> {
                    _state.update {
                        it.copy(
                            selectedHousehold = household.copy(inviteCode = result.data),
                            successMessage = "New invite code generated"
                        )
                    }
                }
                is HouseholdResult.Error -> {
                    _state.update { it.copy(error = result.message) }
                }
            }
        }
    }

    // Dialog controls
    fun showCreateDialog() = _state.update { it.copy(showCreateDialog = true) }
    fun hideCreateDialog() = _state.update { it.copy(showCreateDialog = false) }
    fun showJoinDialog() = _state.update { it.copy(showJoinDialog = true) }
    fun hideJoinDialog() = _state.update { it.copy(showJoinDialog = false) }
    fun showInviteDialog() = _state.update { it.copy(showInviteDialog = true) }
    fun hideInviteDialog() = _state.update { it.copy(showInviteDialog = false) }
    fun showLeaveConfirmation() = _state.update { it.copy(showLeaveConfirmation = true) }
    fun hideLeaveConfirmation() = _state.update { it.copy(showLeaveConfirmation = false) }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSuccessMessage() = _state.update { it.copy(successMessage = null) }

    // Check if current user is owner of selected household
    val isOwner: Boolean
        get() {
            val userId = _state.value.currentUser?.uid ?: return false
            return _state.value.selectedHousehold?.ownerUserId == userId
        }

    // Check if current user is admin or owner
    val isAdminOrOwner: Boolean
        get() {
            val userId = _state.value.currentUser?.uid ?: return false
            val member = _state.value.members.find { it.userId == userId }
            return member?.role == MemberRole.OWNER.name || member?.role == MemberRole.ADMIN.name
        }
}
