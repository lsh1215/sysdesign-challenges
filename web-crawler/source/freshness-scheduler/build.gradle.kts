plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val lombokVersion: String by project

dependencies {
    implementation(project(":common"))
    // Same cycle-avoidance rationale as :crawler-worker — shared-infra picked up at runtime only.
    runtimeOnly(project(":crawler-shared-infra"))

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
