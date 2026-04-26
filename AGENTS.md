# AGENTS.md

## Project at a glance
- Single-module Android app in `:app` (Kotlin, Compose, Hilt, Room, DataStore, WorkManager, Retrofit/OkHttp, Coil).
- Main goal: analyze recent OSM changesets to identify and welcome newcomers in Sicily.
- Read the flow as: `MainActivity` -> `MainViewModel` -> `OsmRepository` / `UserAnalyzer` -> Compose screens.

## Architecture and data flow
- `MainActivity.kt` owns Compose navigation (`user_list`, `user_detail/{userId}`, `settings`) and schedules background sync via `WorkerUtils.scheduleOsmSyncWorker(...)` when `settings.autoRefresh` changes.
- `MainViewModel.kt` is the central coordinator: fetches changesets, loads user details/history in parallel, enriches with `UserAnalyzer`, then persists to Room through `UserLocalRepository`.
- `SettingsViewModel.kt` is a thin wrapper over `SettingsRepository`; use it for preference updates instead of writing DataStore logic in UI.
- `WelcomeToolApplication.kt` initializes Hilt WorkManager, creates the notification channel, plants Timber in debug, and calls `RetrofitClient.initialize(cacheDir)`.

## Data and integration boundaries
- OSM API access lives in `data/network/RetrofitClient.kt` + `OsmApiService`; OSMCha access is built in `di/NetworkModule.kt` with an auth interceptor.
- OSMCha token handling is security-sensitive: prefer `SecureTokenStorage` and the migration path in `SettingsRepository`, not plain DataStore.
- Persistent state is split by concern: `settings` DataStore, `osm_welcomed_registry`, `osm_notified_registry`, and Room `osm_welcome_database`.
- Notifications use `NotificationHelper` / channel id `osm_updates_channel`; new-mapper alerts use `NewUserWorker` and channel `NEW_MAPPER_CHANNEL`.

## Conventions to preserve
- Keep business rules in `domain/UserAnalyzer.kt`; its thresholds mirror README behavior: newcomer < 60 days, power user > 1000 edits, returning > 365 days and < 300 edits.
- Reuse `Result<T>` + `safeApiCall()` from `data/model/Result.kt` for network calls instead of throwing through layers.
- Hilt is the default wiring pattern: `@HiltViewModel`, `@HiltWorker`, `@Module` in `di/`.
- Compose screens are state-driven and live in `ui/screens/`; `ProfileAvatar` is the shared UI component for user images.
- Watch the filename typo `data/entity/Userentity.kt` (class is `UserEntity`); preserve existing package/class names when editing.

## Developer workflows
- CI mirrors local verification: `./gradlew lintDebug`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`.
- Release packaging is `./gradlew assembleRelease` (tag-driven in `.github/workflows/android_release.yml`).
- Android/JDK setup expects JDK 17 and Gradle toolchains; root config uses `repositoriesMode.FAIL_ON_PROJECT_REPOS`.
- Tests currently live under `app/src/test`; Robolectric is used for integration-style tests such as notification and storage flows.

## Before changing code
- Trace changes across layers: UI ↔ ViewModel ↔ repository ↔ network/storage before editing one side.
- Be careful with WorkManager behavior: periodic sync is clamped to a 15-minute minimum in `WorkerUtils`.
- Prefer small, targeted edits; avoid reformatting unrelated Compose or DI code.

