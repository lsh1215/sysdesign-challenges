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
    // shared-infra contributes adapter beans (RestFrontierClient, WebClientHtmlSource, Jsoup
    // parsers, MinIO/JPA persistence) via Spring Boot AutoConfiguration.imports. We keep
    // production scope at runtimeOnly so that worker:compileJava does NOT depend on
    // shared-infra:compileJava — that's what breaks the Gradle task cycle (shared-infra still
    // declares api(project(":crawler-worker")) for the domain port types). Tests reference the
    // adapter classes directly (CrawlPipelineIT, GiantPageDownloadIT, PluggableExtractorTest)
    // so they need testImplementation. compileTestJava → shared-infra:compileJava →
    // worker:compileJava is acyclic since the compile chain doesn't loop back into compileTestJava.
    runtimeOnly(project(":crawler-shared-infra"))
    testImplementation(project(":crawler-shared-infra"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-core")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:minio")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
    testImplementation("io.minio:minio:8.5.11")
}
