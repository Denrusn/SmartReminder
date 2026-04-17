package com.smartreminder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.Reminder
import com.smartreminder.domain.repository.ReminderRepository
import com.smartreminder.service.ReminderScheduler
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
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    
    val reminders: StateFlow<List<Reminder>> = reminderRepository.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    fun toggleReminderEnabled(reminder: Reminder) {
        viewModelScope.launch {
            val newEnabled = !reminder.isEnabled
            reminderRepository.updateReminderEnabled(reminder.id, newEnabled)
            
            if (newEnabled) {
                reminderScheduler.schedule(reminder.id, reminder.triggerCondition)
            } else {
                reminderScheduler.cancel(reminder.id)
            }
        }
    }
    
    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderScheduler.cancel(reminder.id)
            reminderRepository.deleteReminder(reminder.id)
        }
    }
}
