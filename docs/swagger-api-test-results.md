# 박재민 담당 API 검증 결과

검증일: 2026-10-28 / 참조 PR: https://github.com/SDP-1718/sdpcheck_backend/pull/36

## 실행 환경과 방법

- `.env`의 카카오 설정을 사용하되 DB는 별도 일회성 PostgreSQL 17 컨테이너 `sdpcheck-swagger-verify`로 격리했습니다.
- 백엔드: http://localhost:8080 / Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON과 UI HTML·JS·설정 요청, API 직접 HTTP 호출로 확인했습니다. 브라우저에서 Swagger 버튼을 클릭한 검증은 아닙니다.
- 테스트용 회원과 토큰은 격리 DB에만 생성했습니다. 회원가입·아이디 중복 확인 API는 직접 호출하지 않았습니다.
- `./gradlew.bat check`: 총 326개, 실패 0, 오류 0, skip 0. auth/member/device/insight 영역은 118개입니다.
- HTTP·문서 확인 30개 완료. 상세 기록: `build/swagger-http-results.json`
- 검증 스크립트: `build/swagger_verify.py` (동일한 테스트 컨테이너와 실행 중인 서버 필요)
- 생성 문서: `build/swagger-openapi.json`

## 담당 API 11개

| Method | Endpoint | 결과 |
| --- | --- | --- |
| GET | /api/v1/auth/kakao/login | 302, state HttpOnly 쿠키, 실제 카카오 로그인 페이지 200 |
| POST | /api/v1/auth/onboarding | 정상 200, 잘못된 초대 400, 중복 409 |
| POST | /api/v1/auth/access-token | 정상 200, 쿠키 누락·로그아웃 후 401 |
| POST | /api/v1/auth/refresh | 정상 200 및 쿠키 교체, 이전 토큰 401 |
| POST | /api/v1/auth/logout | 정상 204, 쿠키 없어도 204, 이후 발급 401 |
| GET | /api/v1/members/me | 정상 200, 인증 누락 401, 온보딩 전 403 |
| GET | /api/v1/members/me/notification-settings | 정상 200 |
| PATCH | /api/v1/members/me/notification-settings | 부분 수정 200, 생략 필드 유지, 빈 요청 400 |
| GET | /api/v1/members/me/attendance-insight | 출석 기록 없는 회원의 안내 문구 200 |
| POST | /api/v1/devices/push-tokens | 정상 200 및 tokenId, 공백 포함 토큰 400 |
| DELETE | /api/v1/devices/push-tokens/{tokenId} | 정상 204, 재삭제 404 |

## 카카오 로그인 검증의 범위

- 실제 설정으로 `localhost 302 → kauth.kakao.com 302 → accounts.kakao.com 200`을 확인했습니다.
- Python 기본 User-Agent에서는 accounts.kakao.com이 404를 반환했고, 브라우저 User-Agent로 재요청한 결과 200이었습니다.
- 실제 HTTP 콜백은 state 누락·인가 코드 누락 시 400을 확인했습니다.
- 자동 통합 테스트는 카카오 외부 응답을 mock으로 대체하고 신규/기존 회원, 온보딩, 쿠키 발급, 외부 API 실패 등을 검증합니다.
- **실제 계정 로그인·동의 후 받은 인가 코드의 교환과 프론트 콜백까지의 성공은 아직 미확인입니다.** 사용자 브라우저 인증이 필요합니다.
- AI API 실호출은 하지 않았습니다. 테스트 서버는 `openai.enabled=false`이며 기본 문구와 mock 기반 AI 성공/실패 테스트를 검증했습니다.

## 추가 발견

- 기존 `AuthErrorCode`의 `INVALID_OAUTH_STATE`, `KAKAO_AUTHORIZATION_CODE_MISSING` 메시지 문자열이 `???`로 저장되어 있습니다. 상태·코드는 정상이나 화면에 표시되는 오류 설명은 깨집니다. 이번 Swagger 변경에는 포함하지 않았습니다.

## 검토 코드

Swagger 관련 변경 전체: [swagger-review.md](swagger-review.md)
