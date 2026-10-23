package com.ayman.ecolift.android.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.ayman.ecolift.data.room.CycleSlot
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitMemberChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "split_member_choice",
    foreignKeys = [
        ForeignKey(
            entity = CycleSlot::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@Serializable
data class RoomSplitMemberChoice(
    @PrimaryKey val splitId: Long,
    val revision: Long,
    val inSplitJson: String,
    val otherOptionsJson: String,
)

@Dao
interface SplitMemberChoiceDao {
    @Query("SELECT * FROM split_member_choice ORDER BY splitId")
    fun observeAll(): Flow<List<RoomSplitMemberChoice>>

    @Query("SELECT * FROM split_member_choice ORDER BY splitId")
    suspend fun getAll(): List<RoomSplitMemberChoice>

    @Query("SELECT * FROM split_member_choice WHERE splitId = :splitId")
    suspend fun getBySplitId(splitId: Long): RoomSplitMemberChoice?

    @Upsert
    suspend fun upsert(row: RoomSplitMemberChoice)
}

internal fun RoomSplitMemberChoice.toChoice() = SplitMemberChoice(
    splitId = splitId,
    revision = revision,
    board = EffectiveSplitMembers.of(Json.decodeFromString(inSplitJson), Json.decodeFromString(otherOptionsJson)),
)

internal fun SplitMemberChoice.toRoomChoice() = RoomSplitMemberChoice(
    splitId = splitId,
    revision = revision,
    inSplitJson = Json.encodeToString(board.inSplit),
    otherOptionsJson = Json.encodeToString(board.otherOptions),
)
