package com.smartreminder

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartreminder.ui.SmartReminderNavHost
import com.smartreminder.ui.theme.SmartReminderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 0=未检查/未授权, 1=已授权
    private var overlayGranted by mutableStateOf(0)
    private var batteryGranted by mutableStateOf(0)
    private var exactAlarmGranted by mutableStateOf(0)
    private var notificationGranted by mutableStateOf(0)
    private var autoStartGranted by mutableStateOf(0)

    private val alarmManager by lazy { getSystemService(ALARM_SERVICE) as AlarmManager }

    // POST_NOTIFICATIONS 权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationGranted = if (isGranted) 1 else 0
        refreshAndShow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        overlayGranted = if (Settings.canDrawOverlays(this)) 1 else 0
        batteryGranted = if (isIgnoringBatteryOptimizations()) 1 else 0
        exactAlarmGranted = if (canScheduleExactAlarms()) 1 else 0

        // 检查通知权限（Android 13+）
        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) 1 else 0
        } else {
            1 // Android 13以下不需要此权限
        }

        refreshAndShow()
    }

    private fun refreshAndShow() {
        if (overlayGranted == 1 && batteryGranted == 1 && notificationGranted == 1) {
            showMainContent()
        } else {
            showPermissionGuide()
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun getAutoStartIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
            }
            manufacturer.contains("oppo") -> {
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            }
            manufacturer.contains("vivo") -> {
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                )
            }
            manufacturer.contains("samsung") -> {
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                )
            }
            manufacturer.contains("oneplus") -> {
                Intent().setComponent(
                    ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                )
            }
            manufacturer.contains("realme") -> {
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            }
            else -> null
        }
    }

    private fun showMainContent() {
        setContent {
            SmartReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartReminderNavHost()
                }
            }
        }
    }

    private fun showPermissionGuide() {
        setContent {
            SmartReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGuideScreen(
                        overlayGranted = overlayGranted,
                        batteryGranted = batteryGranted,
                        exactAlarmGranted = exactAlarmGranted,
                        notificationGranted = notificationGranted,
                        autoStartGranted = autoStartGranted,
                        onRequestOverlay = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        },
                        onRequestBattery = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                intent.data = Uri.parse("package:$packageName")
                                startActivity(intent)
                            }
                        },
                        onRequestExactAlarm = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                startActivity(intent)
                            }
                        },
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestAutoStart = {
                            getAutoStartIntent()?.let {
                                try { startActivity(it) } catch (e: Exception) { }
                            }
                            autoStartGranted = 1
                            refreshAndShow()
                        },
                        onContinue = {
                            refreshAndShow()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionGuideScreen(
    overlayGranted: Int,
    batteryGranted: Int,
    exactAlarmGranted: Int,
    notificationGranted: Int,
    autoStartGranted: Int,
    onRequestOverlay: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestAutoStart: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "首次使用需要授权",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "以下权限可确保阿智提醒在后台稳定运行，不被系统杀掉",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. 悬浮窗权限
        PermissionItem(
            title = "悬浮窗权限",
            description = "强提醒弹窗需要此权限才能覆盖其他应用显示",
            granted = overlayGranted == 1,
            onClick = onRequestOverlay,
            buttonText = if (overlayGranted == 1) "✓ 已授权" else "去授权"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 电池优化白名单
        PermissionItem(
            title = "电池优化白名单",
            description = "允许应用在后台持续运行，不被系统休眠",
            granted = batteryGranted == 1,
            onClick = onRequestBattery,
            buttonText = if (batteryGranted == 1) "✓ 已授权" else "去授权"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 通知权限
        PermissionItem(
            title = "通知权限",
            description = "允许发送通知提醒，否则无法收到提醒通知",
            granted = notificationGranted == 1,
            onClick = onRequestNotification,
            buttonText = if (notificationGranted == 1) "✓ 已授权" else "去授权"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. 精确闹钟权限
        PermissionItem(
            title = "精确闹钟权限",
            description = "定时精准触发提醒，修改时间后必须重新授权",
            granted = exactAlarmGranted == 1,
            onClick = onRequestExactAlarm,
            buttonText = if (exactAlarmGranted == 1) "✓ 已授权" else "去授权"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 5. 自启动管理
        PermissionItem(
            title = "允许自启动",
            description = "开机后自动启动应用，确保提醒准时触发",
            granted = autoStartGranted == 1,
            onClick = onRequestAutoStart,
            buttonText = if (autoStartGranted == 1) "✓ 已授权" else "去授权"
        )

        Spacer(modifier = Modifier.height(32.dp))

        val allCriticalGranted = overlayGranted == 1 && batteryGranted == 1 && notificationGranted == 1

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (allCriticalGranted) "开始使用" else "继续（功能可能受限）",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (!allCriticalGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("跳过，稍后可在设置中重新授权")
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
    buttonText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            TextButton(
                onClick = onClick,
                enabled = !granted
            ) {
                Text(
                    text = buttonText,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}
