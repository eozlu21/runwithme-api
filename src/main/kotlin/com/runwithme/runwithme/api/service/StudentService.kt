package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.StudentDto
import com.runwithme.runwithme.api.repository.StudentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class StudentService(
    private val studentRepository: StudentRepository,
) {
    fun getStudents(
        page: Int,
        size: Int,
    ): PageResponse<StudentDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 5
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("studentId").ascending())
        val studentPage = studentRepository.findAll(pageRequest)
        return PageResponse.fromPage(studentPage, StudentDto::fromEntity)
    }

    fun getStudentById(id: Long): StudentDto? = studentRepository.findById(id).map(StudentDto::fromEntity).orElse(null)
}
