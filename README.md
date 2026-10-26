# Grenztanken

Android-App (Kotlin, Jetpack Compose): Lohnt sich das Tanken in Tschechien oder Polen gegenüber
deiner deutschen Stammtankstelle – und bis zu welcher Entfernung?

## Was die App zeigt

- **Deutschland:** aktueller Preis deiner gewählten Tankstelle (E5, E10 oder Diesel) über die
  Tankerkönig-API (Daten der Markttransparenzstelle für Kraftstoffe).
- **Tschechien / Polen:** frei angelegte Tankstellen mit Strecke. Welcher Preis genutzt wird:
  1. dein Preis von der Säule, wenn eingetragen
  2. der amtliche Höchstpreis für heute
  3. der amtliche Höchstpreis ab morgen (falls der heutige nicht mehr abrufbar war)
  4. der EU-Wochendurchschnitt des Landes als Richtwert
- **Morgen-Hinweis:** Ist der Höchstpreis für morgen schon verkündet, zeigt die Karte, ob es
  morgen günstiger oder teurer wird.
- **Rechnung pro Tankstelle:** Preisunterschied, Ersparnis bei deiner Tankmenge, Spritkosten für
  Hin- und Rückweg, Ergebnis unterm Strich und die einfache Strecke, bis zu der es sich rechnet.

## Rechenweg

```
Auslandspreis €/l  = Preis Landeswährung / Kurs
Ersparnis brutto   = Liter × (Preis DE − Preis Ausland)
Kosten pro km      = Verbrauch / 100 × Preis Ausland
Fahrtkosten        = 2 × Strecke einfach × Kosten pro km
Unterm Strich      = Ersparnis brutto − Fahrtkosten
Lohnt sich bis     = Ersparnis brutto / Kosten pro km / 2   (einfache Strecke)
```

Annahme: Der Sprit für die Fahrt wird im Ausland nachgetankt. Verschleiß und Zeit sind
bewusst nicht eingerechnet.

## Datenquellen

| Was | Quelle | Hinweis |
|---|---|---|
| Preise DE | Tankerkönig, `creativecommons.tankerkoenig.de` | CC BY 4.0, eigener API-Key pro Nutzer |
| Höchstpreis CZ | Finanzministerium, `mf.gov.cz` (Seite „Maximální přípustné ceny benzinu a nafty“) | Regierung hat am 26.10.2026 bis 30.11.2026 verlängert; werktags ab 14 Uhr steht dort der Preis für morgen |
| Höchstpreis PL | Energieministerium, `gov.pl/web/energia/…` (tägliche Bekanntmachung) | Adresse wird nach bekanntem Muster gebildet |
| Kurs CZK | ČNB `denni_kurz.txt` | |
| Kurs PLN | NBP API, Tabelle A | |
| Richtwert CZ/PL | EU Weekly Oil Bulletin über `fuel-prices.eu` (`/lang/api/country-history.php?cc=CZ`) | wöchentlich, EUR/l inkl. Steuern, CC BY 4.0; Landesdurchschnitt |

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
- Die Ortssuche nutzt den Geocoder des Geräts. Auf Geräten ohne Google-Dienste kann er leer
  bleiben; dann „In der Nähe“ nutzen.

## Bauen

### Über GitHub (empfohlen)

1. Neues Repository auf GitHub anlegen und dieses Projekt hineinpushen (Branch `main`).
2. Unter **Actions** läuft „Build APK“: Unit-Tests, dann Debug-APK.
3. Die APK liegt im Lauf unter **Artifacts → grenztanken-debug-apk**.

### Lokal

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
   Er legt `data/eu-CZ.json` und `data/eu-PL.json` ab und aktualisiert sie jeden Freitag.
2. In `app/src/main/java/de/grenztanken/app/Config.kt` setzen:
   `EU_RELAY_BASE_URL = "https://raw.githubusercontent.com/<nutzer>/<repo>/main/data/"`
3. Neu bauen. Das Repository muss dafür öffentlich sein.

## Signierte Release-Version (für Play Store / Weitergabe)

1. Schlüssel einmalig erzeugen und **sicher aufbewahren** (ohne ihn keine Updates):
   ```
   keytool -genkeypair -v -keystore release.jks -alias grenztanken -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 release.jks > release.jks.b64
   ```
2. In GitHub unter *Settings → Secrets and variables → Actions* anlegen:
   `SIGNING_KEYSTORE_BASE64` (Inhalt von `release.jks.b64`), `SIGNING_STORE_PASSWORD`,
   `SIGNING_KEY_ALIAS` (`grenztanken`), `SIGNING_KEY_PASSWORD`.
3. Der nächste Lauf erzeugt zusätzlich **grenztanken-release** mit signierter APK und AAB.

`release.jks` niemals ins Repository einchecken (steht in `.gitignore`).

## Veröffentlichen

- **Namensnennung EU-Werte:** „Daten: fuel-prices.eu, CC BY 4.0“ (Quelle: EU Weekly Oil Bulletin)
  steht in der App; auch in den Store-Text aufnehmen.
- **Tankerkönig-Bedingungen:** Namensnennung (CC BY 4.0) in der App *und* im Store-Text,
  z. B. „Preisdaten Deutschland: Tankerkönig (tankerkoenig.de), MTS-K, CC BY 4.0“.
  API-Keys dürfen nicht veröffentlicht werden (auch nicht auf GitHub); jeder Nutzer trägt
  seinen eigenen ein. Abfragen nur auf Nutzeraktion, keine Hintergrundabfragen.
- **Play Store:** aktuelle Anforderungen in der Play Console prüfen (Ziel-API-Level,
  Datenschutzerklärung – die App fragt optional den ungefähren Standort ab).
- App-Name und Paketname (`de.grenztanken.app`) vor der ersten Veröffentlichung festlegen;
  der Paketname lässt sich danach im Play Store nicht mehr ändern.

## Projektaufbau

```
app/src/main/java/de/grenztanken/app/
  AppViewModel.kt        Laden, Aktualisieren, Suche
  data/Tankerkoenig.kt   API DE
  data/Sources.kt        Höchstpreise CZ/PL, Kurse
  data/Parsers.kt        Text-Parser (getestet)
  data/Repo.kt           lokale Speicherung (DataStore)
  data/Geo.kt            Ortssuche, Standort
  logic/Calc.kt          Rechnung (getestet)
  logic/PriceResolver.kt Preisquelle wählen, Morgen-Hinweis (getestet)
  Config.kt              optionale Relay-Adresse für EU-Werte
  ui/                    Compose-Oberfläche
app/src/test/            Unit-Tests für Rechnung, Preisquelle und Parser
```
