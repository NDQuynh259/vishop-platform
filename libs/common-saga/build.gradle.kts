dependencies {
    api(project(":libs:common-domain"))
    api(project(":libs:common-events"))
    api(project(":libs:common-persistence"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.springframework.kafka:spring-kafka")
}
