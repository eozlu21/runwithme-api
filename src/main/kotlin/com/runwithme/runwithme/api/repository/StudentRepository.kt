package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.Student
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StudentRepository : JpaRepository<Student, Long>
