# FavorApp Self-Hosted Backend Design

## Goal

Replace the Supabase client and hosted database with a self-hosted backend at
`https://api.zengdeming.cn`, backed by PostgreSQL on the user's Alibaba Cloud
Lightweight Application Server.

## Deployment topology

The existing website keeps ports 80 and 443. DNS `api.zengdeming.cn` points to
the server. The existing reverse proxy terminates HTTPS and forwards API
requests to `127.0.0.1:8080`. The Go API and PostgreSQL run in Docker Compose.
PostgreSQL listens only on the internal Compose network and is never exposed
to the public Internet.

```text
Android -> HTTPS 443 -> existing reverse proxy -> API :8080 -> PostgreSQL :5432
```

## Backend

The API is a small Go service using `net/http`, `pgx`, and `golang.org/x/crypto`.
It owns authentication, authorization, invite matching, score transactions,
and event history. Passwords use Argon2id. Sessions use random bearer tokens;
only token hashes are stored in PostgreSQL. Every protected handler derives the
user from the session and checks couple membership before reading or writing.

Registration accepts a username of 3-20 characters, beginning with a letter and
containing only ASCII letters, digits, and underscores. Usernames are stored in
lowercase. Passwords are 8-64 characters and must contain uppercase, lowercase,
digit, and special characters.

## API surface

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/me`
- `POST /api/v1/invites`
- `POST /api/v1/invites/accept`
- `GET /api/v1/couple`
- `POST /api/v1/scores/events`
- `PUT /api/v1/score-settings`
- `GET /api/v1/score-events`

The Android client uses JSON over HTTPS and stores the session token in Android
preferences. Score writes are transactional and support an idempotency key so
network retries do not duplicate an event.

## Database

PostgreSQL stores users, sessions, couples, invites, score settings, current
balances, and append-only score events. SQL migrations run when the API starts
or through the deployment command. A Docker volume persists database data.

## CI/CD

GitHub Actions runs Go tests, builds a multi-stage Docker image, and pushes
`ghcr.io/dutu007/favorapp-api` with commit and `latest` tags. The Android build
uses `https://api.zengdeming.cn` as its API base URL and uploads the debug APK.

The server deploys by pulling the selected image and running Docker Compose.
Secrets remain on the server or in GitHub Actions secrets; no passwords or
private deployment keys are committed.

## Removal scope

Remove Supabase Android dependencies, client initialization, Supabase keys,
Supabase migration deployment workflow, and Supabase-only SQL/RLS code. The
PostgreSQL schema is recreated as backend-owned migrations with equivalent
authorization checks in Go transactions.

## Acceptance criteria

1. A clean CI run tests and builds the API image.
2. `GET /api/v1/health` works through `https://api.zengdeming.cn`.
3. Android can register, log in, log out, match by copied invite code, view
   both scores, add or remove points, view newest-first history, and update
   initial/min/max settings.
4. The public server exposes only existing ports 80 and 443; PostgreSQL and API
   container ports are not publicly reachable.
