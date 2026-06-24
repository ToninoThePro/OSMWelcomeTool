# AGENTS.md

## Project at a glance
- Single-module Android app in `:app` (Kotlin, Compose, Hilt, Room, DataStore, WorkManager, Retrofit/OkHttp, Coil).
- Goal: monitor OpenStreetMap changesets, identify newcomers, track welcomed users. No geographic focus (configurable bbox via Nominatim search).
- **Design System**: "Cartographic Material" (Stitch-inspired) with grass green primary tones and expressive spacing.

## Core philosophy: API Good Citizenship
This app must be as lightweight as possible on external servers (OSM API, Nominatim, OSMCha). The priority order is:

1. **Zero avoidable load** — never fetch what can be cached or skipped.
2. **Gentle backoff** — prefer stale data over risking a 429. No burst requests.
3. **Transparency** — show users when data is stale and when it was last updated.

HTTP 429 handling and rate limiting are high-priority gaps (see `data/ROADMAP.md`). Work-in-progress.

## Scan strategy

### Initial scan (app start or bbox change)
- Up to **8 windows** of 100 changesets each (800 total), walking backward in time via cursor pagination.
- Early exit when a window returns empty (no more changesets in time range).

### Periodic scan (WorkManager worker, default 30 min, min 15 min)
- **1 window** of 100 changesets for the monitored bbox.
- If the last-known changeset from the previous scan is found in this batch → **done** (no further requests needed).
- If the last-known changeset is **not** found → deep scan: continue paginating **up to 4 windows total** (1 already done + 3 additional = 4×100 max) to find it. If still not found after 4 windows, stop.

### Parallelism
- Changeset scanning is **sequential per window** (no parallel pagination).
- User detail fetching (`refreshUser`) is **sequential per user**.
- The only concurrency: `OsmSyncWorker.checkNewMappers()` uses `Semaphore(6)` to limit simultaneous user detail fetches to 6.
- No parallel requests to different API services for the same user flow.

## Offline mode
- The app works with cached Room data when offline.
- A visual warning is displayed (banner/card) informing the user data may be stale, along with the `lastUpdated` timestamp.
- No automatic refresh when connectivity resumes — user must trigger or wait for next scheduled worker.

## Notifications
- **Default OFF**. User must opt in.
- Two notification types: new changesets in area, and new mappers detected.
- No sound/vibration by default (respects system notification channel settings).

## Data retention
- No automatic pruning. All fetched users are kept in Room indefinitely.
- `WelcomedUserStorage` and `NotifiedUserStorage` persist welcomed/notified status forever to prevent duplicates.

## Architecture and data flow
- `MainActivity.kt` owns Compose navigation and schedules background sync.
- `MainViewModel.kt` coordinates data fetching, analysis (`UserAnalyzer`), and Room persistence.
- `SettingsViewModel.kt` handles preference updates.
- **UI Polish**: High-radius corners (24dp) and **Fading Edge** effects in scrollable areas (`UserListScreen`, `SettingsScreen`) to soften visual transitions.

## Data and integration boundaries
- OSM API access via Retrofit (`OsmApiService`); OSMCha access with auth interceptor.
- OSMCha token stored in `SecureTokenStorage` (EncryptedSharedPreferences).
- Persistent state: settings DataStore, Room `osm_welcome_database`, and specialized registries for welcomed/notified users.

## Conventions to preserve
- **Business Logic**: `UserAnalyzer.kt` thresholds: newcomer < 60 days, power user > 1000 edits, returning > 365 days and < 300 edits.
- **Result Handling**: Consistent use of `Result<T>` and `safeApiCall()`.
- **UI Components**: `ProfileAvatar` (AsyncImage with fallback), `StatusCard` (Expressive style), `ExternalLinkButton` (Standardized links).
- **Naming**: Be aware of `data/entity/Userentity.kt` typo (class is `UserEntity`).

## Developer workflows
- **Verification**: `./gradlew testDebugUnitTest` for logic, **Compose Previews** for UI.
- **Styling**: All colors must reference `MaterialTheme.colorScheme` (updated for Cartographic Material). Avoid hardcoded hex values.
- **Layouts**: Use 8dp baseline grid, 16-24dp margins, and 24dp corner radii for main containers.

## Security model
- Public OSM data is stored without encryption.
- OSMCha tokens are encrypted via AES256-GCM/SIV.
- Timber redacted for auth headers; planted only in debug.

## Before changing code
- Trace changes across UI ↔ ViewModel ↔ Repository ↔ Network.
- Ensure UI changes follow the "Expressive" design guidelines (Rounded 24dp, Fading Edges).
- Every new API call pattern must be reviewed for server impact — prefer cache over fetch.
