plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    kotlin("plugin.jpa") version "2.0.20"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.diffplug.spotless") version "6.25.0"
}

spotless {
    kotlin {
        target("**/*.kt")
        ktlint("1.3.1").editorConfigOverride(
            mapOf(
                "ktlint_code_style" to "official",
                "indent_size" to "4",
                "max_line_length" to "120",
                "end_of_line" to "lf",
                "charset" to "utf-8",
                "insert_final_newline" to "true",
            ),
        )
        // comment out if LICENSE_HEADER.txt doesn't exist yet
        // licenseHeaderFile(rootProject.file("LICENSE_HEADER.txt"))
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint("1.3.1")
    }
}

tasks.named("check").configure { dependsOn("spotlessCheck") }

group = "com.runwithme"
version = "0.1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework:spring-messaging")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(kotlin("reflect"))
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Load .env automatically into Spring Environment for local dev
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    runtimeOnly("org.postgresql:postgresql:42.7.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> { useJUnitPlatform() }

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
