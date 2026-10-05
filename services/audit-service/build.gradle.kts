plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-common:common-web"))
    implementation(project(":platform-common:common-security"))
    implementation(project(":platform-common:common-events"))
    implementation(project(":platform-common:common-persistence"))
    implementation(project(":platform-common:common-observability"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
}
