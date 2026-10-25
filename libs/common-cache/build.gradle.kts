dependencies {
    api("org.springframework.data:spring-data-redis")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("io.projectreactor:reactor-core")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("io.lettuce:lettuce-core")
    testImplementation("com.github.codemonstur:embedded-redis:1.4.3")
}
