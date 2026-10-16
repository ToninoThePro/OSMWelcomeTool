# OSM Welcome Tool

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**OSM Welcome Tool** è un'applicazione Android che aiuta la comunità OpenStreetMap a identificare e accogliere nuovi mappatori. Analizza i changeset recenti e i profili utente per classificare i contributori, monitorare le aree di interesse e tenere traccia dell'accoglienza, il tutto con sincronizzazione in background.

## Funzionalità

- **Onboarding guidato** — configurazione iniziale rapida per definire l'area di monitoraggio e i permessi.
- **Classificazione automatica** degli utenti in base ad attività e anzianità:
  - **Newcomer** — account creato da meno di 60 giorni.
  - **Power User** — oltre 1000 modifiche totali.
  - **Returning User** — account vecchio (> 1 anno) con bassa attività (< 300 modifiche) che torna a contribuire.
- **Esplorazione dei Changeset** — visualizza gli elenchi dei changeset e analizza i dettagli con la minimappa integrata (`BBoxMiniMap`).
- **Integrazione OSMCha** — mostra like/dislike per valutare la qualità dei contributi.
- **Stato accoglienza** — tiene traccia degli utenti già accolti per evitare duplicati.
- **Notifiche** — avvisa quando vengono rilevati nuovi changeset o nuovi mappatori nell'area monitorata.
- **Rispetto delle API** — implementazione di rate limiting (HTTP 429) e sistema avanzato di caching per categoria (OSM, Nominatim, OSMCha).
- **Sincronizzazione automatica** — aggiornamento periodico in background tramite WorkManager (intervallo minimo 15 minuti).
- **Filtri in-app** — filtra per newcomer, returning e power user; cerca per nome utente.

## Tech Stack

- **Linguaggio**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Networking**: Retrofit + OkHttp (con Interceptor per Cache e Rate Limiting)
- **Persistenza**: Room + DataStore + EncryptedSharedPreferences
- **Background**: WorkManager
- **Architettura**: MVVM con repository pattern

## Struttura del Progetto

```
app/
├── data/          # Repository, API services, cache registry, Room entities, DataStore
├── domain/        # UserAnalyzer, regole di business, OsmChangeParser
├── ui/            # Schermate Compose (UserList, UserDetail, Changesets, Settings, Onboarding)
│   └── components/# BBoxMiniMap, ChangesetCard, ProfileAvatar
├── worker/        # OsmSyncWorker, NewUserWorker
├── di/            # Moduli Hilt (Network, Cache, Database, DataStore, Interceptors)
└── utils/         # Constants, ConnectivityObserver, NotificationHelper, LogCapture, AvatarUtils
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
- **[Dati & Networking](app/src/main/java/com/antoninofaro/welcometool/data/ROADMAP.md)** — sync incrementale, export dati
- **[Logica di Dominio](app/src/main/java/com/antoninofaro/welcometool/domain/ROADMAP.md)** — soglie configurabili, template benvenuto
- **[Worker & Sistema](app/src/main/java/com/antoninofaro/welcometool/worker/ROADMAP.md)** — trigger manuale, telemetria, deep link

## Sviluppo con Intelligenza Artificiale

Lo sviluppo di questa applicazione è stato (e continua a essere) assistito da modelli di intelligenza artificiale, utilizzati come supporto nella generazione di codice, refactoring, debugging e documentazione. Il codice prodotto viene sempre rivisto e validato prima dell'integrazione.

---

*Costruito con ❤️ per la Comunità OpenStreetMap.*
