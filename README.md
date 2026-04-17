# 智提醒 (SmartReminder)

一款基于自然语言处理的智能提醒 Android 应用，支持强提醒拦截功能。

## 功能特性

### 核心功能
- 🎙️ **自然语言创建提醒** - 输入"每天早上8点提醒我拿早餐"，AI自动解析
- 🔔 **普通提醒** - 发送系统通知
- 🚨 **强提醒** - 全屏弹窗拦截，必须长按确认才能关闭
- 📊 **执行历史** - 查看所有提醒触发记录

### 触发条件
- 每天 / 每周 / 每月 / 每年
- 工作日指定时间
- 间隔循环（每N分钟/小时/天）
- 指定日期时间
- Cron表达式（自定义）

### 执行动作
- 发送通知
- 强提醒（弹窗+声音+震动）
- 弹出对话框
- 打开URL
- 启动应用
- 拨打电话
- 发送短信
- 设置闹钟
- 清理缓存
- 删除应用

## 技术栈

- **语言**: Kotlin 1.9+
- **UI**: Jetpack Compose (Material 3)
- **架构**: MVVM + Clean Architecture
- **依赖注入**: Hilt
- **数据库**: Room
- **定时任务**: WorkManager + AlarmManager
- **自然语言**: MiniMax API（可选）

## 项目结构

```
app/src/main/java/com/smartreminder/
├── SmartReminderApp.kt      # Application类
├── MainActivity.kt           # 主入口
├── data/                    # 数据层
│   ├── local/db/           # Room数据库
│   └── repository/         # 仓库实现
├── domain/                  # 领域层
│   ├── model/              # 领域模型
│   └── repository/         # 仓库接口
├── ai/                      # AI模块
│   └── NaturalLanguageParser.kt
├── ui/                      # 表现层
│   ├── theme/
│   ├── home/
│   ├── create/
│   ├── edit/
│   ├── history/
│   ├── settings/
│   └── reminder/           # 强提醒Activity
├── service/                 # 服务层
│   ├── ReminderScheduler.kt
│   ├── AlarmReceiver.kt
│   ├── BootReceiver.kt
│   └── StrongReminderService.kt
└── di/                      # Hilt模块
    └── AppModule.kt
```

## 构建

### 前提条件
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34

### 构建步骤

1. 克隆项目
```bash
git clone https://github.com/Denrusn/SmartReminder.git
cd SmartReminder
```

2. 同步 Gradle
```bash
./gradlew --refresh-dependencies
```

3. 构建 Debug APK
```bash
./gradlew assembleDebug
```

4. APK 输出位置
```
app/build/outputs/apk/debug/app-debug.apk
```

## 开发

### 代码规范
- 遵循 Kotlin 编码规范
- 使用 Material Design 3 设计指南
- MVVM 架构，数据单向流动

### 提交规范
- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- style: 代码格式调整
- refactor: 重构
- test: 测试相关
- chore: 构建/工具相关

## License

MIT License - 详见 [LICENSE](LICENSE) 文件
