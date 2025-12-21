package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.CreateSurveyResponseDto
import com.runwithme.runwithme.api.dto.SurveyResponseDto
import com.runwithme.runwithme.api.service.SurveyResponseService
import com.runwithme.runwithme.api.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/survey-responses")
@Tag(name = "Survey Responses", description = "Survey response management APIs")
class SurveyResponseController(
    private val surveyResponseService: SurveyResponseService,
    private val userService: UserService,
) {
    @GetMapping("/my")
    @Operation(summary = "Get my survey responses")
    fun getMySurveyResponses(principal: Principal): ResponseEntity<List<SurveyResponseDto>> {
        val user = userService.getUserByUsername(principal.name) ?: throw IllegalArgumentException("User not found")
        val responses = surveyResponseService.getSurveyResponsesByUserId(user.userId)
        return ResponseEntity.ok(responses)
    }

    @PostMapping
    @Operation(summary = "Create a survey response")
    fun createSurveyResponse(
        principal: Principal,
        @RequestBody request: CreateSurveyResponseDto,
    ): ResponseEntity<SurveyResponseDto> {
        val user = userService.getUserByUsername(principal.name) ?: throw IllegalArgumentException("User not found")
        val response = surveyResponseService.createSurveyResponse(user.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a survey response")
    fun updateSurveyResponse(
        principal: Principal,
        @PathVariable id: Long,
        @RequestBody request: CreateSurveyResponseDto,
    ): ResponseEntity<SurveyResponseDto> {
        val user = userService.getUserByUsername(principal.name) ?: throw IllegalArgumentException("User not found")
        val response = surveyResponseService.updateSurveyResponse(id, user.userId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a survey response")
    fun deleteSurveyResponse(
        principal: Principal,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val user = userService.getUserByUsername(principal.name) ?: throw IllegalArgumentException("User not found")
        surveyResponseService.deleteSurveyResponse(id, user.userId)
        return ResponseEntity.noContent().build()
    }
}
