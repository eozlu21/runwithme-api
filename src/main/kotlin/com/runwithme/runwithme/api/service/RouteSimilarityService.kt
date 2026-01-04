package com.runwithme.runwithme.api.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * JSON structure for routes.json
 */
data class RoutesData(
    val count: Int,
    val embedding_dim: Int,
    val embedding_types: List<String>,
    val feature_names: List<String>,
    val embeddings_info: Map<String, EmbeddingInfo>,
    val routes: List<Map<String, Any?>>,
)

data class EmbeddingInfo(
    val file: String,
    val shape: List<Int>,
    val size_kb: Double,
)

/**
 * Response model for similar routes with all similarity scores
 */
data class SimilarRoute(
    val routeId: String,
    val similarities: Map<String, Double>,
    val metadata: Map<String, Any?>,
)

/**
 * Route Similarity Service with Multi-Feature Support
 *
 * Loads pre-computed embeddings for all features and performs cosine similarity search.
 * Returns similarity scores for: overall, shape, metadata, and all individual features.
 */
@Service
class RouteSimilarityService {
    private val logger = LoggerFactory.getLogger(RouteSimilarityService::class.java)

    // Embeddings for each feature type
    private lateinit var embeddings: MutableMap<String, Array<FloatArray>>
    private lateinit var routeMetadata: List<Map<String, Any?>>
    private lateinit var embeddingTypes: List<String>
    private lateinit var featureNames: List<String>
    private var embeddingDim: Int = 128
    private var routeCount: Int = 0

    private val mapper = jacksonObjectMapper()

    // Configure path - use environment variable or default
    private val dataDir: String = System.getenv("EMBEDDINGS_DIR") ?: "classpath:data"

    @PostConstruct
    fun init() {
        loadEmbeddings()
        logger.info("RouteSimilarityService initialized with $routeCount routes and ${embeddingTypes.size} embedding types")
    }

    /**
     * Load embeddings and metadata from files
     */
    @Synchronized
    fun loadEmbeddings() {
        try {
            // Load metadata JSON
            val metadataStream =
                if (dataDir.startsWith("classpath:")) {
                    javaClass.classLoader.getResourceAsStream("data/routes.json")
                        ?: throw RuntimeException("routes.json not found in classpath")
                } else {
                    File("$dataDir/routes.json").inputStream()
                }

            val data: RoutesData = mapper.readValue(metadataStream)
            routeMetadata = data.routes
            embeddingDim = data.embedding_dim
            embeddingTypes = data.embedding_types
            featureNames = data.feature_names
            routeCount = data.count

            // Load all embedding files
            embeddings = mutableMapOf()

            for (embType in embeddingTypes) {
                val embInfo = data.embeddings_info[embType] ?: continue
                val embDim = embInfo.shape.getOrNull(1) ?: embeddingDim

                val embStream =
                    if (dataDir.startsWith("classpath:")) {
                        javaClass.classLoader.getResourceAsStream("data/${embInfo.file}")
                            ?: continue
                    } else {
                        val file = File("$dataDir/${embInfo.file}")
                        if (file.exists()) file.inputStream() else continue
                    }

                val bytes = embStream.readBytes()
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

                embeddings[embType] =
                    Array(routeCount) { _ ->
                        FloatArray(embDim) { buffer.float }
                    }

                logger.debug("Loaded $embType embeddings: $routeCount x $embDim (${bytes.size / 1024} KB)")
            }

            logger.info("Loaded ${embeddings.size} embedding types for $routeCount routes")
        } catch (e: Exception) {
            throw RuntimeException("Failed to load embeddings: ${e.message}", e)
        }
    }

    /**
     * Find similar routes by route ID with all similarity scores
     *
     * @param routeId The ID of the query route
     * @param topK Number of similar routes to return
     * @return List of similar routes sorted by overall similarity (highest first)
     */
    fun findSimilarByRouteId(
        routeId: String,
        topK: Int = 10,
    ): List<SimilarRoute> {
        val queryIndex = routeMetadata.indexOfFirst { it["id"] == routeId }
        if (queryIndex < 0) {
            throw IllegalArgumentException("Route not found: $routeId")
        }
        return findSimilarByIndex(queryIndex, topK)
    }

    /**
     * Find similar routes by internal index with all feature similarities
     */
    fun findSimilarByIndex(
        queryIndex: Int,
        topK: Int = 10,
    ): List<SimilarRoute> {
        if (queryIndex < 0 || queryIndex >= routeCount) {
            throw IllegalArgumentException("Invalid route index: $queryIndex")
        }

        // Calculate similarities for all embedding types
        val allSimilarities = mutableMapOf<String, DoubleArray>()

        for ((embType, embArray) in embeddings) {
            val queryEmb = embArray[queryIndex]
            allSimilarities[embType] =
                DoubleArray(routeCount) { idx ->
                    if (idx == queryIndex) -1.0 else cosineSimilarity(queryEmb, embArray[idx])
                }
        }

        // Sort by overall similarity
        val overallSims = allSimilarities["overall"] ?: return emptyList()
        val sortedIndices =
            overallSims.indices
                .filter { it != queryIndex }
                .sortedByDescending { overallSims[it] }
                .take(topK)

        return sortedIndices.map { idx ->
            // Build similarities map with all available embedding types
            val similarities = mutableMapOf<String, Double>()
            for ((embType, sims) in allSimilarities) {
                similarities[embType] = (sims[idx] * 100).coerceIn(0.0, 100.0)
            }

            SimilarRoute(
                routeId = routeMetadata[idx]["id"]?.toString() ?: "unknown",
                similarities = similarities,
                metadata = routeMetadata[idx],
            )
        }
    }

    /**
     * Get route metadata by ID
     */
    fun getRouteById(routeId: String): Map<String, Any?>? = routeMetadata.find { it["id"] == routeId }

    /**
     * Get all route IDs
     */
    fun getAllRouteIds(): List<String> = routeMetadata.mapNotNull { it["id"]?.toString() }

    /**
     * Get total number of routes in the index
     */
    fun getRouteCount(): Int = routeCount

    /**
     * Get available embedding types
     */
    fun getEmbeddingTypes(): List<String> = embeddingTypes

    /**
     * Get feature names
     */
    fun getFeatureNames(): List<String> = featureNames

    /**
     * Find similar routes for a NEW route not in the index
     * Calls Python script directly via ProcessBuilder (no separate server needed)
     *
     * @param routeData The new route data as a Map
     * @param topK Number of similar routes to return
     * @return List of similar routes with similarity scores
     */
    fun findSimilarForNewRoute(
        routeData: Map<String, Any?>,
        topK: Int = 10,
    ): List<SimilarRoute> {
        val newEmbeddings = computeEmbeddingViaPython(routeData)

        if (newEmbeddings.isEmpty()) {
            throw RuntimeException("Failed to compute embeddings for new route")
        }

        // Calculate similarities with all indexed routes
        val allSimilarities = mutableMapOf<String, DoubleArray>()

        for ((embType, newEmb) in newEmbeddings) {
            val storedEmbs = embeddings[embType] ?: continue
            allSimilarities[embType] =
                DoubleArray(routeCount) { idx ->
                    cosineSimilarity(newEmb, storedEmbs[idx])
                }
        }

        // Sort by overall similarity
        val overallSims = allSimilarities["overall"] ?: return emptyList()
        val sortedIndices =
            overallSims.indices
                .sortedByDescending { overallSims[it] }
                .take(topK)

        return sortedIndices.map { idx ->
            val similarities = mutableMapOf<String, Double>()
            for ((embType, sims) in allSimilarities) {
                similarities[embType] = (sims[idx] * 100).coerceIn(0.0, 100.0)
            }

            SimilarRoute(
                routeId = routeMetadata[idx]["id"]?.toString() ?: "unknown",
                similarities = similarities,
                metadata = routeMetadata[idx],
            )
        }
    }

    /**
     * Compute embedding by calling Python script directly
     *
     * Requires in your backend resources:
     * - python/inference_script.py
     * - python/modular_features.py
     * - data/model.pt
     *
     * Set PYTHON_PATH env var if python3 is not in PATH
     */
    private fun computeEmbeddingViaPython(routeData: Map<String, Any?>): Map<String, FloatArray> {
        val pythonPath = System.getenv("PYTHON_PATH") ?: "python3"
        val scriptDir =
            System.getenv("INFERENCE_SCRIPT_DIR")
                ?: javaClass.classLoader.getResource("python")?.path
                ?: throw RuntimeException("Python scripts not found. Set INFERENCE_SCRIPT_DIR env var.")

        logger.debug("Starting Python inference for route embedding...")
        val pythonStart = System.currentTimeMillis()

        try {
            // Write route data to temp file
            val tempInput = File.createTempFile("route_input_", ".json")
            val tempOutput = File.createTempFile("embedding_output_", ".json")
            tempInput.deleteOnExit()
            tempOutput.deleteOnExit()

            mapper.writeValue(tempInput, routeData)
            logger.trace("Wrote route data to temp file: ${tempInput.absolutePath}")

            // Call Python script
            val process =
                ProcessBuilder(
                    pythonPath,
                    "$scriptDir/inference_script.py",
                    "--input",
                    tempInput.absolutePath,
                    "--output",
                    tempOutput.absolutePath,
                    "--model-dir",
                    if (dataDir.startsWith("classpath:")) {
                        javaClass.classLoader.getResource("data")?.path ?: "data"
                    } else {
                        dataDir
                    },
                ).redirectErrorStream(true)
                    .start()

            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()

            if (exitCode != 0) {
                logger.error("Python script failed (exit $exitCode): $output")
                throw RuntimeException("Python script failed (exit $exitCode): $output")
            }

            // Read embeddings from output file
            val response: Map<String, List<Double>> = mapper.readValue(tempOutput)

            // Cleanup
            tempInput.delete()
            tempOutput.delete()

            val duration = System.currentTimeMillis() - pythonStart
            logger.debug("Python inference completed in ${duration}ms, generated ${response.size} embedding types")

            return response.mapValues { (_, values) ->
                values.map { it.toFloat() }.toFloatArray()
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - pythonStart
            logger.error("Python inference failed after ${duration}ms: ${e.message}")
            throw RuntimeException("Failed to compute embedding: ${e.message}", e)
        }
    }

    /**
     * Compare two NEW routes directly using their embeddings
     *
     * Computes embeddings for both routes and returns similarity scores
     * for all embedding types (overall, shape, metadata, and per-feature)
     *
     * @param route1 First route data
     * @param route2 Second route data
     * @return RouteComparisonResult with all similarity scores
     */
    fun compareTwoRoutes(
        route1: Map<String, Any?>,
        route2: Map<String, Any?>,
    ): RouteComparisonResult {
        // Compute embeddings for both routes
        val embeddings1 = computeEmbeddingViaPython(route1)
        val embeddings2 = computeEmbeddingViaPython(route2)

        if (embeddings1.isEmpty() || embeddings2.isEmpty()) {
            throw RuntimeException("Failed to compute embeddings for one or both routes")
        }

        // Calculate similarities for all embedding types
        val similarities = mutableMapOf<String, Double>()

        for ((embType, emb1) in embeddings1) {
            val emb2 = embeddings2[embType] ?: continue
            val similarity = cosineSimilarity(emb1, emb2)
            similarities[embType] = (similarity * 100).coerceIn(0.0, 100.0)
        }

        return RouteComparisonResult(
            route1 = route1,
            route2 = route2,
            similarities = similarities,
            embeddingTypes = embeddings1.keys.toList(),
        )
    }

    /**
     * Cosine similarity between two vectors
     */
    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray,
    ): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dot / denominator else 0.0
    }
}

/**
 * Result of comparing two routes
 */
data class RouteComparisonResult(
    val route1: Map<String, Any?>,
    val route2: Map<String, Any?>,
    val similarities: Map<String, Double>,
    val embeddingTypes: List<String>,
)
