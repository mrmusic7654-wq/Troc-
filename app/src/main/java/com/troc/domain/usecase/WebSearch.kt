package com.troc.domain.usecase

import com.troc.data.repository.WebSearchRepository
import javax.inject.Inject

class WebSearch @Inject constructor(
    private val webSearchRepository: WebSearchRepository
) {
    suspend operator fun invoke(query: String, maxResults: Int = 5): Result<String> {
        val result = webSearchRepository.search(query, maxResults)
        return result.map { response ->
            webSearchRepository.formatResultsForLlm(response)
        }
    }

    suspend fun searchRaw(query: String, maxResults: Int = 5) = webSearchRepository.search(query, maxResults)
}
