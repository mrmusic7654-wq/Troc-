package com.troc.data.api

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = "web"
)

data class WebSearchResponse(
    val query: String,
    val results: List<WebSearchResult>,
    val answer: String? = null
)

@Singleton
class WebSearchApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    // Using DuckDuckGo Instant Answer API (free, no key) + Wikipedia + fallback
    suspend fun search(query: String, maxResults: Int = 5): Result<WebSearchResponse> = withContext(Dispatchers.IO) {
        try {
            // Try DuckDuckGo
            val ddgResults = searchDuckDuckGo(query, maxResults)
            if (ddgResults.isNotEmpty()) {
                return@withContext Result.success(
                    WebSearchResponse(
                        query = query,
                        results = ddgResults,
                        answer = ddgResults.firstOrNull()?.snippet
                    )
                )
            }

            // Fallback: Use Wikipedia search via API
            val wikiResults = searchWikipedia(query, maxResults)
            if (wikiResults.isNotEmpty()) {
                return@withContext Result.success(
                    WebSearchResponse(query = query, results = wikiResults)
                )
            }

            // If both fail, return empty with message
            Result.success(
                WebSearchResponse(
                    query = query,
                    results = emptyList(),
                    answer = "No web results found for '$query'. Try rephrasing."
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun searchDuckDuckGo(query: String, maxResults: Int): List<WebSearchResult> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&pretty=1&no_html=1&skip_disambig=1"

            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            val json = JSONObject(body)
            val results = mutableListOf<WebSearchResult>()

            // Abstract
            val abstractText = json.optString("AbstractText")
            val abstractUrl = json.optString("AbstractURL")
            if (abstractText.isNotEmpty()) {
                results.add(
                    WebSearchResult(
                        title = json.optString("Heading", "Summary"),
                        url = abstractUrl,
                        snippet = abstractText,
                        source = json.optString("AbstractSource", "DuckDuckGo")
                    )
                )
            }

            // Related topics
            val related = json.optJSONArray("RelatedTopics")
            if (related != null) {
                for (i in 0 until minOf(related.length(), maxResults)) {
                    val item = related.optJSONObject(i) ?: continue
                    if (item.has("Topics")) {
                        val subTopics = item.optJSONArray("Topics")
                        if (subTopics != null) {
                            for (j in 0 until minOf(subTopics.length(), 2)) {
                                val sub = subTopics.optJSONObject(j) ?: continue
                                val text = sub.optString("Text")
                                val firstUrl = sub.optString("FirstURL")
                                if (text.isNotEmpty()) {
                                    val parts = text.split(" - ", limit = 2)
                                    results.add(
                                        WebSearchResult(
                                            title = parts.getOrNull(0) ?: "Result",
                                            url = firstUrl,
                                            snippet = parts.getOrNull(1) ?: text,
                                            source = "DuckDuckGo"
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        val text = item.optString("Text")
                        val firstUrl = item.optString("FirstURL")
                        if (text.isNotEmpty()) {
                            val parts = text.split(" - ", limit = 2)
                            results.add(
                                WebSearchResult(
                                    title = parts.getOrNull(0) ?: "Result",
                                    url = firstUrl,
                                    snippet = parts.getOrNull(1) ?: text,
                                    source = "DuckDuckGo"
                                )
                            )
                        }
                    }
                }
            }

            // Infobox
            val infobox = json.optJSONObject("Infobox")
            if (infobox != null) {
                val content = infobox.optJSONArray("content")
                if (content != null) {
                    for (i in 0 until minOf(content.length(), 2)) {
                        val c = content.optJSONObject(i) ?: continue
                        val label = c.optString("label")
                        val value = c.optString("value")
                        if (label.isNotEmpty() && value.isNotEmpty()) {
                            results.add(
                                WebSearchResult(
                                    title = label,
                                    url = "",
                                    snippet = value,
                                    source = "Infobox"
                                )
                            )
                        }
                    }
                }
            }

            results.take(maxResults)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchWikipedia(query: String, maxResults: Int): List<WebSearchResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=$maxResults"

            val request = Request.Builder().url(url).header("User-Agent", "Troc/1.0").get().build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            val json = JSONObject(body)
            val queryObj = json.optJSONObject("query")
            val search = queryObj?.optJSONArray("search") ?: return@withContext emptyList()

            val results = mutableListOf<WebSearchResult>()
            for (i in 0 until search.length()) {
                val item = search.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val snippet = item.optString("snippet").replace(Regex("<.*?>"), "")
                val pageId = item.optInt("pageid")
                results.add(
                    WebSearchResult(
                        title = title,
                        url = "https://en.wikipedia.org/?curid=$pageId",
                        snippet = snippet,
                        source = "Wikipedia"
                    )
                )
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Enhanced search that also fetches content from URL if needed
    suspend fun fetchUrlContent(url: String, maxChars: Int = 4000): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "Troc/1.0").get().build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            response.close()

            // Simple HTML to text (strip tags)
            val text = body.replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<.*?>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(maxChars)

            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
