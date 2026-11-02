# 관리자 공지 수정·삭제

## 공지 수정

- `PATCH /api/v1/admin/notices/{noticeId}` (`updateNotice`)
- 관리자 서비스 토큰으로 인증, `Content-Type: application/json`.
- `title`, `content`, `isPinned`, `imageIds` 중 하나 이상 필요. 생략한 필드는 유지.
- 명시적인 `null`, 공백뿐인 제목·본문, 잘못된 JSON 타입, 알 수 없는 필드,
  UUID가 아니거나 중복된 파일 ID는 `400 / VALIDATION_ERROR`.
- 제목·본문은 앞뒤 공백을 제거하여 저장.
- `isPinned: true`는 기존 고정 공지를 해제한 뒤 대상 공지를 고정.
- `isPinned: false`는 대상 공지만 고정 해제.
- `imageIds`는 배열 전체를 교체하며 배열 순서대로 표시. `[]`는 모든 첨부 제거.
- 이미 대상 공지에 연결된 이미지는 최초 업로더가 아닌 관리자도 유지·재정렬 가능.
- 새 이미지는 수정하는 관리자 본인이 직접 업로드한 유효한 `PENDING` 파일이어야 하며,
  업로드 응답의 `attachBefore` 이전에 연결해야 함.
- 다른 공지에 연결했거나 한 번 제거한 파일은 재사용 불가. 새로 업로드하여 새 ID를 사용.
- 파일 검증 실패 시 공지 내용·고정 상태·이미지 목록 변경을 모두 롤백.
- 최초 작성자·작성 시각 유지, 수정 시각 갱신.

```json
{
  "title": "정기 세션 장소 변경",
  "content": "학생회관 301호로 변경되었습니다.",
  "isPinned": true,
  "imageIds": []
}
```

성공: `200`, `NOTICE_UPDATED`, `공지가 수정되었습니다.` 및 `NoticeResponse`.
`images`는 `{fileId, url, expiresAt}` 배열. 기존 PostgreSQL 직접 조회 방식을 사용하므로
`url`은 `/api/v1/files/{fileId}`, `expiresAt`은 `null`.
이미지 조회에도 서비스 토큰이 필요함.
`author.id`는 기존 회원 ID 체계에 따라 숫자를 문자열로 반환.

## 공지 삭제

- `DELETE /api/v1/admin/notices/{noticeId}` (`deleteNotice`)
- 관리자 인증 필요, 요청 본문과 버전 파라미터 없음.
- `deleted_at`·`updated_at` 기록 및 `is_pinned = false`를 함께 처리.
- 관리자·부원 목록과 상세 조회, 첨부 이미지 조회에서 제외.
- 성공: `204 No Content`, 응답 본문 없음.
- 이미 삭제됐거나 없는 ID는 `404 / RESOURCE_NOT_FOUND`.
- 이미지 원본 바이트는 물리 삭제하지 않음. 보존·정리 정책은 별도 운영 범위.

## 공통 오류

| 상태 | 코드 | 설명 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 잘못된 공지 ID 또는 요청 본문 |
| 401 | UNAUTHENTICATED | 인증 토큰 누락·만료·검증 실패 (`WWW-Authenticate: Bearer`) |
| 403 | FORBIDDEN | 관리자 권한 없음 |
| 404 | RESOURCE_NOT_FOUND | 대상 공지가 없거나 삭제됨 |
| 404 | FILE_NOT_FOUND | 새 첨부 파일이 없거나 업로드 소유권 없음 |
| 409 | FILE_NOT_READY | 새 파일의 연결 기한 만료 또는 이미 연결된 상태 |
| 422 | INVALID_IMAGE | 새 첨부 파일의 크기·형식 검증 실패 |
| 500 | INTERNAL_ERROR | 서버 내부 처리 오류 |

서비스 응답은 기존 `ApiResponse` 형식을 사용하고 성공 응답에 `Cache-Control: no-store` 적용.
공지와 이미지가 동일 PostgreSQL에 저장되므로 DB 장애는 서버 내부 오류로 처리.

## 동시성·DB

- 기존 공지 등록과 동일한 PostgreSQL 트랜잭션 advisory lock을 먼저 획득한 후 대상 공지를 잠금.
- 등록·수정·삭제가 동시에 고정 상태를 변경해도 고정 공지는 최대 하나 유지.
- 모든 첨부 파일은 UUID 순서로 잠근 뒤 검증하여 표시 순서에 따른 교착을 방지.
- 서로 다른 필드의 동시 부분 수정은 생략한 필드를 덮어쓰지 않음.
- 버전 파라미터는 사용하지 않음. 동일 필드 수정은 나중에 잠금을 획득한 요청의 값이 반영됨.
- 기존 테이블·제약조건 사용, Flyway 마이그레이션 추가 없음.
- 회원가입 단계에서 필수 회원 정보가 저장되는 현재 인증 구조를 사용하며 별도 온보딩 상태 없음.

## 검증

```powershell
.\gradlew.bat test --tests '*NoticeMutationControllerTest' --tests '*NoticeMutationIntegrationTest'
```

요청 검증·인증·응답, 부분 수정·원작성자 보존, 이미지 유지·교체·재정렬·제거·오류 롤백,
논리 삭제 및 조회 차단, 고정 상태·수정/삭제·첨부 재사용의 동시성, OpenAPI 스키마를 검증.

2026-10-30 검증 결과: 신규 18개 및 관련 회귀 50개, 총 68개 통과.
로컬 Docker Desktop이 임시 소켓 접근 오류로 시작되지 않아 통합 테스트 10개는
동일한 테스트 본문을 분리된 임시 PostgreSQL 18에서 실행함.
저장소의 통합 테스트는 기존 방식대로 Testcontainers의 PostgreSQL 17을 사용함.

### Swagger 실제 실행 검증 (2026-10-30)

- 로컬 최신 서버의 `/swagger-ui/index.html`과 `/v3/api-docs` 정상 표시.
- Swagger 로그인 API로 테스트 관리자 인증 후 Authorize 적용.
- `Try it out`에서 공지 수정 `200 / NOTICE_UPDATED`, 삭제 `204` 직접 확인.
- 삭제 응답에 본문이 없으며 Swagger에도 `204`의 content가 정의되지 않음.
- 수정 요청의 imageIds가 UUID 문자열 배열로 표시되고 내부 검증용 필드는 노출되지 않음.
- 실제 HTTP 검증 30개 항목 통과: 인증·권한·입력값·부분 수정·고정 상태·이미지 연결과 제거·오류 롤백·삭제 후 조회 차단 등.
- Docker 실행 문제로 기존 개발 DB와 분리된 임시 PostgreSQL 18 사용.
