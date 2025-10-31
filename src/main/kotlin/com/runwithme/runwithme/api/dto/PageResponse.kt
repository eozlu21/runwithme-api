package com.runwithme.runwithme.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

@Schema(description = "Paginated response wrapper")
data class PageResponse<T>(
    @Schema(description = "List of items in the current page")
    val content: List<T>,
    @Schema(description = "Current page number (0-indexed)", example = "0")
    val pageNumber: Int,
    @Schema(description = "Number of items per page", example = "5")
    val pageSize: Int,
    @Schema(description = "Total number of items", example = "6")
    val totalElements: Long,
    @Schema(description = "Total number of pages", example = "2")
    val totalPages: Int,
    @Schema(description = "Whether this is the first page")
    val first: Boolean,
    @Schema(description = "Whether this is the last page")
    val last: Boolean,
) {
    companion object {
        fun <T, R> fromPage(
            page: Page<T>,
            mapper: (T) -> R,
        ): PageResponse<R> =
            PageResponse(
                content = page.content.map(mapper),
                pageNumber = page.number,
                pageSize = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                first = page.isFirst,
                last = page.isLast,
            )
    }
}
