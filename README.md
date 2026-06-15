# ArbeitszeitApp

Android-App zur lokalen Arbeitszeiterfassung mit Jetpack Compose.

## Funktionen
- Arbeitsbeginn, Pause, Fortsetzen, Arbeitsende
- Tagesansicht mit Status, Bruttozeit, Nettozeit, automatischer Pause und Tagessaldo
- Historie aller Tage mit Bearbeitung historischer Einträge
- Wochenübersicht mit Brutto- und Netto-Balken pro Tag
- Monatsübersicht mit Brutto- und Netto-Balken pro Tag
- Push-Warnungen ab 9h und 10h Anwesenheit
- Farbige Balken: bis 9h normal, ab 9h dunkel lila, ab 10h pink
- Neues passendes App-Icon
- Arbeitsort im eigenen Tab frei einstellbar
- Lokale Speicherung in einer Room-/SQLite-Datenbank
- Keine Cloud, kein Tracking

## Build

Voraussetzungen:
- Java 17
- Android SDK installiert

Build der Debug-APK:
```bash
./gradlew assembleDebug
```

Tests:
```bash
./gradlew testDebugUnitTest
```

## Download
Offizieller Release:
- [v1.1](https://github.com/seb-labs/Arbeitszeit/releases/tag/v1.1)

Die APK liegt derzeit zusätzlich noch im Repo, damit der Release-Workflow sie hochladen kann:
`APK/ArbeitszeitApp-debug.apk`

## Installation

1. APK vom GitHub Release herunterladen.
2. Auf das Android-Gerät kopieren oder direkt am Gerät öffnen.
3. In den Android-Einstellungen die Installation aus unbekannten Quellen für den verwendeten Dateimanager erlauben.
4. APK öffnen und installieren.

## Nutzung

- Auf **Heute** tippen, um Buchungen zu sehen und den aktuellen Status zu prüfen.
- Auf **Manuelle Korrektur** tippen, um vergangene Tage zu bearbeiten.
- Auf **Woche** tippen, um die Wochenübersicht zu sehen.
- Auf **Monat** tippen, um Monatswerte und Wochenblöcke zu sehen.
- Auf **Arbeitsort** tippen, um die Koordinate für den Geofence einzutragen.

## Technische Hinweise

- Die App speichert Daten nur lokal in einer Room-/SQLite-Datenbank.
- Der Arbeitsort wird im Tab **Arbeitsort** als Latitude/Longitude gespeichert und für den Geofence verwendet.
- Nettoarbeitszeit: immer 5 Minuten Parkplatzweg werden abgezogen; ab mehr als 6 Stunden 30 Minuten Bruttozeit zusätzlich 30 Minuten Pause.
- Manuelle Pausen entstehen aus den Lücken zwischen Arbeitsintervallen.
- Die Zeitberechnung zieht pauschal 5 Minuten Parkplatzweg ab und ab 6:30h Brutto zusätzlich 30 Minuten Pause.

## Kontakt
`Arbeitszeit@seblabs.unbox.at`
