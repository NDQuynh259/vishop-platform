dependencies {
    api(project(":platform-common:common-domain"))
    api(project(":platform-common:common-events"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
}
