# FavorApp architecture

## Product decisions

- A relationship contains exactly two users in the MVP.
- Each user has an independent score for the other person.
- A score change is an integer delta and may include an optional note.
- The home screen combines both users' events and sorts them newest first.
- An invitation is generated once, copied to the clipboard, and sent by the user manually.
- Invitation codes expire after 24 hours and can be accepted only once.
- Empty minimum and maximum values mean that the score is unbounded on that side.
- The initial value is applied to both balances while the relationship has no score events. After the first event, changing the initial value does not rewrite history or current balances.

## Android stack

- Kotlin
- Jetpack Compose and Material 3
- ViewModel plus StateFlow
- Repository layer around `supabase-kt`
- Supabase Auth, PostgREST, Realtime, and PostgreSQL RPCs

Suggested package boundaries:

```text
ui/              Compose screens and reusable components
feature/auth/    Login, registration, password reset
feature/pairing/ Invite creation and acceptance
feature/home/    Score cards and combined event feed
feature/settings Relationship and profile settings
data/            Supabase client, DTOs, repositories
domain/          Models and use cases
```

## Navigation states

```text
Signed out -> Auth screens
Signed in without a couple -> Pairing screen
Signed in with an active couple -> Home screen
```

## Score write path

The client calls `add_score_event`. The database function checks membership, locks the target balance, applies the limits, updates the current balance, and inserts the history row in one transaction. The client never calculates or directly writes the authoritative score. Initial value and range changes go through `update_score_settings`; changing the range is rejected when an existing balance would fall outside it.

## Realtime path

Subscribe to `couple_scores` and `score_events` for the active couple. A score update refreshes the two score cards and prepends the new event to the combined feed. Reconnect by reloading the active couple and the first page of events.

## Security model

Every public table has RLS enabled. Couple members can read only their own relationship data. Invitation creation, invitation acceptance, and score changes go through security-definer RPCs that validate `auth.uid()` inside PostgreSQL.
