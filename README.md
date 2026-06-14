# ArbeitszeitApp

Android-App zur lokalen Arbeitszeiterfassung mit Jetpack Compose.

## Funktionen
- Arbeitsbeginn, Pause, Fortsetzen, Arbeitsende
- Tagesansicht mit Status, Bruttozeit, Nettozeit, automatischer Pause und Tagessaldo
- Historie aller Tage mit Bearbeitung historischer Einträge
- Erfasste Akten pro Tag und Kennzahl Akten pro Stunde
- Wochenübersicht mit Brutto- und Netto-Balken pro Tag
- Monatsübersicht mit Brutto- und Netto-Balken pro Tag
- Push-Warnungen ab 9h und 10h Anwesenheit
- Farbige Balken: bis 9h normal, ab 9h dunkel lila, ab 10h pink
- Neues passendes App-Icon
- Lokale Speicherung in einer internen JSON-Datei
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

## Installation

1. APK auf das Android-Gerät kopieren.
2. In den Android-Einstellungen die Installation aus unbekannten Quellen für den verwendeten Dateimanager erlauben.
3. APK öffnen und installieren.

## Nutzung

- Auf **Heute** tippen, um Buchungen zu setzen.
- Auf **Historie** tippen, um vergangene Tage zu bearbeiten.
- Auf **Woche** tippen, um die Wochenübersicht zu sehen.
- Auf **Monat** tippen, um Monatswerte und Wochenblöcke zu sehen.

## Technische Hinweise

- Die App speichert Daten nur lokal im App-internen Speicher.
- Nettoarbeitszeit: immer 5 Minuten Parkplatzweg werden abgezogen; ab mehr als 6 Stunden 30 Minuten Bruttozeit zusätzlich 30 Minuten Pause.
- Manuelle Pausen entstehen aus den Lücken zwischen Arbeitsintervallen.
- Die Zeitberechnung geht von einer 5-Tage-Woche für die anteilige Sollzeit aus.
