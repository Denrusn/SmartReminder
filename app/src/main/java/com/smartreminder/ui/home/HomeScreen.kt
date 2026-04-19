package com.smartreminder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.smartreminder.domain.model.Reminder
import com.smartreminder.domain.model.ReminderMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToCreate: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val reminders by viewModel.reminders.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Reminder?>(null) }
    var pendingToggleReminder by remember { mutableStateOf<Reminder?>(null) }
    val context = LocalContext.current

    // Handle permission state changes
    LaunchedEffect(permissionState) {
        when (permissionState) {
            is HomeViewModel.PermissionState.ExactAlarmNeeded -> {
                // Dialog will be shown below
            }
            is HomeViewModel.PermissionState.Error -> {
                // Error is shown in Snackbar
            }
            is HomeViewModel.PermissionState.None -> {
                // Nothing to do
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的提醒", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "历史记录")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建提醒")
            }
        }
    ) { paddingValues ->
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "还没有提醒",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击 + 创建一个新提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggleEnabled = { viewModel.toggleReminderEnabled(reminder) },
                        onEdit = { onNavigateToEdit(reminder.id) },
                        onDelete = { showDeleteDialog = reminder }
                    )
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { reminder ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除提醒") },
            text = { Text("确定要删除「${reminder.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReminder(reminder)
                        showDeleteDialog = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 精确闹钟权限请求对话框
    if (permissionState is HomeViewModel.PermissionState.ExactAlarmNeeded) {
        AlertDialog(
            onDismissRequest = {
                // 不要自动取消——点击外部或按返回只关闭对话框，不取消操作
                // 用户可以在主界面重新触发开关操作
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
                        viewModel.clearPermissionState()
                        pendingToggleReminder = null
                    }
                ) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPermissionState()
                        pendingToggleReminder = null
                    }
                ) {
                    Text("稍后")
                }
            }
        )
    }

    // 权限错误提示
    (permissionState as? HomeViewModel.PermissionState.Error)?.let { error ->
        LaunchedEffect(error) {
            // Error handling via snackbar could be added here
            viewModel.clearPermissionState()
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isStrong = reminder.reminderMethod == ReminderMethod.STRONG_REMINDER ||
            reminder.reminderMethod == ReminderMethod.STRONG_REMINDER_WITH_SETTINGS

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (reminder.isEnabled) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 第一行：图标 + 名称 + 开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isStrong) {
                            Icons.Default.NotificationsActive
                        } else {
                            Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = if (reminder.isEnabled) {
                            if (isStrong) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = reminder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (reminder.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }

                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第二行：提醒类型标签 + 触发时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 提醒类型标签
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isStrong) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isStrong) "强提醒" else "普通提醒",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isStrong) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }

                // 触发时间
                if (reminder.isEnabled) {
                    Text(
                        text = "下一次提醒: ${reminder.triggerCondition.getNextTriggerDisplayString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 第三行：描述（如果有）
            if (reminder.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = reminder.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第四行：编辑/删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
