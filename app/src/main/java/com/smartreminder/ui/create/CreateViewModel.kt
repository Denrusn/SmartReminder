package com.smartreminder.ui.create

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.ai.NaturalLanguageParser
import com.smartreminder.domain.model.*
import com.smartreminder.domain.repository.ReminderRepository
import com.smartreminder.service.ReminderScheduler
import com.smartreminder.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val naturalLanguageParser: NaturalLanguageParser,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState

    private val _permissionNeeded = MutableStateFlow(false)
    val permissionNeeded: StateFlow<Boolean> = _permissionNeeded

    fun getExactAlarmSettingsIntent(): Intent? {
        return permissionHelper.getExactAlarmSettingsIntent()
    }

    fun onPermissionGranted() {
        _permissionNeeded.value = false
        // 权限授予后，重新尝试保存提醒
        saveReminder()
    }

    fun onPermissionDenied() {
        _permissionNeeded.value = false
        // 用户取消权限请求后，不再尝试保存（用户需要手动重新点击保存）
    }

    /**
     * 从系统设置返回后调用，检查权限并重新尝试保存
     */
    fun retrySaveAfterPermissionCheck() {
        _permissionNeeded.value = false
        // 检查权限是否已授予
        if (permissionHelper.canScheduleExactAlarms()) {
            // 权限已授予，保存提醒
            saveReminder()
        }
        // 如果权限仍未授予，_needed保持为false，用户需要手动重新点击保存
    }
    
    fun updateUserInput(input: String) {
        _uiState.value = _uiState.value.copy(userInput = input)
    }
    
    fun parseInput() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true)
            
            val result = naturalLanguageParser.parse(_uiState.value.userInput)
            
            when (result) {
                is NaturalLanguageParser.ParseResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isParsing = false,
                        parsedTriggerCondition = result.triggerCondition,
                        parsedReminderMethod = result.reminderMethod,
                        parsedTitle = result.title,
                        parsedContent = result.content,
                        parseError = null
                    )
                }
                is NaturalLanguageParser.ParseResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isParsing = false,
                        parseError = result.message
                    )
                }
            }
        }
    }
    
    fun updateTriggerCondition(condition: TriggerCondition) {
        _uiState.value = _uiState.value.copy(parsedTriggerCondition = condition)
    }
    
    fun updateReminderMethod(method: ReminderMethod) {
        _uiState.value = _uiState.value.copy(parsedReminderMethod = method)
    }
    
    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(parsedTitle = title)
    }
    
    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(parsedContent = content)
    }
    
    fun saveReminder() {
        val state = _uiState.value
        if (state.parsedTriggerCondition == null || state.parsedTitle.isEmpty()) {
            _uiState.value = state.copy(saveError = "请完善提醒信息")
            return
        }

        // Check exact alarm permission before scheduling
        if (permissionHelper.needsExactAlarmPermission()) {
            _permissionNeeded.value = true
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            try {
                val action = when (state.parsedReminderMethod) {
                    ReminderMethod.NOTIFICATION -> ReminderAction.SendNotification(
                        title = state.parsedTitle,
                        content = state.parsedContent
                    )
                    ReminderMethod.STRONG_REMINDER,
                    ReminderMethod.STRONG_REMINDER_WITH_SETTINGS -> ReminderAction.StrongReminder(
                        title = state.parsedTitle,
                        content = state.parsedContent
                    )
                }

                val reminder = Reminder(
                    name = state.parsedTitle,
                    description = state.parsedContent,
                    isEnabled = true,
                    triggerCondition = state.parsedTriggerCondition!!,
                    reminderMethod = state.parsedReminderMethod,
                    actions = listOf(action)
                )

                val id = reminderRepository.insertReminder(reminder)
                reminderScheduler.schedule(id, state.parsedTriggerCondition!!)
                
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                    saveError = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = "保存失败: ${e.message}"
                )
            }
        }
    }
}

data class CreateUiState(
    val userInput: String = "",
    val isParsing: Boolean = false,
    val parsedTriggerCondition: TriggerCondition? = null,
    val parsedReminderMethod: ReminderMethod = ReminderMethod.NOTIFICATION,
    val parsedTitle: String = "",
    val parsedContent: String = "",
    val parseError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val saveError: String? = null
)
