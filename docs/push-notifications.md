# Firebase push notifications

## Enable

The backend uses Firebase project `sdp-check`. Configure the server process:

```text
PUSH_ENABLED=true
GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/sdp-check-firebase-admin.json
```

In IntelliJ, configure both variables on `SdpcheckApplication` and use the actual
local credential path. Never commit a service-account key. Spring's optional
`.env` import can supply `PUSH_ENABLED`, but does not export credentials into the
process environment used by Google's default credential lookup. Containers must
mount the key read-only and set the path inside the container.

Production Compose mounts `/home/ubuntu/sdpcheck-config/firebase` read-only at
`/run/secrets/firebase`. Store the key there as `sdp-check-firebase-admin.json`
and set `PUSH_ENABLED=true` in the server's `.env.prod`. The host directory may
be overridden with `FIREBASE_CREDENTIALS_DIR`. The container credential path is
provided by Compose. The application runs as container UID 10001: the mounted
directory and key must be readable by that UID (for example, owner 10001:10001,
directory mode 700 and key mode 600). A key owned only by ubuntu with mode 600
will not be readable inside the container.

Push defaults to disabled so tests and environments without Firebase credentials
can start. Disabled environments do not enqueue notifications. Flyway applies V18
on startup; no additional API endpoint is required.

## Events and payload

| Trigger | Recipients | FCM data |
| --- | --- | --- |
| Successful notice creation | Onboarded MEMBER/ADMIN with `newNoticeEnabled=true` | `type=NEW_NOTICE`, `noticeId=<UUID>` |
| Successful admin session start | Session attendance targets with `sessionStartEnabled=true` | `type=SESSION_STARTED`, `sessionId=<UUID>` |

All registered devices for eligible members receive the notification. Notice
updates/deletes and session creation do not send notifications. Starting a session
means calling the existing admin start API, rather than reaching its scheduled time.
The visible title identifies the event and the body contains the notice/session title.
Every message also includes `eventId=<type>:<resource UUID>` for client deduplication.
The client must handle foreground display, permission requests, routing after taps,
token refresh registration and logout token deletion. iOS delivery additionally
requires APNs configuration in the Firebase project.

Production session targets include MEMBER and ADMIN semester members, excluding
the admin who presses start, as defined by the existing session roster logic.

## Delivery and operations

Creation/start and per-device outbox inserts share the same database transaction.
A worker polls every five seconds and locks up to 100 due rows with
`FOR UPDATE SKIP LOCKED`, allowing multiple backend instances. Firebase calls run
on the scheduler thread, outside the originating HTTP request. Success/failure is
recorded per device; successful devices are not included in normal retries.

Before sending, the worker rechecks ownership, current notification settings,
onboarding and resource state. Deleted notices, inactive sessions, transferred
tokens and disabled preferences are skipped. Notice jobs expire after 24 hours;
session jobs after one hour. Token deletion cascades to its queued/history rows.
`UNREGISTERED` removes only the matching token and owner. Payload/auth errors keep
tokens and become `FAILED`. Temporary INTERNAL/UNAVAILABLE/QUOTA_EXCEEDED errors
retry up to five total attempts with exponential backoff from 60 seconds, jitter,
and the server's `Retry-After` when present. Firebase SDK retries may also occur.
Token values and Firebase error messages are never logged by this worker.

Monitor `push_outbox.status`, `attempts`, `next_attempt_at`, and warning logs.
Terminal rows are retained for inspection; arrange retention cleanup as appropriate.
Delivery is at least once: a crash after FCM accepts a message but before DB commit
can cause a duplicate. Clients should deduplicate using `eventId`. FCM acceptance
does not prove that a device displayed the notification.

## Verification

Tests use mocked Firebase responses and PostgreSQL Testcontainers, without sending
real notifications. For a device smoke test, register an FCM token from `sdp-check`,
enable its account's notification setting, create a notice/start its target session,
and check `SENT` plus foreground/background/tap behavior on the device. Disable the
setting and repeat to verify suppression. Never use production tokens in tests.
