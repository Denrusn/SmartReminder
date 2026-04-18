# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew :app:test              # Run app module tests
./gradlew ktlintCheck            # Run Kotlin lint
./gradlew --refresh-dependencies # Refresh dependencies
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

**MVVM + Clean Architecture** with three layers:

- **domain/** - Domain models and repository interfaces
- **data/** - Repository implementations, Room database, local storage
- **ui/** - Jetpack Compose screens, ViewModels, navigation

**Dependency injection via Hilt** - All dependencies wired in [AppModule.kt](app/src/main/java/com/smartreminder/di/AppModule.kt)

## Domain Models

Key sealed classes using inheritance hierarchy:

- [TriggerCondition.kt](app/src/main/java/com/smartreminder/domain/model/TriggerCondition.kt) - `Daily`, `Weekly`, `Monthly`, `Yearly`, `Interval`, `Once`, `Cron`
- [ReminderAction.kt](app/src/main/java/com/smartreminder/domain/model/ReminderAction.kt) - `SendNotification`, `StrongReminder`, `ShowDialog`, `OpenUrl`, `LaunchApp`, `MakeCall`, `SendSms`, `SetAlarm`, `ClearCache`, `UninstallApp`

## Navigation

[Navigation.kt](app/src/main/java/com/smartreminder/ui/Navigation.kt) uses `NavHost` with sealed `Screen` class:
- `Home`, `Create`, `Edit(reminderId)`, `History`, `Settings`

## Strong Reminder

Key implementation details:
- Uses `AlarmManager` for precise scheduling via [ReminderScheduler.kt](app/src/main/java/com/smartreminder/service/ReminderScheduler.kt)
- [AlarmReceiver.kt](app/src/main/java/com/smartreminder/service/AlarmReceiver.kt) broadcasts to `StrongReminderService`
- [StrongReminderActivity.kt](app/src/main/java/com/smartreminder/ui/reminder/StrongReminderActivity.kt) shows full-screen overlay requiring long-press confirm
- Requires `SYSTEM_ALERT_WINDOW` and `USE_FULL_SCREEN_INTENT` permissions

## Database

[AppDatabase.kt](app/src/main/java/com/smartreminder/data/local/db/AppDatabase.kt) - Room database with two entities:
- `ReminderEntity` - stores reminder configuration
- `ExecutionLogEntity` - stores trigger history

## Natural Language Parsing

[NaturalLanguageParser.kt](app/src/main/java/com/smartreminder/ai/NaturalLanguageParser.kt) - calls MiniMax API to parse user input into `TriggerCondition` + `ReminderAction`

## Key Constraints

- MinSdk 26, TargetSdk 34, JDK 17
- Compose BOM `2023.10.01`, Kotlin 1.9.20, Compose Compiler 1.5.5
- Material 3 with custom theme in `ui/theme/`
