package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "student")
open class Student(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_id")
    open var studentId: Long? = null,
    @Column(name = "first_name", length = 50, nullable = false)
    open var firstName: String = "",
    @Column(name = "last_name", length = 50, nullable = false)
    open var lastName: String = "",
    @Column(name = "date_of_birth", nullable = false)
    open var dateOfBirth: LocalDate = LocalDate.now(),
    @Column(name = "email", length = 100, unique = true, nullable = false)
    open var email: String = "",
)
