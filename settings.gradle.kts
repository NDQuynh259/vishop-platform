rootProject.name = "core-platform"

include(
    // Platform shared libraries
    "libs:common-domain",
    "libs:common-web",
    "libs:common-security",
    "libs:common-events",
    "libs:common-persistence",
    "libs:common-observability",
    "libs:common-saga",
    "libs:common-test",

    // Microservices
    "services:api-gateway",
    "services:identity-service",
    "services:tenant-service",
    "services:audit-service",
)
