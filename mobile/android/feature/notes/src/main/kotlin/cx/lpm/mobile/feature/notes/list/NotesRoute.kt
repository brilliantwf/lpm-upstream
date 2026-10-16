package cx.lpm.mobile.feature.notes.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.core.navigation.NoteChatKey
import cx.lpm.mobile.core.navigation.NotesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The notebook wired to its ViewModel, its prompts and navigation. */
@Composable
fun NotesRoute(
    key: NotesKey,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel<NotesViewModel, NotesViewModel.Factory>(
        creationCallback = { it.create(key) }
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf<NotesPrompt?>(null) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    DisposableEffect(viewModel) {
        viewModel.open()
        onDispose { viewModel.close() }
    }
    // On appearing and on each keystroke: a pause in typing before the Mac is asked.
    LaunchedEffect(state.search) {
        delay(NotesViewModel.SEARCH_DEBOUNCE_MS)
        viewModel.runSearch()
    }
    val navigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(state.created) {
        val chat = state.created ?: return@LaunchedEffect
        viewModel.consumeCreated()
        navigate(NoteChatKey(key.project, chat.id, chat.title, key.session))
    }

    fun open(chatId: String, title: String) =
        onNavigate(NoteChatKey(key.project, chatId, title, key.session))

    NotesScreen(
        state = state,
        refreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                viewModel.refresh()
                refreshing = false
            }
        },
        modifier = modifier,
        searchActive = searchActive,
        actions = NotesActions(
            back = onBack,
            newChat = { prompt = NotesPrompt.Create },
            search = viewModel::setSearch,
            beginSearch = { searchActive = true },
            endSearch = {
                viewModel.setSearch("")
                searchActive = false
            },
            open = { open(it.id, it.title) },
            openHit = { open(it.chatId, it.chatTitle) },
            rename = { prompt = NotesPrompt.Rename(it) },
            delete = { prompt = NotesPrompt.Delete(it) },
            dismissError = viewModel::dismissError
        )
    )

    prompt?.let {
        NotesPrompts(
            prompt = it,
            onCreate = viewModel::create,
            onRename = viewModel::rename,
            onDelete = { chat: NoteChat ->
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                viewModel.delete(chat)
            },
            onDismiss = { prompt = null }
        )
    }
}
