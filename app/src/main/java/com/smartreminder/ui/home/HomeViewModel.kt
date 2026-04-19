package com.smartreminder.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.Reminder
import com.smartreminder.domain.repository.ReminderRepository
import com.smartreminder.service.ReminderScheduler
import com.smartreminder.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = reminderRepository.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.None)
    val permissionState: StateFlow<PermissionState> = _permissionState

    sealed class PermissionState {
        data object None : PermissionState()
        data object ExactAlarmNeeded : PermissionState()
        data class Error(val message: String) : PermissionState()
    }

    fun toggleReminderEnabled(reminder: Reminder) {
        viewModelScope.launch {
            val newEnabled = !reminder.isEnabled

            if (newEnabled) {
                // Check exact alarm permission before enabling
                if (permissionHelper.needsExactAlarmPermission()) {
                    _permissionState.value = PermissionState.ExactAlarmNeeded
                    return@launch
                }

                try {
                    reminderScheduler.schedule(reminder.id, reminder.triggerCondition)
                } catch (e: Exception) {
                    _permissionState.value = PermissionState.Error("调度失败: ${e.message}")
                    return@launch
                }
            } else {
                reminderScheduler.cancel(reminder.id)
            }

            reminderRepository.updateReminderEnabled(reminder.id, newEnabled)
            _permissionState.value = PermissionState.None
        }
    }

    fun getExactAlarmSettingsIntent(): Intent? {
        return permissionHelper.getExactAlarmSettingsIntent()
    }

    fun clearPermissionState() {
        _permissionState.value = PermissionState.None
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderScheduler.cancel(reminder.id)
            reminderRepository.deleteReminder(reminder.id)
        }
    }
}
