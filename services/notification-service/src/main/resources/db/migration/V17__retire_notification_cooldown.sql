-- Cooldown is no longer part of notification dispatch. Retain all historical data.
-- No runtime entity or repository reads/writes this archive.
ALTER TABLE notification_deduplication RENAME TO notification_deduplication_archive;
COMMENT ON TABLE notification_deduplication_archive IS
    'Historical cooldown state retained by V17. No longer used by notification dispatch.';
