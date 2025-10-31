package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.Student
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Student data transfer object")
data class StudentDto(
    @Schema(description = "Student unique identifier", example = "1")
    val studentId: Long,
    @Schema(description = "First name of the student", example = "John")
    val firstName: String,
    @Schema(description = "Last name of the student", example = "Doe")
    val lastName: String,
    @Schema(description = "Date of birth", example = "2000-01-15")
    val dateOfBirth: LocalDate,
    @Schema(description = "Email address", example = "john.doe@example.com")
    val email: String,
) {
    companion object {
        fun fromEntity(student: Student): StudentDto =
            StudentDto(
                studentId = student.studentId!!,
                firstName = student.firstName,
                lastName = student.lastName,
                dateOfBirth = student.dateOfBirth,
                email = student.email,
            )
    }
}
