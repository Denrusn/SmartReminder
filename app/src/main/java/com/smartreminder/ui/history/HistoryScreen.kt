package com.smartreminder.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartreminder.domain.model.ExecutionLog
import com.smartreminder.domain.model.ExecutionResult
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("执行历史", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "暂无执行记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogCard(log = log)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: ExecutionLog) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (log.result) {
                ExecutionResult.SUCCESS -> MaterialTheme.colorScheme.surface
                ExecutionResult.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ExecutionResult.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                ExecutionResult.STRONG_REMINDER_PENDING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (log.result) {
                    ExecutionResult.SUCCESS -> Icons.Default.CheckCircle
                    ExecutionResult.FAILED -> Icons.Default.Error
                    ExecutionResult.SKIPPED -> Icons.Default.SkipNext
                    ExecutionResult.STRONG_REMINDER_PENDING -> Icons.Default.Pending
                },
                contentDescription = null,
                tint = when (log.result) {
                    ExecutionResult.SUCCESS -> MaterialTheme.colorScheme.primary
                    ExecutionResult.FAILED -> MaterialTheme.colorScheme.error
                    ExecutionResult.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                    ExecutionResult.STRONG_REMINDER_PENDING -> MaterialTheme.colorScheme.tertiary
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.reminderName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(log.executedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                log.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Text(
                text = when (log.result) {
                    ExecutionResult.SUCCESS -> "成功"
                    ExecutionResult.FAILED -> "失败"
                    ExecutionResult.SKIPPED -> "跳过"
                    ExecutionResult.STRONG_REMINDER_PENDING -> "待确认"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (log.result) {
                    ExecutionResult.SUCCESS -> MaterialTheme.colorScheme.primary
                    ExecutionResult.FAILED -> MaterialTheme.colorScheme.error
                    ExecutionResult.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                    ExecutionResult.STRONG_REMINDER_PENDING -> MaterialTheme.colorScheme.tertiary
                }
            )
        }
    }
}
