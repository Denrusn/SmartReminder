package com.smartreminder.ui.create

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartreminder.domain.model.ReminderMethod
import com.smartreminder.util.PermissionHelper
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    viewModel: CreateViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionNeeded by viewModel.permissionNeeded.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // 用于处理从设置返回后重新检查权限
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 用户从设置返回后，重新尝试保存
        viewModel.retrySaveAfterPermissionCheck()
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建提醒", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // 输入区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "描述你的提醒",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = uiState.userInput,
                        onValueChange = { viewModel.updateUserInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：每天早上8点提醒我拿早餐") },
                        minLines = 3,
                        maxLines = 5
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { viewModel.parseInput() },
                            enabled = uiState.userInput.isNotBlank() && !uiState.isParsing
                        ) {
                            if (uiState.isParsing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI解析")
                        }
                    }
                }
            }
            
            // 解析结果
            if (uiState.parsedTriggerCondition != null) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "AI解析结果",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // 触发条件
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "触发条件",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            uiState.parsedTriggerCondition?.toDisplayString() ?: "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 提醒方式
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "提醒方式",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterChip(
                                selected = uiState.parsedReminderMethod == ReminderMethod.NOTIFICATION,
                                onClick = { viewModel.updateReminderMethod(ReminderMethod.NOTIFICATION) },
                                label = { Text("普通通知") },
                                leadingIcon = if (uiState.parsedReminderMethod == ReminderMethod.NOTIFICATION) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = uiState.parsedReminderMethod == ReminderMethod.STRONG_REMINDER,
                                onClick = { viewModel.updateReminderMethod(ReminderMethod.STRONG_REMINDER) },
                                label = { Text("强提醒") },
                                leadingIcon = if (uiState.parsedReminderMethod == ReminderMethod.STRONG_REMINDER) {
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
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 提醒内容
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.parsedTitle,
                            onValueChange = { viewModel.updateTitle(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("标题") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.parsedContent,
                            onValueChange = { viewModel.updateContent(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("内容") },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }
                
                // 错误信息
                uiState.parseError?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 保存按钮
                Button(
                    onClick = { viewModel.saveReminder() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving && uiState.parsedTitle.isNotEmpty()
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("确认创建")
                }
                
                uiState.saveError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // 精确闹钟权限请求对话框
    if (permissionNeeded) {
        AlertDialog(
            onDismissRequest = {
                // 不要在 onDismissRequest 中调用 onPermissionDenied()
                // 因为点击外部或按返回可能导致用户还未完成授权就被阻止
                // 仅关闭对话框，用户可以再次点击保存重新触发
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
                            settingsLauncher.launch(intent)
                        }
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
