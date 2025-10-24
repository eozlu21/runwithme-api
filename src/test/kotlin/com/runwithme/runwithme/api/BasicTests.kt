package com.runwithme.runwithme.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BasicTests {
    @Test
    fun `basic math operations should work correctly`() {
        assertEquals(4, 2 + 2, "Addition should work")
        assertEquals(10, 5 * 2, "Multiplication should work")
        assertTrue(5 > 3, "Comparison should work")
    }

    @Test
    fun `string operations should work correctly`() {
        val greeting = "Hello, RunWithMe!"
        assertTrue(greeting.contains("RunWithMe"), "String should contain expected substring")
        assertEquals(17, greeting.length, "String length should be correct")
    }

    @Test
    fun `list operations should work correctly`() {
        val numbers = listOf(1, 2, 3, 4, 5)
        assertEquals(5, numbers.size, "List should have 5 elements")
        assertTrue(numbers.contains(3), "List should contain 3")
        assertEquals(15, numbers.sum(), "Sum should be 15")
    }

    @Test
    fun `application class should exist`() {
        val appClass = RunwithmeApiApplication::class.java
        assertNotNull(appClass, "Application class should exist")
        assertEquals("RunwithmeApiApplication", appClass.simpleName, "Application class name should be correct")
    }
}
