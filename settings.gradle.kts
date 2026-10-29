rootProject.name = "core-platform"

include(
    // Platform shared libraries
    "libs:common-domain",
    "libs:common-web",
    "libs:common-security",
    "libs:common-cache",
    "libs:common-events",
    "libs:common-persistence",
    "libs:common-observability",
    "libs:common-saga",
    "libs:common-test",

    // Microservices
    "services:iam-service",
    "services:api-gateway",
    "services:audit-service",
    "services:subscription-service",
)
