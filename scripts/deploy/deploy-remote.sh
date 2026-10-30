#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
ROOT=/home/ubuntu/sdpcheck
ENV_FILE=/home/ubuntu/sdpcheck-config/.env.prod
COMMIT=${1:-}
REQUIRE_LOGIN=${2:-0}
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid deployment commit.' >&2; exit 1; }
[[ "$REQUIRE_LOGIN" =~ ^[01]$ ]] || exit 1
RELEASE="$ROOT/releases/$COMMIT"
[[ ! -L "$ROOT" && ! -L "$RELEASE" && "$(realpath "$RELEASE")" == "$RELEASE" ]] || exit 1
exec 9>"$ROOT/deploy.lock"
flock -n 9 || { echo 'Another deployment is already running.' >&2; exit 1; }
cd "$RELEASE"
sha256sum --check checksums.sha256
python3 environment.py status "$REQUIRE_LOGIN"
export DEPLOY_COMMIT="$COMMIT"
compose() {
    sudo -n env "DEPLOY_COMMIT=$COMMIT" docker compose --env-file "$ENV_FILE" \
        -p sdpcheck -f "$RELEASE/compose.prod.yaml" -f "$RELEASE/compose.prebuilt.yaml" "$@"
}
compose config --quiet
python3 environment.py compose "$RELEASE/compose.prod.yaml" "$RELEASE/compose.prebuilt.yaml"
echo 'Building the runtime image from the PC-built JAR; no Gradle build on EC2.'
compose build app
echo 'Starting PostgreSQL and the application. Existing DB volume is preserved.'
compose up -d --no-build
ready=0
for attempt in $(seq 1 60); do
    if curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health 2>/dev/null \
        | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status")=="UP" else 1)' 2>/dev/null; then
        ready=1
        break
    fi
    state=$(sudo -n docker inspect --format '{{.State.Status}}' sdpcheck-app 2>/dev/null || true)
    if [[ "$state" == exited || "$state" == dead ]]; then break; fi
    sleep 3
done
if [[ "$ready" != 1 ]]; then
    echo 'Application health check failed. Deployment is NOT marked successful.' >&2
    echo 'Inspect app logs locally on EC2; do not share secrets. Previous release files and DB volume were kept.' >&2
    compose ps -a
    exit 1
fi
curl -fsSL --max-time 10 --output /dev/null http://127.0.0.1:8080/swagger-ui/index.html
curl -fsS --max-time 10 --output /dev/null http://127.0.0.1:8080/v3/api-docs
printf '%s\n' "$COMMIT" > "$ROOT/current-release"
compose ps
echo "Health and Swagger checks passed. Deployed commit: $COMMIT"
echo 'Browser HTTPS, Kakao callback and frontend integration require separate verification.'
