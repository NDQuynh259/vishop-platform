rootProject.name = "vishop-platform"

include(
    // Platform common modules
    "platform-common:common-domain",
    "platform-common:common-web",
    "platform-common:common-security",
    "platform-common:common-events",
    "platform-common:common-persistence",
    "platform-common:common-observability",
    "platform-common:common-test",

    // Services
    "services:identity-service",
    "services:tenant-service",
    "services:audit-service"
)
