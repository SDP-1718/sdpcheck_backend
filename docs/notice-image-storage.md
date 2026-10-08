# 공지 이미지 저장소

공지와 이미지 파일은 PostgreSQL에 저장한다. EC2와 Docker는 애플리케이션 및 데이터베이스 실행 환경이다. S3 버킷이나 AWS 이미지 저장 자격증명은 필요하지 않다.

## API 흐름

1. 관리자가 `POST /api/v1/files`에 Bearer 토큰과 `multipart/form-data`의 `file` 파트로 이미지를 전송한다. JPEG, PNG, WebP를 허용하며 파일당 최대 5 MiB다.
2. 서버가 파일 바이트를 `uploaded_files.data`(`bytea`)에 저장하고 `201 IMAGE_UPLOADED`로 `fileId`, `attachBefore`를 반환한다. 생성한 파일은 24시간 내 공지에 연결해야 한다.
3. 관리자가 `POST /api/v1/admin/notices`의 `imageIds`에 `fileId`를 표시 순서대로 보낸다. 파일 소유자, 미연결 상태, 연결 기한을 확인하고 공지와 파일 연결을 한 DB 트랜잭션으로 저장한다.
4. 공지 생성 응답의 `images[].url`은 `/api/v1/files/{fileId}`다. 이 경로는 로그인한 회원이 Bearer 토큰을 보내 조회한다. 미연결 파일과 삭제된 공지의 파일은 404다. 프런트에서 `<img>`에 직접 경로를 넣는 방식은 인증 헤더를 보낼 수 없으므로, 인증 요청으로 이미지 바이트를 받은 뒤 표시한다. URL은 만료되지 않아 `expiresAt`은 `null`이다.

기존 `POST /api/v1/files/presigned-url`은 제거했다. 공지 생성·목록 조회의 경로와 `imageIds` 입력 형식은 그대로다.

## 운영

이미지 바이트가 DB 백업에 포함되므로 PostgreSQL 데이터를 영속 볼륨에 두고 정기 백업해야 한다. 연결 기한이 지난 미연결 파일은 다음 업로드 때 삭제한다. 업로드가 오랫동안 없으면 그때까지 남을 수 있다. 배포 전에 실제 PostgreSQL에서 업로드·공지 연결·이미지 조회와 백업 복원을 확인한다.
