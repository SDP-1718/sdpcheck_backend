# Windows PC에서 EC2로 배포하기

## 제공 파일

`scripts/deploy` 폴더에서 파일을 더블클릭합니다.

| 파일 | 동작 |
| --- | --- |
| `Configure.cmd` | 이 PC의 키 경로·EC2 주소·배포 브랜치 설정 |
| `Environment.cmd` | EC2의 카카오·콜백·CORS 허용 주소·쿠키·OpenAI 설정 수정. 비밀 입력은 가림 |
| `OpenAI.cmd` | OpenAI 활성화·모델·키만 입력해 EC2에 저장. API 호출·앱 실행은 하지 않음 |
| `Check.cmd` | SSH·Docker·서버 환경파일 사전 점검. 앱 실행·배포는 하지 않음 |
| `HTTPS.cmd` | sdpcheck.duckdns.org의 DNS 확인 → 별도 Caddy 설치 → HTTPS Swagger 확인 |
| `Deploy.cmd` | 원격 브랜치 조회 → PC 검사·빌드 → EC2 전송 → 실행 → health·Swagger 확인 |

실행에 필요한 설정은 `deploy.settings.local.json`에 저장하며 Git에서 제외합니다.
원본 예시는 `deploy.settings.example.json`입니다. 비밀값은 이 JSON에 넣지 않습니다.
`ExecutionPolicy Bypass`는 해당 PowerShell 프로세스에만 적용하며 PC의 정책을 변경하지 않습니다.

## 내일 할 일

1. 필요한 PR과 오늘의 배포·prod Swagger 설정을 최종 배포 브랜치에 merge하고 CI를 확인합니다.
2. EC2 `sdpcheck-server`를 시작합니다.
3. PC에서 Docker Desktop을 켭니다. 기본 설정의 `check` 실행에 Testcontainers가 사용됩니다.
4. `Deploy.cmd`를 실행합니다. 기본 배포 대상은 **origin/main의 최신 커밋**입니다.
5. 성공 메시지와 Swagger 주소를 확인합니다. 실제 카카오 로그인·프론트 연동은 따로 점검합니다.

로컬 현재 브랜치를 checkout·merge·reset하지 않습니다. 원격 커밋을 별도 `build/deploy` 폴더에
풀어 검사·빌드하므로 로컬 `.env`와 미커밋 변경은 배포 파일에 들어가지 않습니다.
아직 merge되지 않은 변경은 배포되지 않습니다. Dockerfile의 테스트 생략에 의존하지 않고,
기본적으로 PC에서 `check bootJar`를 실행합니다. Java 21·Git·OpenSSH가 필요합니다.

## EC2 IP 변경

현재 AWS CLI가 없는 PC는 마지막 주소로 접속을 시도하고, 연결되지 않으면 현재 공인 IP를
한 번 입력받습니다. AWS 콘솔의 퍼블릭 IPv4 주소를 입력합니다. 키·포트 오류도 점검합니다.

완전한 주소 자동 조회가 필요하면 AWS CLI를 설치하고 `ec2:DescribeInstances`를 사용할 수 있는
프로파일을 별도로 인증한 뒤 `AwsProfile`, `InstanceId`, `Region`을 설정합니다.
AWS 비밀키를 스크립트나 JSON에 넣지 않습니다. SSO 프로파일은 필요 시 먼저 로그인합니다.

`TrustedHostAlias`에는 수동 SSH로 서버 지문을 확인하고 연결했던 주소를 넣습니다.
같은 EC2의 IP만 바뀌었을 때 기존에 신뢰한 호스트 키로 검증합니다. 키가 다르면 중단하며
호스트 키 검증을 끄거나 `known_hosts`를 자동 삭제하지 않습니다.

## 서버 환경변수

- 실제 값: `/home/ubuntu/sdpcheck-config/.env.prod` (권한 600, 폴더 700)
- 변경 안내: `Environment.cmd`
- 값 없는 항목 목록: 저장소의 `.env.prod.example`
- 전달: Compose의 `--env-file`과 app/postgres의 `environment` 설정

DB·JWT는 초기 준비한 운영값을 유지합니다. DB 최초 생성 후 `.env.prod`의 DB 비밀번호만
바꿔도 기존 DB 비밀번호가 바뀌지는 않습니다. 별도 DB 변경 없이 값을 교체하지 않습니다.
환경파일 변경은 실행 중인 컨테이너에 자동 반영되지 않으며 앱 재생성이 필요합니다.
키·환경파일 내용을 채팅에 붙이거나 `docker compose config` 전체 결과를 공유하지 않습니다.

백엔드·프론트 주소가 미정인 동안 `RequireLoginUrls=false`로 서버·Swagger 점검을 준비할 수 있습니다.
이는 로그인 연동 완료를 뜻하지 않습니다. prod 쿠키는 Secure이므로 브라우저 로그인에는
HTTPS가 필요합니다. 주소 확정 후 카카오 Redirect URI 등록, 프론트 콜백, CORS, 쿠키 정책을
맞추고 실제 브라우저에서 검증합니다. URL 입력만으로 HTTPS·DNS·카카오 관리자 등록을 처리하지 않습니다.
실제 OpenAI 사용이 합의되기 전에는 `OPENAI_ENABLED=false`를 유지합니다.

### 프론트 주소를 받은 뒤 CORS 설정

`Environment.cmd`의 `Frontend CORS origin(s)` 질문에 프론트의 기본 주소를 입력합니다.

```text
프론트 기본 주소: https://app.example.com
CORS_ALLOWED_ORIGINS: https://app.example.com
FRONTEND_AUTH_CALLBACK_URL: https://app.example.com/auth/callback
```

CORS에는 `/auth/callback` 같은 경로를 붙이지 않습니다. 여러 주소는 쉼표로 구분합니다.
HTTP 개발 주소도 명시적으로 입력할 수 있지만 운영 프론트에는 HTTPS 주소를 사용합니다.
빈 설정은 다른 출처의 접근을 허용하지 않습니다. 와일드카드와 경로·쿼리 포함 주소는 거부합니다.
저장한 뒤 `Deploy.cmd`로 재배포하면 해당 주소에서 쿠키·Authorization 헤더를 사용하는
요청과 OPTIONS 사전 요청을 허용합니다. 실제 API에는 기존 로그인·권한 검사가 적용됩니다.

프론트의 쿠키 요청에는 `credentials: 'include'`(fetch) 또는 `withCredentials: true`(Axios)가
필요합니다. 서로 다른 사이트 간 쿠키는 SameSite 정책과 브라우저의 쿠키 제한에도 영향을 받으므로,
주소 구성에 맞춰 쿠키 설정을 확인합니다. CORS 허용만으로 이 설정까지 자동 변경하지 않습니다.

## OpenAI 키를 발급받은 뒤

1. 팀에서 비용 처리 방식과 사용할 API 프로젝트·Responses API 지원 모델을 확정합니다.
2. EC2를 켜고 PC에서 `OpenAI.cmd`를 실행합니다.
3. 활성화 질문에 `true`, 모델 질문에 확정한 모델 ID, 키 질문에 발급한 API 키를 입력합니다.
   키 입력은 가려지며 서버 환경파일에만 저장됩니다. Enter는 기존 값을 유지합니다.
4. `Check.cmd`에서 OpenAI enabled와 key/model configured가 모두 True인지 확인합니다.
   이 결과는 설정 존재 여부이며 키 유효성·잔액·모델 접근 권한을 실제로 검증한 결과가 아닙니다.
5. `Deploy.cmd`로 배포합니다. 이미 배포했다면 재실행해 변경된 환경변수를 반영합니다.
6. 출석 기록이 있는 테스트 회원으로 인사이트 API를 호출하고 AI 결과와 OpenAI 프로젝트 사용량을
   함께 확인합니다. 실제 호출에는 비용이 발생할 수 있습니다.

출석 기록이 없으면 키가 있어도 AI를 호출하지 않습니다. 호출 실패·사용 불가·처리할 수 없는
AI 응답이면 기본 출석 요약을 반환하므로 **HTTP 200만으로 실제 AI 성공을 판단하지 않습니다.**
이 도구 자체는 OpenAI에 요청하지 않으며, 현재까지 실제 키를 이용한 호출은 검증하지 않았습니다.
호출할 때마다 생성 요청을 보내는 현재 구현에는 결과 캐시가 없으므로 반복 테스트 횟수를 관리합니다.

## EC2 실행과 데이터

### DuckDNS HTTPS 설정

EC2 보안 그룹에서 TCP 80·443을 인터넷에 허용하고 DuckDNS의 current ip를 EC2 공인 IP로
설정한 뒤, 백엔드를 먼저 배포하고 `HTTPS.cmd`를 더블클릭합니다. 기존 PC SSH 설정을
재사용합니다. Caddy는 서버의 `~/sdpcheck-https`에 별도 Compose 프로젝트로 실행되며
인증서를 영구 볼륨에 보관하고 자동 갱신합니다. 운영 앱과 DB는 재시작하지 않습니다.
80·443이 다른 서비스에서 사용 중이거나 기존 폴더가 도구 관리 대상이 아니면 중단합니다.
HTTPS 확인이 실패하면 포트·DNS·백엔드 상태를 확인한 뒤 다시 실행합니다.
EC2 공인 IP가 바뀌면 DuckDNS IP도 갱신해야 합니다. 카카오 콘솔 URI 등록과 서버의
`KAKAO_REDIRECT_URI`, 프론트 콜백·CORS 설정은 별도로 진행합니다.

EC2에서는 PC에서 만든 app.jar를 포함하는 **실행용 이미지**만 빌드합니다.
Compose 프로젝트 이름은 항상 `sdpcheck`로 고정해 DB 볼륨을 재배포마다 재사용합니다.
운영 데이터는 `sdpcheck_postgres_prod_data`에 저장됩니다. 배포 과정에서 `down -v`,
`docker prune`, DB 초기화나 마이그레이션 강제 수정은 실행하지 않습니다.

앱은 1GB 인스턴스를 고려한 Java 메모리 상한을 사용합니다. 실제 서비스 부하에서 충분한지는
실행 후 관찰해야 합니다. PostgreSQL은 shared_buffers=64MB, max_connections=30으로 시작합니다.
같은 EC2의 8080 포트나 `sdpcheck-app`/`sdpcheck-postgres` 이름이 다른 서비스에서 사용 중이면
먼저 용도를 확인합니다. Docker Compose의 restart 정책 때문에 EC2 재시작 시 앱도 켜질 수 있습니다.

## 실패 시

health 또는 Swagger 검사 실패는 배포 실패로 표시합니다. 비밀값이 섞일 수 있는 앱 로그를
자동 출력하지 않습니다. 이전 이미지·release 파일·DB 데이터는 보존합니다.
배포 커밋은 `/home/ubuntu/sdpcheck/current-release`에 성공한 뒤 기록합니다.
Flyway가 적용한 DB 변경은 자동으로 되돌리지 않습니다. 이전 버전 재배포 전 DB 호환성을 확인합니다.
현재 제공 파일 검증과 실제 merge된 코드의 운영 배포 성공은 별도로 구분해야 합니다.
