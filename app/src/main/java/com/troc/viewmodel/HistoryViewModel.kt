package com.troc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troc.data.repository.ChatRepository
import com.troc.domain.model.ChatInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val chats: List<ChatInfo> = emptyList(),
    val searchQuery: String = "",
    val filteredChats: List<ChatInfo> = emptyList()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.observeChats().collect { chats ->
                _uiState.update { it.copy(chats = chats, filteredChats = filter(chats, it.searchQuery)) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, filteredChats = filter(it.chats, query)) }
    }

    private fun filter(chats: List<ChatInfo>, query: String): List<ChatInfo> {
        if (query.isBlank()) return chats
        return chats.filter { it.title.contains(query, ignoreCase = true) }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId)
        }
    }
}
