plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-security"))
    implementation(project(":libs:common-observability"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.6.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("io.projectreactor:reactor-test")
}
