package com.omran.notizapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omran.notizapp.data.local.AppDatabase
import com.omran.notizapp.data.repository.AudioAttachmentRepository
import com.omran.notizapp.data.repository.AudioFileCleaner
import com.omran.notizapp.data.repository.CategoryRepository
import com.omran.notizapp.data.repository.ImageFileCleaner
import com.omran.notizapp.data.repository.NoteRepository
import com.omran.notizapp.data.repository.TrashRepository
import com.omran.notizapp.navigation.AppNavigation
import com.omran.notizapp.ui.theme.NotizAppTheme
import com.omran.notizapp.ui.viewmodel.CategoryViewModel
import com.omran.notizapp.ui.viewmodel.NoteViewModel
import com.omran.notizapp.ui.viewmodel.TrashViewModel

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotizAppTheme {
                val database = remember {
                    AppDatabase.getInstance(applicationContext)
                }
                val categoryRepository = remember(database) {
                    CategoryRepository(database)
                }
                val noteRepository = remember(database) {
                    NoteRepository(database)
                }
                val trashRepository = remember(database) {
                    TrashRepository(
                        database = database,
                        imageFileCleaner = ImageFileCleaner(
                            applicationContext,
                            database
                        ),
                        audioFileCleaner = AudioFileCleaner(
                            applicationContext,
                            database
                        )
                    )
                }
                val audioRepository = remember(database) {
                    AudioAttachmentRepository(
                        applicationContext,
                        database
                    )
                }
                LaunchedEffect(audioRepository) {
                    runCatching {
                        audioRepository.cleanupOrphanedFiles()
                    }
                }

                val categoryViewModel: CategoryViewModel =
                    viewModel(
                        factory = CategoryViewModel.factory(
                            categoryRepository
                        )
                    )

                val noteViewModel: NoteViewModel =
                    viewModel(
                        factory = NoteViewModel.factory(
                            noteRepository
                        )
                    )

                val trashViewModel: TrashViewModel =
                    viewModel(
                        factory = TrashViewModel.factory(
                            trashRepository
                        )
                    )

                AppNavigation(
                    audioRepository = audioRepository,
                    categoryViewModel = categoryViewModel,
                    noteViewModel = noteViewModel,
                    trashViewModel = trashViewModel
                )
            }
        }
    }
}
