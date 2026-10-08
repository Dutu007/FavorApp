# FavorApp

FavorApp is an Android app for two partners to record mutual favor scores.

The app uses a self-hosted Go API and PostgreSQL. The production API address is:

```text
https://api.zengdeming.cn
```

## Features

- Username and password registration and login
- One-time invitation code matching
- Mutual score balances and newest-first history
- Optional minimum and maximum score limits
- Score rule changes applied only after the partner approves
- Partner nicknames and date/keyword searchable records
- Transactional score updates with retry protection
- A shared agreement list with two-column cards, optional dates and multiline notes
- Either partner can edit, complete, restore or delete agreements, with conflict protection

## Android development

1. Open the repository in Android Studio.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` to the local Android SDK path.
4. Keep `api.baseUrl=https://api.zengdeming.cn` unless using a local API.
5. Run the `app` configuration or build `assembleDebug`.

Run `testDebugUnitTest lintDebug assembleDebug` to check the Android app before
merging; the Android workflow runs the same checks.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Agreement list

The new destination, **约定清单**, records things both partners want to do
together. Cards are arranged in two columns. Titles are required (up to 40
Unicode code points); notes support multiple lines (up to 2000 code points) and
the hoped-for completion date is optional. Completed agreements remain in a
separate view and can be restored, including undoing the latest completion.
Deleting an agreement removes it from both partners' lists after confirmation.

App messages appear in notice boxes within the current page's layout.
They disappear after three seconds, or five seconds for errors and completion
messages with an undo action (longer when requested by accessibility settings).
Completion shows one message; dismissing it also removes its undo action.

The list refreshes on entry, on returning to the foreground and on manual
refresh. Records are scoped to the active couple. Version checks prevent silent
overwrites, and creation keys prevent duplicate records after network retries.
The API applies migration `0012_agreements.sql` on startup. Deploy the backend
from this branch together with the Android update before using this feature.

Backend checks can be run with `cd backend && go test -race ./...`. To include
database integration tests, set `AGREEMENTS_TEST_DATABASE_URL` to a disposable
PostgreSQL database; each test creates and removes its own isolated schema.

## Server deployment

The server keeps ports 80 and 443 for the existing reverse proxy. The API is
bound to `127.0.0.1:8080`; PostgreSQL is available only inside Docker.
The Compose limits reserve at most 256 MB for PostgreSQL and 128 MB for the API,
384 MB total. These limits are suitable for the expected small, low-frequency
workload and leave memory for the host system and Docker.
The database service uses a GHCR image built from the official `postgres:16-alpine`
image.

Upload `compose.yaml` and create a server `.env` from `.env.example` with a long
random database password. The backend image is public, so no GHCR login is
required. Run:

```bash
docker compose -f compose.yaml pull
docker compose -f compose.yaml up -d
```

To pin a tested image or roll back, set `IMAGE_TAG` in `.env` to a commit SHA
published by GitHub Actions, then run the same two commands with `-f compose.yaml`.

The API runs migrations on startup. Configure the existing reverse proxy to
forward `https://api.zengdeming.cn` to `http://127.0.0.1:8080`.

Set both GitHub Packages entries, `favorapp-api` and `favorapp-postgres`, to
**Public** in their package settings. A newly created package may default to
Private; a private package requires GHCR login on the server.

Example Nginx location:

```nginx
server {
    server_name api.zengdeming.cn;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

The existing HTTPS certificate manager should issue the certificate for
`api.zengdeming.cn`.


## CI/CD

GitHub Actions builds the Go image and the PostgreSQL wrapper image. It publishes
them to:

```text
ghcr.io/dutu007/favorapp-api
ghcr.io/dutu007/favorapp-postgres:16-alpine
```

The Android workflow builds an APK configured for the production API and uploads
it as the `FavorApp-debug-apk` artifact.
