package com.kamalahmed.prayerapp.alarm

import com.kamalahmed.prayerapp.core.model.Prayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAlarmHooks @Inject constructor() : AlarmHooks {
    override suspend fun onAlarm(type: AlarmType, prayer: Prayer) = Unit
    override suspend fun onSystemChange() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmModule {
    @Binds abstract fun hooks(impl: DefaultAlarmHooks): AlarmHooks
}
