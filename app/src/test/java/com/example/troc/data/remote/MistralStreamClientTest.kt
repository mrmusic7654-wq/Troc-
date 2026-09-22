package com.example.troc.data.remote

import com.example.troc.domain.model.ApiMessage
import com.example.troc.domain.model.ChatRequestSpec
import com.example.troc.domain.model.StreamEvent
import com.example.troc.domain.repository.MistralClient
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MistralStreamClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MistralClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        client = MistralClientImpl(OkHttpClient(), json, retrofitApi(json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun retrofitApi(json: Json): MistralApi =
        retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MistralApi::class.java)

    @Test
    fun `stream collects tokens then done`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"id":"1","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}

                    data: {"id":"1","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":null}]}

                    data: {"id":"1","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        val events = client.stream(
            ChatRequestSpec(
                baseUrl = server.url("/").toString(),
                apiKey = "test-key",
                model = "mistral-small-latest",
                messages = listOf(ApiMessage("user", "hi"))
            )
        ).toList()

        val tokens = events.filterIsInstance<StreamEvent.Token>().joinToString("") { it.text }
        assertEquals("Hello", tokens)
        assertTrue(events.any { it is StreamEvent.Done })
    }

    @Test
    fun `stream surfaces http errors as friendly events`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"message":"Unauthorized","code":401}""")
        )
        val events = client.stream(
            ChatRequestSpec(
                baseUrl = server.url("/").toString(),
                apiKey = "bad-key",
                model = "mistral-small-latest",
                messages = listOf(ApiMessage("user", "hi"))
            )
        ).toList()

        val error = events.filterIsInstance<StreamEvent.Error>().first()
        assertEquals(401, error.code)
        assertTrue(error.message.contains("401"))
    }

    @Test
    fun `list models parses response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"mistral-small-latest","max_context_length":32768}]}""")
        )
        val models = client.listModels(server.url("/").toString(), "test-key")
        assertEquals(listOf("mistral-small-latest"), models.map { it.id })
    }
}
