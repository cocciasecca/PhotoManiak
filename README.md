# inPho(to)Maniak — Android 0.2.0

Galleria Android nativa in Kotlin/Jetpack Compose per le foto e i video già presenti su kDrive. Questa versione essenziale usa le **API ufficiali**, al posto di WebDAV.

## Uso

1. Scarica l’APK dagli artefatti di una compilazione completata nella scheda **Actions**, oppure compilalo seguendo le istruzioni sotto. Gli APK della cartella locale `dist/` non sono inclusi nel repository.
2. Nel [Manager Infomaniak](https://manager.infomaniak.com/v3/ng/accounts/token/list), genera un token con ambito **drive**. Questa versione non richiede `user_info`; se è già selezionato, il token va comunque bene.
3. Nell’app inserisci il token e l’ID numerico del kDrive, che trovi nell’indirizzo web dopo `/drive/`.
4. In **Cartelle**, includi quelle desiderate; tutti i livelli sottostanti sono compresi. Puoi escludere sottocartelle.
5. Premi **Mostra la mia libreria**. La timeline compare dopo l’elenco dei file; le date vengono poi raffinate leggendo i metadati. Il simbolo dell’orologio indica una data ancora provvisoria.

Aggiornando da 0.1.0 devi collegare nuovamente l’account con il token e scegliere di nuovo le cartelle. La vecchia password WebDAV non viene usata. Non inserire token nei sorgenti, negli issue o nei log di GitHub.

## Funzioni essenziali

- Un account/drive attivo per volta, configurato dall’utente nell’app.
- Lettura ricorsiva e paginata delle cartelle, con selezione ed esclusioni modificabili.
- Timeline per data di scatto; zoom foto, scorrimento e riproduzione video.
- Miniature richieste alle API kDrive, originali letti per metadati e visualizzazione.
- Indice SQLite e cache immagini privata fino a 256 MB.
- Token cifrato tramite Android Keystore; logout con cancellazione dei dati locali.
- Solo richieste GET/HEAD. Nessun upload, modifica, spostamento o cancellazione remota.

## Date e rete

Rimane il lettore EXIF/XMP della versione precedente: priorità di scatto/creazione ispirata a Immich, metadati video esposti da Android, poi nome file non ambiguo e ultima modifica remota. La data di caricamento/scaricamento non sostituisce una data EXIF valida. I dettagli mostrano l’origine della data. Non è una replica completa di ExifTool: sidecar e tag proprietari non sono tutti supportati.

La prima scansione richiede rete per leggere i metadati (fino a 16 MB per immagine e 24 MB per analisi video). Le scansioni successive riutilizzano i risultati dei file invariati. Le richieste alle API sono distanziate per contenere il traffico; se kDrive risponde 429, attendi prima di aggiornare. Per i video viene riutilizzato il collegamento di download durante le letture parziali.

Le miniature dipendono dai formati supportati da kDrive, la riproduzione video dai codec del telefono. La scansione si interrompe se Android termina il processo; un successivo aggiornamento riprende dai metadati salvati. Non è un servizio permanente in background.

## Compilazione

Android minimo 8.0 (API 26). Servono JDK 17, Android SDK Platform 35 e Build Tools 35.0.0.

Imposta `ANDROID_HOME`, oppure `sdk.dir` nel file locale `local.properties` ignorato da Git, quindi:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

L’APK si trova in `app/build/outputs/apk/debug/app-debug.apk`. Per i test con un emulatore o telefono collegato:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Il workflow `.github/workflows/android.yml` compila, verifica e allega l’APK come artefatto quando il progetto viene caricato su GitHub. Non contiene credenziali. Gli APK debug generati su macchine diverse possono avere firme differenti; la distribuzione stabile richiederà una chiave di firma propria, esclusa dal repository.

## Compatibilità da verificare

L’utente ha confermato il funzionamento dell’accesso con il proprio account. Miniature, streaming e tempi della prima scansione vanno verificati con la propria raccolta; i test automatici API usano un server simulato. La disponibilità delle API e delle cartelle dipende dai permessi del token e dell’account; non viene dichiarato supporto universale per tutti i piani Infomaniak. Questa è una versione di sviluppo, non una release Google Play.

## Riferimenti tecnici

- [API e token Infomaniak](https://developer.infomaniak.com/getting-started)
- [Rotte del client Android ufficiale](https://github.com/Infomaniak/android-kDrive/blob/master/app/src/main/java/com/infomaniak/drive/data/api/ApiRoutes.kt): consultate per endpoint e formato della paginazione; nessun codice copiato.
- [kPhotos](https://github.com/mateocarciu/kPhotos): riferimento per accesso con token e miniature; nessun codice copiato.
- [Date Immich](https://github.com/immich-app/immich/blob/main/server/src/services/metadata.service.ts).

App indipendente, non affiliata a Infomaniak.
