# Stato attuale — versione 0.2.0

La versione essenziale mantiene l’interfaccia Android, la selezione ricorsiva delle cartelle e la timeline EXIF. Il trasporto è stato migrato alle API REST kDrive, con ID drive e token personale configurati nell’app. Le miniature provengono dal cloud; GET/HEAD sono gli unici metodi ammessi. Il token è inviato solo all’origine API e viene rimosso sui redirect di download. Vedi README.md per istruzioni e limiti attuali.

## Specifica iniziale (storica, WebDAV sostituito dalle API)

# inPho(to)Maniak

Galleria Android in sola lettura, utilizzabile da qualsiasi utente con un account Infomaniak kDrive. Ogni installazione configura il proprio account e seleziona le proprie cartelle nell’app; nessun dato account o percorso è fissato nel codice. Un account attivo per volta.

## Requisiti concordati
- Sfogliare foto e video già presenti nel cloud; nessun upload, cancellazione, spostamento o modifica.
- Selezione di cartelle remote, scansione ricorsiva a qualsiasi profondità, esclusioni di sottocartelle.
- Timeline unica in stile Google Foto/Immich, più recenti prima, raggruppata per giorno.
- Originali e struttura remota intatti. Nessuna deduplicazione dei file.
- Migliaia di elementi, indice e miniature locali; visualizzatore a schermo intero e video.

## Scelte tecniche
Android nativo: Kotlin, Jetpack Compose, database locale e cache limitata.
Connessione iniziale via WebDAV HTTPS ufficiale di kDrive; operazioni remote ammesse: PROPFIND, GET, HEAD.
Credenziali memorizzate con Android Keystore. Nessun segreto nel repository.
La scansione deve essere incrementale, interrompibile e mantenere l'indice precedente in caso di errore.

## Date
Riferimento: https://github.com/immich-app/immich/blob/main/server/src/services/metadata.service.ts
Priorità Immich verificata il 26 ottobre 2026:
SubSecDateTimeOriginal, SubSecCreateDate, DateTimeOriginal, CreationDate, CreateDate,
MediaCreateDate, DateTimeCreated, GPSDateTime, DateTimeUTC, SonyDateTime2, SourceImageCreateTime.
Usare i tag supportati dal lettore, preservare offset e data locale dello scatto; non usare la data di download.
Mostrare la provenienza della data nei dettagli. Se nessun metadato valido è disponibile,
provare un nome file con data non ambigua, poi last-modified remoto; distinguere questo fallback.
Non dichiarare parità completa con ExifTool: Immich effettua elaborazione sul server.

## Vincolo da risolvere
WebDAV non fornisce normalmente miniature o date EXIF. La prima indicizzazione può richiedere letture
remote dei contenuti. Preferire API kDrive per miniature e metadati se disponibili con autenticazione
adatta a un client Android; altrimenti usare letture parziali/cache e rendere esplicito il progresso.

## Fonti
- https://github.com/mateocarciu/kPhotos (riferimento web Nuxt/Vue, nessun codice copiato)
- https://www.infomaniak.com/fr/support/faq/2409/se-connecter-a-kdrive-par-webdav-obtenir-le-id-kdrive
- https://github.com/immich-app/immich/blob/main/server/src/services/metadata.service.ts
