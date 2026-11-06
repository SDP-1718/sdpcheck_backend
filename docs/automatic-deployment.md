# 운영 자동 배포

main push(일반적으로 PR merge) → CI 전체 검사 및 bootJar → GitHub에서 Docker 이미지 빌드 → `production-<전체 SHA>` Release 게시 → EC2가 2분마다 확인하여 적용합니다.

공개 저장소의 Release를 사용하는 pull 방식입니다. GitHub에 운영 환경 변수나 SSH 키를 복사하지 않으며, GitHub 실행기용 SSH 방화벽 규칙도 필요 없습니다. 이미지에는 빌드한 JAR만 포함합니다. 공개 저장소이므로 이미지와 배포 파일도 공개됩니다. 저장소를 비공개로 전환할 때는 이 배포 방식을 함께 변경해야 합니다.

## 최초 설치 (Ubuntu EC2)

기존 수동 배포가 완료되어 `current-release`, 운영 env, Docker 컨테이너가 존재해야 합니다. main의 `scripts/deploy/auto-deploy.py`와 `install-auto-deploy.sh`를 서버에 복사한 뒤 ubuntu 사용자로 실행합니다.

```bash
bash /home/ubuntu/sdpcheck-config/tools/install-auto-deploy.sh /home/ubuntu/sdpcheck-config/tools
```

## 동작과 복구

- draft 상태에서 모든 이미지·런타임·체크섬을 업로드한 후 Release를 공개합니다. PR이나 develop push는 배포하지 않습니다.
- EC2는 현재 main의 SHA와 일치하는 Release만 적용합니다. 이미 적용했거나 실패한 SHA는 자동으로 재실행하지 않습니다.
- GitHub 제공 asset digest와 체크섬을 모두 검증하고, 런타임 압축 파일의 경로·크기·파일 종류를 검사합니다.
- 배포 전에 기존 DB를 custom dump로 백업하고 pg_restore로 파일을 검증합니다. 백업은 `/home/ubuntu/sdpcheck-config/backups`에 권한 600으로 보관합니다.
- 이미지를 로드한 뒤 앱 컨테이너만 교체합니다. PostgreSQL 컨테이너, 데이터 볼륨, Caddy, 운영 환경 변수는 유지합니다. DB 데이터 초기화는 실행하지 않습니다.
- 새 이미지의 health·Swagger 검사 실패 시 직전 앱 이미지로 복구하며 workflow 성공을 운영 정상 판정으로 취급하지 않습니다. 새 버전 시작 시 Flyway migration은 실행될 수 있습니다. DB를 자동으로 되돌리지는 않으므로 migration은 직전 버전과 호환되게 작성해야 합니다. 복구 이미지도 실행되지 않으면 수동 조치가 필요합니다.
- 현재와 직전 Release·이미지를 보관합니다. DB 백업은 자동 삭제하지 않으므로 디스크 상태에 맞춰 별도 보관/정리합니다. 디스크 여유가 부족하면 앱 교체 전 중단합니다.
- 배포 중 앱 교체에 따른 짧은 중단이 있습니다. systemd timer는 서버 재시작 후에도 동작합니다.

## 확인 및 제어

```bash
# 실제 운영 배포 결과
sudo journalctl -u sdpcheck-auto-deploy.service -n 80 --no-pager
cat /home/ubuntu/sdpcheck/current-release
sudo systemctl list-timers sdpcheck-auto-deploy.timer

# 즉시 확인/배포
sudo systemctl start sdpcheck-auto-deploy.service

# 자동 배포 중지 (앱과 DB는 계속 실행)
sudo systemctl stop sdpcheck-auto-deploy.timer

# 다시 켜기
sudo systemctl start sdpcheck-auto-deploy.timer

# 실패 원인을 해결한 뒤 같은 Release 수동 재시도
python3 /home/ubuntu/sdpcheck-config/tools/auto-deploy.py --retry
```

GitHub Actions의 `production-release` 성공은 이미지 게시 성공을 뜻합니다. 최종 배포 결과는 서버 journal과 `current-release`로 확인합니다. 수동 Deploy.cmd도 같은 배포 잠금을 사용하므로 동시에 앱을 교체하지 않습니다.
