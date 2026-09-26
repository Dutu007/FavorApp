# FavorApp architecture

## Runtime topology

```text
Android -> HTTPS 443 -> reverse proxy -> Go API :8080 -> PostgreSQL :5432
```

The reverse proxy owns public ports 80 and 443. Docker publishes the API only
on `127.0.0.1:8080`, while PostgreSQL has no host port mapping.

## Android

- Kotlin, Jetpack Compose, Material 3
- ViewModel plus StateFlow
- Ktor JSON client
- Android private preferences for the bearer session token

## Backend

- Go `net/http`
- `pgx` PostgreSQL driver
- Argon2id password hashes
- Random bearer sessions with hashed tokens
- Server-side authorization for every couple operation

## Product behavior

- A relationship contains exactly two users.
- An invite is copied and sent manually, expires after 24 hours, and is single-use.
- Each user has an independent score for the other person.
- Score changes are integer deltas with optional notes.
- The combined event feed is newest first.
- Empty minimum and maximum values mean unbounded on that side.
- Before the first event, changing the initial score updates both balances.
- After the first event, range changes cannot exclude an existing balance.

## Authentication rules

- Username: 3-20 characters, starts with a letter, then ASCII letters, digits, or `_`.
- Password: 8-64 characters with uppercase, lowercase, digit, and special character.
- Usernames are normalized to lowercase and unique.

## Deployment

GitHub Actions tests and publishes the public image
`ghcr.io/dutu007/favorapp-api`. The server uploads `compose.yaml` and uses Docker
Compose with a persistent PostgreSQL volume. The API runs embedded SQL
migrations on startup. Secrets stay in the server `.env`.
