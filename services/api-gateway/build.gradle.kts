plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-common:common-domain"))
    implementation(project(":platform-common:common-observability"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
