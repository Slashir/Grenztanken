# Grenztanken

Android-App (Kotlin, Jetpack Compose): Lohnt sich das Tanken in Tschechien oder Polen gegenüber
deiner deutschen Stammtankstelle – und bis zu welcher Entfernung?

Dir hilft die App? Über Ko-fi kannst du mir einen Kaffee ausgeben:

[![Unterstütze Slashir auf Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/slashir)

> **Vor dem ersten Start: eigenen API-Schlüssel bei Tankerkönig beantragen.**
> Für die Preise in Deutschland braucht jeder Nutzer einen eigenen, persönlichen Schlüssel von
> Tankerkönig. Er ist **kostenlos** und schnell beantragt: <https://onboarding.tankerkoenig.de/>.
> Den Schlüssel in der App unter *Einstellungen → Tankerkönig-Schlüssel* eintragen. Er bleibt auf dem
> Gerät und wird nur an Tankerkönig gesendet. Schlüssel nicht weitergeben und nicht veröffentlichen.

## Installation

1. Unter [Releases](https://github.com/Slashir/Grenztanken/releases) die neueste
   `grenztanken-v….apk` auf dem Handy herunterladen.
2. Datei öffnen. Android fragt einmal, ob der Browser Apps installieren darf: erlauben.
3. App starten, unter *Einstellungen* den Tankerkönig-Schlüssel eintragen und die eigene deutsche
   Tankstelle wählen.

Neue Versionen installieren sich genauso über die alte, die Einstellungen bleiben erhalten.

## Was die App zeigt

- **Deutschland:** aktueller Preis deiner gewählten Tankstelle (E5, E10 oder Diesel) über die
  Tankerkönig-API (Daten der Markttransparenzstelle für Kraftstoffe). Ist sie geschlossen, bleibt der
  letzte bekannte Preis mit Zeitstempel stehen.
- **Einordnung des deutschen Preises:**
  - *Umgebung jetzt:* Durchschnitt und günstigste der geöffneten Tankstellen im Umkreis von 5 km
    (dieselbe Tankerkönig-Umkreisabfrage liefert auch den eigenen Preis, also keine Zusatzabfrage).
  - *Ø heute / Ø 7 Tage:* aus den lokal mitgeschriebenen eigenen Abrufen (14 Tage). Jeder Preis gilt
    bis zum nächsten Abruf, höchstens 2 Stunden. Tankerkönig erlaubt keine Hintergrundabfragen, daher
    ist der Wert nur so dicht wie die eigenen Abrufe; die App zeigt die Zahl der Abrufe an.
- **Tschechien / Polen:** Tankstellen per OpenStreetMap-Suche (rund um die deutsche Tankstelle oder
  um einen Ort) oder frei angelegt. Bei Tankstellen aus der Suche berechnet die App die Autostrecke
  ab der deutschen Tankstelle selbst und bietet „Route in Google Maps“ an. Welcher Preis genutzt wird:
  1. dein Preis von der Säule, wenn eingetragen
  2. der amtliche Höchstpreis für heute
  3. der amtliche Höchstpreis ab morgen (falls der heutige nicht mehr abrufbar war)
  4. der EU-Wochendurchschnitt des Landes als Richtwert
- **Morgen-Hinweis:** Ist der Höchstpreis für morgen schon verkündet, zeigt die Karte, ob es
  morgen günstiger oder teurer wird.
- **Rechnung pro Tankstelle:** Preisunterschied, Ersparnis bei deiner Tankmenge, Spritkosten und
  (optional) Verschleiß für Hin- und Rückweg, Ergebnis unterm Strich und die einfache Strecke, bis zu
  der es sich rechnet.
- **Verschleiß aus dem ADAC-Autokatalog:** Marke → Modell → Generation → Variante wählen; die App
  übernimmt die Werkstatt- und Reifenkosten (optional Wertverlust und ADAC-Verbrauch) passend zur
  eigenen Jahresfahrleistung und Haltedauer bzw. Leasinglaufzeit. Wer least und einen Wartungsvertrag hat, lässt das Feld leer
  oder trägt nur den Mehrkilometer-Satz ein.

## Rechenweg

```
Auslandspreis €/l  = Preis Landeswährung / Kurs
Ersparnis brutto   = Liter × (Preis DE − Preis Ausland)
Sprit pro km       = Verbrauch / 100 × Preis Ausland
Kosten pro km      = Sprit pro km + Verschleiß pro km (Einstellung, Standard 0)
Verschleiß (ADAC)  = Zusatzkosten pro km: (Monatskosten bei km+5.000 − bei km−5.000) × 12 / 10.000
                     (ADAC-Werte zwischen den Stufen linear umgerechnet)
Fahrtkosten        = 2 × Strecke einfach × Kosten pro km
Unterm Strich      = Ersparnis brutto − Fahrtkosten
Lohnt sich bis     = Ersparnis brutto / Kosten pro km / 2   (einfache Strecke)
```

Annahme: Der Sprit für die Fahrt wird im Ausland nachgetankt. Verschleiß trägt jeder selbst in ct/km
ein oder übernimmt ihn aus dem ADAC-Autokatalog. Für neuere Varianten liefert der ADAC Werte für
2–5 Jahre Haltedauer und 5.000–60.000 km pro Jahr (auf der Seite sichtbar ist nur 5 Jahre/15.000 km).
Für den Umweg zählt, was jeder *zusätzliche* km kostet, nicht der Durchschnitt: Werkstatt und vor allem
Wertverlust fallen zum großen Teil auch ohne Fahren an. Beispiel Tucson 1.6 T-GDI (ab 09/25), 5 Jahre:
Werkstatt 69 € bei 10.000 km, 110 € bei 20.000 km → ein km mehr kostet 4,9 ct, der Durchschnitt bei
15.000 km wäre 6,8 ct. Ältere Varianten haben nur einen Wert (5 Jahre, 15.000 km); dann nimmt die App
den Durchschnitt der Werkstattkosten pro km und bietet keinen Wertverlust an. Zeit ist bewusst nicht
eingerechnet.

## Datenquellen

| Was | Quelle | Hinweis |
|---|---|---|
| Preise DE | Tankerkönig, `creativecommons.tankerkoenig.de` | CC BY 4.0, eigener API-Key pro Nutzer |
| Höchstpreis CZ | Finanzministerium, `mf.gov.cz` (Seite „Maximální přípustné ceny benzinu a nafty“) | Regierung hat am 26.10.2026 bis 30.11.2026 verlängert; werktags ab 14 Uhr steht dort der Preis für morgen |
| Höchstpreis PL | Energieministerium, `gov.pl/web/energia/…` (tägliche Bekanntmachung) | Adresse wird nach bekanntem Muster gebildet |
| Kurs CZK | ČNB `denni_kurz.txt` | |
| Kurs PLN | NBP API, Tabelle A | |
| Richtwert CZ/PL | EU Weekly Oil Bulletin über `fuel-prices.eu` (`/lang/api/country-history.php?cc=CZ`) | wöchentlich, EUR/l inkl. Steuern, CC BY 4.0; Landesdurchschnitt |
| Tankstellensuche CZ/PL | OpenStreetMap über Nominatim (`nominatim.openstreetmap.org`, `amenity=fuel`) | © OpenStreetMap-Mitwirkende, ODbL; höchstens 1 Anfrage/s, nur auf Nutzeraktion |
| Strecke | OSRM-Demoserver (`router.project-osrm.org`) | Autostrecke einfach, ab der deutschen Tankstelle |
| Autokosten | ADAC Autokatalog (`adac.de/rund-ums-fahrzeug/autokatalog/marken-modelle/…`) | keine offizielle Schnittstelle; die App liest die Daten, die jede Katalogseite als JSON (`window.__APOLLO_STATE__`) mitliefert, nur auf Nutzeraktion |

**Grenzen:**
- Höchstpreise sind Obergrenzen. An der Säule ist es oft günstiger, dann rechnet die App vorsichtig.
- Endet eine Regulierung, nutzt die App den EU-Wochendurchschnitt. Das ist ein Landesdurchschnitt;
  Grenztankstellen können deutlich abweichen. Wer es genau will, trägt den Säulenpreis ein.
- Den Höchstpreis für heute kennt die App nur, wenn sie ihn abgerufen hat, bevor das
  Ministerium den für morgen veröffentlicht (CZ werktags 14 Uhr). Sonst rechnet sie mit dem für morgen
  und sagt das dazu.
- Die polnischen Bekanntmachungen werden über das Muster ihrer Webadresse gefunden
  (`…-obowiazujaca-21-pazdziernika-2026-r` bzw. `…-w-dniach-24-26-pazdziernika-2026-r`).
  Ändert das Ministerium das Muster, muss `Parsers.plCandidates` angepasst werden.
- Nominatim und der OSRM-Demoserver sind öffentliche Server für geringe Last. Für eine
  Veröffentlichung mit vielen Nutzern einen eigenen oder kommerziellen Dienst eintragen (`data/Osm.kt`).
  Die Suche liefert höchstens 40 Treffer im Umkreis von 15 km; in dichten Gegenden den Ort genauer wählen.
- Die Strecke bezieht sich auf die deutsche Tankstelle zum Zeitpunkt der Berechnung. Nach einem
  Wechsel der deutschen Tankstelle im Dialog „Strecke neu berechnen“ tippen.
- Der ADAC-Katalog hat keine offizielle Schnittstelle. Ändert der ADAC den Aufbau seiner Seiten, muss
  `data/Adac.kt` angepasst werden. Für ganz neue Modelle gibt es oft noch keine Kostenrechnung.
- Die Ortssuche für die deutsche Tankstelle nutzt den Geocoder des Geräts. Auf Geräten ohne Google-Dienste kann er leer
  bleiben; dann „In der Nähe“ nutzen.

## Selbst bauen

Projekt in Android Studio öffnen, oder auf der Kommandozeile (JDK 17 oder neuer, Android-SDK in
`local.properties` als `sdk.dir` eingetragen):

```
gradlew testDebugUnitTest assembleDebug
```

Die APK liegt dann unter `app/build/outputs/apk/debug/app-debug.apk`. Der Wrapper nutzt Gradle 8.13.

## Optional: EU-Werte über das eigene Repo verteilen

Standardmäßig fragt jede App die EU-Werte direkt bei fuel-prices.eu ab (höchstens einmal pro Tag
und Land, nur wenn kein Höchstpreis gilt). Bei vielen Nutzern ist es fairer, die Daten einmal pro
Woche selbst abzulegen:

1. Workflow **EU-Wochenpreise** einmal manuell starten (Actions → EU-Wochenpreise → Run workflow).
   Er legt `data/eu-CZ.json` und `data/eu-PL.json` ab. Der Zeitplan (jeden Freitag) ist
   abgeschaltet; zum automatischen Aktualisieren den `schedule`-Block in
   `.github/workflows/eu-weekly.yml` wieder einkommentieren.
2. In `app/src/main/java/de/grenztanken/app/Config.kt` setzen:
   `EU_RELAY_BASE_URL = "https://raw.githubusercontent.com/<nutzer>/<repo>/main/data/"`
3. Neu bauen. Das Repository muss dafür öffentlich sein.

## Projektaufbau

```
app/src/main/java/de/grenztanken/app/
  AppViewModel.kt        Laden, Aktualisieren, Suche
  data/Tankerkoenig.kt   API DE
  data/Sources.kt        Höchstpreise CZ/PL, Kurse
  data/Parsers.kt        Text-Parser (getestet)
  data/Repo.kt           lokale Speicherung (DataStore)
  data/Geo.kt            Ortssuche, Standort
  data/Osm.kt            Tankstellensuche CZ/PL (Nominatim), Strecke (OSRM) (Parser getestet)
  data/Adac.kt           ADAC-Autokatalog: Marken, Modelle, Varianten, Kosten (Parser getestet)
  logic/Calc.kt          Rechnung (getestet)
  logic/PriceResolver.kt Preisquelle wählen, Morgen-Hinweis (getestet)
  logic/Stats.kt         Umgebung, mitgeschriebener Verlauf, zeitgewichteter Ø (getestet)
  Config.kt              optionale Relay-Adresse für EU-Werte
  ui/                    Compose-Oberfläche (Theme.kt: Farbschema hell/dunkel und Schrift Inter,
                         Components.kt: gemeinsame Bausteine wie Zapfsäulen-Preis, Ergebnis-Karte,
                         gruppierte Listen, Quellen-Dialog; AdacScreen.kt: Auswahl im ADAC-Katalog)
app/src/test/            Unit-Tests für Rechnung, Preisquelle, Parser, OSM-Suche und ADAC-Katalog
```
