package com.mgomanager.app.di

import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.dao.LogDao
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.usecase.CreateBackupUseCase
import com.mgomanager.app.domain.usecase.RestoreBackupUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAccountRepository(
        accountDao: AccountDao
    ): AccountRepository {
        return AccountRepository(accountDao)
    }

    @Provides
    @Singleton
    fun provideLogRepository(
        logDao: LogDao,
        settingsDataStore: SettingsDataStore
    ): LogRepository {
        return LogRepository(logDao, settingsDataStore)
    }

    @Provides
    @Singleton
    fun provideBackupRepository(
        createBackupUseCase: CreateBackupUseCase,
        restoreBackupUseCase: RestoreBackupUseCase
    ): BackupRepository {
        return BackupRepository(createBackupUseCase, restoreBackupUseCase)
    }
}
