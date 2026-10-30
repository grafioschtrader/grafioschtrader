# Mobile-App: Dashboard-Widgets auf dem Smartphone

**Code baseline:** backend 0.39.2, highest Flyway script `V0_39_2__typed_null_globalparameters.sql`; frontend mit
`@angular/core` 22.2.4, `@ionic/angular` 9.0.9 und Projekt `gt-mobile`; Arbeitsbaum vom 30.10.2026.

Dieses Dokument plant die verbleibende Arbeit an der Mobile-App von Grafioschtrader. Die App zeigt die Widgets des
Dashboards an; das Erfassen von Transaktionen gehört nicht zum Umfang. Es bleiben zwei Teile:

- die Abnahme der Progressive Web App (PWA) auf echten Installationen und Geräten mit einer Playwright-Spec (§2);
- die Store-App mit Capacitor, nur wenn native Fähigkeiten gebraucht werden (§3).

## 1. Ausgangslage

- Angular-Projekt `gt-mobile` (`frontend/src/gt-mobile/`, Ionic, keine Optimus UI), Build nach `dist/browser/m`
  mit Base Href `/grafioschtrader/m/`, Hash-Routing, Service Worker nur für die App-Hülle. Regeln in
  `frontend/CLAUDE.md` → „The mobile application“.
- Ansichten: `#/login`, die Tabs `#/tabs/dashboard` (Cards in der Reihenfolge des Layouts, Pull-to-Refresh) und
  `#/tabs/watchlists` (Kursliste je Watchlist), `#/widget/:instanceId` (Vollbild). Dashboard-Daten über
  `MobileDashboardService` aus `GET /api/dashboard`, `/api/dashboard/group/{n}` und `/api/dashboard/widget/{id}`, der
  gemeinsame Zustand in `MobileDashboardStore`; Watchlists über `MobileWatchlistService` aus
  `GET /api/watchlist/tenant`, `/api/watchlist/{id}` und `/api/watchlist/{id}/quote`.
- Anmeldung über `POST /api/login`, JWT im `sessionStorage` (`MobileLoginService`). Nach dem Schliessen der App ist
  eine neue Anmeldung nötig.
- Dev-Server: `npm run start:mobile` auf Port 4203 mit `proxy.conf.json`.

## 2. Abnahme der PWA

### 2.1 Playwright-Spec

Neues Playwright-Projekt `gt-mobile-e2e` in `frontend/playwright.config.ts` mit `devices['Pixel 7']` und
`baseURL` `http://localhost:4203`, abhängig vom bestehenden Setup-Projekt nur für die Benutzerdaten. Die Spec
(Nummer vor den Teardown-Specs `844`/`888`, nach den Specs, die Transaktionen anlegen):

- meldet sich mit einem Benutzer aus `frontend/e2e` an, dessen Mandant Transaktionen hat;
- wartet, bis jede Card einen Endzustand erreicht (Daten, Leertext oder Fehlertext, kein Spinner);
- öffnet die Vollbild-Ansicht von `PERFORMANCE_TOTAL_CHART`, wechselt den Zeitraum und prüft, dass
  `GET /api/dashboard/widget/{id}?config=` mit dem gewählten `range` gesendet wird und das Plotly-Element neu
  gezeichnet ist;
- wechselt in den Tab `#/tabs/watchlists`, wählt eine Watchlist mit einem gehaltenen Wertpapier und prüft, dass
  dessen Zeile Bestand, Kursgewinn und Total Position zeigt und eine nicht gehaltene Zeile keine dritte Zeile hat;
- meldet sich ab und prüft, dass `#/tabs/dashboard` wieder auf `#/login` umleitet.

Die Spec legt keine Daten an und braucht deshalb keine Bereinigung.

### 2.2 Echte Installationen und Geräte

- `npm run buildprod` erzeugt `dist/browser/index.html` und `dist/browser/m/index.html`.
- Unter Docker (Caddy, `docker/Caddyfile`) und auf einer klassischen Installation mit nginx und mit Apache
  (`util/installer/src/81-web-sites.sh`) liefert `/grafioschtrader/m/` die App und `/grafioschtrader/` weiterhin den
  Desktop. Bei Caddy ist zu prüfen, dass `try_files {path} /grafioschtrader/index.html` für das Verzeichnis
  `/grafioschtrader/m/` dessen `index.html` liefert und nicht auf die Desktop-Datei zurückfällt.
- In Chrome für Android und Safari für iOS lässt sich die App zum Startbildschirm hinzufügen und startet im
  Vollbildmodus mit dem Icon aus `src/gt-mobile/assets/icons/`.
- Der Service Worker der App hat den Scope `/grafioschtrader/m/` und beeinflusst den Desktop nicht.

## 3. Stufe 2: Store-App mit Capacitor

Stufe 2 wird erst begonnen, wenn mindestens eine dieser Fähigkeiten gebraucht wird: dauerhaftes Login mit
biometrischer Entsperrung, Push-Benachrichtigungen, native Home-Screen-Widgets oder Präsenz in App Store und
Google Play.

### 3.1 Hülle

- Capacitor-Projekt um den Build von `gt-mobile`, ohne Base Href `/grafioschtrader/m/`, Routing weiterhin mit Hash.
- Da die App nicht mehr von der Origin der Installation geladen wird, gibt der Benutzer beim ersten Start die Adresse
  seiner GT-Installation ein. Die App prüft sie mit `GET /api/gtinfo` und speichert sie. `authInterceptor` wirkt nur
  bei derselben Origin; die App braucht dafür einen eigenen Interceptor, der den Bearer-Token an die gespeicherte
  Adresse anhängt.
- Das Backend muss für diese Origin (`capacitor://localhost`, `https://localhost`) CORS erlauben.

### 3.2 Anmeldung über OAuth

Der OAuth-Server der MCP-Schnittstelle (`grafioschtrader.mcp.oauth`) unterstützt bereits Authorization Code mit PKCE,
Refresh-Token für öffentliche Clients und Client ID Metadata Documents. Für die App wird er erweitert:

- neuer Scope `app.read` neben `mcp.read` in `OAuthScopes`;
- Annahme dieses Tokens auf den lesenden `/api/dashboard/**`-Endpunkten (heute prüft
  `AccessTokenAuthenticationService` die Zielgruppe des MCP-Endpunkts);
- ein eigener Schalter für die App neben `gt.mcp.use`, damit ein Administrator die App freigeben kann, ohne MCP zu
  öffnen;
- das Client ID Metadata Document der App mit Redirect-URI im App-Schema.

Die App meldet sich über den System-Browser an (PKCE), legt den Refresh-Token im sicheren Speicher des Geräts ab und
entsperrt ihn biometrisch. Ein Schreib-Scope wird nicht vergeben.

### 3.3 Push und Home-Screen-Widgets

- Push über APNs und FCM braucht Zugangsdaten des App-Herausgebers. Da jede GT-Installation selbst gehostet ist, ist
  dafür ein zentraler Relay-Dienst nötig, der eine eigene Spezifikation bekommt. Bis dahin genügt Web Push aus der PWA
  (Android, iOS ab 16.4 nach Installation), das ohne Relay auskommt.
- Home-Screen-Widgets werden nativ geschrieben (iOS WidgetKit in Swift, Android Glance in Kotlin) und lesen ein
  einzelnes Widget-Ergebnis über `GET /api/dashboard/widget/{instanceId}` mit dem Token aus §3.2.

## 4. Entscheidungen

| Frage | Entscheidung |
|---|---|
| Framework | Angular + Ionic, in Stufe 2 Capacitor |
| Eigenes Projekt oder responsives Desktop-Frontend | eigenes Projekt `gt-mobile`; Optimus-UI-Ansichten werden nicht für Smartphones umgebaut |
| Layout bearbeiten in der App | nein, nur im Desktop |
| Token-Ablage der PWA | `sessionStorage` wie im Desktop, neue Anmeldung pro App-Start; dauerhaftes Login erst mit §3.2 |
| Erfassen von Transaktionen | nicht Teil dieser Spezifikation |
