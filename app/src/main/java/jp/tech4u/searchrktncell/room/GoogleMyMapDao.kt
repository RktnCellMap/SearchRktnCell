package jp.tech4u.searchrktncell.room

import androidx.room.*

@Dao
interface GoogleMyMapDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(googleMyMapEntity : GoogleMyMapEntity)

    @Delete
    suspend fun delete(googleMyMapEntity : GoogleMyMapEntity)

    @Query("SELECT * FROM GoogleMyMapEntity")
    suspend fun loadAllGoogleMyMapEntities(): Array<GoogleMyMapEntity>
}