#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
SOURCE=${1:?Pass the directory containing auto-deploy.py}
[[ "$(id -un)" == ubuntu ]] || { echo 'Run as ubuntu.'; exit 1; }
CONFIG=/home/ubuntu/sdpcheck-config
[[ ! -L "$CONFIG" && ! -L "$CONFIG/tools" ]] || exit 1
test -f "$CONFIG/.env.prod"
test -f /home/ubuntu/sdpcheck/current-release
sudo -n docker inspect sdpcheck-postgres >/dev/null
install -d -m 700 "$CONFIG/tools"
if [[ "$(realpath "$SOURCE/auto-deploy.py")" != "$CONFIG/tools/auto-deploy.py" ]]; then
    install -m 700 "$SOURCE/auto-deploy.py" "$CONFIG/tools/auto-deploy.py"
else
    chmod 700 "$CONFIG/tools/auto-deploy.py"
fi
# Dry run validates public GitHub access without touching the app or DB.
python3 "$CONFIG/tools/auto-deploy.py" --dry-run
sudo -n tee /etc/systemd/system/sdpcheck-auto-deploy.service >/dev/null <<'UNIT'
[Unit]
Description=Apply tested SDPCheck production releases
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=oneshot
User=ubuntu
Group=ubuntu
WorkingDirectory=/home/ubuntu/sdpcheck
ExecStart=/usr/bin/python3 /home/ubuntu/sdpcheck-config/tools/auto-deploy.py
UMask=0077
TimeoutStartSec=20min
Nice=10
UNIT
sudo -n tee /etc/systemd/system/sdpcheck-auto-deploy.timer >/dev/null <<'UNIT'
[Unit]
Description=Check SDPCheck production releases every two minutes

[Timer]
OnBootSec=60s
OnUnitInactiveSec=120s
RandomizedDelaySec=10s
Unit=sdpcheck-auto-deploy.service

[Install]
WantedBy=timers.target
UNIT
sudo -n systemctl daemon-reload
sudo -n systemctl enable --now sdpcheck-auto-deploy.timer
sudo -n systemctl list-timers sdpcheck-auto-deploy.timer --no-pager
