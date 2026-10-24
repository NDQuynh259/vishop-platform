dependencies {
    api(project(":libs:common-domain"))
    api(project(":libs:common-security"))
    api("org.springframework.kafka:spring-kafka")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
