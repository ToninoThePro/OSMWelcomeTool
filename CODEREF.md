# OSM Welcome Tool — Code Reference

## Purpose
Android app that monitors OpenStreetMap changesets in configurable areas, identifies newcomers, analyzes their profiles, and lets community members track who has been welcomed. Integrates OSMCha for quality signals, persists data locally, and supports background sync via WorkManager.

---

## Entry point
`WelcomeToolApplication.kt` — `@HiltAndroidApp`, plants Timber, creates notification channel, initializes `LogCaptureTree`.
`MainActivity.kt` — `@AndroidEntryPoint`, owns Compose NavHost with routes: `user_list`, `user_detail/{userId}`, `settings`.

---

## Package map

### `ui/screens/`
| File | Role |
|------|------|
| `MainViewModel.kt` | Central coordinator. Fetches OSM changesets → extracts UIDs → loads user details + changeset history in parallel → runs `UserAnalyzer` → persists via `UserDao`. Holds `MainUiState` (users, filters, pagination). Paginates from Room with LOCAL_PAGE_SIZE=100. Semaphore(6) on concurrent API calls. |
| `SettingsViewModel.kt` | Thin proxy over `SettingsRepository` + `NominatimRepository` + `LogCaptureTree`. Exposes `settings: StateFlow<AppSettings>`. |
| `UserListScreen.kt` | Compose: `SearchBar` + `FilterChip` (newcomer/power user) + `PullToRefreshBox` + `LazyColumn` of `UserListItem` cards. "Load more" pagination footer. |
| `UserDetailScreen.kt` | Compose: avatar, stats cards (newcomer/returning/pro), details list (created date, edits, OSMCha, last active), external links (HDYC, OSMCha, OSM profile), welcome toggle button. |
| `SettingsScreen.kt` | Compose: dark mode toggle, auto-refresh interval slider, min edits filter, Nominatim area search, saved areas list, notifications on/off, OSMCha token input with validation, cache toggle, debug log capture toggle, reset to defaults. |

### `ui/components/`
| File | Role |
|------|------|
| `ProfileAvatar.kt` | Coil `AsyncImage` with Gravatar fallback (`AvatarUtils.getGravatarUrl`), then initial-letter fallback. |

### `data/model/`
| File | Role |
|------|------|
| `Models.kt` | `OsmUser`, `OsmChangeset`, `OsmChangesetWrapper`, `OsmUserWrapper`, `CountWrapper`, `UserImage`, `OsmChaResponse`, `OsmChaFeature`, `OsmChaProperties`, `NominatimPlace` — Retrofit DTOs with `@SerializedName`. |
| `Result.kt` | Sealed class `Success<T>` / `Error` / `Loading`. Extensions: `safeApiCall()`, `onSuccess`, `onError`, `log`, `getOrNull`, `getOrDefault`. |

### `data/network/`
| File | Role |
|------|------|
| `OsmApiService.kt` | Retrofit: `getRecentChangesets(bbox, time, limit)`, `getUserDetail(id)`, `getUserChangesets(userId)`, `getChangesetsByUsername(display_name)`. Base: `https://api.openstreetmap.org/`. |
| `OsmChaService.kt` | Retrofit: `getUserChangesets(users)`. Base: `https://osmcha.org/api/v1/`. |
| `NominatimApiService.kt` | Retrofit: `searchPlaces(q, format, limit)`. Base: `https://nominatim.openstreetmap.org/`. |

### `data/entity/`
| File | Role |
|------|------|
| `UserEntity.kt` | Room `@Entity(users)`: id (PK), displayName, accountCreated, description, accountAge, isNewcomer, isReturning, isPowerUser, totalEdits, firstChangesetDate, lastActiveDate, osmchaLikes, osmchaDislikes, isWelcomed, lastUpdated, imgUrl. |
| `UserAreaActivityEntity.kt` | Room `@Entity(user_area_activity)`: composite PK(bbox, userId), lastChangesetDate, lastChangesetId, lastUpdated. Tracks which bbox a user was seen in. |

### `data/local/`
| File | Role |
|------|------|
| `AppDatabase.kt` | Room DB (version 1): `UserEntity` + `UserAreaActivityEntity`. `fallbackToDestructiveMigration(false)`. Singleton via double-checked locking. |
| `UserDao.kt` | Room DAO: `insertUser`, `insertUsers`, `insertUserAreaActivities`, `getUserById`, `getUserIdsForBBox`, `getUserCountForBBox`, `getUsersForBBoxPage` (paginated join query sorted by lastChangesetDate DESC), `searchUsersForBBox` (LIKE on displayName), `observeAllUsers` (Flow), `updateWelcomedStatus`, `deleteAllUsers`. |
| `UserAreaActivityWithUser.kt` | `@Embedded UserEntity` + bbox + lastChangesetDate + lastChangesetId + areaLastUpdated. Result type for JOIN queries. |

### `data/repository/`
| File | Role |
|------|------|
| `OsmRepository.kt` | Wraps `OsmApiService`. Methods: `fetchRecentChangesets(bbox, timeRange, limit)`, `fetchUserDetail(userId)`, `fetchUserChangesets(userId)`, `searchUserByUsername(username)`. All return `Result<T>`. |
| `OsmChaRepository.kt` | Wraps `OsmChaService`. `getUserOsmChaStats(username)` → `Pair(likes, dislikes)`. Reads `osmchaChangesetsLimit` from settings. Handles HTTP 401/403 gracefully. |
| `NominatimRepository.kt` | Wraps `NominatimApiService`. `searchAreas(query)` → `Result<List<MonitoringArea>>`. Converts Nominatim [south,north,west,east] to OSM [west,south,east,north] bbox format. |
| `SettingsRepository.kt` | DataStore<Preferences> "settings". Exposes `settingsFlow: Flow<AppSettings>`. Individual `updateXxx()` methods for each field. OSMCha token handled via `SecureTokenStorage` with normalization. `AppSettings` data class contains: darkMode, autoRefresh, autoRefreshInterval, defaultBBox, defaultAreaName, showNotifications, minChangesetsFilter, cacheEnabled, osmchaToken, osmchaChangesetsLimit, lastKnownChangesetId, monitoringAreas, debugLogsEnabled. |
| `NotifiedUserStorage.kt` | Thin wrapper over `SetDataStore` with key "notified_ids". Methods: `isNotified`, `markAsNotified`, `markAsNotifiedBatch`, `getAllNotifiedIds`, `removeNotified`, `clearAll`. |
| `WelcomedUserStorage.kt` | Thin wrapper over `SetDataStore` with key "welcomed_ids". Methods: `isWelcomed`, `setWelcomed`, `getWelcomedCount`, `getAllWelcomedIds`, `clearAll`. |
| `SetDataStore.kt` | Generic `Set<String>` persistence over DataStore. Constructor takes `DataStore<Preferences>` + `keyName`. Methods: `flow`, `getAll`, `contains`, `add`, `addAll`, `remove`, `clear`. |
| `SecureTokenStorage.kt` | EncryptedSharedPreferences for OSMCha token. Methods: `saveOsmchaToken`, `getOsmchaToken`, `clearOsmchaToken`, `hasOsmchaToken`. Falls back to plain SharedPreferences if encryption setup fails. Has `injectTestPrefs()` for test injection. |

### `domain/`
| File | Role |
|------|------|
| `UserAnalyzer.kt` | Pure stateless object. `analyze(user, userChangesets, recentChangeset, osmchaLikes, osmchaDislikes, now)` → `UserAnalysis`. Thresholds: newcomer = accountAge < 60 days, powerUser = totalEdits > 1000, returning = accountAge > 365 days AND totalEdits < 300. Uses `ThreadLocal<SimpleDateFormat>` for ISO date parsing. |

### `worker/`
| File | Role |
|------|------|
| `OsmSyncWorker.kt` | `@HiltWorker` — periodic via WorkManager. Reads settings, fetches 1 recent changeset, compares maxId with `lastKnownChangesetId`, sends notification if new ones found. First run saves ID without notification. |
| `NewUserWorker.kt` | `@HiltWorker` — fetches changesets, deduplicates by UID, filters already-notified, processes remaining in parallel (Semaphore 6), runs `UserAnalyzer`, sends notification for newcomers, batch-marks notified. |

### `utils/`
| File | Role |
|------|------|
| `Constants.kt` | `DEFAULT_AREA_NAME = "Italia"`, `ITALY_BBOX = "6.6,35.3,18.6,47.2"`. |
| `WorkerUtils.kt` | `scheduleOsmSyncWorker(context, intervalMinutes, enabled)` — enqueue/cancel `PeriodicWorkRequest<OsmSyncWorker>` with `NetworkType.CONNECTED` + battery/storage constraints. Minimum interval clamped to 15 min (WorkManager limit). |
| `NotificationHelper.kt` | `@Singleton`. `createNotificationChannel()` (channel "osm_updates_channel"), `sendNewChangesetsNotification(newCount)` with PendingIntent to MainActivity. Checks `POST_NOTIFICATIONS` permission on API 33+. |
| `LogCapture.kt` | `LogCaptureTree` (`@Singleton`, extends `Timber.Tree`) — ring buffer of 500 `LogEntry`s, toggleable via `setEnabled()`. Methods: `clearLogs`, `exportLogs`. Plants in `WelcomeToolApplication.onCreate`. |
| `AvatarUtils.kt` | `getGravatarUrl(displayName, size)` → MD5 hash of `$normalizedName@openstreetmap.org` → Gravatar URL. `getProfileImageUrl(osmImageUrl, displayName, size)` — prioritizes OSM image, falls back to Gravatar. |

### `di/`
| File | Role |
|------|------|
| `NetworkModule.kt` | `@Module`: provides `OsmApiService`, `OsmChaService` (with auth interceptor reading `SettingsRepository.getOsmchaTokenOnce()`), `NominatimApiService`. Each has its own OkHttpClient with cache (10 MB). |
| `DatabaseModule.kt` | `@Module`: provides `AppDatabase` → `UserDao`. |
| `DataStoreModule.kt` | `@Module`: provides `@NotifiedDataStore DataStore<Preferences>` + `@WelcomedDataStore DataStore<Preferences>` each backed by their own file. Also provides `ImageLoader` (Coil with separate OkHttpClient + 50 MB cache). |
| `CoilModule.kt` | `@Module`: provides `ImageLoader` with Coil + OkHttp + 50 MB disk cache. |

### Other
| File | Role |
|------|------|
| `WelcomeToolApplication.kt` | `@HiltAndroidApp`, `Configuration.Provider` for WorkManager. Creates notification channel "NEW_MAPPER_CHANNEL", plants `Timber.DebugTree` in debug + `LogCaptureTree`, loads initial debug log capture state from settings. |
| `MainActivity.kt` | `@AndroidEntryPoint`. Creates both ViewModels. `LaunchedEffect` for auto-refresh scheduling + default bbox sync. Navigation: `user_list` → `user_detail/{id}` → `settings`. |

---

## Data flow (detailed)
1. `MainViewModel.loadData()` fetches recent changesets from OSM API (paginated across up to 6 scan windows of 100 each).
2. Extracts unique UIDs, processes each in parallel (semaphore = 6).
3. For each UID: checks Room cache (TTL = 24 h), if stale → parallel `fetchUserDetail` + `fetchUserChangesets` from OSM, runs `UserAnalyzer.analyze()`, saves to Room.
4. `UserAreaActivityEntity` tracks which bbox each user was seen in (join table for multi-area queries).
5. Results served from Room via paginated JOIN query (`UserDao.getUsersForBBoxPage`).
6. OSMCha stats loaded on demand when user detail screen opens (`loadOsmchaForUser`).

## DI graph
```
AppDatabase → UserDao
NetworkModule → OsmApiService, OsmChaService, NominatimApiService
DataStoreModule → @NotifiedDataStore DataStore, @WelcomedDataStore DataStore
CoilModule → ImageLoader
All ViewModels: @HiltViewModel with @Inject constructor
All Workers: @HiltWorker with @AssistedInject
```

## Testing
- `app/src/test/java/` — JUnit4 + Mockito + Robolectric.
- Key tests: `OsmRepositoryTest`, `OsmChaRepositoryTest`, `SecureTokenStorageTest`, `UserAnalyzerTest`, `NewUserWorkerLogicTest`.
- `./gradlew testDebugUnitTest` to run.
- `./gradlew lintDebug` + `./gradlew assembleDebug` for CI-equivalent verification.
- `NotifiedUserStorageTest` / `TokenMigrationIntegrationTest` use in-memory DataStore files; may fail on Windows due to Robolectric file locks (pre-existing).

## Room schema
```
users (id PK, displayName, accountCreated, description, accountAge, isNewcomer,
       isReturning, isPowerUser, totalEdits, firstChangesetDate, lastActiveDate,
       osmchaLikes, osmchaDislikes, isWelcomed, lastUpdated, imgUrl)

user_area_activity (bbox PK, userId PK, lastChangesetDate, lastChangesetId, lastUpdated)
```

## Key strings / queries
- `UserDao.getUsersForBBoxPage`: `INNER JOIN users u ON u.id = a.userId WHERE a.bbox = :bbox ORDER BY a.lastChangesetDate DESC LIMIT :limit OFFSET :offset`
- OSM bbox format: `"min_lon,min_lat,max_lon,max_lat"` (e.g., `"6.6,35.3,18.6,47.2"` for Italy)
- OSMCha token expected length: 40 characters
- WorkManager minimum period: 15 minutes
- User cache TTL: 24 hours
