# Release notes

## v1.0.0 (build 2) - Final PoE  -  changes since the Part 2 prototype (v0.2.0)

### Innovative features added in this version
These are the three innovative features promised in Part 1. The first shipped in the prototype; the other two are **new** in this release.

| Innovative feature (Part 1) | Status | What it does |
|---|---|---|
| **AI Note-to-Quiz generator** | In the prototype; **improved** | Quizzes are now generated from your *saved* note (the app syncs it first), tagged "AI" or "smart text extraction", and attempts/scores are tracked. |
| **Gamification: badges and levels** (*new*) | **Added in v1.0.0** | 10 milestone badges (first check-in, 3/7/14/30-day streaks, habit collector, perfect day, note taker, quiz starter, quiz ace), levels every 10 check-ins, and a celebration message when you unlock one. Works offline. |
| **GitHub-style streak board** (*new*) | **Added in v1.0.0** | A 15-week colour grid (darker = more of that day's habits done), a 28-day history for every habit colour-coded by priority, and your 30-day completion rate. |

### New required features
- **Single sign-on:** "Sign in with Google" (Android Credential Manager). The server verifies Google's ID token before issuing its own session, and links to an existing account with the same verified email.
- **Offline mode with sync:** habits, ticks and notes are stored in a local Room database and work with no internet. A WorkManager job syncs when the phone is online (also every ~15 min). Conflicts use **last write wins** by timestamp. A banner and Settings > "Offline sync" show pending changes.
- **Real-time push notifications (Firebase Cloud Messaging):** habit reminders at your reminder time, "your streak is at risk" alerts at 20:00, and a "Send test notification" button. Android 13+ permission is requested once.
- **Multi-language:** English, isiZulu and Afrikaans, switchable inside Settings (all screens, the app's own input-validation messages and notifications).

### Other improvements
- Delete your account and all data from inside the app (Settings > Delete account) - required by Google Play.
- Streaks are calculated on the phone as well as on the server (same rules, covered by tests on both sides).
- Settings: language picker, cloud/sync status, notification permission handling, "signed in with" indicator.
- Release build: R8 shrinking, signing configuration read from a git-ignored `keystore.properties`, adaptive + themed app icon, Play Store graphics.
- Targets Android 16 (API 36) as Google Play now requires; Kotlin 2.2, AGP 8.9.1, Room 2.7.
- API: Google login, device registration, test push, reminder scheduler endpoint, `updatedAt` and full completion history on habits.
- CI: backend tests, Android unit tests, debug build and an R8 release build.

### Known limitations
- Generating a quiz needs internet (it runs on the server); everything else works offline.
- Reminders are sent by the server once a minute; on Render's free tier an external cron ping is needed to keep that running (see `docs/PART3_SETUP.md`).
- Error messages that come back from the server (for example "Invalid email or password") are still in English.
- The isiZulu and Afrikaans translations were machine-assisted and should be reviewed by a first-language speaker.
- Badges that depend on quizzes are only re-evaluated while online (already-earned badges are remembered).

## v0.2.0-prototype - Part 2
Registration/login (bcrypt + JWT), settings (theme, notifications switch), REST API on Render + MongoDB Atlas, habit tracker with streaks, note editor with search and Markdown preview, note-to-quiz with scoring, unit tests and GitHub Actions.
