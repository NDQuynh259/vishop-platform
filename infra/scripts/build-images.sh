#!/usr/bin/env bash
# =============================================================================
# ViShop Platform - Build All Docker Images (Bash)
# =============================================================================
set -euo pipefail

TAG="${1:-latest}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "=========================================================="
echo " Building ViShop Platform Docker Images (Tag: ${TAG})"
echo " Context root: ${ROOT_DIR}"
echo "=========================================================="

services=(
  "api-gateway:infra/docker/Dockerfile.api-gateway"
  "iam-service:infra/docker/Dockerfile.iam-service"
  "audit-service:infra/docker/Dockerfile.audit-service"
  "subscription-service:infra/docker/Dockerfile.subscription-service"
)

cd "${ROOT_DIR}"

for entry in "${services[@]}"; do
  name="${entry%%:*}"
  dockerfile="${entry##*:}"
  image="vishop/${name}:${TAG}"

  echo -e "\n>>> Building ${image} using ${dockerfile}..."
  docker build -t "${image}" -f "${dockerfile}" .
  echo "✓ Successfully built ${image}"
done

echo -e "\n=========================================================="
echo " All ViShop images built successfully!"
echo "=========================================================="
