dependencies {
    api(project(":libs:common-domain"))
    compileOnlyApi("jakarta.servlet:jakarta.servlet-api")
    api("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    api("org.springframework.security:spring-security-core")
    api("org.springframework.security:spring-security-web")
    api("org.springframework.security:spring-security-config")
    api("org.springframework:spring-web")

    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework.security:spring-security-test")
}
