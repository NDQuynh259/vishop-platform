-- Cooldown and per-attempt history are not used by the current notification flow.
-- Delivery status, attempt counters and provider IDs remain in notification_deliveries.
-- Technical errors and individual send attempts belong in centralized logging.
DROP TABLE notification_deduplication_archive;
DROP TABLE notification_delivery_attempts;
