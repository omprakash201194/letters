#!/usr/bin/env bash
# Build and push both Docker images to the local registry.
# Usage: ./build-and-push.sh [version]   (default: 1.0.0)
# Requires: VITE_FIREBASE_API_KEY, VITE_FIREBASE_AUTH_DOMAIN, VITE_FIREBASE_PROJECT_ID
#           to be set in the environment before running.

set -euo pipefail

VERSION="${1:-1.0.0}"
REGISTRY="localhost:30500/homelab"

echo "=== Building Letters v${VERSION} ==="

# ── Backend ────────────────────────────────────────────────────────────────
echo ""
echo "--- Building Spring Boot backend ---"
cd backend
mvn package -DskipTests -q
docker build -t "${REGISTRY}/letters-backend:${VERSION}" .
docker push "${REGISTRY}/letters-backend:${VERSION}"
echo "✓ Backend pushed: ${REGISTRY}/letters-backend:${VERSION}"
cd ..

# ── Frontend ───────────────────────────────────────────────────────────────
echo ""
echo "--- Building React frontend ---"
cd frontend

if [ -z "${VITE_FIREBASE_API_KEY:-}" ]; then
  echo "ERROR: VITE_FIREBASE_API_KEY is not set."
  echo "Export your Firebase env vars before running this script:"
  echo "  export VITE_FIREBASE_API_KEY=..."
  echo "  export VITE_FIREBASE_AUTH_DOMAIN=..."
  echo "  export VITE_FIREBASE_PROJECT_ID=..."
  exit 1
fi

npm ci --silent
npm run build
docker build -t "${REGISTRY}/letters-frontend:${VERSION}" .
docker push "${REGISTRY}/letters-frontend:${VERSION}"
echo "✓ Frontend pushed: ${REGISTRY}/letters-frontend:${VERSION}"
cd ..

echo ""
echo "=== Done. Update image tags in k8s/ to ${VERSION}, then: ==="
echo "    kubectl apply -f k8s/"
