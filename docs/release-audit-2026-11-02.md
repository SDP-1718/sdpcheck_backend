# Develop release audit — 2026-11-02

All repository PRs were checked against develop 8ccba66. #47 alone remained open;
its base was the old session branch. Its check-in changes are ported onto develop,
preserving roster targets and renumbering attendance IDs from V11 to V17.
Existing V1–V16 scripts are preserved. Stored ABSENT records now count in attendance
summary and AI insight (session end already writes these records).

## Image feature coverage

| Group | Included APIs |
|---|---|
| Auth | Kakao start/callback, onboarding, logout, refresh, access-token; legacy signup/check-login-id retained but hidden |
| Mypage | member info, attendance history/summary/AI insight, notification GET/PATCH, push token POST/DELETE |
| Home/read | home, notices list/detail, schedules list |
| Admin members | generations and role change (#52/#58) |
| Statistics | summary, member statistics, Excel export (#53/#55) |
| Admin notice | list/create/update/delete, AI draft MOCK (#36/#48/#50) |
| Admin schedule | list/create/update/delete (#12/#36) |
| Sessions | create/update/delete/next/detail/start/end/QR/live attendance/manual attendance; current session and check-in (#33/#57/#47) |
| Images | POST /api/v1/files multipart upload and GET /api/v1/files/{fileId}; replaces image screenshot's obsolete presigned-url path, documented in notice-image-storage.md |

AI notice drafts remain the approved MOCK implementation; attendance insight uses OpenAI.
PR #20 was superseded by #33, #1 is the closed Discord test. Older Docker PR #5
is superseded by #6/#9. Feature PRs merged into other feature branches were integrated
through #23/#25/#53/#55; no unrelated old branch is blindly merged.

## PR inventory (snapshot before integration)

| PR | State | Base | Title |
|---|---|---|---|
| #58 | merged | develop | [feat]: #56 회원 권한 변경 API 구현 |
| #57 | merged | develop | 34 feat 진행중인 세션 관련 api 구현 |
| #55 | merged | develop | [feat]: #39 #40 회원별 통계·엑셀 다운로드 develop 통합 |
| #54 | merged | develop | [feat] #19 온보딩 완료 시 운영 학기 회원 명단 자동 등록 |
| #53 | merged | develop | [fix] develop에 홈·통계 API 및 학기별 회원 명단 통합 |
| #52 | merged | develop | [feat]: #51 관리자 기수 목록 조회 API 구현 |
| #50 | merged | develop | [feat]: AI 공지 초안 생성 API 구현 |
| #48 | merged | develop | [Feat]: 관리자 공지 수정/삭제 API 구현 |
| #47 | not merged | 34-feat-진행중인-세션-관련-api-구현 | [feat]: #41 출석체크 API 명세 반영 및 동시성 처리 |
| #46 | merged | feat/#39-member-attendance-statistics-api | [feat]: #40 통계 엑셀 다운로드 API 구현 |
| #45 | merged | feat/#38-admin-statistics-summary-api | [feat]: #39 회원별 출석 통계 조회 API 구현 |
| #44 | merged | feat/#31-my-attendance-summary-api | [feat]: #38 통계 요약 조회 API 구현 |
| #43 | merged | feat/#31-my-attendance-summary-api | [feat]: #37 홈 조회 API 구현 |
| #36 | merged | develop | [feat]: #35 관리자 일반 일정 수정·삭제 API 구현 및 Swagger 문서 정리 |
| #33 | merged | develop | [Feat:세션 crud 및 세션 시작 종료 api 구현 |
| #32 | merged | develop | [feat]: #31 내 출석 요약 조회 API 구현 |
| #30 | merged | feat/#27-attendance-insight | [feat]: #29 내 정보 조회 API 구현 및 인증·마이페이지 Swagger 문서 정리 |
| #28 | merged | feat/#24-notification-settings | [feat]: #27 출석 AI 인사이트 조회 API 구현 |
| #26 | merged | feat/#22-push-token | [feat]: #24 notification settings |
| #25 | merged | develop | [chore]: 공지·일정·출석 조회 기능 develop 통합 |
| #23 | merged | develop | [feat]: #22 push token |
| #21 | merged | develop | [feat]: #19 kakao auth |
| #20 | not merged | main | [feat]: #10 세션 생성 수정 삭제 시작 api 구현 |
| #18 | merged | feat/#15-schedule-list-api | [feat]: #17 내 출석 이력 조회 API 구현 |
| #16 | merged | feat/#13-notice-read-apis | [feat]: #15 일정 목록 조회 API 구현 |
| #14 | merged | feat/admin-notice-schedule-apis | [feat]: #13 공지사항 목록·상세 조회 API 구현 |
| #12 | merged | develop | Feat/admin notice schedule apis |
| #11 | merged | develop | [feat]: #7 회원가입·로그인 및 JWT 인증 기능 구현 |
| #9 | merged | develop | [chore] #8 Docker 기반 배포 환경 구성 |
| #6 | merged | develop | [chore]: #4 Docker 애플리케이션 실행 환경 구성 |
| #5 | merged | main | [chore]: #4 Docker 애플리케이션 실행 환경 구성 |
| #3 | merged | develop | [feat]: #2 도메인 패키지 및 공통 응답·예외 처리 기반 구성 |
| #1 | not merged | develop | Test/discord webhook |

## Deployment boundary

Main is not merged or pushed. Environment/EC2 are not modified. Before deployment,
verify the existing DB's flyway_schema_history against V1–V17 (especially previously
renumbered session scripts); do not repair or reset automatically. Configure current
semester, HTTPS, Kakao callback, frontend/CORS and OpenAI credentials on the server.
