#!/usr/bin/env bash
# =============================================================================
# Generate a self-signed TLS certificate for the HHS nginx frontend.
# =============================================================================
# The nginx container mounts this directory read-only at /etc/nginx/certs.
# Re-run after changing the IP/domain; an existing certificate is kept unless
# --force is passed, so this is safe to call from deploy automation.
#
# Usage:
#   ./scripts/gen-selfsigned-cert.sh                    # idempotent
#   ./scripts/gen-selfsigned-cert.sh --force            # regenerate
#   HOST_SAN="1.2.3.4,DNS:hhs.example.com" \
#     ./scripts/gen-selfsigned-cert.sh --force         # custom SAN
# =============================================================================
set -euo pipefail

CERT_DIR="${HHS_CERT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/certs}"
DEFAULT_SAN="IP:47.112.6.180,IP:127.0.0.1,DNS:localhost"
SAN="${HOST_SAN:-$DEFAULT_SAN}"
DAYS="${CERT_DAYS:-3650}"
FORCE=false

for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

mkdir -p "$CERT_DIR"
chmod 700 "$CERT_DIR"

if [[ -f "$CERT_DIR/server.crt" && -f "$CERT_DIR/server.key" && "$FORCE" != true ]]; then
  echo "Certificate already exists in $CERT_DIR (use --force to regenerate)"
  openssl x509 -in "$CERT_DIR/server.crt" -noout -subject -dates
  exit 0
fi

echo "Generating self-signed certificate"
echo "  SAN : $SAN"
echo "  Days: $DAYS"
echo "  Dir : $CERT_DIR"

openssl req -x509 -nodes -newkey rsa:2048 \
  -days "$DAYS" \
  -keyout "$CERT_DIR/server.key" \
  -out "$CERT_DIR/server.crt" \
  -subj "/C=CN/ST=Guangdong/L=Shenzhen/O=HHS/CN=47.112.6.180" \
  -addext "subjectAltName=$SAN"

# nginx's master process runs as root in this image and reads the key at
# startup; workers only handle session traffic. 600 works today, but it breaks
# silently if the master is ever run unprivileged, so keep the key readable by
# the container user. The directory stays 700, and the key never enters git.
chmod 644 "$CERT_DIR/server.crt"
chmod 644 "$CERT_DIR/server.key"

echo "Done:"
ls -la "$CERT_DIR"