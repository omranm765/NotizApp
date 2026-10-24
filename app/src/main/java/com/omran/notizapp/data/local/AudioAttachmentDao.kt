
package com.omran.notizapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioAttachmentDao {

    @Query(
        """
        SELECT * FROM audio_attachments
        WHERE noteId = :noteId
        ORDER BY createdAt DESC, id DESC
        """
    )
    fun observeForNote(
        noteId: Long
    ): Flow<List<AudioAttachmentEntity>>

    @Insert
    suspend fun insert(
        attachment: AudioAttachmentEntity
    ): Long

    @Query(
        """
        SELECT * FROM audio_attachments
        WHERE id = :attachmentId
        LIMIT 1
        """
    )
    suspend fun getById(
        attachmentId: Long
    ): AudioAttachmentEntity?

    @Query("DELETE FROM audio_attachments WHERE id = :attachmentId")
    suspend fun deleteById(attachmentId: Long)

    @Query("SELECT fileName FROM audio_attachments")
    suspend fun allFileNames(): List<String>
}
