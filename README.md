# FavorApp

FavorApp is an Android app for two partners to record mutual favor scores.

## Current scope

- Email registration and login through Supabase Auth
- One-time invitation code matching
- Mutual score balances and an append-only change history
- Optional minimum and maximum score limits
- Realtime updates for both partners

The invitation flow intentionally generates a code and provides a copy action. Users send the copied code themselves.

## Repository layout

- `docs/architecture.md` — product and technical design
- `supabase/migrations/20260925130000_initial.sql` — database schema, RLS, and transactional RPCs
- `.github/workflows/supabase-migrations.yml` — applies pending migrations after changes reach `main`

## Supabase setup

1. Create a Supabase project.
2. Enable email/password authentication and decide whether email confirmation is required.
3. Apply the migrations in `supabase/migrations/`.
4. Copy `local.properties.example` to `local.properties`, then add the Supabase project URL and publishable key.
5. Open the project in Android Studio and run the `app` configuration.

The Android client must never contain a `service_role` key.

## Automatic migrations

The GitHub Actions workflow applies only pending files under `supabase/migrations/` after a push to `main`. Add these encrypted repository secrets before the first migration deployment:

- `SUPABASE_ACCESS_TOKEN` — a Supabase personal access token
- `SUPABASE_DB_PASSWORD` — the database password for the `FavorApp` project

The workflow targets project ref `lclcldzcndbkluelfphe`.

## Android local setup

Create `local.properties` in the repository root. It is ignored by Git:

```properties
sdk.dir=C\\:\\Users\\<your-user>\\AppData\\Local\\Android\\Sdk
supabase.url=https://lclcldzcndbkluelfphe.supabase.co
supabase.publishableKey=<your-publishable-key>
```

The app uses only the publishable key. Never put a Supabase `service_role` or secret key in `local.properties` or the APK.
