package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.service.RouteSimilarityService
import com.runwithme.runwithme.api.service.SimilarRoute
import com.runwithme.runwithme.api.service.UserPreferenceService
import com.runwithme.runwithme.api.service.UserPreferences
import com.runwithme.runwithme.api.service.CombinedSimilarityResult
import com.runwithme.runwithme.api.service.RouteComparisonResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for Route Similarity API
 * 
 * Provides endpoints for:
 * - Finding similar routes using ML embeddings
 * - Combined route + user preference matching
 * - New route similarity (via Python inference service)
 */
@RestController
@RequestMapping("/api/route-similarity")
class RouteSimilarityController(
    private val similarityService: RouteSimilarityService,
    private val preferenceService: UserPreferenceService
) {
    
    // ========================================================================
    // INDEXED ROUTE ENDPOINTS (pre-computed embeddings - fast)
    // ========================================================================
    
    /**
     * Get similar routes with all feature similarity scores
     * 
     * GET /api/route-similarity/{routeId}/similar?topK=10
     * 
     * Response includes similarity percentages for:
     * - overall: Combined similarity score
     * - shape: Route shape/geometry similarity
     * - metadata: Combined metadata similarity
     * - pace, terrain, distance, time, runner_type: Individual feature similarities
     */
    @GetMapping("/{routeId}/similar")
    fun getSimilarRoutes(
        @PathVariable routeId: String,
        @RequestParam(defaultValue = "10") topK: Int
    ): ResponseEntity<SimilarRoutesResponse> {
        return try {
            val similarRoutes = similarityService.findSimilarByRouteId(routeId, topK)
            val queryRoute = similarityService.getRouteById(routeId)
            
            ResponseEntity.ok(SimilarRoutesResponse(
                success = true,
                queryRouteId = routeId,
                queryRoute = queryRoute,
                similarRoutes = similarRoutes,
                totalIndexedRoutes = similarityService.getRouteCount(),
                embeddingTypes = similarityService.getEmbeddingTypes()
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(SimilarRoutesResponse(
                success = false,
                error = e.message,
                queryRouteId = routeId,
                similarRoutes = emptyList(),
                totalIndexedRoutes = similarityService.getRouteCount(),
                embeddingTypes = emptyList()
            ))
        }
    }
    
    /**
     * Get similar routes with COMBINED scoring (route ML + user preferences)
     * 
     * POST /api/route-similarity/{routeId}/similar-with-preferences?topK=10&routeWeight=0.6
     * 
     * Request body - user preferences:
     * {
     *   "experience_level": "intermediate",
     *   "activity_type": "competitive",
     *   "intensity_preference": "high",
     *   "social_vibe": "social",
     *   "motivation": "training",
     *   "coaching_style": "pusher",
     *   "music_preference": "headphone",
     *   "match_gender": false,
     *   "preferred_day": "weekend",
     *   "time_of_day": "morning"
     * }
     */
    @PostMapping("/{routeId}/similar-with-preferences")
    fun getSimilarRoutesWithPreferences(
        @PathVariable routeId: String,
        @RequestParam(defaultValue = "10") topK: Int,
        @RequestParam(defaultValue = "0.6") routeWeight: Double,
        @RequestBody queryUserPreferences: Map<String, Any?>
    ): ResponseEntity<CombinedSimilarityResponse> {
        return try {
            val queryPrefs = preferenceService.validatePreferences(
                preferenceService.parsePreferences(queryUserPreferences)
            )
            
            val similarRoutes = similarityService.findSimilarByRouteId(routeId, topK * 2)
            val queryRoute = similarityService.getRouteById(routeId)
            
            val combinedResults = similarRoutes.mapNotNull { route ->
                val otherUserPrefs = preferenceService.parsePreferences(route.metadata)
                val prefMatches = preferenceService.getPreferenceMatchScores(queryPrefs, otherUserPrefs)
                val routeSimilarity = route.similarities["overall"] ?: 0.0
                val combinedScore = preferenceService.calculateCombinedScore(
                    routeSimilarity, prefMatches, routeWeight
                )
                
                CombinedSimilarityResult(
                    routeId = route.routeId,
                    routeSimilarities = route.similarities,
                    preferenceMatches = prefMatches,
                    combinedScore = combinedScore,
                    routeMetadata = route.metadata,
                    userPreferences = otherUserPrefs
                )
            }
            .sortedByDescending { it.combinedScore }
            .take(topK)
            
            ResponseEntity.ok(CombinedSimilarityResponse(
                success = true,
                queryRouteId = routeId,
                queryRoute = queryRoute,
                queryPreferences = queryPrefs,
                routeWeight = routeWeight,
                preferenceWeight = 1.0 - routeWeight,
                results = combinedResults,
                totalIndexedRoutes = similarityService.getRouteCount()
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(CombinedSimilarityResponse(
                success = false,
                error = e.message,
                queryRouteId = routeId,
                results = emptyList(),
                totalIndexedRoutes = similarityService.getRouteCount()
            ))
        }
    }
    
    // ========================================================================
    // NEW ROUTE ENDPOINTS (calls Python inference service)
    // ========================================================================
    
    /**
     * Find similar routes for a NEW route not in the index
     * 
     * POST /api/route-similarity/new/similar?topK=10
     * 
     * Request body - full route data:
     * {
     *   "Geometry": {
     *     "type": "LineString",
     *     "coordinates": [[lng, lat], [lng, lat], ...]
     *   },
     *   "Pace": 5.5,
     *   "Distance": 10.2,
     *   "RunnerType": "intermediate",
     *   "Terrain": "road"
     * }
     * 
     * NOTE: Requires Python inference service running:
     *       python inference_service.py --serve
     */
    @PostMapping("/new/similar")
    fun getSimilarForNewRoute(
        @RequestParam(defaultValue = "10") topK: Int,
        @RequestBody routeData: Map<String, Any?>
    ): ResponseEntity<NewRouteSimilarityResponse> {
        return try {
            val similarRoutes = similarityService.findSimilarForNewRoute(routeData, topK)
            
            ResponseEntity.ok(NewRouteSimilarityResponse(
                success = true,
                inputRoute = routeData,
                similarRoutes = similarRoutes,
                totalIndexedRoutes = similarityService.getRouteCount(),
                embeddingTypes = similarityService.getEmbeddingTypes()
            ))
        } catch (e: RuntimeException) {
            ResponseEntity.status(503).body(NewRouteSimilarityResponse(
                success = false,
                error = e.message,
                inputRoute = routeData,
                similarRoutes = emptyList(),
                totalIndexedRoutes = similarityService.getRouteCount(),
                embeddingTypes = emptyList()
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(NewRouteSimilarityResponse(
                success = false,
                error = e.message,
                inputRoute = routeData,
                similarRoutes = emptyList(),
                totalIndexedRoutes = similarityService.getRouteCount(),
                embeddingTypes = emptyList()
            ))
        }
    }
    
    /**
     * Find similar routes for a NEW route with user preference matching
     * 
     * POST /api/route-similarity/new/similar-with-preferences?topK=10&routeWeight=0.6
     */
    @PostMapping("/new/similar-with-preferences")
    fun getSimilarForNewRouteWithPreferences(
        @RequestParam(defaultValue = "10") topK: Int,
        @RequestParam(defaultValue = "0.6") routeWeight: Double,
        @RequestBody request: NewRouteWithPreferencesRequest
    ): ResponseEntity<CombinedNewRouteResponse> {
        return try {
            val queryPrefs = preferenceService.validatePreferences(
                preferenceService.parsePreferences(request.userPreferences)
            )
            
            val similarRoutes = similarityService.findSimilarForNewRoute(
                request.routeData, topK * 2
            )
            
            val combinedResults = similarRoutes.mapNotNull { route ->
                val otherUserPrefs = preferenceService.parsePreferences(route.metadata)
                val prefMatches = preferenceService.getPreferenceMatchScores(queryPrefs, otherUserPrefs)
                val routeSimilarity = route.similarities["overall"] ?: 0.0
                val combinedScore = preferenceService.calculateCombinedScore(
                    routeSimilarity, prefMatches, routeWeight
                )
                
                CombinedSimilarityResult(
                    routeId = route.routeId,
                    routeSimilarities = route.similarities,
                    preferenceMatches = prefMatches,
                    combinedScore = combinedScore,
                    routeMetadata = route.metadata,
                    userPreferences = otherUserPrefs
                )
            }
            .sortedByDescending { it.combinedScore }
            .take(topK)
            
            ResponseEntity.ok(CombinedNewRouteResponse(
                success = true,
                inputRoute = request.routeData,
                queryPreferences = queryPrefs,
                routeWeight = routeWeight,
                preferenceWeight = 1.0 - routeWeight,
                results = combinedResults,
                totalIndexedRoutes = similarityService.getRouteCount()
            ))
        } catch (e: RuntimeException) {
            ResponseEntity.status(503).body(CombinedNewRouteResponse(
                success = false,
                error = e.message,
                inputRoute = request.routeData,
                results = emptyList(),
                totalIndexedRoutes = similarityService.getRouteCount()
            ))
        }
    }
    
    // ========================================================================
    // ROUTE COMPARISON ENDPOINT
    // ========================================================================
    
    /**
     * Compare two NEW routes directly
     * 
     * POST /api/route-similarity/compare
     * 
     * Request body:
     * {
     *   "route1": {
     *     "Geometry": { "type": "LineString", "coordinates": [[lng, lat], ...] },
     *     "Pace": 5.5,
     *     "Distance": 10.2,
     *     "Terrain": "urban"
     *   },
     *   "route2": {
     *     "Geometry": { "type": "LineString", "coordinates": [[lng, lat], ...] },
     *     "Pace": 6.0,
     *     "Distance": 8.5,
     *     "Terrain": "park"
     *   }
     * }
     * 
     * Response includes similarity percentages for:
     * - overall: Combined similarity score
     * - shape: Route shape/geometry similarity
     * - metadata: Combined metadata similarity  
     * - pace, terrain, distance, time: Individual feature similarities
     */
    @PostMapping("/compare")
    fun compareTwoRoutes(
        @RequestBody request: CompareRoutesRequest
    ): ResponseEntity<CompareRoutesResponse> {
        return try {
            val result = similarityService.compareTwoRoutes(request.route1, request.route2)
            
            ResponseEntity.ok(CompareRoutesResponse(
                success = true,
                route1 = result.route1,
                route2 = result.route2,
                similarities = result.similarities,
                embeddingTypes = result.embeddingTypes
            ))
        } catch (e: RuntimeException) {
            ResponseEntity.status(503).body(CompareRoutesResponse(
                success = false,
                error = e.message,
                route1 = request.route1,
                route2 = request.route2,
                similarities = emptyMap(),
                embeddingTypes = emptyList()
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(CompareRoutesResponse(
                success = false,
                error = e.message,
                route1 = request.route1,
                route2 = request.route2,
                similarities = emptyMap(),
                embeddingTypes = emptyList()
            ))
        }
    }
    
    // ========================================================================
    // UTILITY ENDPOINTS
    // ========================================================================
    
    /**
     * Compare preferences between two users (standalone)
     * 
     * POST /api/route-similarity/compare-preferences
     */
    @PostMapping("/compare-preferences")
    fun comparePreferences(
        @RequestBody request: ComparePreferencesRequest
    ): ResponseEntity<PreferenceComparisonResponse> {
        val user1Prefs = preferenceService.validatePreferences(
            preferenceService.parsePreferences(request.user1)
        )
        val user2Prefs = preferenceService.validatePreferences(
            preferenceService.parsePreferences(request.user2)
        )
        
        val result = preferenceService.calculatePreferenceMatch(user1Prefs, user2Prefs)
        
        return ResponseEntity.ok(PreferenceComparisonResponse(
            user1 = user1Prefs,
            user2 = user2Prefs,
            overallMatchPercent = result.overallMatchPercent,
            matches = result.matches.mapValues { 
                mapOf(
                    "user1" to it.value.user1Value,
                    "user2" to it.value.user2Value,
                    "match" to it.value.isMatch,
                    "percent" to it.value.matchPercent
                )
            }
        ))
    }
    
    /**
     * Get route metadata by ID
     * 
     * GET /api/route-similarity/route/{routeId}
     */
    @GetMapping("/route/{routeId}")
    fun getRoute(@PathVariable routeId: String): ResponseEntity<Map<String, Any?>> {
        val route = similarityService.getRouteById(routeId)
        return if (route != null) {
            ResponseEntity.ok(route)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * Get service stats and health
     * 
     * GET /api/route-similarity/stats
     */
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<StatsResponse> {
        return ResponseEntity.ok(StatsResponse(
            totalRoutes = similarityService.getRouteCount(),
            embeddingTypes = similarityService.getEmbeddingTypes(),
            featureNames = similarityService.getFeatureNames(),
            status = "healthy"
        ))
    }
    
    /**
     * List all indexed route IDs (paginated)
     * 
     * GET /api/route-similarity/routes?page=0&size=20
     */
    @GetMapping("/routes")
    fun listRoutes(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<RouteListResponse> {
        val allIds = similarityService.getAllRouteIds()
        val start = (page * size).coerceAtMost(allIds.size)
        val end = ((page + 1) * size).coerceAtMost(allIds.size)
        
        return ResponseEntity.ok(RouteListResponse(
            routes = allIds.subList(start, end),
            page = page,
            size = size,
            totalRoutes = allIds.size,
            totalPages = (allIds.size + size - 1) / size
        ))
    }
}

// ============================================================================
// Response Models
// ============================================================================

data class SimilarRoutesResponse(
    val success: Boolean,
    val queryRouteId: String,
    val queryRoute: Map<String, Any?>? = null,
    val similarRoutes: List<SimilarRoute>,
    val totalIndexedRoutes: Int,
    val embeddingTypes: List<String>,
    val error: String? = null
)

data class CombinedSimilarityResponse(
    val success: Boolean,
    val queryRouteId: String,
    val queryRoute: Map<String, Any?>? = null,
    val queryPreferences: UserPreferences? = null,
    val routeWeight: Double = 0.6,
    val preferenceWeight: Double = 0.4,
    val results: List<CombinedSimilarityResult>,
    val totalIndexedRoutes: Int,
    val error: String? = null
)

data class NewRouteSimilarityResponse(
    val success: Boolean,
    val inputRoute: Map<String, Any?>,
    val similarRoutes: List<SimilarRoute>,
    val totalIndexedRoutes: Int,
    val embeddingTypes: List<String>,
    val error: String? = null
)

data class CombinedNewRouteResponse(
    val success: Boolean,
    val inputRoute: Map<String, Any?>,
    val queryPreferences: UserPreferences? = null,
    val routeWeight: Double = 0.6,
    val preferenceWeight: Double = 0.4,
    val results: List<CombinedSimilarityResult>,
    val totalIndexedRoutes: Int,
    val error: String? = null
)

data class ComparePreferencesRequest(
    val user1: Map<String, Any?>,
    val user2: Map<String, Any?>
)

data class PreferenceComparisonResponse(
    val user1: UserPreferences,
    val user2: UserPreferences,
    val overallMatchPercent: Double,
    val matches: Map<String, Map<String, Any?>>
)

data class StatsResponse(
    val totalRoutes: Int,
    val embeddingTypes: List<String>,
    val featureNames: List<String>,
    val status: String
)

data class RouteListResponse(
    val routes: List<String>,
    val page: Int,
    val size: Int,
    val totalRoutes: Int,
    val totalPages: Int
)

data class NewRouteWithPreferencesRequest(
    val routeData: Map<String, Any?>,
    val userPreferences: Map<String, Any?>
)

data class CompareRoutesRequest(
    val route1: Map<String, Any?>,
    val route2: Map<String, Any?>
)

data class CompareRoutesResponse(
    val success: Boolean,
    val route1: Map<String, Any?>,
    val route2: Map<String, Any?>,
    val similarities: Map<String, Double>,
    val embeddingTypes: List<String>,
    val error: String? = null
)
