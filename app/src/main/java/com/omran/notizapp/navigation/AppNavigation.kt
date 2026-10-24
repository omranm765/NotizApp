package com.omran.notizapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.omran.notizapp.ui.screens.CategoryListScreen
import com.omran.notizapp.ui.screens.NoteEditorScreen
import com.omran.notizapp.ui.screens.TrashScreen
import com.omran.notizapp.ui.viewmodel.CategoryViewModel
import com.omran.notizapp.ui.viewmodel.NoteViewModel
import com.omran.notizapp.ui.viewmodel.TrashViewModel
import com.omran.notizapp.ui.screens.AudioTestScreen
import com.omran.notizapp.data.repository.AudioAttachmentRepository
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

@Composable
fun AppNavigation(
    categoryViewModel: CategoryViewModel,
    noteViewModel: NoteViewModel,
    trashViewModel: TrashViewModel,
    audioRepository: AudioAttachmentRepository
) {

    val navController = rememberNavController()




    NavHost(
        navController = navController,
        startDestination = HomeRoute,

        enterTransition = {
            EnterTransition.None
        },

        exitTransition = {
            ExitTransition.None
        },

        popEnterTransition = {
            EnterTransition.None
        },

        popExitTransition = {
            ExitTransition.None
        },

        predictivePopEnterTransition = { _ ->
            EnterTransition.None
        },

        predictivePopExitTransition = { _ ->
            ExitTransition.None
        }
    ) {


    // Hauptseite
        composable<HomeRoute> { entry ->

            CategoryListScreen(
                parentId = null,
                viewModel = categoryViewModel,
                noteViewModel = noteViewModel,
                trashViewModel = trashViewModel,

                onOpenCategory = { id ->
                    if (
                        navController.currentBackStackEntry?.id ==
                        entry.id
                    ) {
                        navController.navigate(
                            CategoryRoute(categoryId = id)
                        )
                    }
                },

                onCreateNote = {},
                onOpenNote = {},

                onOpenTrash = {
                    if (
                        navController.currentBackStackEntry?.id ==
                        entry.id
                    ) {
                        navController.navigate(TrashRoute)
                    }
                },

                onOpenAudioTest = {
                    if (
                        navController.currentBackStackEntry?.id ==
                        entry.id
                    ) {
                        navController.navigate(AudioTestRoute)
                    }
                }
            )
        }


        composable<AudioTestRoute> { entry ->

            var backRequested by remember(entry.id) {
                mutableStateOf(false)
            }

            AudioTestScreen(
                onBack = {
                    val isCurrent =
                        navController.currentBackStackEntry?.id ==
                                entry.id

                    if (
                        !backRequested &&
                        isCurrent &&
                        navController.previousBackStackEntry != null
                    ) {
                        backRequested = true

                        if (!navController.popBackStack()) {
                            backRequested = false
                        }
                    }
                }
            )
        }


        // Kategorien und Unterkategorien
        composable<CategoryRoute> { entry ->

            val route = entry.toRoute<CategoryRoute>()

            var backRequested by remember(entry.id) {
                mutableStateOf(false)
            }

            CategoryListScreen(
                parentId = route.categoryId,
                viewModel = categoryViewModel,
                noteViewModel = noteViewModel,
                trashViewModel = trashViewModel,

                onOpenCategory = { id ->
                    if (
                        navController.currentBackStackEntry?.id ==
                        entry.id
                    ) {
                        navController.navigate(
                            CategoryRoute(categoryId = id)
                        )
                    }
                },

                onCreateNote = {
                    if (
                        navController.currentBackStackEntry?.id ==
                        entry.id
                    ) {
                        navController.navigate(
                            NoteEditorRoute(
                                categoryId = route.categoryId
                            )
                        )
                    }
                },

                onOpenNote = { noteId ->
                    if (
                        navController.currentBackStackEntry?.id ==
                        entry.id
                    ) {
                        navController.navigate(
                            NoteEditorRoute(
                                categoryId = route.categoryId,
                                noteId = noteId
                            )
                        )
                    }
                },

                onOpenTrash = {},

                backEnabled = !backRequested,

                onBack = {
                    val isCurrent =
                        navController.currentBackStackEntry?.id ==
                                entry.id

                    if (
                        !backRequested &&
                        isCurrent &&
                        navController.previousBackStackEntry != null
                    ) {
                        backRequested = true

                        if (!navController.popBackStack()) {
                            backRequested = false
                        }
                    }
                }
            )
        }

        // Notizeditor
        composable<NoteEditorRoute> { entry ->

            val route = entry.toRoute<NoteEditorRoute>()

            var backRequested by remember(entry.id) {
                mutableStateOf(false)
            }

            NoteEditorScreen(
                audioRepository = audioRepository,
                categoryId = route.categoryId,

                noteId = route.noteId.takeIf {
                    it > 0L
                },

                viewModel = noteViewModel,

                onNavigateBack = {
                    val isCurrent =
                        navController.currentBackStackEntry?.id ==
                                entry.id

                    if (
                        !backRequested &&
                        isCurrent &&
                        navController.previousBackStackEntry != null
                    ) {
                        backRequested = true

                        if (!navController.popBackStack()) {
                            backRequested = false
                        }
                    }
                }
            )
        }

        // Papierkorb
        composable<TrashRoute> { entry ->

            var backRequested by remember(entry.id) {
                mutableStateOf(false)
            }

            TrashScreen(
                viewModel = trashViewModel,
                backEnabled = !backRequested,

                onBack = {
                    val isCurrent =
                        navController.currentBackStackEntry?.id ==
                                entry.id

                    if (
                        !backRequested &&
                        isCurrent &&
                        navController.previousBackStackEntry != null
                    ) {
                        backRequested = true

                        if (!navController.popBackStack()) {
                            backRequested = false
                        }
                    }
                }
            )
        }
    }
}
