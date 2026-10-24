package com.ayman.ecolift.data

import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ayman.ecolift.data.room.CardioSession as RoomCardioSession

class HealthConnectEditImportTest {
    @Test
    fun a_saved_edit_survives_a_newer_health_connect_record() = runTest {
        val rows = mutableListOf<RoomCardioSession>()
        var nextId = 1L
        val dao = mock<CardioSessionDao>()
        whenever(dao.insert(any())).thenAnswer { invocation ->
            val stored = invocation.getArgument<RoomCardioSession>(0).copy(id = nextId++)
            rows += stored
            stored.id
        }
        whenever(dao.update(any())).thenAnswer { invocation ->
            val row = invocation.getArgument<RoomCardioSession>(0)
            val index = rows.indexOfFirst { it.id == row.id }
            if (index >= 0) rows[index] = row
            null
        }
        whenever(dao.getByHealthConnectUid(any())).thenAnswer { invocation ->
            val uid = invocation.getArgument<String>(0)
            rows.firstOrNull { it.hcUid == uid }
        }
        whenever(dao.getById(any())).thenAnswer { invocation ->
            val id = invocation.getArgument<Long>(0)
            rows.firstOrNull { it.id == id }
        }
        val database = mock<AppDatabase>()
        whenever(database.cardioSessionDao()).thenReturn(dao)
        val repository = CardioRepository(database)

        val imported = repository.upsertHealthConnect(
            healthConnect("2026-10-01", CardioActivityType.TREADMILL.name, durationSec = 1800, calories = null, modified = 1_000L),
        )
        repository.update(
            imported.copy(
                date = "2026-10-20",
                activityType = CardioActivityType.ROW.name,
                durationSec = 900,
            ),
        )
        val merged = repository.upsertHealthConnect(
            healthConnect("2026-11-02", CardioActivityType.BIKE.name, durationSec = 2400, calories = 220, modified = 9_000_000_000_000L),
        )

        assertEquals(1, rows.size)
        assertEquals(imported.id, merged.id)
        assertEquals("2026-10-20", merged.date)
        assertEquals(CardioActivityType.ROW.name, merged.activityType)
        assertEquals(900, merged.durationSec)
        assertEquals(220, merged.calories)
    }

    private fun healthConnect(
        date: String,
        activityType: String,
        durationSec: Int,
        calories: Int?,
        modified: Long,
    ) = CardioSession(
        localUuid = "hc-row",
        date = date,
        activityType = activityType,
        durationSec = durationSec,
        calories = calories,
        source = CardioSessionSource.HEALTH_CONNECT,
        hcUid = "uid-1",
        hcLastModifiedTime = modified,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}
