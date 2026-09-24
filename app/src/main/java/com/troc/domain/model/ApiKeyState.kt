package com.troc.domain.model

sealed class ApiKeyState {
    object Missing : ApiKeyState()
    data class Default(val key: String, val masked: String) : ApiKeyState()
    data class UserProvided(val key: String, val masked: String) : ApiKeyState()
    object Invalid : ApiKeyState()

    fun isAvailable(): Boolean = this is Default || this is UserProvided
    fun keyOrNull(): String? = when (this) {
        is Default -> key
        is UserProvided -> key
        else -> null
    }
    fun maskedOrEmpty(): String = when (this) {
        is Default -> masked
        is UserProvided -> masked
        is Missing -> "Not set"
        is Invalid -> "Invalid"
    }
}

fun maskKey(key: String): String {
    if (key.length <= 8) return "••••••••"
    return "••••••••" + key.takeLast(4)
}

sealed class MistralKeyState : ApiKeyState()
sealed class GroqKeyState : ApiKeyState()

// Actually unify via typealias or separate sealed hierarchies
sealed class GenericKeyState {
    object Missing : GenericKeyState()
    data class Default(val key: String) : GenericKeyState()
    data class UserProvided(val key: String) : GenericKeyState()
    object Invalid : GenericKeyState()
}
