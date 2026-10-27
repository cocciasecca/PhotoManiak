# Verifica versione API 0.2.0 — 27 ottobre 2026

- Build APK e Android Lint completati senza errori.
- 31 test JVM superati: 10 date, 6 selezione cartelle, 15 API (paginazione, errori, credenziali, redirect, sola lettura).
- 4 test Android 15 superati: cifratura token, persistenza cartelle, parsing JSON Android e aggiornamento SQLite.
- Firma APK verificata uguale a quella della versione 0.1.0 distribuita da questo progetto: aggiornamento senza disinstallazione.
- Nessun token reale utilizzato. L’accesso al proprio kDrive e la visualizzazione dei contenuti reali richiedono ancora una prova dell’utente.

APK: `dist/inPhoToManiak-0.2.0-debug.apk`.

SHA-256: `4b7d34677d4a6f9ee36c123b79c2ec0b0b6347fa1520a9cf88a141cb864d0d6d`

---

## Verifiche storiche della versione precedente

# Verifica della versione 0.1.0

Eseguita il 26 ottobre 2026.

- Compilazione APK debug riuscita con JDK 17, Gradle 8.11.1 e Android SDK 35.
- 25 test JVM superati: 10 date, 6 selezione ricorsiva cartelle, 9 protocollo WebDAV/sicurezza delle richieste.
- 4 test su emulatore Android 15 (API 35) superati: cifratura e cancellazione credenziali, aggiornamento SQLite senza perdita di altre righe, parsing XML nativo Android, persistenza selezione cartelle.
- Android Lint completato senza errori. Restano suggerimenti su versioni più recenti delle dipendenze e uso delle estensioni KTX.
- APK installato e schermata di accesso avviata su emulatore Pixel 6; nessun crash AndroidRuntime rilevato.
- Corretto il contrasto delle icone della barra di stato dopo il controllo visivo.

## Limiti della verifica

Nessun account Infomaniak reale è stato utilizzato. Autenticazione, compatibilità delle risposte del proprio kDrive, codec video e prestazioni su migliaia di elementi richiedono una prova sul telefono con la propria raccolta. Il comportamento su Android 8 è controllato staticamente, non provato su emulatore API 26.

## Artefatto

`dist/inPhoToManiak-0.1.0-debug.apk` — APK di sviluppo, firmato con chiave debug.

SHA-256: `348e2e9e0a136c2cd8038b2445980470d3df39480bcc346f5f31e89177902675`
