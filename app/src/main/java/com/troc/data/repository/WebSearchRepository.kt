package com.troc.data.repository

import com.troc.data.api.WebSearchApi
import com.troc.data.api.WebSearchResponse
import com.troc.data.api.WebSearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchRepository @Inject constructor(
    private val webSearchApi: WebSearchApi
) {
    suspend fun search(query: String, maxResults: Int = 5): Result<WebSearchResponse> {
        return webSearchApi.search(query, maxResults)
    }

    suspend fun fetchContent(url: String): Result<String> {
        return webSearchApi.fetchUrlContent(url)
    }

    fun formatResultsForLlm(response: WebSearchResponse): String {
        if (response.results.isEmpty()) {
            return "Web search for '${response.query}' returned no results."
        }

        val sb = StringBuilder()
        sb.appendLine("Web search results for '${response.query}':")
        sb.appendLine()

        response.results.forEachIndexed { index, result ->
            sb.appendLine("${index + 1}. ${result.title}")
            sb.appendLine("   URL: ${result.url}")
            sb.appendLine("   ${result.snippet}")
            sb.appendLine("   Source: ${result.source}")
            sb.appendLine()
        }

        if (response.answer != null) {
            sb.appendLine("Summary: ${response.answer}")
        }

        return sb.toString()
    }
}
