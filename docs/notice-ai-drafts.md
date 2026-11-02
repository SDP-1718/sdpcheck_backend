# AI 공지 초안 생성

POST /api/v1/admin/notices/ai-drafts

온보딩 완료 ADMIN이 {"memo":"공지에 포함할 메모"}를 보내면 실제 OpenAI Responses API로
제목(1~100자), 본문(1~10000자), mode: AI를 반환합니다. 메모는 공백 제거 후 1~2000자이며
미지원 필드와 문자열 이외 값은 거부합니다. 초안을 저장하거나 공지를 자동 게시하지 않습니다.

기존 OPENAI_ENABLED=true, OPENAI_API_KEY, OPENAI_MODEL 설정을 재사용합니다.
NOTICE_AI_DRAFT_ENABLED=false 또는 OpenAI 설정 누락 시 503 FEATURE_UNAVAILABLE입니다.
AI 오류·시간 초과·거절·불완전 응답·잘못된 출력도 503이며 MOCK으로 대체하지 않습니다.
연결 제한 3초, 응답 제한 30초, store=false 및 strict JSON schema를 사용합니다.
날짜·장소 등 없는 정보는 지어내지 않도록 지시하며 관리자는 게시 전 내용을 검토해야 합니다.

회원별 60초 10회 제한과 Retry-After는 유지합니다. 설정 누락은 제한을 소모하지 않으며,
시도된 AI 요청은 실패하더라도 제한에 포함됩니다. 요청 제한용 DB 트랜잭션은 외부 호출 전 끝납니다.
성공은 200 NOTICE_DRAFT_CREATED와 Cache-Control: no-store입니다.
프론트는 기존 MOCK 대신 AI 모드를 처리해야 합니다.

일반 CI는 OpenAI HTTP mock과 PostgreSQL 통합 테스트로 검증합니다.
NOTICE_AI_LIVE_TEST=true일 때만 OpenAiNoticeDraftLiveTest가 로컬 .env로 실제 유료 호출합니다.
키·메모·생성 결과·외부 오류 본문은 로그에 남기지 않습니다.