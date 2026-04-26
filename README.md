# OSM Welcome Tool

**OSM Welcome Tool** è un'applicazione Android progettata per assistere la comunità OpenStreetMap (OSM) nell'identificare e accogliere nuovi mappatori. Analizzando i changeset recenti e i profili utente, lo strumento aiuta i costruttori di comunità a coinvolgere i nuovi arrivati, riconoscere gli utenti esperti e riattivare i contributori di ritorno.

## 🚀 Funzionalità Principali

- **Classificazione Utenti**: Categorizza automaticamente gli utenti in base alla loro attività:
  - **Nuovo Arrivato (Newcomer)**: Account creato meno di 60 giorni fa.
  - **Utente Esperto (Power User)**: Più di 1000 modifiche totali.
  - **Utente di Ritorno (Returning User)**: Vecchio account (> 1 anno) con bassa attività (< 300 modifiche) che torna attivo.
- **Monitoraggio Changeset**: Recupera i recenti changeset OSM all'interno di una regione specifica (attualmente configurata per la Sicilia).
- **Integrazione OSMCha**: Visualizza le statistiche "Like" e "Dislike" da OSMCha per aiutare a valutare la qualità dei contributi.
- **Dettagli Utente**: Visualizza informazioni dettagliate su un mappatore, inclusa la data del primo edit e l'ultimo accesso.
- **Stato Accoglienza**: (Pianificato) Traccia quali utenti sono già stati accolti per evitare messaggi duplicati.

## 🛠 Tech Stack

Il progetto è costruito con pratiche moderne di sviluppo Android:

- **Linguaggio**: [Kotlin](https://kotlinlang.org/)
- **Framework UI**: [Jetpack Compose](https://developer.android.com/jetbrains/compose)
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Caricamento Immagini**: [Coil](https://coil-kt.github.io/coil/)
- **Persistenza Locale**: [Room](https://developer.android.com/training/data-storage/room) & [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Task in Background**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **Architettura**: MVVM (Model-View-ViewModel) con principi di Clean Architecture.

## 📂 Struttura del Progetto

Il codice segue una struttura modulare all'interno del modulo `app`:

- **`data/`**: Repository, servizi API, entità del database Room e DataStore.
- **`domain/`**: Logica di business, casi d'uso e il motore `UserAnalyzer`.
- **`ui/`**: Schermate Jetpack Compose, ViewModel e componenti UI.
- **`worker/`**: Worker in background per la sincronizzazione periodica dei dati.
- **`di/`**: Moduli di iniezione delle dipendenze Hilt.
- **`utils/`**: Classi di utilità e costanti.

## 🚦 Come Iniziare

### Prerequisiti
- Android Studio Ladybug o più recente.
- JDK 17 (configurato tramite Gradle Toolchains).

### Installazione
1. **Clona il repository**:
   ```bash
   git clone https://github.com/siciliamapper/welcometool.git
   cd welcometool
   ```

2. **Apri in Android Studio**:
   - Seleziona "Open" e scegli la directory del progetto.
   - Consenti a Gradle di sincronizzare e scaricare le dipendenze.

3. **Build & Run**:
   - Collega un dispositivo Android o avvia un Emulatore.
   - Clicca il pulsante **Run** (freccia verde) o esegui:
     ```bash
     ./gradlew installDebug
     ```

## 🗺 Roadmap

Abbiamo piani ambiziosi per migliorare lo strumento! Consulta le roadmap dettagliate situate nelle directory sorgente per aree specifiche:

- **[UI/UX Roadmap](app/src/main/java/com/antoninofaro/welcometool/ui/ROADMAP.md)**: Modalità scura, visualizzazione mappa e animazioni.
- **[Data Layer Roadmap](app/src/main/java/com/antoninofaro/welcometool/data/ROADMAP.md)**: Supporto multi-regione, offline-first e funzioni di esportazione.
- **[Domain Logic Roadmap](app/src/main/java/com/antoninofaro/welcometool/domain/ROADMAP.md)**: Euristiche avanzate e gamification.
- **[System & Workers Roadmap](app/src/main/java/com/antoninofaro/welcometool/worker/ROADMAP.md)**: Notifiche e sincronizzazione configurabile.

---

*Costruito con ❤️ per la Comunità OpenStreetMap.*
