package com.runwithme.runwithme.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("RunWithMe API")
                    .version("1.0.0")
                    .description("REST API for RunWithMe application"),
            ).servers(listOf(Server().url("http://localhost:8080").description("Local Dev")))
}
