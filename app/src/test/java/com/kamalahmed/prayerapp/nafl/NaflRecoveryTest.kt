package com.kamalahmed.prayerapp.nafl

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import com.kamalahmed.prayerapp.ui.nafl.NaflViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class NaflRecoveryTest {
    @Test fun databaseReadFailureIsVisibleAndRetryRestartsObservation() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            var fail = true
            val dao = object : NaflDao by db.nafl() {
                override fun observeLogs(): Flow<List<NaflLog>> = flow {
                    if (fail) { fail = false; throw java.io.IOException("Injected read failure") }
                    emitAll(db.nafl().observeLogs())
                }
            }
            val vm = NaflViewModel(NaflRepository(dao), QuranReminders(ctx, dao))
            store.put("nafl", vm)
            val collector = launch(Dispatchers.Unconfined) { vm.state.collect() }
            withTimeout(10000) { vm.loadFailed.first { it } }
            assertNull(vm.state.value)
            vm.retry()
            withContext(Dispatchers.IO) { withTimeout(10000) { vm.state.first { it != null && it.items.isNotEmpty() } } }
            assertFalse(vm.loadFailed.value)
            collector.cancel()
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }
}
