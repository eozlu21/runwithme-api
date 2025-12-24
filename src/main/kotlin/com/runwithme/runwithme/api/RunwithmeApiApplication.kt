package com.runwithme.runwithme.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class RunwithmeApiApplication

fun main(args: Array<String>) {
    runApplication<RunwithmeApiApplication>(*args)
}
