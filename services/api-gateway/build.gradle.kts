plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-observability"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
