package com.runwithme.runwithme.api.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {
    data class ErrorResponse(
        val message: String?,
        val field: String? = null,
        val emailVerified: Boolean? = null,
    )

    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message, ex.field))

    @ExceptionHandler(UnauthorizedActionException::class)
    fun handleUnauthorizedAction(ex: UnauthorizedActionException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(ex.message))

    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleEmailNotVerified(ex: EmailNotVerifiedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ex.message, emailVerified = false))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(ex.message))
}
