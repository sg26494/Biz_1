#!/usr/bin/env sh
# Run on the VM by the CI deploy job:  IMAGE_TAG=<sha> GH_OWNER=<owner> ./deploy.sh
# Flyway migrations run when the API starts. Migrations must be backward compatible (expand, then contract)
# so rolling back is just re-running this script with the previous IMAGE_TAG.
set -eu
cd "$(dirname "$0")"
export IMAGE_TAG GH_OWNER
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --remove-orphans
echo "$IMAGE_TAG" > .last_deployed_tag
# Smoke test
for i in $(seq 1 30); do
  if docker compose -f docker-compose.prod.yml exec -T api wget -qO- http://localhost:8080/health | grep -q UP; then
    echo "Healthy: $IMAGE_TAG"; exit 0
  fi
  sleep 5
done
echo "Health check failed for $IMAGE_TAG" >&2
exit 1
