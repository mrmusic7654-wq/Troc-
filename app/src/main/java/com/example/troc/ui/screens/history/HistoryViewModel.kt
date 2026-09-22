package com.example.troc.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.troc.domain.model.Chat
import com.example.troc.domain.model.SandboxSessionRecord
import com.example.troc.domain.repository.ChatHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryState(
    val query: String = "",
    val pendingDeleteChat: Chat? = null,
    val pendingDeleteSession: SandboxSessionRecord? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: ChatHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    val chats: StateFlow<List<Chat>> = _state
        .flatMapLatest { s ->
            historyRepository.observeChats()
        }
        .combine(_state) { chats, s ->
            if (s.query.isBlank()) chats
            else chats.filter { it.title.contains(s.query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions: StateFlow<List<SandboxSessionRecord>> = historyRepository.observeSessions()
        .combine(_state) { sessions, s ->
            if (s.query.isBlank()) sessions
            else sessions.filter {
                it.tool.contains(s.query, ignoreCase = true) || it.input.contains(s.query, ignoreCase = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun askDeleteChat(chat: Chat) = _state.update { it.copy(pendingDeleteChat = chat) }

    fun askDeleteSession(session: SandboxSessionRecord) =
        _state.update { it.copy(pendingDeleteSession = session) }

    fun dismissDialog() = _state.update { it.copy(pendingDeleteChat = null, pendingDeleteSession = null) }

    fun confirmDelete() {
        val chat = _state.value.pendingDeleteChat
        val session = _state.value.pendingDeleteSession
        _state.update { it.copy(pendingDeleteChat = null, pendingDeleteSession = null) }
        viewModelScope.launch {
            chat?.let { historyRepository.deleteChat(it.id) }
            session?.let { historyRepository.deleteSession(it.id) }
        }
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
