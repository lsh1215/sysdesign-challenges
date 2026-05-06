plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val lombokVersion: String by project

dependencies {
    implementation(project(":common"))
    // Phase 1 stated worker depends on shared-infra. To break the Gradle cycle introduced when
    // shared-infra implements worker.domain ports, we keep that dependency at runtimeOnly scope.
    // At runtime the Spring Boot launcher loads shared-infra adapters (via @Import in worker's
    // main app, or component scan once Phase 5 wires them).
    runtimeOnly(project(":crawler-shared-infra"))

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
