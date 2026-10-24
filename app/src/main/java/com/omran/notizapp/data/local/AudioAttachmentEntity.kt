
package com.omran.notizapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("noteId"),
        Index(value = ["fileName"], unique = true)
    ]
)
data class AudioAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val noteId: Long,
    val fileName: String,
    val createdAt: Long,
    val durationMs: Long,
    val byteCount: Long
)
