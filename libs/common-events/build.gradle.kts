dependencies {
    api(project(":platform-common:common-domain"))
    api(project(":platform-common:common-security"))
    api("org.springframework.kafka:spring-kafka")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
