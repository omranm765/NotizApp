package com.omran.notizapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageAttachmentDao {
    @Query("SELECT * FROM image_attachments WHERE noteId = :noteId ORDER BY createdAt DESC, id DESC")
    fun observeForNote(noteId: Long): Flow<List<ImageAttachmentEntity>>

    @Insert
    suspend fun insert(image: ImageAttachmentEntity): Long

    @Query("SELECT * FROM image_attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ImageAttachmentEntity?

    @Query("DELETE FROM image_attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT fileName FROM image_attachments")
    suspend fun allFileNames(): List<String>
}
