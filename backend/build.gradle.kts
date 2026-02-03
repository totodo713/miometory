import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    jacoco
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("org.owasp.dependencycheck") version "12.1.1"
}

group = "com.worklog"
version = "0.0.1-SNAPSHOT"
description = "WorkLog Backend Service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    // Shibboleth repository for OpenSAML (required for SAML2 support)
    maven {
        url = uri("https://build.shibboleth.net/maven/releases/")
    }
}

dependencies {
    // Production dependencies
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.security:spring-security-saml2-service-provider")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    runtimeOnly("org.postgresql:postgresql")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers for PostgreSQL integration tests
    testImplementation("org.testcontainers:testcontainers:1.21.1")
    testImplementation("org.testcontainers:postgresql:1.21.1")
    testImplementation("org.testcontainers:junit-jupiter:1.21.1")

    // Database Rider for database testing with datasets
    testImplementation("com.github.database-rider:rider-spring:1.44.0")
    testImplementation("com.github.database-rider:rider-junit5:1.44.0")

    // Instancio for test data generation
    testImplementation("org.instancio:instancio-junit:5.4.0")

    // Kotlin test support
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.14.2")
}

tasks.withType<Test> {
    useJUnitPlatform {
        // Exclude performance tests by default (they are slow and may be flaky in CI)
        // Run performance tests with: ./gradlew test -PincludeTags=performance
        if (!project.hasProperty("includeTags")) {
            excludeTags("performance")
        } else {
            includeTags(project.property("includeTags") as String)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// JaCoCo configuration for code coverage
jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
	
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
    }
	
    // Exclude auto-generated and framework classes
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "**/BackendApplication*",
                        "**/config/**",
                        "**/infrastructure/config/**",
                    )
                }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
	
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
		
        rule {
            element = "PACKAGE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            includes = listOf("com.worklog.domain.*")
        }
    }
}

// ktlint configuration
ktlint {
    version.set("1.5.0")
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}
