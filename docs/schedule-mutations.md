# 관리자 일반 일정 수정·삭제

- `PATCH /api/v1/admin/schedules/{scheduleId}`: `version` 필수. `title`, `startsAt`, `location` 중 하나 이상을 전달합니다. 생략한 필드는 유지하며, `location: null`은 장소를 지웁니다. 성공 시 `200 SCHEDULE_UPDATED`와 수정 결과를 반환합니다.
- `DELETE /api/v1/admin/schedules/{scheduleId}?version=1`: 본문 없이 요청합니다. 일반 일정을 논리 삭제하고 `204`(본문 없음)를 반환합니다.
- 공통 인증: 서비스 Bearer 토큰과 ADMIN 권한이 필요합니다. 누락·잘못된 토큰은 `401 UNAUTHENTICATED`, 일반 회원은 `403 FORBIDDEN`입니다. 기존 다른 API의 인증 오류 코드는 유지합니다.
- 현재 가입 흐름에서 이름·기수·초대코드를 입력하고 회원을 생성하므로 가입된 회원은 온보딩 완료 상태입니다. 별도의 온보딩 상태 컬럼은 없습니다.
- 수정 제목은 앞뒤 공백 제거 후 1~100자, 장소는 최대 200자입니다. `startsAt`은 offset을 포함한 RFC 3339 문자열입니다. 과거 일시도 수정할 수 있습니다. 응답 시각은 Asia/Seoul 기준입니다.
- 수정·삭제 모두 트랜잭션에서 활성 events 행을 FOR UPDATE로 잠그고 버전을 확인합니다. 최신 버전이 아니면 `409 VERSION_CONFLICT`이며 데이터는 변경하지 않습니다. 성공 시 version을 1 올립니다. 동일 버전의 수정 재요청은 409, 삭제 재요청은 404입니다.
- 없는 일정, 삭제된 일정, 세션 ID는 `404 RESOURCE_NOT_FOUND`입니다. study_sessions는 변경하지 않습니다. 삭제된 일정은 공통 schedule_items 뷰 및 이를 사용하는 목록에서 제외됩니다.
- 이미 배포된 V3 스키마를 변경하지 않습니다. 기존 events의 version, updated_at, deleted_at 컬럼을 사용합니다.
- 이번 작업에는 새로운 회원용 목록·상세 API나 관리자 상세 API를 추가하지 않습니다. version은 기존 관리자 일정 목록 응답에서 받을 수 있습니다.

## 요청 예시

```http
PATCH /api/v1/admin/schedules/28e6b6bb-4f30-47f8-b9c4-54e3b06d18c0
Authorization: Bearer <accessToken>
Content-Type: application/json

{"location": null, "version": 1}
```

## 검증

`./gradlew test --tests '*ScheduleMutationControllerTest' --tests '*ScheduleMutationIntegrationTest'`

통합 테스트는 Docker에서 PostgreSQL 17 Testcontainer를 실행합니다. 필드 유지·장소 제거·시간대·논리 삭제·세션 보호·동일 버전 재요청·동시 수정 및 수정/삭제 경합을 검증합니다.
