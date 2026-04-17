package com.smartreminder.di

import android.content.Context
import com.google.gson.Gson
import com.smartreminder.data.local.db.AppDatabase
import com.smartreminder.data.local.db.ExecutionLogDao
import com.smartreminder.data.local.db.ReminderDao
import com.smartreminder.data.repository.ReminderRepositoryImpl
import com.smartreminder.domain.repository.ReminderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    @Provides
    @Singleton
    fun provideReminderDao(database: AppDatabase): ReminderDao {
        return database.reminderDao()
    }
    
    @Provides
    @Singleton
    fun provideExecutionLogDao(database: AppDatabase): ExecutionLogDao {
        return database.executionLogDao()
    }
    
    @Provides
    @Singleton
    fun provideReminderRepository(
        reminderDao: ReminderDao,
        executionLogDao: ExecutionLogDao,
        gson: Gson
    ): ReminderRepository {
        return ReminderRepositoryImpl(reminderDao, executionLogDao, gson)
    }
}
