# 智能提醒APP - 开发需求文档

<div align="center">

**项目名称**：智提醒 (SmartReminder)

**项目愿景**：一款基于自然语言处理的智能提醒应用，通过AI理解用户意图，自动创建定时提醒任务。核心亮点是"强提醒"功能——在任务触发时强制弹窗拦截，确保用户必须确认才能关闭。

**版本**：V1.0.0

**日期**：2026-04-17

**作者**：小鸭经理 🦆

</div>

---

## 一、项目概述

### 1.1 项目背景

在日常生活中，人们需要记忆大量定时任务：起床闹钟、会议提醒、生日祝福、账单缴费等。传统的提醒应用需要用户手动选择日期、时间、重复频率等参数，操作繁琐。用户期待一种更自然的交互方式——说出需求，系统自动解析并创建提醒。

### 1.2 项目目标

- **降低使用门槛**：用户无需学习复杂的时间设置规则，直接用自然语言描述需求
- **确保提醒送达**：强提醒功能确保用户在任何界面都能被拦截并确认
- **灵活的任务执行**：支持多种触发条件和执行动作，满足多样化需求

### 1.3 目标用户

- 日常生活节奏快，需要智能提醒辅助记忆的用户
- 对提醒可靠性有高要求（如重要会议、缴费截止等）
- 习惯使用语音/文字描述需求，不喜欢手动配置的用户

---

## 二、产品功能规格

### 2.1 功能范围

#### 2.1.1 核心功能（V1.0优先开发）

| 模块 | 功能 | 优先级 |
|------|------|--------|
| 自然语言解析 | 用户输入描述，自动识别时间/频率/内容 | P0 |
| 提醒管理 | 创建、编辑、删除、启用/禁用提醒 | P0 |
| 普通提醒 | 发送系统通知 | P0 |
| 强提醒 | 全屏弹窗拦截 + 声音 + 震动 | P0 |
| 执行日志 | 记录提醒触发历史 | P1 |
| 强提醒设置 | 配置弹窗类型/提示音/震动 | P1 |

#### 2.1.2 执行动作（V1.0支持）

| 动作 | 说明 | 优先级 |
|------|------|--------|
| 发送通知 | 显示标题+内容的通知 | P0 |
| 强提醒 | 全屏弹窗+声音+震动 | P0 |
| 弹出对话框 | 显示标题+内容的对话框 | P1 |
| 执行URL | 打开指定网页 | P1 |
| 执行应用 | 启动手机上的其他APP | P1 |
| 打电话 | 拨打指定号码 | P2 |
| 发短信 | 发送预定义内容的短信 | P2 |
| 设置闹钟 | 创建系统闹钟 | P2 |
| 清理缓存 | 清理本APP缓存 | P2 |
| 删除应用 | 卸载指定APP | P2 |

#### 2.1.3 触发条件（V1.0支持）

| 条件 | 说明 | 优先级 |
|------|------|--------|
| 每天 | 每日固定时间循环 | P0 |
| 每周 | 指定周几+时间循环 | P0 |
| 每月 | 指定日期+时间循环 | P0 |
| 每年 | 指定月日+时间循环 | P0 |
| 指定日期时间 | 一次性提醒 | P0 |
| 每间隔 | N分钟/小时/天循环 | P0 |
| Cron表达式 | 自定义复杂周期 | P1 |

### 2.2 用户使用场景

#### 场景1：早晨提醒
```
用户输入："每天早上8点提醒我拿早餐"
AI解析结果：
  - 触发条件：每天 08:00
  - 执行动作：强提醒（标题：拿早餐，内容：该去拿早餐啦）
```

#### 场景2：月度固定任务
```
用户输入："每个月的26日下午2点提醒我充话费"
AI解析结果：
  - 触发条件：每月 26日 14:00
  - 执行动作：强提醒（标题：充值话费，内容：月底了，记得充话费）
```

#### 场景3：工作日打卡
```
用户输入："工作日的下午6点02分提醒我下班打卡"
AI解析结果：
  - 触发条件：周一至周五 18:02
  - 执行动作：普通通知（标题：下班打卡，内容：时间到，记得打卡）
```

#### 场景4：强提醒拦截
```
触发场景：用户正在刷抖音，突然收到强提醒
体验流程：
1. 屏幕强制弹出全屏弹窗（覆盖当前界面）
2. 播放提示音 + 震动
3. 用户必须长按「我知道了」按钮2秒
4. 弹窗关闭，记录用户已确认
```

---

## 三、产品界面设计

### 3.1 页面结构

```
┌─────────────────────────────────────┐
│           智提醒 APP                 │
├─────────────────────────────────────┤
│                                     │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐│
│  │ 首页    │ │ 创建    │ │ 设置    ││
│  │ 我的提醒│ │  +      │ │ ⚙       ││
│  └─────────┘ └─────────┘ └─────────┘│
│                                     │
└─────────────────────────────────────┘
```

### 3.2 页面清单

| 页面 | 路由 | 功能 |
|------|------|------|
| 首页/我的提醒 | `/` | 展示所有提醒列表 |
| 创建提醒 | `/create` | 自然语言输入 → AI解析 → 确认配置 |
| 编辑提醒 | `/edit/:id` | 修改已有提醒 |
| 强提醒 | 全屏Activity | 弹窗拦截界面 |
| 执行历史 | `/history` | 查看提醒触发记录 |
| 设置 | `/settings` | 强提醒参数配置 |

### 3.3 核心页面原型

#### 3.3.1 首页 - 我的提醒

```
┌─────────────────────────────────────┐
│ ← 我的提醒                    [+] ⚙ │
├─────────────────────────────────────┤
│ [🔍 搜索提醒...]                     │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ 📌 拿早餐                        │ │
│ │ 每天 08:00 · 强提醒              │ │
│ │ [●] 已启用            [✏️] [🗑️] │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 📌 下班打卡                      │ │
│ │ 工作日 18:02 · 通知              │ │
│ │ [●] 已启用            [✏️] [🗑️] │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 📌 充话费                        │ │
│ │ 每月26日 14:00 · 强提醒          │ │
│ │ [○] 已禁用            [✏️] [🗑️] │ │
│ └─────────────────────────────────┘ │
│                                     │
└─────────────────────────────────────┘
```

#### 3.3.2 创建提醒

```
┌─────────────────────────────────────┐
│ ← 创建提醒              [提交] [测试]│
├─────────────────────────────────────┤
│                                     │
│  请描述你的提醒：                     │
│  ┌─────────────────────────────────┐ │
│  │ 每天早上8点提醒我拿早餐          │ │
│  │                                 │ │
│  └─────────────────────────────────┘ │
│                                     │
│  [🎤 语音输入]                       │
│                                     │
│  ─────── AI解析结果 ───────          │
│                                     │
│  触发条件：每天 08:00               │
│  执行动作：强提醒                   │
│  提醒内容：                          │
│  标题：[拿早餐]                      │
│  内容：[该去拿早餐啦]                 │
│                                     │
│  [📝 手动调整]                       │
│                                     │
└─────────────────────────────────────┘
```

#### 3.3.3 强提醒页面

```
┌─────────────────────────────────────┐
│ ██████████████████████████████████ │
│ ██████████████████████████████████ │
│ █████                         █████ │
│ █████   ⏰ 智提醒             █████ │
│ █████                         █████ │
│ █████   拿早餐               █████ │
│ █████   该去拿早餐啦          █████ │
│ █████                         █████ │
│ █████                         █████ │
│ █████   ┌─────────────────┐  █████ │
│ █████   │  长按2秒确认    │  █████ │
│ █████   │   👆 我知道了   │  █████ │
│ █████   └─────────────────┘  █████ │
│ █████                         █████ │
│ █████   [稍后提醒]           █████ │
│ ██████████████████████████████████ │
│ ██████████████████████████████████ │
└─────────────────────────────────────┘
```

---

## 四、技术架构设计

### 4.1 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **语言** | Kotlin 1.9+ | 现代化Android开发 |
| **最小SDK** | API 26 (Android 8.0) | 覆盖主流设备 |
| **目标SDK** | API 34 (Android 14) | 最新系统特性 |
| **UI框架** | Jetpack Compose | 声明式UI |
| **架构** | MVVM + Clean Architecture | 清晰分层 |
| **DI** | Hilt | 依赖注入 |
| **数据库** | Room | 本地持久化 |
| **定时任务** | WorkManager + AlarmManager | 后台调度 |
| **自然语言** | MiniMax API | AI意图识别 |
| **协程** | Kotlin Coroutines + Flow | 异步编程 |
| **导航** | Jetpack Navigation Compose | 页面导航 |

### 4.2 项目结构

```
com.smartreminder/
├── SmartReminderApp.kt          # Application类
├── MainActivity.kt               # 主入口
│
├── data/                         # 数据层
│   ├── local/                    # 本地数据
│   │   ├── db/                   # Room数据库
│   │   │   ├── ReminderDao.kt
│   │   │   ├── ReminderEntity.kt
│   │   │   ├── ExecutionLogDao.kt
│   │   │   └── AppDatabase.kt
│   │   └── preferences/          # DataStore
│   │       └── SettingsPrefs.kt
│   └── repository/               # 仓库模式
│       └── ReminderRepository.kt
│
├── domain/                       # 领域层
│   ├── model/                    # 领域模型
│   │   ├── Reminder.kt
│   │   ├── TriggerCondition.kt
│   │   ├── ReminderAction.kt
│   │   └── ExecutionLog.kt
│   ├── usecase/                  # 用例
│   │   ├── CreateReminderUseCase.kt
│   │   ├── ParseNaturalLanguageUseCase.kt
│   │   └── ExecuteReminderUseCase.kt
│   └── repository/               # 仓库接口
│       └── ReminderRepository.kt
│
├── ai/                           # AI模块
│   ├── NaturalLanguageParser.kt # 自然语言解析
│   └── MiniMaxApiService.kt      # MiniMax API调用
│
├── ui/                           # 表现层
│   ├── theme/                    # Compose主题
│   ├── components/              # 通用组件
│   ├── home/                     # 首页
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── create/                   # 创建提醒
│   │   ├── CreateScreen.kt
│   │   └── CreateViewModel.kt
│   ├── edit/                     # 编辑提醒
│   ├── history/                  # 执行历史
│   └── settings/                 # 设置
│
├── service/                      # 服务
│   ├── ReminderScheduler.kt      # 定时调度
│   ├── AlarmReceiver.kt          # 闹钟接收
│   └── StrongReminderService.kt  # 强提醒服务
│
└── util/                         # 工具类
    ├── NotificationHelper.kt
    └── PermissionHelper.kt
```

### 4.3 数据模型

#### 4.3.1 Reminder（提醒）

```kotlin
data class Reminder(
    val id: Long = 0,
    val name: String,                    // 提醒名称
    val description: String,             // 描述
    val isEnabled: Boolean = true,       // 是否启用
    val triggerCondition: TriggerCondition, // 触发条件
    val reminderMethod: ReminderMethod,  // 提醒方式
    val actions: List<ReminderAction>,   // 执行动作列表
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ReminderMethod {
    NOTIFICATION,      // 普通通知
    STRONG_REMINDER,   // 强提醒
    STRONG_REMINDER_WITH_SETTINGS  // 强提醒+自定义设置
}
```

#### 4.3.2 TriggerCondition（触发条件）

```kotlin
sealed class TriggerCondition {
    data class Daily(val hour: Int, val minute: Int) : TriggerCondition()
    
    data class Weekly(val dayOfWeek: Int, val hour: Int, val minute: Int) : TriggerCondition()
    // dayOfWeek: 1=周一, 7=周日
    
    data class Monthly(val dayOfMonth: Int, val hour: Int, val minute: Int) : TriggerCondition()
    
    data class Yearly(val month: Int, val day: Int, val hour: Int, val minute: Int) : TriggerCondition()
    
    data class Interval(val interval: Long, val unit: IntervalUnit) : TriggerCondition()
    // unit: MINUTES, HOURS, DAYS
    
    data class Once(val timestamp: Long) : TriggerCondition()
    
    data class Cron(val expression: String) : TriggerCondition()
}

enum class IntervalUnit { MINUTES, HOURS, DAYS }
```

#### 4.3.3 ReminderAction（执行动作）

```kotlin
sealed class ReminderAction {
    data class SendNotification(
        val title: String,
        val content: String
    ) : ReminderAction()
    
    data class StrongReminder(
        val title: String,
        val content: String,
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        val popupType: PopupType = PopupType.FULL_SCREEN
    ) : ReminderAction()
    
    data class ShowDialog(
        val title: String,
        val content: String
    ) : ReminderAction()
    
    data class OpenUrl(
        val url: String
    ) : ReminderAction()
    
    data class LaunchApp(
        val packageName: String
    ) : ReminderAction()
    
    data class MakeCall(
        val phoneNumber: String
    ) : ReminderAction()
    
    data class SendSms(
        val phoneNumber: String,
        val content: String
    ) : ReminderAction()
    
    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String,
        val vibrationEnabled: Boolean = true
    ) : ReminderAction()
    
    data class ClearCache() : ReminderAction()
    
    data class UninstallApp(
        val packageName: String
    ) : ReminderAction()
}

enum class PopupType {
    FULL_SCREEN,    // 全屏
    ACTIVITY,       // 活动窗口
    HEAD_UP         // 横幅
}
```

#### 4.3.4 ExecutionLog（执行日志）

```kotlin
data class ExecutionLog(
    val id: Long = 0,
    val reminderId: Long,
    val reminderName: String,
    val executedAt: Long,
    val result: ExecutionResult,
    val errorMessage: String? = null
)

enum class ExecutionResult {
    SUCCESS,
    FAILED,
    SKIPPED,
    STRONG_REMINDER_PENDING  // 强提醒待确认
}
```

### 4.4 强提醒实现方案

#### 4.4.1 权限申请

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

#### 4.4.2 强提醒Activity配置

```xml
<activity
    android:name=".ui.reminder.StrongReminderActivity"
    android:exported="false"
    android:launchMode="singleInstance"
    android:taskAffinity=""
    android:excludeFromRecents="true"
    android:showOnLockScreen="true"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:theme="@style/Theme.SmartReminder.StrongReminder" />
```

---

## 五、自然语言解析设计

### 5.1 解析流程

```
用户输入 → AI预处理 → MiniMax API → 结构化结果 → 用户确认 → 创建提醒
```

### 5.2 AI解析Prompt设计

```markdown
你是一个智能提醒时间解析器。请从用户的自然语言描述中提取以下信息：

输入示例：
- "每天早上8点提醒我拿早餐"
- "每个月的26日下午2点提醒我充话费"
- "工作日的下午6点02分提醒我下班打卡"

输出要求（JSON格式）：
{
  "trigger": {
    "type": "daily|weekly|monthly|yearly|once|interval",
    "time": {"hour": 8, "minute": 0},
    "daysOfWeek": [1,2,3,4,5],  // 仅weekly
    "dayOfMonth": 26,            // 仅monthly
    "month": 6, day: 26,         // 仅yearly
    "interval": 30, "intervalUnit": "minutes"  // 仅interval
  },
  "action": {
    "type": "notification|strong_reminder",
    "title": "提醒标题",
    "content": "提醒内容"
  }
}

注意：
1. "工作日"对应周一到周五
2. 时间可以用"早上/下午/晚上"等描述
3. 如果用户没指定内容，title使用提醒名称
```

### 5.3 解析结果确认

```
┌─────────────────────────────────────┐
│  AI解析结果确认                       │
├─────────────────────────────────────┤
│                                     │
│  我这样理解对吗？                     │
│                                     │
│  📅 触发：每天 08:00                │
│  🔔 方式：强提醒                     │
│  📝 标题：拿早餐                     │
│  📄 内容：该去拿早餐啦               │
│                                     │
│  [✅ 确认创建]  [✏️ 修改] [❌ 取消]  │
│                                     │
└─────────────────────────────────────┘
```

---

## 六、优先级与开发计划

### 6.1 V1.0 功能优先级

| 阶段 | 功能 | 预计工时 |
|------|------|----------|
| **Phase 1** | 项目框架搭建、数据库设计、基础UI | 4h |
| **Phase 2** | 普通提醒功能（创建/列表/通知） | 4h |
| **Phase 3** | 强提醒功能（弹窗/震动/声音） | 4h |
| **Phase 4** | 自然语言解析（MiniMax API集成） | 4h |
| **Phase 5** | 执行动作扩展（URL/APP/电话等） | 4h |
| **Phase 6** | 设置页面、历史记录、细节优化 | 4h |
| **总计** | | **24h** |

### 6.2 V1.0后迭代方向

- Cron表达式支持
- 位置触发提醒（到达/离开某地）
- 节假日排除（春节不打卡）
- 数据统计与可视化
- 云同步（多设备）

---

## 七、验收标准

### 7.1 功能验收

- [ ] 用户输入"每天早上8点提醒我拿早餐"，系统能正确解析并创建提醒
- [ ] 到达设定时间，系统能发送普通通知
- [ ] 强提醒能在锁屏状态下弹出全屏弹窗
- [ ] 用户必须长按确认按钮才能关闭强提醒
- [ ] 提醒列表支持增删改查和启用/禁用
- [ ] 执行日志能正确记录每次触发结果

### 7.2 性能验收

- [ ] APP冷启动时间 < 2秒
- [ ] 强提醒弹窗响应时间 < 500ms
- [ ] 数据库操作 < 100ms
- [ ] AI解析等待时间 < 3秒（网络正常）

### 7.3 兼容性验收

- [ ] 支持 Android 8.0 (API 26) 及以上
- [ ] 在主流机型上测试通过（华为、小米、OPPO等）

---

## 八、风险与挑战

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 强提醒被系统拦截 | 高 | 引导用户手动授权悬浮窗权限 |
| AI解析不准确 | 中 | 提供手动调整入口，用户可修改 |
| 后台被杀导致闹钟失效 | 中 | 使用AlarmManager + WorkManager双重保障 |
| 不同厂商ROM兼容 | 中 | 分厂商测试，适配主流机型 |
| 通知权限被拒 | 高 | 首次引导授权，提供重试入口 |

---

## 九、附录

### 9.1 术语表

| 术语 | 说明 |
|------|------|
| 强提醒 | 必须用户主动确认才能关闭的提醒形式 |
| 触发条件 | 决定提醒何时执行的规则 |
| 执行动作 | 提醒触发后执行的具体操作 |
| 自然语言解析 | AI理解用户口语化描述的过程 |

### 9.2 参考资料

- Jetpack Compose 文档：https://developer.android.com/compose
- WorkManager 指南：https://developer.android.com/topic/libraries/architecture/workmanager
- Room 数据库：https://developer.android.com/training/data-storage/room
- Hilt 依赖注入：https://developer.android.com/training/dependency-injection/hilt-android

---

<div align="center">

**文档版本**：V1.0

**创建日期**：2026-04-17

**作者**：小鸭经理 🦆

</div>
