# 푸시 토큰 등록·삭제

`POST /api/v1/devices/push-tokens`는 MEMBER 또는 ADMIN의 Access Token으로
인증한 회원에게 요청 본문의 `token`을 연결한다.

- 토큰은 필수이며 최대 512자이고 공백을 포함할 수 없다.
- 처음 등록하는 토큰은 새 행을 생성한다.
- 동일 회원의 동일 토큰 재등록은 같은 `tokenId`를 반환한다.
- 다른 회원에게 등록된 토큰은 현재 회원으로 연결을 변경한다. ID는 유지한다.
- 한 회원이 여러 기기의 토큰을 등록할 수 있다.
- PostgreSQL `ON CONFLICT`로 중복 등록 및 계정 전환을 원자적으로 처리한다.

계정 전환 정책은 기존 API 명세의 비즈니스 규칙에도 반영해야 한다. 프론트는
FCM에서 획득한 현재 기기의 토큰을 전달하고, 로그아웃 시 해당 토큰을 해제하는
흐름이 필요하다. 이 API는 실제 FCM 유효성을 조회하거나 알림을 발송하지 않는다.

## 응답

- 200: `PUSH_TOKEN_REGISTER_SUCCESS`, `result: {"tokenId": 37}`
- 400: `VALIDATION_ERROR` (입력 검증 실패, 잘못된 JSON, 본문 누락)
- 401: `AUTHENTICATION_REQUIRED` (Access Token 누락)
- 401: `INVALID_TOKEN` (토큰 만료, 검증 실패 또는 인증 대상 회원 없음)
- 404: `MEMBER_NOT_FOUND` (인증 이후 서비스의 회원 존재 확인 실패)
- 403: `ACCESS_DENIED` (허용된 회원 권한이 없음)

입력 검증은 `@Valid`와 기존 `GlobalExceptionHandler`를 사용하고, 인증 오류는
기존 `SecurityExceptionHandler`를 사용한다. API별 전용 Handler는 두지 않는다.
Notion 명세의 오류 응답도 이 공통 정책에 맞춰 수정해야 한다.

## 삭제

`DELETE /api/v1/devices/push-tokens/{tokenId}`는 MEMBER 또는 ADMIN의 Access
Token이 필요하다. `tokenId`는 등록 응답의 양의 정수 ID이며 요청 본문은 없다.

- 현재 회원이 소유한 해당 토큰 행만 삭제한다. 다른 기기의 토큰은 유지한다.
- HTTP 200, `PUSH_TOKEN_DELETE_SUCCESS`, `푸시 토큰 삭제에 성공했습니다.`,
  `result: null`을 반환한다.
- 없는 토큰, 다른 회원의 토큰, 이미 삭제된 토큰은 모두 HTTP 404
  `PUSH_TOKEN_NOT_FOUND`를 반환한다.
- 유효하지 않은 ID는 HTTP 400 `VALIDATION_ERROR`를 반환한다.
- 인증·회원 오류는 등록 API와 동일한 공통 정책을 따른다.
- ID와 회원 ID를 한 DELETE 쿼리에 함께 적용하여 계정 전환과 삭제가 동시에
  일어나더라도 다른 회원의 토큰을 삭제하지 않도록 한다.

이 API는 우리 DB의 연결만 제거하며 Firebase의 토큰 자체를 폐기하지 않는다.
로그아웃 API는 본문 없이 호출하며 푸시 토큰 삭제를 자동으로 수행하지 않는다.
프론트는 인증 가능한 상태에서 별도 삭제 API로 DB 연결을 해제할 수 있다.
인증 토큰이 없어도 로그아웃은 진행하며, 현재 브라우저의 푸시 구독 해제는
프론트에서 Firebase `deleteToken()`을 사용해 처리한다.

## 마이그레이션 및 검증

`V10__create_push_tokens.sql`은 토큰 unique 제약, 회원 FK, 회원별 조회 인덱스를
추가한다. 아직 병합되지 않은 카카오 인증 브랜치의 V9와 번호가 겹치지 않도록
V10을 사용한다. 공유 DB에는 V9 및 다른 선행 마이그레이션을 먼저 반영해야 한다.

```powershell
.\gradlew.bat test --tests '*PushTokenControllerTest' --tests '*PushTokenServiceTest' --tests '*SecurityConfigTest'
.\gradlew.bat test --tests '*PushTokenIntegrationTest'
```

통합 테스트는 실행 중인 Docker가 필요하며 DB 스키마, 중복 등록, 계정 전환,
동시 등록, 다중 기기 등록, 토큰 삭제·소유권 및 DB 제약을 검증한다.
