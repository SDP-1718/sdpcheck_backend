#!/usr/bin/env bash
set -euo pipefail
command -v docker >/dev/null
command -v ss >/dev/null
docker compose version >/dev/null
curl --fail --silent --output /dev/null --max-time 10 http://127.0.0.1:8080/swagger-ui/index.html || {
    echo 'Backend Swagger is not available on port 8080. Run Deploy.cmd first.' >&2
    exit 1
}
target="$HOME/sdpcheck-https"
if [ -e "$target" ] && [ ! -f "$target/.sdpcheck-managed" ]; then
    echo 'Existing unmanaged sdpcheck-https directory found. Stopped without changing it.' >&2
    exit 1
fi
managed_running=''
if [ -f "$target/compose.yaml" ]; then
    managed_running=$(docker compose -p sdpcheck-https -f "$target/compose.yaml" ps --status running -q caddy)
fi
if [ -z "$managed_running" ] && ss -H -ltn | awk '{print $4}' | grep -Eq ':(80|443)$'; then
    echo 'Port 80 or 443 is already in use. Stopped without changing that service.' >&2
    exit 1
fi
mkdir -p "$target"
touch "$target/.sdpcheck-managed"
cd "$target"
cat > Caddyfile <<'EOF'
sdpcheck.duckdns.org {
    reverse_proxy 127.0.0.1:8080
}
EOF
cat > compose.yaml <<'EOF'
services:
  caddy:
    image: caddy:2
    restart: unless-stopped
    network_mode: host
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
volumes:
  caddy_data:
  caddy_config:
EOF
docker compose -p sdpcheck-https run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose -p sdpcheck-https up -d
echo 'Caddy started. Certificates are persisted and renewed automatically.'
