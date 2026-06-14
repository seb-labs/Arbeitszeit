# ArbeitszeitApp – lokale Web-Simulation

Diese kleine Vorschau bildet die wichtigsten Funktionen der Android-App im Browser nach, damit du sie am PC testen kannst.

## Start

Im Projektordner ausführen:

```bash
cd /home/seb/Schreibtisch/ArbeitszeitApp/preview
python3 -m http.server 8000
```

Dann im Browser öffnen:

```text
http://127.0.0.1:8000/
```

## Funktionen

- Arbeitsbeginn, Pause, Fortsetzen, Arbeitsende
- automatische 30-Minuten-Pause ab mehr als 6 Stunden ununterbrochener Arbeit
- Tagesaldo, Wochenübersicht, Monatsübersicht
- Historie mit Bearbeitung
- Wochen-Sollzeit pro Kalenderwoche anpassbar
- lokale Speicherung im Browser über `localStorage`

## Hinweise

- Die Web-Simulation ist nur für den PC-Test gedacht.
- Sie ersetzt nicht die Android-App, hilft aber beim schnellen Prüfen von Logik und Bedienung.
- Mit **Beispieldaten laden** kannst du sofort sehen, wie die App reagiert.
