package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.CreateRouteRequest
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RouteDto
import com.runwithme.runwithme.api.dto.UpdateRouteRequest
import com.runwithme.runwithme.api.service.RouteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/routes")
@Tag(name = "Routes", description = "Running route management APIs")
class RouteController(
    private val routeService: RouteService,
) {
    @GetMapping
    @Operation(
        summary = "Get all routes with pagination",
        description = "Retrieves a paginated list of all routes. Default page size is 10.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved routes",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            PageResponse::class,
                                    ),
                            ),
                        ],
                ),
            ],
    )
    fun getAllRoutes(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<RouteDto>> = ResponseEntity.ok(routeService.getAllRoutes(page, size))

    @GetMapping("/public")
    @Operation(
        summary = "Get all public routes with pagination",
        description =
            "Retrieves a paginated list of all public routes. Default page size is 10.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved public routes",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            PageResponse::class,
                                    ),
                            ),
                        ],
                ),
            ],
    )
    fun getPublicRoutes(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<RouteDto>> = ResponseEntity.ok(routeService.getPublicRoutes(page, size))

    @GetMapping("/difficulty/{level}")
    @Operation(
        summary = "Get routes by difficulty level with pagination",
        description = "Retrieves a paginated list of routes filtered by difficulty level.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved routes",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            PageResponse::class,
                                    ),
                            ),
                        ],
                ),
            ],
    )
    fun getRoutesByDifficulty(
        @Parameter(description = "Difficulty level", example = "intermediate")
        @PathVariable
        level: String,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<RouteDto>> = ResponseEntity.ok(routeService.getRoutesByDifficulty(level, page, size))

    @GetMapping("/{id}")
    @Operation(
        summary = "Get route by ID",
        description = "Retrieves a single route by its ID",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved route",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            RouteDto::class,
                                    ),
                            ),
                        ],
                ),
                ApiResponse(responseCode = "404", description = "Route not found"),
            ],
    )
    fun getRouteById(
        @Parameter(description = "Route ID", example = "1") @PathVariable id: Long,
    ): ResponseEntity<RouteDto> {
        val route = routeService.getRouteById(id)
        return if (route != null) {
            ResponseEntity.ok(route)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @PostMapping
    @Operation(
        summary = "Create a new route",
        description = "Creates a new route.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "Route created successfully",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            RouteDto::class,
                                    ),
                            ),
                        ],
                ),
                ApiResponse(responseCode = "400", description = "Invalid request"),
            ],
    )
    fun createRoute(
        @RequestBody request: CreateRouteRequest,
        authentication: Authentication,
    ): ResponseEntity<RouteDto> {
        val route = routeService.createRoute(request, authentication.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(route)
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update route",
        description = "Updates an existing route.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Route updated successfully",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            RouteDto::class,
                                    ),
                            ),
                        ],
                ),
                ApiResponse(responseCode = "404", description = "Route not found"),
            ],
    )
    fun updateRoute(
        @Parameter(description = "Route ID", example = "1") @PathVariable id: Long,
        @RequestBody request: UpdateRouteRequest,
    ): ResponseEntity<RouteDto> {
        val route = routeService.updateRoute(id, request)
        return if (route != null) {
            ResponseEntity.ok(route)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete route",
        description = "Deletes a route by ID.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "204",
                    description = "Route deleted successfully",
                ),
                ApiResponse(responseCode = "404", description = "Route not found"),
            ],
    )
    fun deleteRoute(
        @Parameter(description = "Route ID", example = "1") @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = routeService.deleteRoute(id)
        return if (deleted) {
            ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @GetMapping("/{id}/similar")
    @Operation(
        summary = "Get similar routes",
        description =
            "Retrieves a list of similar routes based on spatial proximity (KNN on centroid).",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved similar routes",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            RouteDto::class,
                                    ),
                            ),
                        ],
                ),
                ApiResponse(responseCode = "404", description = "Route not found"),
            ],
    )
    fun getSimilarRoutes(
        @Parameter(description = "Route ID", example = "1") @PathVariable id: Long,
        @Parameter(description = "Maximum distance in meters", example = "5000")
        @RequestParam(defaultValue = "5000")
        maxDistance: Double,
        @Parameter(description = "Number of routes to return", example = "5")
        @RequestParam(defaultValue = "5")
        limit: Int,
    ): ResponseEntity<List<RouteDto>> {
        val routes = routeService.getSimilarRoutes(id, maxDistance, limit)
        return ResponseEntity.ok(routes)
    }

    @GetMapping("/nearby")
    @Operation(
        summary = "Get nearby routes",
        description =
            "Retrieves a paginated list of routes starting within a specified radius of a location.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved nearby routes",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            PageResponse::class,
                                    ),
                            ),
                        ],
                ),
            ],
    )
    fun getNearbyRoutes(
        @Parameter(description = "Latitude", example = "40.7829") @RequestParam lat: Double,
        @Parameter(description = "Longitude", example = "-73.9654") @RequestParam lon: Double,
        @Parameter(description = "Radius in meters", example = "5000")
        @RequestParam(defaultValue = "5000")
        radius: Double,
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10")
        size: Int,
    ): ResponseEntity<PageResponse<RouteDto>> = ResponseEntity.ok(routeService.getNearbyRoutes(lat, lon, radius, page, size))
}
