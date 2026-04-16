#!/usr/bin/env bash
# run-radius.sh — Start FreeRADIUS in debug mode with curriculum config mounts
# Usage: ./docker/run-radius.sh [--no-debug]
#
# Mac M2 note: image is amd64-only, runs under Rosetta 2 emulation automatically.

set -euo pipefail

CONTAINER_NAME="freeradius"
IMAGE="freeradius/freeradius-server:latest"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

# Clean up any existing container with the same name
docker rm -f "$CONTAINER_NAME" 2>/dev/null || true

DEBUG_FLAG="-fxx -l stdout"
if [[ "${1:-}" == "--no-debug" ]]; then
  DEBUG_FLAG=""
fi

echo "Starting FreeRADIUS container: $CONTAINER_NAME"
echo "Mounts from: $REPO_ROOT/docker/"
echo ""

docker run -it --rm \
  --name "$CONTAINER_NAME" \
  -p 1812:1812/udp \
  -p 1813:1813/udp \
  -p 2083:2083/tcp \
  -v "$REPO_ROOT/docker/users:/etc/freeradius/users:ro" \
  -v "$REPO_ROOT/docker/clients.conf:/etc/freeradius/clients.conf:ro" \
  -v "$REPO_ROOT/docker/certs:/etc/freeradius/certs:ro" \
  -v "$REPO_ROOT/docker/radsec-site-tls:/etc/freeradius/sites-enabled/tls:ro" \
  "$IMAGE" $DEBUG_FLAG
