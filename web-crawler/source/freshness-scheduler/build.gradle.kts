plugins {
    java
    id("org.springframework.boot")
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
    implementation(project(":common"))
    // Domain port types (VisitedUrlRepository, FrontierClient, VisitedUrl) live in crawler-worker.
    // freshness-scheduler → crawler-worker is acyclic (worker does not depend on scheduler).
    implementation(project(":crawler-worker"))
    // shared-infra contributes adapter beans (VisitedUrlRepositoryImpl, RestFrontierClient, JPA,
    // Flyway, MinIO, Redis) via Spring Boot AutoConfiguration.imports. runtimeOnly avoids adding
    // shared-infra:compileJava to our compile classpath — it transitively brings crawler-worker
    // which we already declare directly above.
    runtimeOnly(project(":crawler-shared-infra"))
    testImplementation(project(":crawler-shared-infra"))

    implementation("org.springframework.boot:spring-boot-starter")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
}
