package com.shottimer.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CustomDrillDaoTest {

    private lateinit var db: ShotTimerDatabase
    private lateinit var dao: CustomDrillDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ShotTimerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.customDrillDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `observeAll orders drills alphabetically by name`() = runBlocking {
        dao.insert(CustomDrillEntity(name = "Zed Drill", instructions = "", roundCount = 5))
        dao.insert(CustomDrillEntity(name = "Alpha Drill", instructions = "", roundCount = 3))

        val names = dao.observeAll().first().map { it.name }

        assertEquals(listOf("Alpha Drill", "Zed Drill"), names)
    }

    @Test
    fun `insert then delete removes exactly that drill`() = runBlocking {
        val keepId = dao.insert(CustomDrillEntity(name = "Keep", instructions = "", roundCount = 1))
        val deleteId = dao.insert(CustomDrillEntity(name = "Drop", instructions = "", roundCount = 1))

        dao.delete(CustomDrillEntity(id = deleteId, name = "Drop", instructions = "", roundCount = 1))

        val remaining = dao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(keepId, remaining.single().id)
    }
}
