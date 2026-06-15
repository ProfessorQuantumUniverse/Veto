// di/RepositoryModule.kt
package com.quantum_prof.vtscansuite.di

import com.quantum_prof.vtscansuite.data.repository.VirusTotalRepositoryImpl
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVirusTotalRepository(
        impl: VirusTotalRepositoryImpl
    ): VirusTotalRepository
}