dependencies {
    api(project(":platform-common:common-domain"))
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
}
