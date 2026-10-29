# Swagger 변경 코드 검토

참조: https://github.com/SDP-1718/sdpcheck_backend/pull/36

PR #36의 `@Operation(summary, description)`, `bearerAuth`, 빈 204 응답 문서 형식을 따랐습니다.

- AUTH / MYPAGE 태그로 담당 API를 묶었습니다.
- 사용 중인 온보딩의 `@Hidden`을 제거했습니다. 회원가입·로그인 아이디 중복 확인의 숨김은 유지합니다.
- 실제 성공 응답에 맞춰 카카오 로그인·콜백은 302, 로그아웃·푸시 토큰 삭제는 본문 없는 204로 표시합니다.
- 회원 API와 온보딩에 Bearer 인증을 표시하고 서버가 주입하는 인증 객체는 숨깁니다.
- Swagger UI 의존성·인증 스키마·GET 접근 허용을 추가합니다. PR #36과 동일하게 prod에서는 문서 생성을 비활성화합니다.
- API 실행 로직과 .env는 변경하지 않았습니다.

아래는 기존 파일 7개의 전체 변경 diff입니다. 신규 OpenApiConfig는 뒤에 전체 코드로 첨부합니다.


```diff
diff --git a/build.gradle b/build.gradle
index aa460ef..4e09ac2 100644
--- a/build.gradle
+++ b/build.gradle
@@ -18,7 +18,7 @@ repositories {
 }
 
 dependencies {
-	implementation 'io.swagger.core.v3:swagger-annotations-jakarta:2.2.52'
+	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'
 	implementation 'org.springframework.boot:spring-boot-starter-flyway'
 	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
 	implementation 'org.springframework.boot:spring-boot-starter-validation'
diff --git a/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/AuthController.java b/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/AuthController.java
index 247b456..c898149 100644
--- a/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/AuthController.java
+++ b/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/AuthController.java
@@ -9,6 +9,11 @@ import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
 import com.sdpcheck.sdpcheck.global.response.ApiResponse;
 import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
 import io.swagger.v3.oas.annotations.Hidden;
+import io.swagger.v3.oas.annotations.Operation;
+import io.swagger.v3.oas.annotations.Parameter;
+import io.swagger.v3.oas.annotations.media.Content;
+import io.swagger.v3.oas.annotations.security.SecurityRequirement;
+import io.swagger.v3.oas.annotations.tags.Tag;
 import jakarta.validation.Valid;
 import lombok.RequiredArgsConstructor;
 import org.springframework.http.CacheControl;
@@ -23,6 +28,7 @@ import org.springframework.web.bind.annotation.*;
 @RestController
 @RequiredArgsConstructor
 @RequestMapping("/api/v1/auth")
+@Tag(name = "AUTH", description = "인증 및 온보딩 API")
 public class AuthController {
 
     private final AuthService authService;
@@ -42,6 +48,7 @@ public class AuthController {
                 .body(ApiResponse.created(signupResult.response()));
     }
 
+    @Operation(summary = "로그인", description = "아이디와 비밀번호로 액세스 토큰을 발급하고 HttpOnly 리프레시 토큰 쿠키를 설정합니다.")
     @PostMapping("/login")
     public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest){
         LoginResult loginResult = authService.login(loginRequest);
@@ -54,6 +61,9 @@ public class AuthController {
                 .body(ApiResponse.success(loginResult.response()));
     }
 
+    @Operation(summary = "로그아웃", description = "쿠키의 리프레시 토큰을 폐기하고 쿠키를 삭제합니다. 액세스 토큰과 요청 본문은 필요하지 않으며, 쿠키가 없어도 204를 반환합니다. 기존 액세스 토큰은 만료 전까지 유효합니다.",
+            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
+                    responseCode = "204", description = "로그아웃 완료 (본문 없음)", content = @Content))
     @PostMapping("/logout")
     public ResponseEntity<Void> logout(
             @CookieValue(value = "refreshToken", required = false) String refreshToken) {
@@ -67,10 +77,11 @@ public class AuthController {
                 .build();
     }
 
-    @Hidden
+    @Operation(summary = "온보딩 등록", description = "카카오 신규 회원이 이름·기수·초대코드를 등록합니다. 온보딩 전 액세스 토큰으로 호출하며 잘못된 초대코드는 400, 이미 완료한 회원은 409를 반환합니다.")
+    @SecurityRequirement(name = "bearerAuth")
     @PostMapping("/onboarding")
     public ResponseEntity<ApiResponse<OnboardingResponse>> completeOnboarding(
-            @AuthenticationPrincipal AuthenticatedMember principal,
+            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember principal,
             @Valid @RequestBody OnboardingRequest request
             ){
         OnboardingResponse response = authService.completeOnboarding(principal.memberId(), request);
@@ -80,6 +91,7 @@ public class AuthController {
                 .body(ApiResponse.success(response));
     }
 
+    @Operation(summary = "토큰 재발급", description = "HttpOnly refreshToken 쿠키로 액세스 토큰과 새 리프레시 토큰을 발급합니다. 새 리프레시 토큰은 Set-Cookie로 전달하며 이전 토큰은 사용할 수 없습니다. 쿠키 누락·만료·불일치는 401입니다.")
     @PostMapping("/refresh")
     public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
             @CookieValue(value = "refreshToken", required = false) String refreshToken) {
@@ -93,6 +105,7 @@ public class AuthController {
                 .body(ApiResponse.success(refreshResult.response()));
     }
 
+    @Operation(summary = "액세스 토큰 발급", description = "카카오 로그인 후 HttpOnly refreshToken 쿠키로 액세스 토큰과 온보딩 완료 여부·이름·기수를 조회합니다. 리프레시 토큰은 갱신하지 않습니다. 쿠키 누락·만료·불일치는 401입니다.")
     @PostMapping("/access-token")
     public ResponseEntity<ApiResponse<AccessTokenResponse>> issueAccessToken(
             @CookieValue(value = "refreshToken", required = false)
diff --git a/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/KakaoAuthController.java b/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/KakaoAuthController.java
index 817466f..51aa836 100644
--- a/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/KakaoAuthController.java
+++ b/src/main/java/com/sdpcheck/sdpcheck/domain/auth/controller/KakaoAuthController.java
@@ -20,9 +20,14 @@ import org.springframework.web.bind.annotation.RequestParam;
 import org.springframework.web.bind.annotation.RestController;
 
 import java.net.URI;
+import io.swagger.v3.oas.annotations.Operation;
+import io.swagger.v3.oas.annotations.Parameter;
+import io.swagger.v3.oas.annotations.media.Content;
+import io.swagger.v3.oas.annotations.tags.Tag;
 
 @RestController
 @RequiredArgsConstructor
+@Tag(name = "AUTH")
 public class KakaoAuthController {
     private final AuthService authService;
     private final RefreshTokenCookieProvider refreshTokenCookieProvider;
@@ -33,6 +38,9 @@ public class KakaoAuthController {
     @Value("${auth.frontend-callback-url}")
     private String frontendCallbackUrl;
 
+    @Operation(summary = "카카오 로그인", description = "5분짜리 HttpOnly state 쿠키를 설정하고 카카오 인증 화면으로 이동합니다. Swagger Try it out 대신 브라우저 주소창에서 이 경로를 여세요. 인증 완료 후 백엔드 콜백을 거쳐 프론트 콜백으로 이동합니다.",
+            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
+                    responseCode = "302", description = "카카오 인증 화면으로 이동 (본문 없음)", content = @Content))
     @GetMapping("/api/v1/auth/kakao/login")
     public ResponseEntity<Void> startKakaoLogin() {
         String state = stateService.createState();
@@ -43,12 +51,15 @@ public class KakaoAuthController {
                 .location(authorizeUri).build();
     }
 
+    @Operation(summary = "카카오 로그인 콜백", description = "카카오가 전달한 인가 코드와 state 쿠키를 검증하고 회원을 식별합니다. 신규 회원은 온보딩 미완료로 생성합니다. 리프레시 토큰 쿠키 설정 후 프론트 콜백으로 이동합니다. state 오류·코드 누락은 400, 카카오 API 실패는 502입니다. 직접 호출하지 않습니다.",
+            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
+                    responseCode = "302", description = "프론트 콜백으로 이동 (본문 없음)", content = @Content))
     @GetMapping("/login/oauth2/code/kakao")
     public ResponseEntity<Void> kakaoCallback(
             @RequestParam(value = "code", required = false) String authorizationCode,
             @RequestParam(value = "state", required = false) String state,
             @CookieValue(value = KakaoOAuthStateService.COOKIE_NAME, required = false) String cookieState,
-            HttpServletResponse response) {
+            @Parameter(hidden = true) HttpServletResponse response) {
         response.addHeader(HttpHeaders.SET_COOKIE, stateService.clearCookie().toString());
         response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
         stateService.validate(state, cookieState);
diff --git a/src/main/java/com/sdpcheck/sdpcheck/domain/device/controller/PushTokenController.java b/src/main/java/com/sdpcheck/sdpcheck/domain/device/controller/PushTokenController.java
index ce8b3e8..106bed4 100644
--- a/src/main/java/com/sdpcheck/sdpcheck/domain/device/controller/PushTokenController.java
+++ b/src/main/java/com/sdpcheck/sdpcheck/domain/device/controller/PushTokenController.java
@@ -12,17 +12,25 @@ import org.springframework.http.CacheControl;
 import org.springframework.http.ResponseEntity;
 import org.springframework.security.core.annotation.AuthenticationPrincipal;
 import org.springframework.web.bind.annotation.*;
+import io.swagger.v3.oas.annotations.Operation;
+import io.swagger.v3.oas.annotations.Parameter;
+import io.swagger.v3.oas.annotations.media.Content;
+import io.swagger.v3.oas.annotations.security.SecurityRequirement;
+import io.swagger.v3.oas.annotations.tags.Tag;
 
 @RestController
 @RequiredArgsConstructor
 @RequestMapping("/api/v1/devices/push-tokens")
+@Tag(name = "MYPAGE")
+@SecurityRequirement(name = "bearerAuth")
 public class PushTokenController {
     private final PushTokenService pushTokenService;
 
+    @Operation(summary = "푸시 토큰 등록", description = "로그인한 회원의 기기 푸시 토큰을 등록하고 tokenId를 반환합니다. 토큰은 공백 없이 최대 512자입니다. 기존 토큰은 현재 회원 소유로 갱신하며 성공 시 200을 반환합니다.")
     @PostMapping
     public ResponseEntity<ApiResponse<PushTokenResponse>> addPushToken(
             @Valid @RequestBody PushTokenRequest pushTokenRequest,
-            @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
+            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
         PushTokenResponse pushTokenResponse = pushTokenService.addPushToken(
                 authenticatedMember.memberId(),
                 pushTokenRequest
@@ -34,10 +42,13 @@ public class PushTokenController {
                         "푸시 토큰 등록에 성공했습니다.", pushTokenResponse));
     }
 
+    @Operation(summary = "푸시 토큰 삭제", description = "로그인한 회원이 소유한 푸시 토큰을 삭제합니다. tokenId는 양수이며 없는 토큰·다른 회원의 토큰·이미 삭제된 토큰은 404입니다.",
+            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
+                    responseCode = "204", description = "푸시 토큰 삭제 완료 (본문 없음)", content = @Content))
     @DeleteMapping("/{tokenId}")
     public ResponseEntity<Void> deletePushToken(
             @PathVariable @Positive Long tokenId,
-            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
+            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
         pushTokenService.deletePushToken(authenticatedMember.memberId(), tokenId);
 
         return ResponseEntity.noContent()
diff --git a/src/main/java/com/sdpcheck/sdpcheck/domain/member/controller/MemberController.java b/src/main/java/com/sdpcheck/sdpcheck/domain/member/controller/MemberController.java
index ab14957..a0d57c8 100644
--- a/src/main/java/com/sdpcheck/sdpcheck/domain/member/controller/MemberController.java
+++ b/src/main/java/com/sdpcheck/sdpcheck/domain/member/controller/MemberController.java
@@ -14,17 +14,24 @@ import org.springframework.http.CacheControl;
 import org.springframework.http.ResponseEntity;
 import org.springframework.security.core.annotation.AuthenticationPrincipal;
 import org.springframework.web.bind.annotation.*;
+import io.swagger.v3.oas.annotations.Operation;
+import io.swagger.v3.oas.annotations.Parameter;
+import io.swagger.v3.oas.annotations.security.SecurityRequirement;
+import io.swagger.v3.oas.annotations.tags.Tag;
 
 @RestController
 @RequiredArgsConstructor
 @RequestMapping("/api/v1/members/me")
+@Tag(name = "MYPAGE", description = "내 정보 및 알림 설정 API")
+@SecurityRequirement(name = "bearerAuth")
 public class MemberController {
     private final MemberService memberService;
     private final AttendanceInsightService attendanceInsightService;
 
+    @Operation(summary = "내 정보 조회", description = "온보딩을 완료한 로그인 회원의 이름·기수·권한을 조회합니다. 토큰의 회원 정보만 조회합니다.")
     @GetMapping
     public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(
-            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
+            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
         MyInfoResponse response = memberService.getMyInfo(authenticatedMember.memberId());
 
         return ResponseEntity.ok()
@@ -33,9 +40,10 @@ public class MemberController {
                         "내 정보 조회에 성공했습니다.", response));
     }
 
+    @Operation(summary = "알림 설정 조회", description = "로그인한 회원의 세션 시작 알림과 새 공지 알림 수신 여부를 조회합니다.")
     @GetMapping("/notification-settings")
     public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings(
-            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
+            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
         NotificationSettingsResponse response = memberService.getNotificationSettings(
                 authenticatedMember.memberId());
 
@@ -45,10 +53,11 @@ public class MemberController {
                         "알림 설정 조회에 성공했습니다.", response));
     }
 
+    @Operation(summary = "알림 설정 수정", description = "sessionStartEnabled·newNoticeEnabled 중 전달한 값을 수정합니다. 생략하거나 null인 필드는 유지하며 하나 이상의 boolean 값이 필요합니다. 수정된 전체 설정을 반환합니다.")
     @PatchMapping("/notification-settings")
     public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
             @Valid @RequestBody NotificationSettingsRequest request,
-            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
+            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
         NotificationSettingsResponse response = memberService.updateNotificationSettings(
                 authenticatedMember.memberId(), request);
 
@@ -58,9 +67,10 @@ public class MemberController {
                         "알림 설정 수정에 성공했습니다.", response));
     }
 
+    @Operation(summary = "출석 AI 인사이트 조회", description = "로그인한 회원의 출석 기록을 요약한 인사이트를 조회합니다. 기록이 없으면 안내 문구를, AI 비활성화·호출 실패 시 기본 출석 요약을 반환합니다.")
     @GetMapping("/attendance-insight")
     public ResponseEntity<ApiResponse<AttendanceInsightResponse>> getAttendanceInsight(
-            @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
+            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
         String insight = attendanceInsightService.getAttendanceInsight(authenticatedMember.memberId());
 
         AttendanceInsightResponse response = new AttendanceInsightResponse(insight);
diff --git a/src/main/java/com/sdpcheck/sdpcheck/global/security/config/SecurityConfig.java b/src/main/java/com/sdpcheck/sdpcheck/global/security/config/SecurityConfig.java
index 2ba4573..bf67d1a 100644
--- a/src/main/java/com/sdpcheck/sdpcheck/global/security/config/SecurityConfig.java
+++ b/src/main/java/com/sdpcheck/sdpcheck/global/security/config/SecurityConfig.java
@@ -28,6 +28,10 @@ public class SecurityConfig {
                                                    SecurityExceptionHandler exceptionHandler) throws Exception{
         var paths = PathPatternRequestMatcher.withDefaults();
         var publicEndpoints = new OrRequestMatcher(
+                paths.matcher(HttpMethod.GET, "/swagger-ui.html"),
+                paths.matcher(HttpMethod.GET, "/swagger-ui/**"),
+                paths.matcher(HttpMethod.GET, "/v3/api-docs"),
+                paths.matcher(HttpMethod.GET, "/v3/api-docs/**"),
                 paths.matcher(HttpMethod.POST, "/api/v1/auth/signup"),
                 paths.matcher(HttpMethod.POST, "/api/v1/auth/login"),
                 paths.matcher(HttpMethod.POST, "/api/v1/auth/logout"),
diff --git a/src/main/resources/application-prod.properties b/src/main/resources/application-prod.properties
index 2b76ceb..9cafc52 100644
--- a/src/main/resources/application-prod.properties
+++ b/src/main/resources/application-prod.properties
@@ -2,4 +2,7 @@
 spring.datasource.url=${DB_URL}
 spring.datasource.username=${DB_USERNAME}
 spring.datasource.password=${DB_PASSWORD}
-jwt.refresh-cookie-secure=true
\ No newline at end of file
+jwt.refresh-cookie-secure=true
+
+springdoc.api-docs.enabled=false
+springdoc.swagger-ui.enabled=false
```

New: `src/main/java/com/sdpcheck/sdpcheck/global/config/OpenApiConfig.java`

```java
package com.sdpcheck.sdpcheck.global.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
```
