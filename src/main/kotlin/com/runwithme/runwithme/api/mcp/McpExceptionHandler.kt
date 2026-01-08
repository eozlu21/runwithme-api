package com.runwithme.runwithme.api.mcp

import com.runwithme.runwithme.api.exception.GlobalExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [McpController::class])
class McpExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(ex: NoSuchElementException): ResponseEntity<GlobalExceptionHandler.ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(GlobalExceptionHandler.ErrorResponse(ex.message))
}
