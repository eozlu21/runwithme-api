package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Component
class GeminiClient(
    private val restTemplate: RestTemplate,
    private val properties: McpProperties,
) {
    private val objectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(GeminiClient::class.java)

    // Asks Gemini to choose one of the allowed routes (function calling style).
    fun selectRoute(prompt: String, routes: List<McpRoute>): GeminiRouteDecision {
        if (routes.isEmpty()) {
            logger.warn("Route selection skipped because whitelist is empty.")
            return GeminiRouteDecision(routeName = null, reason = "Tanimli rota olmadigi icin secim yapilamadi.", arguments = null)
        }
        if (properties.geminiApiKey.isBlank()) {
            logger.warn("Route selection skipped because MCP_GEMINI_API_KEY is blank.")
            return GeminiRouteDecision(routeName = null, reason = "Gemini API anahtari tanimli olmadigi icin rota secilemedi.", arguments = null)
        }

        val functionList =
            routes.joinToString(separator = "\n") { route ->
                val params =
                    if (route.parameters.isEmpty()) {
                        "parametre yok"
                    } else {
                        route.parameters.joinToString { param ->
                            "${param.name}(required=${param.required}, ${param.description})"
                        }
                    }
                "- ${route.name}: ${route.description} [${route.method} ${route.pathTemplate}] Params: $params"
            }
        val selectionPrompt = buildString {
            appendLine("Gorev: Kullanici istegini incele ve sadece izin verilen fonksiyonlardan birini sec.")
            appendLine("Fonksiyon listesi:")
            appendLine(functionList)
            appendLine("Kurallar:")
            appendLine("1) Sadece listede yer alan routeName degerlerini sec.")
            appendLine("2) Cikis formati JSON olsun: {\"routeName\": \"...\", \"reason\": \"...\", \"arguments\": {\"param\": \"deger\"}}")
            appendLine("3) Parametre gerekiyorsa `arguments` icinde anahtar=deger olarak doldur.")
            appendLine("4) Aciklama kisa ve anlasilir olsun.")
            appendLine("Kullanici promptu: $prompt")
        }
        logger.debug("Selecting route for prompt. promptPreview='{}'", prompt.take(120))
        val selectionResult = callGemini(selectionPrompt)
        if (selectionResult.error != null) {
            logger.warn("Route selection failed: {}", selectionResult.error)
            return GeminiRouteDecision(routeName = null, reason = selectionResult.error, arguments = null)
        }
        val responseText =
            selectionResult.text
                ?: run {
                    logger.warn("Route selection returned empty text.")
                    return GeminiRouteDecision(routeName = null, reason = "Gemini yaniti alinamadi.", arguments = null)
                }
        val payload =
            try {
                objectMapper.readValue(responseText, RouteSelectionPayload::class.java)
            } catch (ex: Exception) {
                logger.warn("Route selection JSON parse failed: {}", ex.message)
                return GeminiRouteDecision(
                    routeName = null,
                    reason = "Rota cevabi JSON formatinda degil: ${ex.message}. Model cevabi: $responseText",
                    arguments = null,
                )
            }
        val reason = payload.reason?.takeIf { it.isNotBlank() } ?: "Model sebep belirtmedi."
        logger.info(
            "Route selected by Gemini. route='{}', reason='{}', arguments={}",
            payload.routeName,
            reason,
            payload.arguments,
        )
        return GeminiRouteDecision(payload.routeName?.trim(), reason, payload.arguments ?: emptyMap())
    }

    // Sends the merged prompt to Gemini and returns the readable text.
    fun generateAnswer(prompt: String, routeDescription: String, apiBody: String): String {
        if (properties.geminiApiKey.isBlank()) {
            logger.warn("generateAnswer skipped because MCP_GEMINI_API_KEY is blank.")
            return "Gemini API anahtari tanimli olmadigi icin yanit uretilemedi."
        }

        val combinedPrompt = buildString {
            appendLine("Kullanici istegi: $prompt")
            appendLine("Secilen aksiyon: $routeDescription")
            appendLine("API cevabi: $apiBody")
            append("Veriyi kisaca yorumla ve basit aksiyon oner.")
        }
        logger.debug("Requesting Gemini answer for routeDescription='{}'", routeDescription)
        val answerResult = callGemini(combinedPrompt)
        return answerResult.text ?: answerResult.error ?: "Gemini yaniti alinamadi."
    }

    private fun callGemini(prompt: String): GeminiCallResult {
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiTextPart(text = prompt))),
            ),
        )
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/${properties.geminiModel}:generateContent?key=${properties.geminiApiKey}"
        val response =
            try {
                restTemplate.postForObject(url, request, GeminiResponse::class.java)
            } catch (ex: RestClientException) {
                logger.error("Gemini HTTP call failed: {}", ex.message)
                return GeminiCallResult(
                    text = null,
                    error = "Gemini istegi basarisiz oldu: ${ex.message}",
                )
            }
        val text = response?.firstText()
        if (text == null) {
            logger.warn("Gemini response did not contain text.")
        }
        return GeminiCallResult(text = text, error = null)
    }
}

data class GeminiCallResult(
    val text: String?,
    val error: String?,
)

data class GeminiRouteDecision(
    val routeName: String?,
    val reason: String?,
    val arguments: Map<String, String>?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RouteSelectionPayload(
    val routeName: String?,
    val reason: String?,
    val arguments: Map<String, String>? = emptyMap(),
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
)

data class GeminiContent(
    val parts: List<GeminiTextPart>,
)

data class GeminiTextPart(
    val text: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiCandidate(
    val content: GeminiResponseContent? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponseContent(
    val parts: List<GeminiResponsePart>? = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponsePart(
    val text: String? = null,
)

// Helper to grab the very first candidate text.
private fun GeminiResponse.firstText(): String? =
    candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.firstOrNull()
        ?.text
