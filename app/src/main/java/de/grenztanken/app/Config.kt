package de.grenztanken.app

object Config {
    /**
     * Optional: Basis-URL der wöchentlich vom GitHub-Workflow "EU-Wochenpreise" abgelegten Dateien,
     * z. B. "https://raw.githubusercontent.com/<nutzer>/<repo>/main/data/".
     * Die App lädt dann "eu-CZ.json" und "eu-PL.json" von dort.
     * Leer = direkt bei fuel-prices.eu abfragen (höchstens einmal pro Tag und Land).
     */
    const val EU_RELAY_BASE_URL = ""
}
