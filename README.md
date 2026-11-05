# RoutinePulse

> A habit tracker and study companion for students - **OPSC6312 Open Source Coding (Intermediate), final PoE.**

RoutinePulse treats studying as a trackable habit: tick off daily habits, keep your lecture notes in one place and turn any note into a
multiple-choice quiz with one tap. It works offline, signs in with Google, sends reminders and speaks English, isiZulu and Afrikaans.

| | |
|---|---|
| **Demo video** | `TODO: paste your unlisted YouTube link here` |
| **Live API** | `API_BASE_URL=https://exciting-hourly-movies-zinc.trycloudflare.com/` |
| **CI status** | `TODO: ![CI](https://github.com/<you>/<repo>/actions/workflows/ci.yml/badge.svg)` |
| **Release notes** | [RELEASE_NOTES.md](RELEASE_NOTES.md) |
| **How to run it** | [docs/RUN_GUIDE.md](docs/RUN_GUIDE.md) (start here) |
| **Play Store** | `TODO: screenshot / link of your Play Console upload` |


## Features

| Feature | Details |
|---|---|
| Register / log in | Email + password (bcrypt-hashed on the server, JWT session) with inline validation |
| **Single sign-on** | Sign in with Google (Credential Manager). The server verifies Google's ID token before issuing its own session |
| Settings | Language, theme, notifications (with Android 13+ permission), offline-sync status, cloud status, account, log out, delete account |
| REST API | Custom Node.js + Express API on Render, MongoDB Atlas database |
| Habit tracker | Daily / weekday / custom habits, reminder time, priority, live streaks |
| Note editor | Subjects, tags, search, Markdown preview |
| Note-to-Quiz *(innovative)* | One tap -> multiple-choice quiz (AI if configured, otherwise rule-based); retake and track scores |
| **Offline mode with sync** | Room database + WorkManager; last-write-wins conflict rule; "changes waiting to sync" banner |
| **Push notifications** | Firebase Cloud Messaging: habit reminders, streak-at-risk alerts, test notification |
| **Multi-language** | English, isiZulu, Afrikaans (203 strings each) switchable at runtime |
| **Badges and levels** *(innovative, feature 4)* | 10 milestone badges, levels, unlock messages |
| **Streak board** *(innovative, feature 5)* | GitHub-style 15-week grid, 28-day history per habit, 30-day completion rate |
| Play Store ready | API 36 target, signing config, R8, icon, listing text, privacy policy, in-app account deletion |

## Design considerations
- **Follows the Part 1 design** (navy `#1B365D` / emerald `#2ECC71`, bottom navigation, Node/Express + MongoDB Atlas on Render, Room, FCM, Google Sign-In, three languages).
- **Offline-first:** the UI only talks to the local Room database. Every change is marked "dirty" and a background job pushes it when the phone is online, then pulls the server's data. A record changed on both sides keeps the version with the **latest timestamp** (the server compares the app's `clientUpdatedAt` with its own `updatedAt`). Ticking a habit does not count as an edit, so it never causes a conflict.
- **Streaks are computed twice** (server `streak.js`, phone `StreakCalculator.kt`) with identical rules, each covered by tests, so offline streaks match the server.
- **Push messages are data-only:** the server sends `type`, habit title etc.; the app builds the visible text, so notifications follow the language chosen in Settings and respect the in-app switch.
- **Security:** no secrets in the repo (`.env`, keystore, service-account key and `google-services.json` are git-ignored); HTTPS only; passwords hashed with bcrypt; request bodies never logged; the Google ID token is verified server-side; data is wiped from the phone on logout or when a different person logs in; the cron endpoint needs a shared secret.
- **Deliberate changes from Part 1:** bcrypt instead of salted SHA-256 (SHA-256 is too fast for passwords); questions embedded in the Quiz document; navy text on emerald buttons for contrast; forgot-password email flow not implemented (needs an email provider).
- **Known limits:** quiz generation needs internet; translations need review by first-language speakers; server error messages are English.

## Tech stack
Android: Kotlin, Jetpack Compose (Material 3), Navigation, Retrofit/OkHttp/Gson, Room (KSP), WorkManager, DataStore, Credential Manager (Google), Firebase Cloud Messaging, AppCompat per-app locales.
Backend: Node.js, Express, Mongoose, bcryptjs, jsonwebtoken, google-auth-library, firebase-admin. Database: MongoDB Atlas. Hosting: Render. CI: GitHub Actions.

## Project structure
```
RoutinePulse/
|-- .github/workflows/ci.yml        backend tests + Android tests/build + R8 release build
|-- backend/                        Node.js / Express API  (see backend/PART3_CHANGES.md)
|-- android/                        Android Studio project (open THIS folder)
|   `-- app/src/main/java/com/routinepulse/app/
|       |-- data/local/             DataStore settings + Room database (db/)
|       |-- data/remote/            Retrofit service, error handling
|       |-- data/repository/        offline-first repositories, mappers, badge tracker
|       |-- data/sync/              SyncEngine, WorkManager worker + coordinator
|       |-- domain/                 validation, streaks, board, achievements (pure, unit tested)
|       |-- push/                   FCM service, notifications, push registration, language helper
|       `-- ui/                     theme, components, screens, view models, navigation
|-- store/                          Play listing text, privacy policy, data-safety answers, graphics, release checklist
`-- docs/                           setup guide, rubric checklists, demo video plans
```

## API (additions in the final version in **bold**)
All routes except `/health`, register and login need `Authorization: Bearer <token>`. Errors are `{"error": "message"}`.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | API + database status |
| POST | `/api/auth/register` | `{email, password, displayName}` |
| POST | `/api/auth/login` | `{email, password}` **or `{googleIdToken}`** |
| GET / **DELETE** | `/api/auth/me` | profile / **delete account and all data** |
| GET / POST | `/api/habits` | list (live streaks, **`updatedAt`, `completionDates`**) / create |
| GET / PUT / DELETE | `/api/habits/:id` | read / update (**with `clientUpdatedAt`, last-write-wins**) / delete |
| POST / DELETE | `/api/habits/:id/complete` | tick `{date}` / undo `?date=` |
| GET / POST / PUT / DELETE | `/api/notes[/:id]` | notes (**PUT supports `clientUpdatedAt`**) |
| POST | `/api/notes/:id/generate-quiz` | build a quiz from the note |
| GET | `/api/quizzes[/:id]` | list / questions (answers hidden) |
| POST | `/api/quizzes/:id/submit` | `{answers:[...]}` -> score + feedback |
| **POST** | **`/api/devices`, `/api/devices/unregister`, `/api/devices/test`** | **register this phone for push / unregister / send a test push** |
| **POST** | **`/api/internal/run-reminders`** | **cron trigger (needs `x-cron-secret`)** |


| Area | What is tested |
|---|---|
| Validation, domain logic, API error parsing | rules, dashboard maths, next reminder, tag parsing, note search |
| `StreakCalculator` / backend `streak.js` | streaks incl. weekdays/custom days, best-ever run |
| Offline repositories | create/tick/edit/delete with **no network**, using in-memory fake DAOs |
| `SyncEngine` | offline changes reach a fake server when it comes online, failed syncs lose nothing, pulls from other devices, conflicts, deletions |
| Badges, streak board, mappers | thresholds, board calendar maths, entity <-> DTO <-> request conversion |
| ViewModels | login/register/Google sign-in, dashboard actions |
| Backend reminders | time zones, which reminders/streak alerts are due, de-duplication |

