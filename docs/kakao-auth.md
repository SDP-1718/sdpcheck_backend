# 카카오 로그인과 온보딩

카카오 사용자 ID로 회원을 식별한다. Refresh Token은 HttpOnly 쿠키로 전달하고, Access Token은 프론트에서 별도 API로 발급받는다.

## API 흐름

1. 프론트가 브라우저를 `GET /api/v1/auth/kakao/login`으로 이동시킨다. 서버는 5분짜리 state 쿠키를 설정하고 카카오 인증 화면으로 302 리다이렉트한다.
2. 카카오가 `GET /login/oauth2/code/kakao`로 인가 코드와 state를 전달한다. 서버는 state를 검증하고 카카오 사용자 정보를 조회한다. 신규 회원은 온보딩 미완료 상태로 생성한다.
3. 서버는 Refresh Token 쿠키를 설정하고 프론트 콜백으로 302 리다이렉트한다. state 쿠키는 제거하며 응답 본문은 없다.
4. 프론트 콜백에서 쿠키를 포함해 `POST /api/v1/auth/access-token`을 호출한다. 200 응답의 `result`에 `accessToken`, `onboardingCompleted`, `name`, `generation`이 포함된다. 온보딩 미완료 회원의 이름과 기수는 null이다.
5. 온보딩 미완료 회원은 Bearer 토큰과 이름·기수·초대 코드로 `POST /api/v1/auth/onboarding`을 호출한다. 성공 시 200으로 이름과 기수를 반환한다. 잘못된 초대 코드는 400, 이미 완료한 회원은 409다.
6. `POST /api/v1/auth/refresh`는 쿠키로 Access Token과 새 Refresh Token을 발급한다. `POST /api/v1/auth/logout`은 Refresh Token을 정리하고 쿠키를 제거한 뒤 204를 반환한다. 로그아웃은 요청 본문과 Access Token이 필요하지 않다.

## 설정 및 주의 사항

`KAKAO_CLIENT_ID`, `KAKAO_REDIRECT_URI`, `FRONTEND_AUTH_CALLBACK_URL`을 설정한다. `KAKAO_CLIENT_SECRET`은 사용하는 경우 설정한다. 카카오 Developers에 등록하는 Redirect URI는 프론트 주소가 아니라 백엔드 콜백 주소다.

콜백 오류는 백엔드에서 JSON으로 반환하며 프론트 오류 화면으로 자동 이동하지 않는다. state 오류·인가 코드 누락은 400, 카카오 토큰 교환·사용자 조회 실패는 502다. 쿠키의 Secure와 SameSite는 실행 환경에 맞춰 설정한다. 프론트와 백엔드의 출처가 다르면 쿠키를 허용하는 CORS 설정이 필요하다.

온보딩 미완료 회원은 일반 회원 API에 접근할 수 없다. 액세스 토큰 발급은 Refresh Token을 유지하고, 토큰 재발급은 Refresh Token도 갱신한다. 회원 스키마 변경은 Flyway V9로 반영한다.
