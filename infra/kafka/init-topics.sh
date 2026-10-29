#!/usr/bin/env bash
# =============================================================================
# ViShop Platform - Kafka Topics Initialization Script
# =============================================================================
set -e

KAFKA_BROKER="${KAFKA_BROKER:-kafka:9092}"

echo "Waiting for Kafka to be available at ${KAFKA_BROKER}..."
cub kafka-ready -b "${KAFKA_BROKER}" 1 30 || true

TOPICS=(
  "vishop.tenant.events:3:1"
  "vishop.user.events:3:1"
  "vishop.audit.events:3:1"
  "vishop.billing.events:3:1"
)

echo "Creating core Kafka topics..."
for topic_spec in "${TOPICS[@]}"; do
  IFS=':' read -r topic partitions replication <<< "$topic_spec"
  echo "Creating topic: $topic (partitions: $partitions, replication: $replication)"
  kafka-topics --bootstrap-server "${KAFKA_BROKER}" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$replication" || true
done

echo "Kafka topics initialized successfully."
