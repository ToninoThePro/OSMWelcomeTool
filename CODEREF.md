# OSM Welcome Tool — Code Reference

## Purpose
Android app that monitors OpenStreetMap changesets, identifies newcomers, and tracks welcomed users. Integrates OSMCha, persists data locally, and supports background sync. Designed to be a **good API citizen** — minimizes load on OSM, Nominatim, and OSMCha servers. Features a "Cartographic Material" UI with expressive spacing and smooth transitions.

---

## Entry point
`WelcomeToolApplication.kt` — `@HiltAndroidApp`, initializes logging (Timber).
`MainActivity.kt` — `@AndroidEntryPoint`, Navigation Host for `user_list`, `user_detail`, `settings`.

---

## Package map

### `ui/screens/`
| File | Role |
|------|------|
| `MainViewModel.kt` | Fetches changesets → enriches details → `UserAnalyzer` → Persistence. Paginates Room (size 100). |
| `UserListScreen.kt` | Expressive cards (24dp radius), Fading Edge scroll effect, Category filters, **stale data warning banner**. |
| `UserDetailScreen.kt` | Large avatar (120dp) + name in `headlineMedium`, StatusCards, Detail cards (24dp radius), Action buttons. |
| `SettingsScreen.kt` | Expandable sections with expressive spacing (20-24dp padding), Card-based UI (24dp radius), Fading Edge. |

### `ui/theme/`
| File | Role |
|------|------|
| `Color.kt` | Cartographic Material palette: Grass Green (Primary), Water Blue (Secondary), Earth Brown (Tertiary). |
| `Type.kt` | Scale: Display-lg (57sp), Headline-lg (32sp), Title-lg (22sp), Body-lg (16sp). Plus Jakarta Sans approximation. |
| `Theme.kt` | M3 Theme setup with Dynamic Color support. |

### `data/`
- `model/`: Retrofit DTOs (`OsmUser`, `OsmChaResponse`, `NominatimPlace`).
- `entity/`: Room entities (`UserEntity`, `UserAreaActivityEntity`).
- `network/`: API Services (`OsmApiService`, `OsmChaService`, `NominatimApiService`).
- `repository/`: Network and local storage repositories. `SecureTokenStorage` for OSMCha encryption.

### `domain/`
- `UserAnalyzer.kt`: Stateless classification: newcomer/returning/power-user.

### `worker/`
- `OsmSyncWorker.kt`: Periodic check for new changesets and newcomers.

---

## Scan strategy (critical for API citizenship)

### Constants
| Constant | Value | Context |
|----------|-------|---------|
| `INITIAL_SCAN_WINDOWS` | 8 | `MainViewModel.kt` — max pages paginated on fresh start/bbox change |
| `PERIODIC_SCAN_WINDOWS` | 1 | Worker's single-window check |
| `MAX_DEEP_SCAN_WINDOWS` | 4 | Max total windows when catching up after missed changeset |
| `LOCAL_PAGE_SIZE` | 100 | Room query page size |
| `MAX_RECENT_CHANGESETS` | 100 | OSM API `limit` param (matches API max) |

### Initial scan (`syncRecentUsersForCurrentBBox`)
1. Fetches up to `INITIAL_SCAN_WINDOWS` pages of changesets (each `limit=100`), walking backward via cursor pagination.
2. Cursor from each batch: oldest `createdAt` timestamp → next window's end bound.
3. Early exit if a window returns empty (no more changesets).
4. Deduplication: `seenUserIds` set prevents reprocessing the same UID across windows.
5. Collects the **newest** changeset per user via `latestChangesetByUid` (`putIfAbsent`).

### Periodic scan (`OsmSyncWorker`)
1. Fetches **1 window** (100 changesets) for the monitored bbox.
2. Compares against `lastKnownChangesetDate`:
   - **Found** in batch → done. No further requests.
   - **Not found** → paginate deeper, **up to `MAX_DEEP_SCAN_WINDOWS` total** (1 + 3 extra), trying to find it.
   - If still not found after 4 windows → stop. The changeset may be too old or the user changed areas.
3. This prevents routine re-fetching of hundreds of changesets per cycle.

### User detail refresh
- Stale users (Room `lastUpdated` > 24h) are fetched in batches of 50 from the batch OSM API (`/api/0.6/users.json?users=id1,id2,...`).
- Users dormant > 15 days (`DORMANT_USER_THRESHOLD_MS`) are skipped even if TTL expired.

---

## Offline behavior
- Room data is served regardless of connectivity.
- `UserListScreen` shows a **stale data warning** (banner/card) when no fresh data is available, including `lastUpdated` timestamp from the most recent fetch.
- ViewModel tracks sync status; UI observes it.
- No automatic retry on reconnection — waits for next scheduled worker or user-triggered sync.

---

## Caching layers (top to bottom)

| Layer | Detail | TTL |
|-------|--------|-----|
| OkHttp disk cache (50 MB, shared) | Shared across all OkHttp clients | Depends on server Cache-Control |
| Network interceptor Cache-Control | OSM user profiles: 86400s, changesets: 300s | 24h / 5m |
| | OSMCha: 3600s | 1h |
| Application TTL (`MainViewModel`) | `USER_CACHE_TTL_MS` | 24h |
| | `CACHE_PAGE_TTL_MS` | 30m |
| | `OSMCHA_AUTO_REFRESH_DAYS` | 1d |
| Room database | Indefinite persistence | No eviction (yet) |
| DataStore (registries) | Welcomed/notified IDs | Indefinite |

**Gap**: `settings.cacheEnabled` flag is persisted but not wired to runtime cache. See `data/ROADMAP.md`.

---

## Rate limiting (current state)
- No HTTP 429 detection or handling yet.
- `safeApiCall()` catches exceptions but doesn't parse `Retry-After` headers or implement backoff.
- WorkManager worker has `EXPONENTIAL` backoff (30s base) on failure.
- `retryOnConnectionFailure(true)` on OkHttp handles TCP-level retransmission only.
- **Priority**: implement rate limit awareness before adding new API consumers.

---

## Room schema
- `users`: Core profile and analysis status.
- `user_area_activity`: User presence across bboxes (Composite PK: `bbox`, `userId`).

---

## External APIs

| Service | Base URL | Notes |
|---------|----------|-------|
| OSM API | `https://api.openstreetmap.org/` | Public, no auth needed for reads |
| OSMCha | `https://osmcha.org/api/v1/` | Bearer token (AES256 encrypted) |
| Nominatim | `https://nominatim.openstreetmap.org/` | Requires `User-Agent` header. Respect usage policy (1 req/sec recommended). |

---

## Key constants
| Constant | Value | File |
|----------|-------|------|
| OSMCha token length | 40 | `SecureTokenStorage.kt` |
| WorkManager min period | 15 min | `WorkerUtils.kt` |
| Default sync interval | 30 min | `SettingsScreen.kt` |
| User cache TTL | 24h | `MainViewModel.kt` |
| Dormant user threshold | 15 days | `MainViewModel.kt` |
| Primary Color | `#436900` (Grass Green) | `Color.kt` |

---

## Data flow (detailed)
1. `MainViewModel` scans changeset windows (8 initial / 1 periodic + up to 3 deep).
2. Parallel processing (`Semaphore(6)`) for user profiles only in worker context.
3. 24h cache TTL for user data in Room.
4. UI served via paginated JOIN queries (`UserDao.getUsersForBBoxPage`).
5. If offline or stale, UI shows warning + `lastUpdated` timestamp.

## UI Patterns (Cartographic Material / Stitch Design)
- **Rounded Edges**: Main containers use `RoundedCornerShape(24.dp)`.
- **Fading Edge**: Scroll areas use `verticalGradient` with `BlendMode.DstIn` to soften top transitions.
- **Expressive Spacing**: Internal padding of 20dp-24dp in cards.
- **Stale data states**: Warning banner indicating data recency (shown when connectivity lost or cache expired).
