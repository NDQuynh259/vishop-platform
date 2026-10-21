dependencies {
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-registry-prometheus")
    api("io.micrometer:micrometer-tracing-bridge-otel")
    api("io.opentelemetry:opentelemetry-exporter-otlp")
    api("net.logstash.logback:logstash-logback-encoder:7.4")

    compileOnlyApi("org.springframework:spring-web")
    compileOnlyApi("jakarta.servlet:jakarta.servlet-api")
    api(project(":platform-common:common-domain"))
    compileOnly(project(":platform-common:common-security"))

    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework:spring-web")
    testImplementation(project(":platform-common:common-domain"))
    testImplementation(project(":platform-common:common-security"))
}
