package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.StudentDto
import com.runwithme.runwithme.api.service.StudentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/students")
@Tag(name = "Students", description = "Student management APIs")
class StudentController(
    private val studentService: StudentService,
) {
    @GetMapping
    @Operation(
        summary = "Get all students with pagination",
        description = "Retrieves a paginated list of students. Default page size is 5.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved students",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getStudents(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        page: Int,
        @Parameter(description = "Number of items per page", example = "5")
        @RequestParam(defaultValue = "5")
        size: Int,
    ): ResponseEntity<PageResponse<StudentDto>> = ResponseEntity.ok(studentService.getStudents(page, size))

    @GetMapping("/{id}")
    @Operation(
        summary = "Get student by ID",
        description = "Retrieves a single student by their ID",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved student",
                content = [Content(schema = Schema(implementation = StudentDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "Student not found"),
        ],
    )
    fun getStudentById(
        @Parameter(description = "Student ID", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<StudentDto> {
        val student = studentService.getStudentById(id)
        return if (student != null) ResponseEntity.ok(student) else ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }
}
