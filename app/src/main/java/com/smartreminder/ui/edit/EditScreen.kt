package com.smartreminder.ui.edit

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class EditViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    private val _reminder = MutableStateFlow<Reminder?>(null)
    val reminder: StateFlow<Reminder?> = _reminder

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState

    private val _permissionNeeded = MutableStateFlow(false)
    val permissionNeeded: StateFlow<Boolean> = _permissionNeeded

    fun getExactAlarmSettingsIntent(): Intent? {
        return permissionHelper.getExactAlarmSettingsIntent()
    }

    fun onPermissionGranted() {
        _permissionNeeded.value = false
    }

    fun onPermissionDenied() {
        _permissionNeeded.value = false
    }

    fun loadReminder(reminderId: Long) {
        viewModelScope.launch {
            val r = reminderRepository.getReminderById(reminderId)
            _reminder.value = r
            r?.let {
                _uiState.value = EditUiState(
                    name = it.name,
                    description = it.description,
                    triggerCondition = it.triggerCondition,
                    reminderMethod = it.reminderMethod
                )
            }
        }
    }
    
    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }
    
    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }
    
    fun updateReminderMethod(method: ReminderMethod) {
        _uiState.value = _uiState.value.copy(reminderMethod = method)
    }

    fun updateTriggerTime(hour: Int, minute: Int) {
        val current = _uiState.value.triggerCondition ?: return
        val newCondition = when (current) {
            is TriggerCondition.Daily -> current.copy(hour = hour, minute = minute)
            is TriggerCondition.Weekly -> current.copy(hour = hour, minute = minute)
            is TriggerCondition.Monthly -> current.copy(hour = hour, minute = minute)
            is TriggerCondition.Yearly -> current.copy(hour = hour, minute = minute)
            is TriggerCondition.Once -> {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = current.timestamp
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                }
                TriggerCondition.Once(cal.timeInMillis)
            }
            else -> current
        }
        _uiState.value = _uiState.value.copy(triggerCondition = newCondition)
    }
    
    fun saveReminder() {
        val reminder = _reminder.value ?: return
        val state = _uiState.value
        val triggerCondition = state.triggerCondition ?: return

        // Check exact alarm permission before scheduling
        if (permissionHelper.needsExactAlarmPermission()) {
            _permissionNeeded.value = true
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)

            try {
                val action = when (state.reminderMethod) {
                    ReminderMethod.NOTIFICATION -> ReminderAction.SendNotification(
                        title = state.name,
                        content = state.description
                    )
                    ReminderMethod.STRONG_REMINDER,
                    ReminderMethod.STRONG_REMINDER_WITH_SETTINGS -> ReminderAction.StrongReminder(
                        title = state.name,
                        content = state.description
                    )
                }

                val updated = reminder.copy(
                    name = state.name,
                    description = state.description,
                    triggerCondition = triggerCondition,
                    reminderMethod = state.reminderMethod,
                    actions = listOf(action),
                    updatedAt = System.currentTimeMillis()
                )

                reminderRepository.updateReminder(updated)

                // 重新调度
                reminderScheduler.cancel(reminder.id)
                if (updated.isEnabled) {
                    reminderScheduler.schedule(updated.id, updated.triggerCondition)
                }

                _uiState.value = state.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = state.copy(isSaving = false, saveError = e.message)
            }
        }
    }
}

data class EditUiState(
    val name: String = "",
    val description: String = "",
    val triggerCondition: TriggerCondition? = null,
    val reminderMethod: ReminderMethod = ReminderMethod.NOTIFICATION,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val saveError: String? = null,
    val showTimePicker: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    reminderId: Long,
    viewModel: EditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val reminder by viewModel.reminder.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val permissionNeeded by viewModel.permissionNeeded.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // TimePicker 状态
    val currentCondition = uiState.triggerCondition
    val initialHour = when (currentCondition) {
        is TriggerCondition.Daily -> currentCondition.hour
        is TriggerCondition.Weekly -> currentCondition.hour
        is TriggerCondition.Monthly -> currentCondition.hour
        is TriggerCondition.Yearly -> currentCondition.hour
        is TriggerCondition.Once -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentCondition.timestamp }
            cal.get(java.util.Calendar.HOUR_OF_DAY)
        }
        else -> 8
    }
    val initialMinute = when (currentCondition) {
        is TriggerCondition.Daily -> currentCondition.minute
        is TriggerCondition.Weekly -> currentCondition.minute
        is TriggerCondition.Monthly -> currentCondition.minute
        is TriggerCondition.Yearly -> currentCondition.minute
        is TriggerCondition.Once -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentCondition.timestamp }
            cal.get(java.util.Calendar.MINUTE)
        }
        else -> 0
    }
    // TimePicker 对话框（创建在 if 内，确保 currentCondition 已就绪）
    if (showTimePicker) {
        val dialogTimePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTriggerTime(dialogTimePickerState.hour, dialogTimePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("修改时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = dialogTimePickerState)
                }
            }
        )
    }

    LaunchedEffect(reminderId) {
        viewModel.loadReminder(reminderId)
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑提醒", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (reminder == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("提醒名称") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.updateDescription(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("描述（可选）") },
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 触发条件（可点击修改时间）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("触发条件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                uiState.triggerCondition?.toDisplayString() ?: "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = "修改时间",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 提醒方式
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("提醒方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(
                                selected = uiState.reminderMethod == ReminderMethod.NOTIFICATION,
                                onClick = { viewModel.updateReminderMethod(ReminderMethod.NOTIFICATION) },
                                label = { Text("普通通知") },
                                leadingIcon = if (uiState.reminderMethod == ReminderMethod.NOTIFICATION) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = uiState.reminderMethod == ReminderMethod.STRONG_REMINDER,
                                onClick = { viewModel.updateReminderMethod(ReminderMethod.STRONG_REMINDER) },
                                label = { Text("强提醒") },
                                leadingIcon = if (uiState.reminderMethod == ReminderMethod.STRONG_REMINDER) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.saveReminder() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving && uiState.name.isNotEmpty()
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("保存修改")
                }

                uiState.saveError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // 精确闹钟权限请求对话框
    if (permissionNeeded) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onPermissionDenied()
            },
            icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
            title = { Text("需要精确闹钟权限") },
            text = {
                Text("精确闹钟权限可确保提醒在准确的时间触发。\n\n请前往系统设置开启精确闹钟权限，否则提醒可能会延迟触发。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.getExactAlarmSettingsIntent()?.let { intent ->
                            context.startActivity(intent)
                        }
                        viewModel.onPermissionDenied()
                    }
                ) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onPermissionDenied()
                    }
                ) {
                    Text("稍后")
                }
            }
        )
    }
}
