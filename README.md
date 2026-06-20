# OSM Welcome Tool

**OSM Welcome Tool** è un'applicazione Android che aiuta la comunità OpenStreetMap a identificare e accogliere nuovi mappatori. Analizza i changeset recenti e i profili utente per classificare i contributori, monitorare le aree di interesse e tenere traccia dell'accoglienza, il tutto con sincronizzazione in background.

## Funzionalità

- **Classificazione automatica** degli utenti in base ad attività e anzianità:
  - **Newcomer** — account creato da meno di 60 giorni.
  - **Power User** — oltre 1000 modifiche totali.
  - **Returning User** — account vecchio (> 1 anno) con bassa attività (< 300 modifiche) che torna a contribuire.
- **Monitoraggio changeset** — recupera i changeset OSM recenti entro un'area configurabile (default: Italia).
- **Integrazione OSMCha** — mostra like/dislike per valutare la qualità dei contributi.
- **Stato accoglienza** — tiene traccia degli utenti già accolti per evitare duplicati.
- **Notifiche** — avvisa quando vengono rilevati nuovi changeset o nuovi mappatori nell'area monitorata.
- **Aree multiple** — cerca e salva aree geografiche tramite Nominatim.
- **Sincronizzazione automatica** — aggiornamento periodico in background tramite WorkManager (intervallo minimo 15 minuti).
- **Filtri in-app** — filtra per newcomer, returning e power user; cerca per nome utente.

## Tech Stack

- **Linguaggio**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Networking**: Retrofit + OkHttp
- **Persistenza**: Room + DataStore + EncryptedSharedPreferences
- **Background**: WorkManager
- **Architettura**: MVVM con repository pattern

## Struttura del Progetto

```
app/
├── data/          # Repository, API services, Room entities, DataStore
├── domain/        # UserAnalyzer, regole di business
├── ui/            # Schermate Compose, ViewModel, componenti
│   ├── screens/   # UserList, UserDetail, Settings, Licenses
│   └── components/# ProfileAvatar (condiviso)
├── worker/        # OsmSyncWorker, NewUserWorker
├── di/            # Moduli Hilt (Network, Database, DataStore, Coil)
└── utils/         # Constants, WorkerUtils, NotificationHelper, LogCapture, AvatarUtils
```

## Come Iniziare

### Prerequisiti
- Android Studio Ladybug o più recente
- JDK 17

### Installazione
```bash
git clone https://github.com/ToninoThePro/OSMWelcomeTool.git
cd welcometool
```

Apri in Android Studio, attendi la sincronizzazione Gradle, poi esegui su dispositivo o emulatore.

### Build
```bash
./gradlew assembleDebug                    # build
./gradlew lintDebug                        # lint
./gradlew testDebugUnitTest                # test
```

## Roadmap

- **[UI/UX](app/src/main/java/com/antoninofaro/welcometool/ui/ROADMAP.md)** — i18n, loading/error, accessibilità
- **[Dati & Networking](app/src/main/java/com/antoninofaro/welcometool/data/ROADMAP.md)** — rate limiting, cache, sync incrementale
- **[Logica di Dominio](app/src/main/java/com/antoninofaro/welcometool/domain/ROADMAP.md)** — soglie configurabili, template benvenuto
- **[Worker & Sistema](app/src/main/java/com/antoninofaro/welcometool/worker/ROADMAP.md)** — trigger manuale, telemetria, deep link

## Sviluppo con Intelligenza Artificiale

Lo sviluppo di questa applicazione è stato (e continua a essere) assistito da modelli di intelligenza artificiale, utilizzati come supporto nella generazione di codice, refactoring, debugging e documentazione. Il codice prodotto viene sempre rivisto e validato prima dell'integrazione.

---

*Costruito con ❤️ per la Comunità OpenStreetMap.*
