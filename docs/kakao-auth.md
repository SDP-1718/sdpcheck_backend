# Kakao login and onboarding

## Frontend integration

1. Navigate the browser to `GET {backendUrl}/api/v1/auth/kakao/login` using
   a link or `window.location.href`. The backend sets a five-minute HttpOnly
   state cookie and redirects to Kakao. Do not navigate directly to Kakao.
2. Kakao redirects to the backend callback:
   `GET /login/oauth2/code/kakao?code={authorizationCode}&state={state}`.
   Register the exact backend callback URL in Kakao Developers.
3. The backend validates the signed state and matching cookie, clears the state
   cookie, and exchanges the code, looks up the Kakao ID, creates a pending
   member if necessary, and saves the SDP Refresh Token. It sets the HttpOnly
   refresh-token cookie and returns a bodyless 302 redirect to
   `FRONTEND_AUTH_CALLBACK_URL`.
4. The frontend callback calls `POST /api/v1/auth/access-token` with credentials
   included. The response contains `accessToken`, `onboardingCompleted`, `name`,
   and `generation`. This endpoint does not rotate the Refresh Token.
   Name and generation are null until onboarding is complete.
5. If onboarding is incomplete, send `POST /api/v1/auth/onboarding` with a
   Bearer Access Token and
   `{ "name": "...", "generation": 17, "inviteCode": "..." }`.
   The response contains name and generation. Repeated registration returns
   `ONBOARDING_ALREADY_COMPLETED` (409).
6. If onboarding is complete, use name and generation from `/access-token`
   and navigate directly to the home page. No member-information GET request
   is needed. The frontend derives the avatar's initial from the name.

## Callback errors

Invalid/missing/expired state returns HTTP 400 (`INVALID_OAUTH_STATE`).
Cancellation without a code returns HTTP 400 (`KAKAO_AUTHORIZATION_CODE_MISSING`).
Kakao token exchange or user lookup failures return HTTP 502 in the common
ApiResponse format, without setting a new refresh-token cookie (the state cookie is cleared). Errors are
not redirected to the frontend in this simple callback flow.
State cookies are HttpOnly, SameSite=Lax, and limited to the callback path.
Secure follows the existing refresh-cookie setting. States use the existing
JWT signing secret with a separate token type; no Access/Refresh Token claims
are changed. State cookies are cleared on success and failure. Starting login
again replaces the pending cookie; complete the most recently started login.

## Configuration and access

Supply `KAKAO_CLIENT_ID`, optional `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`,
and `FRONTEND_AUTH_CALLBACK_URL` through the running environment. Actual
credentials must not be committed. The frontend callback is a complete URL,
for example `http://localhost:5173/auth/callback`.

Login start, callback, and access-token issuance are public. Onboarding requires a valid
Access Token; other protected endpoints require MEMBER or ADMIN. Incomplete
members cannot access general protected APIs. Existing ID/password
authentication and refresh-token rotation remain available.

For frontend/backend on different origins, the backend needs an explicit
credential-enabled CORS allowlist, and the frontend must include credentials
for cookie requests. CORS configuration remains a separate integration step.
Refresh Token cookie Secure and SameSite follow the existing configuration.

## Validation

Kakao HTTP responses are mocked in `KakaoClientTest`.
`KakaoAuthIntegrationTest` uses PostgreSQL Testcontainers to check callback,
member/token persistence, onboarding, returning-member login, and home
information in the access-token response. Run `./gradlew check` with Docker.

V9 changes the member schema. V6-V8 must be merged/applied before V9 in shared
environments; a fresh test database can apply the currently present V1-V5
and V9 migrations.
