package com.smartreminder.ui.edit

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    
    private val _reminder = MutableStateFlow<Reminder?>(null)
    val reminder: StateFlow<Reminder?> = _reminder
    
    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState
    
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
    
    fun saveReminder() {
        val reminder = _reminder.value ?: return
        val state = _uiState.value
        val triggerCondition = state.triggerCondition ?: return
        
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            
            try {
                val updated = reminder.copy(
                    name = state.name,
                    description = state.description,
                    triggerCondition = triggerCondition,
                    reminderMethod = state.reminderMethod,
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
    val saveError: String? = null
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
                
                // 触发条件
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("触发条件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            uiState.triggerCondition?.toDisplayString() ?: "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 提醒方式
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("提醒方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.reminderMethod == ReminderMethod.NOTIFICATION,
                                onClick = { viewModel.updateReminderMethod(ReminderMethod.NOTIFICATION) },
                                label = { Text("普通通知") }
                            )
                            FilterChip(
                                selected = uiState.reminderMethod == ReminderMethod.STRONG_REMINDER,
                                onClick = { viewModel.updateReminderMethod(ReminderMethod.STRONG_REMINDER) },
                                label = { Text("强提醒") }
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
}
