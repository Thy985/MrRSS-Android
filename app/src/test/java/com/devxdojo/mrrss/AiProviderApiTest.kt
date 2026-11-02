package com.devxdojo.mrrss

import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderApiTest {
    @Test
    fun addsChatCompletionsPathToVersionedBaseUrl() {
        assertEquals(
            "https://provider.example/v1/chat/completions",
            AiProviderApi.normalizeEndpoint("https://provider.example/v1").toString(),
        )
    }

    @Test
    fun defaultsRootUrlToV1Endpoint() {
        assertEquals(
            "https://provider.example/v1/chat/completions",
            AiProviderApi.normalizeEndpoint("https://provider.example").toString(),
        )
    }

    @Test
    fun preservesExplicitChatCompletionsPath() {
        assertEquals(
            "https://provider.example/custom/chat/completions",
            AiProviderApi.normalizeEndpoint("https://provider.example/custom/chat/completions").toString(),
        )
    }

    @Test
    fun derivesModelsPathFromVersionedBaseUrl() {
        assertEquals(
            "https://provider.example/v1/models",
            AiProviderApi.normalizeModelsEndpoint("https://provider.example/v1").toString(),
        )
    }

    @Test
    fun derivesModelsPathFromExplicitCompletionsUrl() {
        assertEquals(
            "https://provider.example/custom/v1/models",
            AiProviderApi.normalizeModelsEndpoint("https://provider.example/custom/v1/chat/completions").toString(),
        )
    }

    @Test
    fun preservesExplicitCompletionsPathWithoutVersionPrefix() {
        assertEquals(
            "https://provider.example/chat/completions",
            AiProviderApi.normalizeEndpoint("https://provider.example/chat/completions").toString(),
        )
        assertEquals(
            "https://provider.example/models",
            AiProviderApi.normalizeModelsEndpoint("https://provider.example/chat/completions").toString(),
        )
    }

    @Test
    fun parsesAndSortsOpenAiCompatibleModelIds() {
        assertEquals(
            listOf("alpha", "beta"),
            AiProviderApi.parseModels("""{"object":"list","data":[{"id":"beta"},{"id":"alpha"},{"id":"beta"}]}"""),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCredentialsEmbeddedInEndpoint() {
        AiProviderApi.normalizeEndpoint("https://user:password@provider.example/v1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsQueryParametersInEndpoint() {
        AiProviderApi.normalizeEndpoint("https://provider.example/v1?token=secret")
    }
}
