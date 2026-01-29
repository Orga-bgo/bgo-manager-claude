package com.mgomanager.app.di

import com.mgomanager.app.domain.operation.BackupRunner
import com.mgomanager.app.domain.operation.CreateAccountRunner
import com.mgomanager.app.domain.operation.DefaultOperationPreflightDependencies
import com.mgomanager.app.domain.operation.OperationLogger
import com.mgomanager.app.domain.operation.OperationPreflightDependencies
import com.mgomanager.app.domain.operation.OperationSessionProvider
import com.mgomanager.app.domain.operation.OperationLogWriter
import com.mgomanager.app.domain.operation.RestoreRunner
import com.mgomanager.app.domain.operation.SettingsSessionProvider
import com.mgomanager.app.domain.usecase.CreateBackupUseCase
import com.mgomanager.app.domain.usecase.CreateNewAccountUseCase
import com.mgomanager.app.domain.usecase.RestoreBackupUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OperationModule {

    @Binds
    abstract fun bindBackupRunner(impl: CreateBackupUseCase): BackupRunner

    @Binds
    abstract fun bindCreateAccountRunner(impl: CreateNewAccountUseCase): CreateAccountRunner

    @Binds
    abstract fun bindRestoreRunner(impl: RestoreBackupUseCase): RestoreRunner

    @Binds
    @Singleton
    abstract fun bindSessionProvider(impl: SettingsSessionProvider): OperationSessionProvider

    @Binds
    @Singleton
    abstract fun bindOperationLogger(impl: OperationLogWriter): OperationLogger

    @Binds
    @Singleton
    abstract fun bindPreflightDependencies(
        impl: DefaultOperationPreflightDependencies
    ): OperationPreflightDependencies
}
