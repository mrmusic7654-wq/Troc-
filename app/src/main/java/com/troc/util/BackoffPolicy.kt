package com.troc.util

import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

object BackoffPolicy {
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 2,
        initialDelayMs: Long = 1000,
        factor: Double = 2.0,
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return block(attempt)
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) throw e
                if (e.message?.contains("429") == true || e.message?.contains("rate") == true) {
                    delay(currentDelay + Random.nextLong(0, 300))
                    currentDelay = (currentDelay * factor).toLong()
                } else {
                    throw e
                }
            }
        }
        return block(maxRetries)
    }
}

fun exponentialBackoff(attempt: Int): Long {
    return (1000 * 2.0.pow(attempt.toDouble())).toLong()
}
