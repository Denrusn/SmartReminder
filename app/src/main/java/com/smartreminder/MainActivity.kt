package com.smartreminder

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartreminder.ui.SmartReminderNavHost
import com.smartreminder.ui.theme.SmartReminderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 0=未检查/未授权, 1=已授权
    private var overlayGranted by mutableStateOf(0)
    private var batteryGranted by mutableStateOf(0)
    // 自启动无法可靠检测，默认0，用户手动确认
    private var autoStartGranted by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 用户从设置页返回，重新检查所有权限
        checkPermissions()
    }

    private fun checkPermissions() {
        overlayGranted = if (Settings.canDrawOverlays(this)) 1 else 0
        batteryGranted = if (isIgnoringBatteryOptimizations()) 1 else 0
        // autoStartGranted 保持用户上次的操作结果，不重复弹窗

        if (overlayGranted == 1 && batteryGranted == 1 && autoStartGranted == 1) {
            showMainContent()
        } else {
            showPermissionGuide()
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
                        onRequestAutoStart = {
                            getAutoStartIntent()?.let {
                                try { startActivity(it) } catch (e: Exception) { }
                            }
                            // 自启动设置页无法检测，用户自行确认后点击"已授权"
                            autoStartGranted = 1
                            // 重新检查是否全部完成
                            if (overlayGranted == 1 && batteryGranted == 1) {
                                showMainContent()
                            }
                        },
                        onContinue = {
                            showMainContent()
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
    autoStartGranted: Int,
    onRequestOverlay: () -> Unit,
    onRequestBattery: () -> Unit,
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
            text = "以下权限可确保智提醒在后台稳定运行，不被系统杀掉",
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

        // 3. 自启动管理
        PermissionItem(
            title = "允许自启动",
            description = "开机后自动启动应用，确保提醒准时触发",
            granted = autoStartGranted == 1,
            onClick = onRequestAutoStart,
            buttonText = if (autoStartGranted == 1) "✓ 已授权" else "去授权"
        )

        Spacer(modifier = Modifier.height(32.dp))

        val allGranted = overlayGranted == 1 && batteryGranted == 1 && autoStartGranted == 1

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (allGranted) "开始使用" else "继续（功能可能受限）",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (!allGranted) {
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
