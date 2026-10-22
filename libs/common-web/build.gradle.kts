dependencies {
    api(project(":libs:common-domain"))
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework:spring-tx")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework:spring-tx")
}
