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
- Transactional score updates with retry protection

## Android development

1. Open the repository in Android Studio.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` to the local Android SDK path.
4. Keep `api.baseUrl=https://api.zengdeming.cn` unless using a local API.
5. Run the `app` configuration or build `assembleDebug`.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Server deployment

The server keeps ports 80 and 443 for the existing reverse proxy. The API is
bound to `127.0.0.1:8080`; PostgreSQL is available only inside Docker.
The Compose limits reserve at most 256 MB for PostgreSQL and 128 MB for the API,
384 MB total. These limits are suitable for the expected small, low-frequency
workload and leave memory for the host system and Docker.
The database service uses the official `postgres:16-alpine` image.

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
