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
- Partner nicknames and avatars, with date and keyword searchable records
- Transactional score updates with retry protection
- A shared relationship start date in Settings, with days together shown on the home card
- A shared agreement list with single-column cards, optional dates and multiline notes
- Either partner can edit, complete, restore or delete agreements, with conflict protection
- Gift rewards with partner-set score goals, a preparer-owned timeline and paged history
- In-app updates that download and install a new signed APK without leaving the app

## Android development

1. Open the repository in Android Studio.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` to the local Android SDK path.
4. Keep `api.baseUrl=https://api.zengdeming.cn` unless using a local API.
5. Run the `app` configuration or build `assembleDebug`.

Run `testDebugUnitTest lintDebug assembleDebug` to check the Android app before
merging; the Android workflow runs the same checks.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Days together

Set **在一起的日期** in Settings. The home card displays `n Days` below TOGETHER,
centered above the two avatars, in soft pink Dancing Script handwriting.
The start date counts as day 1. Counts use the device's local calendar date and
refresh at midnight and when the app returns to the foreground. Clearing and
saving the date hides the count. Either partner can update the shared date.

Migration `0013_together_since.sql` adds the shared column. The API applies it
automatically on startup; couples that existed before it starts with no date set.

## Agreement list

The **约定清单** destination records things both partners want to do together.
Each card occupies one row and grows to fit its full title and notes.
Titles are required (up to 40 Unicode code points). Notes support multiple lines
(up to 2000 code points), and the hoped-for completion date is optional.
Completed agreements remain in a
separate view and can be restored, including undoing the latest completion.
Deleting an agreement removes it from both partners' lists after confirmation.

App messages appear in notice boxes within the current page's layout.
They disappear after three seconds, or five seconds for errors and completion
messages with an undo action (longer when requested by accessibility settings).
Completion shows one message; dismissing it also removes its undo action.

Server-backed pages can refresh with the circular-arrow toolbar icon or a pull
gesture at the top of the list. Refresh progress appears beside the page title
as a light-weight `（刷新中...）` label, cycling through one to three dots. Record
refreshes preserve the current search and date filters. Settings uses a matching
outline gear icon. Scaffold padding is consumed before keyboard padding so the
same bottom space is not reserved twice when typing.

The list refreshes on entry, on returning to the foreground and on manual
refresh. Records are scoped to the active couple. Version checks prevent silent
overwrites, and creation keys prevent duplicate records after network retries.
Migration `0012_agreements.sql` creates the table; the API applies it on startup.

Backend checks can be run with `cd backend && go test -race ./...`. To include
database integration tests, set `AGREEMENTS_TEST_DATABASE_URL` to a disposable
PostgreSQL database; each test creates and removes its own isolated schema.

## Gift rewards

**阶段性奖励** turns points into a present. Each partner sets the other's goal:
you decide how many points your partner must reach before they can ask you for
something. Once their score is at or above that target, they raise a request with
a title (up to 40 characters) and an optional note (up to 200). Only the partner
who will prepare the gift can accept it, and only they own the plan afterwards:
one to eight steps of up to twelve characters, with completion times stamped as
progress advances.

Completing the last step marks the gift received. Only the requester can cancel,
and only while it is still requested or active. Either partner can delete a
received or cancelled record after confirming. The API allows one in-flight gift
per person; a second attempt is rejected as a conflict instead of duplicating the
request. Finished and cancelled gifts move to **过往礼物**, twenty per page,
newest first.

## In-app updates

A commit touching `app/**` builds a signed release APK, uploads `favorapp.apk` and
`latest.json` to `favor-app/apks` on the server, and publishes the version through
`GET /api/v1/app/latest`. The app checks on launch and from **检查更新** in
Settings, then downloads from `GET /api/v1/app/apk` when the published
`versionCode` is higher. Versions follow `1.0.<build number>`, so every release is
monotonic and the in-place install works without uninstalling.

The GitHub Actions secrets, keystore handling and the server volume mount are
documented in `ARCHITECTURE_AND_UPDATE.md`. Losing the keystore breaks update
installs for every device that already has the app.

## Server deployment

The server keeps ports 80 and 443 for the existing reverse proxy. The API is
bound to `127.0.0.1:8080`; PostgreSQL is available only inside Docker.
Favor's Compose manages only the API (128 MB limit). PostgreSQL 16 is managed
independently at `/home/dutu/workspace/infra/postgres` as `shared-postgres` with
the `shared-postgres-data` volume and a 256 MB limit. The API joins the external
`shared-db` and `zengdeming-edge` networks.

The administrator first creates the `favorapp` database and its ordinary owner
role `favorapp_app`. Use that role's credentials in the server `.env`; it has no
CONNECT permission on the separate `mywebsites` database. The bootstrap role is
retained as `cluster_owner` with NOLOGIN; only `db_admin` is used for cluster administration.
Neither application should use administrator credentials.

Upload `compose.yaml` and create a server `.env` from `.env.example` with the
existing application password. The public backend image does not require GHCR
login. Run:

```bash
docker compose -f compose.yaml pull api
docker compose -f compose.yaml up -d --wait api
```

To pin a tested image or roll back, set `IMAGE_TAG` in `.env` to a commit SHA
published by GitHub Actions, then run the same two commands with `-f compose.yaml`.

The API runs migrations on startup. Configure the existing reverse proxy to
forward `https://api.zengdeming.cn` to `http://127.0.0.1:8080`.

Set the `favorapp-api` GitHub Package to **Public** in its settings. A new package may default to
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

GitHub Actions tests and publishes only the Go API image:

```text
ghcr.io/dutu007/favorapp-api
```

The independent PostgreSQL image is managed by infrastructure configuration;
application releases do not rebuild or recreate the database container. Startup
migrations only move the schema forward; rolling back an image does not undo them.

The Android workflow runs `testDebugUnitTest`, `lintDebug` and `assembleDebug`
against the production API and uploads the debug APK as the
`FavorApp-debug-apk` artifact. When the signing secrets are configured it also
builds a signed release APK, uploads it as `FavorApp-release-apk`, and publishes
it to the server so both phones can update in place.
