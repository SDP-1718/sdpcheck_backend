# 공지 이미지 업로드 API

## API 설명

관리자가 공지에 첨부할 이미지 파일을 서버에 직접 업로드하는 API입니다.
공지 작성 화면에서 이미지를 선택한 후 호출합니다. 백엔드는 검증한 파일의 원본 바이트를 PostgreSQL에 저장하고 `fileId`를 반환합니다.

이 API는 임시 업로드 URL을 발급하지 않습니다. 업로드 성공 후 별도의 저장소 PUT 요청 없이 공지 생성 API의 `imageIds`에 `fileId`를 전달합니다.
현재 공지 수정 API는 구현되어 있지 않습니다. 향후 공지 수정에서 첨부 교체를 지원할 경우 해당 API의 파일 연결 규칙도 별도로 정의해야 합니다.

## 기본 정보

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| Endpoint | `/api/v1/files` |
| operationId | `upload` (현재 컨트롤러 메서드 및 Swagger 기준) |
| Content-Type | `multipart/form-data` |
| 인증 / 권한 | 서비스 Bearer 토큰 / `ADMIN` |
| 관련 화면 ID | `MN-02` |
| 성공 응답 | `201 Created` / `IMAGE_UPLOADED` |

## 비즈니스 규칙

- 로그인한 관리자만 업로드할 수 있습니다. `/admin` 경로가 아니더라도 관리자 권한을 검사합니다.
- 현재 회원 가입 시 이름·기수·초대코드를 입력받으므로 가입 완료 회원을 온보딩 완료 상태로 취급합니다. 별도의 온보딩 상태 필드는 없습니다.
- 요청마다 이미지 한 개를 `file` 파트로 전달합니다. 여러 이미지는 파일별로 호출합니다.
- JPEG, PNG, WebP만 허용합니다. MIME 타입은 각각 `image/jpeg`, `image/png`, `image/webp`입니다.
- 파일 크기는 1바이트 이상, 최대 5 MiB(`5,242,880`바이트)입니다. 전체 multipart 요청 한도는 현재 6 MB 설정입니다.
- 원본 파일명은 공백만으로 구성할 수 없으며 길이는 255자 이하여야 합니다. 현재 구현은 파일명의 앞뒤 공백을 제거하지 않습니다.
- MIME 타입 및 JPEG/PNG/WebP의 파일 시그니처를 검사합니다. 이미지 전체 디코딩 검증이나 악성 파일 검사를 수행하는 기능은 아닙니다.
- 서버가 UUID `fileId`를 생성하고 파일 바이트를 `uploaded_files.data`(`BYTEA`)에 저장합니다. 원본 파일명은 식별 키나 파일 경로로 사용하지 않습니다.
- 업로드 파일의 최초 상태는 `PENDING`입니다. 해당 파일을 업로드한 관리자만 공지에 연결할 수 있습니다.
- 파일은 업로드 시점부터 24시간 이내인 `attachBefore` 이전에 공지에 연결해야 합니다. 기한이 지났거나 이미 연결된 파일은 다시 연결할 수 없습니다.
- 호출은 비멱등입니다. 같은 파일을 다시 업로드해도 새로운 `fileId`가 생성됩니다.
- 공지와 파일 연결은 하나의 DB 트랜잭션에서 처리합니다. 성공하면 파일 상태를 `ATTACHED`로 변경합니다.
- 만료된 미연결 파일은 다음 업로드 처리 시 정리합니다. 주기적인 정리 작업은 현재 없습니다.
- 체크섬 입력, 사용자별 분당 30회 제한, 업로드 URL 만료·재발급 기능은 현재 제공하지 않습니다.
- 응답의 `attachBefore`는 UTC offset을 포함한 시각이며 현재 `Asia/Seoul` 기준으로 반환합니다.

## 업로드 순서

1. 이 API에 Bearer 토큰과 실제 이미지 파일을 전송합니다.
2. `201 IMAGE_UPLOADED` 응답에서 `fileId`를 받습니다. 이 응답은 파일이 DB에 저장되었다는 의미입니다.
3. `POST /api/v1/admin/notices`의 `imageIds`에 받은 ID들을 표시 순서대로 전달합니다.
4. 공지에 연결한 이미지는 로그인한 회원이 `GET /api/v1/files/{fileId}`로 조회합니다.

## RequestHeader

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| Authorization | string | O | `Bearer <accessToken>` |
| Content-Type | string | O | `multipart/form-data; boundary=...` |

브라우저의 `FormData`를 사용할 때는 `Content-Type`을 직접 지정하지 않습니다. 브라우저가 boundary를 포함해 자동 설정합니다.

## PathVariable / RequestParam

없음.

## RequestBody

JSON이 아닌 `multipart/form-data`입니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| file | file(binary) | O | JPEG·PNG·WebP 이미지 한 개, 최대 5 MiB |

`fileName`, `contentType`, `sizeBytes`, `checksumSha256`을 별도 JSON 필드로 전달하지 않습니다. 파일명·MIME 타입은 multipart 파일 파트에서, 크기·시그니처는 수신 파일에서 확인합니다.

브라우저 호출 예시:

```javascript
const formData = new FormData();
formData.append("file", selectedFile);

const response = await fetch("/api/v1/files", {
  method: "POST",
  headers: { Authorization: `Bearer ${accessToken}` },
  body: formData,
});

if (!response.ok) {
  // 오류를 처리하고 공지 생성 요청에 이 파일을 포함하지 않습니다.
  throw new Error("이미지 업로드에 실패했습니다.");
}

const { result } = await response.json();
// result.fileId를 공지 생성 요청의 imageIds 배열에 추가합니다.
```

## Response

**201 Created**

```json
{
  "isSuccess": true,
  "code": "IMAGE_UPLOADED",
  "message": "이미지가 업로드되었습니다.",
  "result": {
    "fileId": "28e6b6bb-4f30-47f8-b9c4-54e3b06d18c0",
    "attachBefore": "2026-10-28T17:00:00+09:00"
  }
}
```

위 시각은 예시이며 실제 마감 시각은 응답 값을 사용합니다.

| result 필드 | 타입 | 설명 |
| --- | --- | --- |
| fileId | string(UUID) | 공지 생성 요청의 `imageIds`에 전달할 파일 ID |
| attachBefore | string(date-time) | 파일을 공지에 연결할 수 있는 마감 시각 |

공지 생성 요청의 첨부 부분 예시:

```json
{
  "title": "정기 세션 안내",
  "content": "첨부 이미지를 확인해주세요.",
  "isPinned": false,
  "imageIds": ["28e6b6bb-4f30-47f8-b9c4-54e3b06d18c0"]
}
```

## 예외 케이스

| HTTP Status | Error Code | 상황 | 클라이언트 처리 |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | file 파트 누락, 빈 파일, 공백 파일명, 파일명 255자 초과 | 입력을 수정하고 다시 업로드 |
| 401 | `AUTHENTICATION_REQUIRED` | 인증 토큰 누락 | 인증 후 다시 업로드 |
| 401 | `INVALID_TOKEN` | 토큰 형식 오류·만료·검증 실패 | 인증 갱신 또는 재로그인 |
| 403 | `ACCESS_DENIED` | 관리자 권한 없음 | 업로드 중단 및 권한 확인 |
| 413 | `PAYLOAD_TOO_LARGE` | 파일 또는 전체 multipart 요청 크기 초과 | 파일 크기를 줄이고 다시 업로드 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 허용하지 않는 MIME 타입 또는 요청 본문 형식 | JPEG·PNG·WebP와 multipart 형식 확인 |
| 422 | `INVALID_IMAGE` | 파일 시그니처가 선언한 MIME 타입과 불일치 | 올바른 이미지 파일로 교체 |
| 500 | `INTERNAL_ERROR` | 파일 읽기 실패 또는 DB 등 내부 오류 | 결과를 확인하고 재시도 |

현재 업로드 API의 인증·파일 오류 코드는 위 표를 따릅니다. 기존 URL 발급 명세의 `UNAUTHENTICATED`, `FORBIDDEN`, `FILE_TOO_LARGE`, `UNSUPPORTED_IMAGE_TYPE`과 이름이 다릅니다.

오류 응답 예시:

```json
{
  "isSuccess": false,
  "code": "PAYLOAD_TOO_LARGE",
  "message": "요청 크기가 허용 범위를 초과했습니다.",
  "result": null
}
```

## 이미지 조회

- `GET /api/v1/files/{fileId}`로 로그인한 회원이 첨부 이미지를 조회합니다.
- 응답은 공통 JSON이 아닌 실제 이미지 바이트이며 `Content-Type`은 저장한 이미지 타입입니다.
- 미연결 파일, 삭제·비공개 공지의 이미지는 `404 FILE_NOT_FOUND`입니다.
- 프론트에서 `<img src="/api/v1/files/...">`만 사용하면 Bearer 헤더를 추가할 수 없습니다. 인증 요청으로 파일을 받아 Blob URL로 표시합니다.
- 공지 생성 응답의 이미지 URL은 `/api/v1/files/{fileId}`이며 서명 URL이 아닙니다. `images[].expiresAt`은 `null`입니다.

## 운영 및 기존 URL 발급 명세 변경점

- EC2와 Docker는 애플리케이션·PostgreSQL 실행 환경입니다. 이미지 저장에 S3 버킷이나 AWS 이미지 저장 자격증명은 사용하지 않습니다.
- 이미지 바이트가 DB 백업에 포함되므로 영속 볼륨, 정기 백업 및 복원 검증을 준비합니다.
- 기존 `POST /api/v1/files/presigned-url`은 현재 제공하지 않습니다. 프론트는 URL 발급 → 저장소 PUT 흐름을 제거하고 위 직접 업로드 흐름을 사용합니다.
- 업로드 응답의 `uploadUrl`, `method`, `headers`, `expiresAt`을 제거합니다. `fileId`, `attachBefore`는 유지합니다.
- 이 문서는 실제 구현을 설명합니다. Swagger 업로드 메서드는 `upload`이며 런타임 성공 응답은 `201`입니다.

## 관련 이슈 / PR

- Issue: 미등록
- PR: 미등록
