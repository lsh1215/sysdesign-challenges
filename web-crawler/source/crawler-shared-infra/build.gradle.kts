plugins {
    `java-library`
    id("io.spring.dependency-management")
}

val springBootVersion: String by project
val lombokVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        mavenBom("org.testcontainers:testcontainers-bom:1.19.7")
    }
}

dependencies {
    api(project(":common"))
    // shared-infra implements outbound ports declared in crawler-worker.domain. Keep this at
    // api scope so consumers compile against the same port types. Cycle is broken on the worker
    // side: worker declares shared-infra at runtimeOnly + testImplementation only, so
    // worker:compileJava does NOT depend on shared-infra:compileJava.
    api(project(":crawler-worker"))

    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    api("org.springframework.boot:spring-boot-starter-data-redis")

    api("io.minio:minio:8.5.11")

    api("org.springframework.boot:spring-boot-starter-webflux")

    api("org.jsoup:jsoup:1.17.2")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:minio")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
}
