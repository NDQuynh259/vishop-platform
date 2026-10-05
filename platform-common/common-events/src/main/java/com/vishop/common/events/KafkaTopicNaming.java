package com.vishop.common.events;

/**
 * Standard topic naming strategy for ViShop platform.
 * Follows the pattern: vishop.{domain}.{aggregate}.events
 */
public final class KafkaTopicNaming {

    public static final String PREFIX = "vishop";
    public static final String DLQ_SUFFIX = ".DLT";

    private KafkaTopicNaming() {
    }

    /**
     * Builds topic name for an aggregate type in kebab-case.
     * e.g. "Tenant" -> "vishop.tenant.events"
     */
    public static String topicFor(String aggregateType) {
        String clean = aggregateType.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
        return PREFIX + "." + clean + ".events";
    }

    /**
     * Builds dead-letter topic name for a given source topic.
     */
    public static String dlqTopicFor(String sourceTopic) {
        return sourceTopic + DLQ_SUFFIX;
    }
}
