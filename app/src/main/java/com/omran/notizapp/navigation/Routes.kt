package com.omran.notizapp.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class CategoryRoute(
    val categoryId: Long
)


@Serializable
data class NoteEditorRoute(
    val categoryId: Long,
    val noteId: Long = 0L
)


@Serializable
data object TrashRoute


@Serializable
data object AudioTestRoute
