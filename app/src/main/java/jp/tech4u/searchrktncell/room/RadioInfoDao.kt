package jp.tech4u.searchrktncell.room

import androidx.room.*

@Dao
interface RadioInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(radioInfo : RadioInfoEntity)

    @Delete
    suspend fun delete(radioInfo : RadioInfoEntity)

    @Query("SELECT * FROM RadioInfoEntity")
    suspend fun loadAllRadioInfos(): Array<RadioInfoEntity>
}