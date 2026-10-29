dependencies {
    api("com.fasterxml.jackson.core:jackson-databind")
    api(project(":libs:common-domain"))
    compileOnlyApi("jakarta.servlet:jakarta.servlet-api")
    api("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    api("org.springframework.security:spring-security-core")
    api("org.springframework.security:spring-security-web")
    api("org.springframework.security:spring-security-config")
    api("org.springframework:spring-web")
    api("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework.security:spring-security-test")
}
