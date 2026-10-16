# AGENTS.md

## Project at a glance
- Single-module Android app in `:app` (Kotlin, Compose, Hilt, Room, DataStore, WorkManager, Retrofit/OkHttp, Coil).
- Goal: monitor OpenStreetMap changesets, identify newcomers, track welcomed users. No geographic focus (configurable bbox via Nominatim search).
- Internal docs: `CODEREF.md` (code reference) and `data/ROADMAP.md` (known gaps and planned work).

## Core philosophy: API good citizenship
The app must be as light as possible on external servers (OSM API, Nominatim, OSMCha). Priority order:

1. **Zero avoidable load**: never fetch what can be cached or skipped.
2. **Gentle backoff**: prefer stale data over risking a 429. No burst requests.
3. **Transparency**: show users when data is stale and when it was last updated.

HTTP 429 handling and rate limiting are implemented via `RateLimitInterceptor`.
Every new API call pattern must be reviewed for server impact: prefer cache over fetch.

## Scan strategy

**Initial scan** (app start or bbox change)
- Up to 8 windows of 100 changesets each (800 total), walking backward in time via cursor pagination.
- Early exit when a window returns empty (no more changesets in the time range).

**Periodic scan** (WorkManager worker, default 30 min, minimum 15 min)
- 1 window of 100 changesets for the monitored bbox.
- Last-known changeset from the previous scan found in the batch: done, no further requests.
- Not found: deep scan, paginating up to 4 windows in total (1 already done + 3 more, 4x100 max). Still not found after 4 windows: stop.

**Parallelism**
- Changeset scanning is sequential per window (no parallel pagination).
- User detail fetching (`refreshUser`) is sequential per user.
- The only concurrency is `OsmSyncWorker.checkNewMappers()`, which uses `Semaphore(6)` to run at most 6 user detail fetches at once.
- No parallel requests to different API services for the same user flow.

## Offline mode
- The app works with cached Room data when offline.
- A visual warning (banner/card) tells the user data may be stale, with the `lastUpdated` timestamp.
- No automatic refresh when connectivity resumes: the user triggers it or waits for the next scheduled worker.

## Notifications
- Default OFF. The user must opt in.
- Two types: new changesets in the area, new mappers detected.
- No sound/vibration by default (respects system notification channel settings).

## Data retention
- No automatic pruning. All fetched users stay in Room indefinitely. The changesets table also grows for now (pruning is deferred).
- Users that have appeared in the list must never be deleted, including by any "clear cache" action.
- `WelcomedUserStorage` and `NotifiedUserStorage` persist welcomed/notified status forever to prevent duplicates.
- Cache controls: turning a category off only stops saving, it never deletes what is already stored. Deletion is always explicit, and the confirmation shows how much space will be freed.

## Architecture and data flow
- `MainActivity.kt` owns Compose navigation and schedules background sync.
- `MainViewModel.kt` coordinates data fetching, analysis (`UserAnalyzer`) and Room persistence.
- `SettingsViewModel.kt` handles preference updates.
- OSM API access via Retrofit (`OsmApiService`); OSMCha access via an auth interceptor.
- Persistent state: settings DataStore, Room `osm_welcome_database`, and the welcomed/notified registries.
- When changing code, trace the change across UI <-> ViewModel <-> Repository <-> Network.

## Security
- Public OSM data is stored without encryption.
- The OSMCha token lives in `SecureTokenStorage` (EncryptedSharedPreferences, AES256-GCM/SIV).
- Timber redacts auth headers and is planted only in debug builds.
- Never commit tokens or API keys.

## Conventions to preserve
- **Business logic**: `UserAnalyzer.kt` thresholds are newcomer < 60 days, power user > 1000 edits, returning > 365 days and < 300 edits. Do not change them unless asked.
- **Result handling**: use `Result<T>` and `safeApiCall()` consistently.
- **Reusable UI**: `ProfileAvatar` (AsyncImage with fallback), `StatusCard` (Expressive style), `ExternalLinkButton` (standardized links).
- **Naming**: `data/entity/Userentity.kt` has a typo in the file name (the class is `UserEntity`). Do not rename it as a side effect of another task.

## UI and design
- Design system "Cartographic Material" (Stitch-inspired): grass green primary tones, expressive spacing.
- All colors reference `MaterialTheme.colorScheme`. No hardcoded hex values.
- 8dp baseline grid, 16-24dp margins, 24dp corner radius on main containers.
- Fading Edge effect on scrollable areas (`UserListScreen`, `SettingsScreen`) to soften transitions.

## Code and scope rules (every agent, Jules included)
- Write the minimum code needed. Use the Kotlin/Android standard library and the dependencies already present first; do not add new dependencies unless asked.
- Do not add functions, abstractions or options that were not requested.
- Tests that were requested are always included.
- Stay within the task's scope: no refactors or reformatting of unrelated code.

## Verification
- Logic: `./gradlew testDebugUnitTest`. The project must still build: `./gradlew assembleDebug`.
- UI: Compose Previews.

---

# Regole per delegare a Jules

Valgono ogni volta che chiedo di far fare dei compiti a Jules, anche in modo informale (per esempio "fai 10 sessioni"), con o senza i comandi `/jules-*`.

## Prima di creare le sessioni
- Controlla `git status`: Jules parte da GitHub e non vede commit o modifiche locali non pushati. Se un compito ne dipende, dimmelo e fermati.
- Se due compiti toccano gli stessi file o uno dipende dall'altro, dimmelo prima di lanciarli.
- Scrivi per ogni sessione un prompt autosufficiente: obiettivo, file coinvolti, vincoli, cosa NON toccare, come verificare. Includi sempre questo vincolo: "scrivi il minimo codice necessario, usa prima libreria standard e dipendenze già presenti, non aggiungere funzioni o astrazioni non richieste; i test richiesti vanno comunque inclusi".
- Non mandare a Jules richieste che ampliano lo scope del compito.

## Gestione delle sessioni
- Con più compiti, crea tutte le sessioni di seguito con `jules_create`, senza aspettare tra una e l'altra, fino al limite del piano (controllalo con `jules_limits`). I compiti in eccesso restano in coda e li lanci man mano che si libera uno slot.
- Non aspettare mai una sola sessione. Dopo aver creato le sessioni usa `jules_wait`: controlla tutte le sessioni aperte e torna appena una ha bisogno di te. Gestisci subito quella (piano: approva con `jules_approve` o correggi con `jules_send`; domanda: rispondi o chiedimelo; completata: `jules_changes` e valutazione), poi richiama `jules_wait`. Se scade senza novità, richiamalo: è normale.
- Dopo un riavvio di OpenCode usa prima `jules_status` per riprendere a seguire le sessioni ancora attive, senza crearne di doppie.
- Valuta un risultato alla volta, con riassunti brevi invece di diff interi. Massimo 3 giri correttivi per sessione.
- Non inventare esiti; se un tool fallisce, dimmelo.

## Applicazione
- Non fare mai push e non chiedere PR a Jules: le modifiche restano in locale. Il push lo faccio io.
- Con più sessioni, usa `jules_status` per vedere le sovrapposizioni tra le patch prima di applicare.
- Applica in locale solo dopo il mio ok, una sessione alla volta, con `jules_apply`. Se una patch non si applica pulita, fermati e dimmelo prima di usare `threeWay`.
- Alla fine ricordami che test e push li faccio io.
