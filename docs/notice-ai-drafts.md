# 관리자 공지 초안 생성 (고정 목업)

## API

- `POST /api/v1/admin/notices/ai-drafts`
- operationId: `generateNoticeDraft`
- `Content-Type: application/json`, 온보딩 완료 관리자 서비스 Bearer 토큰 필요.
- 요청은 `memo` 문자열만 허용. 필수, 앞뒤 공백 제거 후 1~2,000자.
- 누락, null, 공백뿐인 문자열, 다른 JSON 타입, 추가 필드는 `400 / VALIDATION_ERROR`.

```json
{"memo":"10월 2일 금요일 오후 7시 정기 세션 공지"}
```

성공: `200 OK`, `Cache-Control: no-store`.

```json
{
  "isSuccess": true,
  "code": "NOTICE_DRAFT_CREATED",
  "message": "공지 초안이 생성되었습니다.",
  "result": {
    "title": "정기 세션 안내",
    "content": "10월 2일 금요일 오후 7시에 정기 세션이 진행됩니다.",
    "mode": "MOCK"
  }
}
```

입력 메모가 달라도 응답은 동일. 실제 AI 호출, 초안 저장, 공지 자동 게시 없음.
관리자가 폼에서 편집한 뒤 별도 공지 생성 API를 호출하여 게시.
1.5초 생성 중 표시는 프런트 UI 동작으로, 서버 지연은 추가하지 않음.

## 요청 제한

- 관리자 회원 ID별 최근 60초간 성공 요청을 최대 10회 허용하는 rolling window 적용.
- 초과 요청은 `429 / RATE_LIMITED`, `Retry-After`에 재시도까지 대기할 정수 초 반환.
- 인증 실패·잘못된 요청·비활성 상태의 요청·횟수 초과 요청은 허용 횟수를 소비하지 않음.
- DB 서버 시각과 사용자별 트랜잭션 advisory lock을 사용하므로 서버 재시작과 여러 인스턴스에서도 제한 유지.
- `notice_draft_requests`에는 회원 ID와 호출 시각만 저장. 메모와 초안 내용은 저장하지 않음.
- 같은 회원의 다음 호출에서 만료된 기록을 정리. 회원별 최대 10행 유지, 회원 삭제 시 함께 삭제.
- 새 Flyway 마이그레이션: `V10__create_notice_draft_requests.sql`.

## 기능 비활성화

환경 변수 `NOTICE_AI_DRAFT_ENABLED=false` 또는 Spring 속성
`notice.ai-draft.enabled=false`로 비활성화 후 서버 재시작. 기본값은 `true`.
비활성 상태에서는 호출 횟수를 소비하지 않고 `503 / FEATURE_UNAVAILABLE` 반환.

## 오류

| 상태 | 코드 | 설명 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 메모 누락·공백·길이 초과·잘못된 형식·추가 필드 |
| 401 | UNAUTHENTICATED | 토큰 누락·만료·검증 실패. `WWW-Authenticate: Bearer` |
| 403 | FORBIDDEN | 관리자 권한 없음 또는 온보딩 미완료 |
| 429 | RATE_LIMITED | 최근 60초간 10회 초과. `Retry-After: <초>` |
| 500 | INTERNAL_ERROR | DB 장애 등 서버 내부 오류 |
| 503 | FEATURE_UNAVAILABLE | 초안 기능 비활성 상태 |

성공·오류는 기존 `ApiResponse` 형식 사용. 실제 AI 연동의 502·504 오류는 현재 범위에 포함하지 않음.

## 검증

```powershell
.\gradlew.bat test --tests '*NoticeDraftControllerTest' --tests '*NoticeDraftServiceTest' --tests '*NoticeDraftIntegrationTest'
```

요청 경계값, 인증·온보딩 권한, 고정 목업, DB 미게시, 사용자별 제한, 동시 요청,
만료된 요청의 재허용, 재생성한 limiter의 공유 상태, 기능 비활성화, 오류 응답 및 Swagger 스키마 검증.

### 2026-10-30 검증 결과

- 전체 테스트 302개 통과. Swagger 오류 예시 보완 후 관련 19개 재검증 통과.
- Docker 시작 문제로 통합 테스트는 격리된 PostgreSQL 18에서 실행.
- Swagger Try it out에서 `200 / NOTICE_DRAFT_CREATED`, 고정 제목·본문·`MOCK`, `Cache-Control: no-store` 확인.
- 동일 관리자의 11번째 요청에서 `429 / RATE_LIMITED`, 실제 `Retry-After: 17` 확인.
- OpenAPI의 memo 문자열·필수·최대 2,000자 및 429 오류 예시·헤더 검증.
