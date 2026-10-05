dependencies {
    api(project(":platform-common:common-domain"))
    api(project(":platform-common:common-events"))
    api(project(":platform-common:common-persistence"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.springframework.kafka:spring-kafka")
}
