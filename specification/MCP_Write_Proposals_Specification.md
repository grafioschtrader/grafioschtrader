# MCP-Server: Buchungsvorschläge von KI-Assistenten

**Code baseline:** backend 0.38.3, highest versioned Flyway script `V0_39_0__watchlist_indicator_and_user_access_token.sql`.

**Umfang:** Der MCP-Server unter `/api/mcp` bietet acht lesende Werkzeuge (`grafioschtrader.mcp.GTMcpReadTools`) und
nimmt persönliche Zugriffstoken sowie OAuth-Zugriffstoken an, beide nur mit der Berechtigung `READ`. Diese
Spezifikation plant den Schreibzugriff: Ein Assistent legt Buchungsvorschläge an, und der Benutzer gibt sie in der
GT-Oberfläche frei. Gebucht wird nur durch diese Freigabe.

## 1. Grundlagen

### 1.1 Grundsatz

Der Assistent bucht nie selbst. Er legt Vorschläge an, und der Benutzer gibt sie in der GT-Oberfläche in seiner
normalen Sitzung frei. Zwei Werkzeugaufrufe würden nichts garantieren, denn den zweiten kann das Modell selbst
auslösen. `destructiveHint` und MCP-Elicitation laufen über den Client und sind Hinweise, kein serverseitiger
Nachweis einer Zustimmung. Die Freigabe ist ein JWT-authentifizierter Pfad der Hauptkette von `SecurityGTConfig`,
den ein MCP-Token nicht erreicht, weil `AccessTokenAuthenticationFilter` nur in der Kette von `McpSecurityConfig`
liegt.

### 1.2 Schichtung

| Teil | Modul | Grund |
|---|---|---|
| Freigabe der Berechtigung `WRITE` in `UserAccessTokenJpaRepositoryImpl.validateToken`, Auswahl im Token-Dialog | `grafiosch-server-base`, Frontend-Bibliotheksteil `src/app/lib/accesstoken/` | Tokens und ihre Berechtigung sind Bibliothekscode |
| Entität `McpProposal`, Werkzeuge `propose_*`, Scope-Filter, Buchungsdienst, Task `PURGE_MCP_PROPOSAL` | `grafioschtrader-common` / `grafioschtrader-server` | Vorschläge buchen Anwendungsentitäten |
| Freigabeseite, Liste offener Vorschläge | Frontend, Anwendungsteil `src/app/mcp/` | gehört zum Menüeintrag `mcp-settings-menu.ts` |

`mcp_proposal` darf auf `user_access_token` und `oauth_authorization` verweisen; keine Bibliothekstabelle verweist
auf `mcp_proposal`.

### 1.3 Mandant und Schalter

Ein Vorschlag gehört immer zum Heimatmandanten seines Benutzers, wie jeder MCP-Zugriff
(`AccessTokenAuthenticationService`, `TenantAccessResolver.decide(user, user.getIdTenant(), user.getIdTenant())`).
Solange der Globalparameter `gt.mcp.use` den Wert `0` hat, beantworten auch die Pfade unter `/api/mcpproposal` jede
Anfrage mit 404 (`McpAvailability.isOpen()`), und es wird nichts freigegeben. Offene Vorschläge laufen dann über
`PURGE_MCP_PROPOSAL` ab.

## 2. Schreibberechtigung

### 2.1 Persönliche Token

- `UserAccessTokenJpaRepositoryImpl.validateToken` lässt `WRITE` zu, ausser für Benutzer mit
  `User.isHomeTenantReadOnly()`. Ein Token mit `WRITE` lebt höchstens 90 Tage, ein Token mit `READ` weiterhin
  höchstens `UserAccessToken.MAX_EXPIRES_IN_DAYS` (365).
- `UserAccessTokenCreateRequest.scope` erhält ein `@DynamicFormField` mit Auswahl `READ`/`WRITE`, und
  `UserAccessTokenCreateDialogComponent` sendet den gewählten Scope statt des festen Werts `'READ'`.

### 2.2 OAuth

- `OAuthScopes.toScope` gewährt `WRITE`, wenn die Anfrage `mcp.write` enthält. `mcp.write` schliesst `mcp.read` ein.
- Beide Metadaten-Dokumente (`OAuthMetadataController`) nennen `scopes_supported: [mcp.read, mcp.write]`.
  Clients wie ChatGPT fordern alle angekündigten Scopes an; deshalb bietet die Consent-Seite
  (`McpOAuthAuthorizeComponent`) bei einer angefragten Schreibberechtigung die Wahl, nur Lesen zu erlauben. Die
  Wahl geht mit `ConsentRequest` an `/api/oauth/consent`, und die Familie (`oauth_authorization.scope`) erhält den
  gewählten Scope. Ein Refresh erweitert ihn nie.
- Für Benutzer mit `isHomeTenantReadOnly()` gewährt der Consent immer nur `READ`.

### 2.3 Scope-Prüfung

Der Transport beantwortet jeden angenommenen JSON-RPC-Aufruf mit 200, und `McpToolExecutor` kann nur ein
Werkzeugergebnis liefern. Die Prüfung liegt deshalb in der Kette von `McpSecurityConfig`: Ein Filter nach
`AccessTokenAuthenticationFilter` liest bei `tools/call` den Werkzeugnamen aus dem Body und vergleicht ihn mit dem
Scope des `AccessTokenPrincipal`. Ein Werkzeug, das `WRITE` verlangt, antwortet einem `READ`-Token mit 403 und
`WWW-Authenticate: Bearer error="insufficient_scope", scope="mcp.write"`. Die Zuordnung Werkzeug → Scope steht an
einer Stelle neben der Registrierung in `McpSecurityConfig.gtMcpToolSpecifications`.

## 3. Vorschläge

- **Werkzeuge** (`destructiveHint = false`, denn sie ändern keine Buchung):
  - `propose_security_transaction` (`ACCUMULATE`, `REDUCE`, `DIVIDEND`) und `propose_cash_transfer` verlangen
    Scope `WRITE`;
  - `get_proposal_status(idProposal)` ist lesend.
- **Ablauf von `propose_*`:**
  - Instrumente und Konten werden über Ids aufgelöst. Ein mehrdeutiger Name ist ein Fehler, es gibt keine stille
    Wahl des ersten Treffers.
  - Pflichtfelder werden geprüft und der Geldbetrag auf dem Server berechnet.
  - Das Werkzeug gibt die Vorschau und die Vorschlagsnummer zurück. Ist `gt.mcp.public.base.url` gesetzt, kommt die
    Freigabe-URL dazu: Basis-URL + `gt.mcp.proposal.page` (Standard `/grafioschtrader/mcpproposal/`) + Id. Es gibt
    kein Geheimnis, das das Modell einlösen könnte.
  - Jede Instrumenten-Id durchläuft `McpInstrumentAccess.requireVisibleSecurity`. Jedes Konto muss zum
    Heimatmandanten gehören.
  - Jeder Aufruf zählt wie jedes Werkzeug gegen das Tagesbudget `McpToolCall` (`McpToolExecutor`).
- **Entität `grafioschtrader.entities.McpProposal`** (`mcp_proposal`) in `grafioschtrader-common`, Spalten:
  - `id_mcp_proposal`, `id_user`, `id_tenant` (der Heimatmandant beim Anlegen), `proposal_type`;
  - die Herkunft: `origin_kind` (`PERSONAL` oder `OAUTH`) und genau eine der Spalten `id_user_access_token` oder
    `id_oauth_authorization`, je mit Fremdschlüssel `ON DELETE SET NULL`. Ist die Herkunftsspalte leer, obwohl
    `origin_kind` gesetzt ist, wurde die Herkunft widerrufen;
  - `payload` (kanonisches JSON, unveränderlich) und `payload_hash`;
  - die berechneten Vorschaubeträge;
  - `status` (`PENDING`, `EXECUTED`, `REJECTED`, `EXPIRED`, `FAILED`);
  - `created_at`, `expires_at` (24 Stunden);
  - `decided_at`, `id_transaction` (Ergebnis), `failure_message`.
- **Pflichten der Entität** (Skill `new-entity`):
  - Limit `MAX_MCP_PROPOSAL_OPEN`, Standard 20 Vorschläge mit `status = PENDING` pro Benutzer, mit eigenem Zähler in
    `LimitKeyConfig` und gleichem Seed in der Produktionsmigration und in
    `src/test/resources/db/migration/test/V4__seed_entity_limits.sql`;
  - Export/Löschung in `MyDataExportDeleteDefinition.exportDefinitions` mit `ID_TENANT` und nur `DELETE_USE`.
    Vorschläge sind ein Protokoll der Assistententätigkeit, kein Bestand, der beim Import gebraucht wird;
  - Bereinigung durch den Anwendungstask `PURGE_MCP_PROPOSAL` (58) in `TaskTypeExtended`, mit dem Frontend-Spiegel
    `frontend/src/app/shared/types/task.type.extended.ts` und dem NLS-Schlüssel in beiden Sprachen. Er setzt
    abgelaufene `PENDING` auf `EXPIRED` und löscht abgeschlossene Vorschläge 90 Tage nach `decided_at` bzw.
    `expires_at`, einmal täglich.
- **Oberfläche:** Der Dialog **KI-Assistenten (MCP)** zeigt die offenen Vorschläge des Benutzers mit Verweis auf die
  Freigabeseite. So erreicht der Benutzer einen Vorschlag auch ohne Freigabe-URL.

## 4. Freigabe und Ausführung

**Eigentum vor jedem Zugriff.** Jeder Zugriff auf einen Vorschlag sucht ihn mit
`id_mcp_proposal = ? AND id_user = ? AND id_tenant = ?`, mit dem authentifizierten Benutzer und seinem Heimatmandanten
(`User.getActualIdTenant()`). Das gilt für Vorschau (`GET /api/mcpproposal/{id}`), Freigabe, Ablehnung, die
Wiederholung eines gespeicherten Ergebnisses und `get_proposal_status`. Ein fremder und ein nicht existierender
Vorschlag ergeben dieselbe Antwort (404). Eine bekannte Id mit bekanntem `payload_hash` ersetzt diese Prüfung nicht.

**Kein Freigeben im gewechselten Mandanten.** Arbeitet der Benutzer in der Oberfläche in einem gewechselten Mandanten
(`getIdTenant() != getActualIdTenant()`, gesetzt von `JwtTokenHandler`), lehnen Freigabe und Ablehnung mit der
Aufforderung ab, zum Heimatmandanten zurückzukehren. Der Vorschlag bleibt `PENDING`. Gebucht wird immer im
`id_tenant` des Vorschlags, nie im gerade aktiven Mandanten.

**Gültige Herkunft.** Die Freigabe verlangt eine weiterhin gültige Herkunft mit Schreibrecht:

- **`PERSONAL`:** Das Token existiert noch, ist nicht abgelaufen und hat Scope `WRITE`. Ist es abgelaufen oder
  widerrufen, braucht es einen neuen Vorschlag.
- **`OAUTH`:** Die Autorisierungsfamilie ist nicht widerrufen, nicht abgelaufen und hat `WRITE`. Ablauf und
  Rotation einzelner Zugriffstokens berühren den Vorschlag nicht, denn er gehört zur Familie und nicht zu einem
  Zugriffstoken.

Ein Widerruf der Herkunft verhindert jede weitere Freigabe.

Die Angular-Seite zeigt den Vorschlag mit seinen Beträgen. Freigabe: `POST /api/mcpproposal/{id}/approve` mit
`payloadHash`. Ablehnung: `POST /api/mcpproposal/{id}/reject`. Beide Pfade gehören nicht in
`SecurityGTConfig.READ_ONLY_WRITE_ALLOW_LIST`, damit `TenantReadOnlyFilter` einen schreibgeschützten Mandanten
abweist. Die Freigabe läuft in **einer** Datenbanktransaktion:

1. **Sperren:** `SELECT … FROM mcp_proposal WHERE id_mcp_proposal = ? AND id_user = ? AND id_tenant = ? FOR UPDATE`.
   Eine zweite, gleichzeitige Freigabe wartet auf diese Zeilensperre und sieht danach den neuen Status. Ist der
   Status `EXECUTED`, kommt das gespeicherte Ergebnis zurück, ohne erneut zu buchen. Bei `REJECTED`, `EXPIRED` oder
   `FAILED` wird mit diesem Status abgelehnt. Weiter geht es nur bei `PENDING` mit passendem `payload_hash` und
   `expires_at` in der Zukunft.
2. **Erneut prüfen, ob die Ausführung zulässig ist:**
   - die gültige Herkunft (oben);
   - der Heimatmandant ist nicht schreibgeschützt, entschieden über `TenantAccessResolver.decide`;
   - Instrumente sind sichtbar (`McpInstrumentAccess`), Konten gehören dem Mandanten des Vorschlags, und der Tag
     liegt nicht vor `closedUntil`.
3. **Neu rechnen:** Der Geldbetrag wird neu berechnet. Weicht er von der Vorschau ab, weil sich Kurs, Wechselkurs
   oder Pflichtfeld geändert haben, ist das ein fachlicher Fehler (unten).
4. **Buchen:** Ausgeführt wird über einen gemeinsamen Buchungsdienst, den auch `TransactionResource` verwendet. Er
   bündelt, was heute auf Resource und Repository verteilt ist:
   - die Bean-Validierung mit der Gruppe `SecurityTransaction` bzw. `CashTransaction`, die heute am REST-Einstieg
     sitzt (`TransactionResource.java:215,277`);
   - `throwWhenTransactionLimitReached(idTenant, 1)` bzw. `(idTenant, 2)` für einen Transfer
     (`TransactionResource.java:217,282`). Ein Tageslimit für Transaktionen gibt es auf dem REST-Pfad nicht, denn
     `Transaction` ist eine `TenantBaseID`; die Menge der Vorschläge begrenzen `McpToolCall` und
     `MAX_MCP_PROPOSAL_OPEN`;
   - `TransactionJpaRepositoryImpl.saveOnlyAttributes` bzw. `updateCreateCashaccountTransfer`. Dort laufen
     `closedUntil`, Handelsperiode, Überziehung, Stückzahl, Hold-Tabellen und Wechselkurs.
     `transactionJpaRepository.save()` wird nie aufgerufen.

   Der Mandant der Buchung (`transaction.setIdTenant`) ist der `id_tenant` des Vorschlags, der in Schritt 1 gegen den
   Heimatmandanten des Benutzers geprüft wurde. Die Kontoprüfungen des Repositories verwenden
   `transaction.getIdTenant()` (`TransactionJpaRepositoryImpl.java:279,326`). Ein vom Modell geliefertes Objekt
   bestimmt den Mandanten nie. Der Buchungsdienst und alles, was er aufruft, nehmen an der äusseren Transaktion teil
   (`Propagation.REQUIRED`). Eine Methode mit `REQUIRES_NEW` auf diesem Pfad würde die Atomarität brechen und wird bei
   der Umsetzung ausgeschlossen.
5. **Abschliessen:** `status = EXECUTED`, `id_transaction` und `decided_at` setzen und committen. Sperre, Prüfungen,
   Buchung und Erfolgsstatus liegen damit in derselben Transaktion. Bricht der Prozess vor dem Commit ab, rollt die
   Datenbank alles zurück: Der Vorschlag bleibt `PENDING`, es gibt keine Teilbuchung, und der Benutzer kann erneut
   freigeben. Einen Zwischenstatus, der nach einem Abbruch hängen bleiben könnte, gibt es nicht.

**Fehler.** Jeder Fehler rollt die Freigabetransaktion zurück. Damit geht auch jeder darin gesetzte Status verloren.
Was danach gilt, hängt von der Fehlerart ab:

- **Fachliche Fehler** setzen danach in einer eigenen Transaktion
  `UPDATE mcp_proposal SET status = 'FAILED', failure_message = ?, decided_at = ? WHERE id_mcp_proposal = ? AND
  status = 'PENDING'`. Dazu gehören ein abweichender Betrag, `closedUntil`, ein Limit, eine Validierung, eine
  widerrufene oder abgelaufene Herkunft und ein schreibgeschützter Mandant. Die Bedingung auf `PENDING` verhindert,
  dass ein inzwischen von einer parallelen Freigabe abgeschlossener Vorschlag überschrieben wird. Für einen neuen
  Versuch braucht es einen neuen Vorschlag.
- **Technische Fehler** lassen den Vorschlag `PENDING`. Das sind Deadlock, Verbindungsabbruch und unerwartete
  Ausnahmen. Die Oberfläche bietet die erneute Freigabe an, und eine Doppelbuchung ist durch Schritt 1 ausgeschlossen.
- **Der gewechselte Mandant** ist kein Fehler des Vorschlags. Er bleibt `PENDING`.

## 5. Abnahme

- Ein Vorschlag bucht ohne Freigabe in der Oberfläche nie. Kein MCP-Werkzeug kann freigeben.
- Ein `READ`-Token erhält bei `propose_*` 403 `insufficient_scope`; ein `WRITE`-Token liest weiterhin alles.
- Ein Benutzer mit schreibgeschütztem Heimatmandanten kann weder ein `WRITE`-Token erstellen noch per OAuth
  `mcp.write` erhalten.
- Wählt der Benutzer auf der Consent-Seite nur Lesen, erhält die Familie `READ`, und ein Refresh ändert das nicht.
- Benutzer B kann den Vorschlag von A auch mit bekannter Id und bekanntem `payload_hash` weder sehen noch freigeben,
  ablehnen oder dessen Ergebnis abrufen, auch nicht nach `EXECUTED`.
- A kann seinen Vorschlag nicht freigeben, solange die Oberfläche in einem gewechselten Mandanten arbeitet. Nach der
  Rückkehr zum Heimatmandanten gelingt die Freigabe.
- Parallele Freigaben ergeben genau eine Buchung. Die zweite erhält das gespeicherte Ergebnis.
- Eine verlorene Antwort mit erneuter Freigabe ergibt keine zweite Buchung. Ein Neustart nach dem Commit ändert das
  Ergebnis nicht.
- Ein Prozessabbruch nach der Sperre und vor dem Commit hinterlässt keine Teilbuchung. Der Vorschlag ist `PENDING`
  und lässt sich erneut freigeben.
- Ein fachlicher Fehler rollt die Buchung zurück und setzt danach `FAILED`. Ein `FAILED` überschreibt nie ein
  `EXECUTED`.
- Abgelaufene Vorschläge werden abgelehnt.
- Ein Vorschlag aus einem persönlichen Token scheitert, wenn das Token vor der Freigabe abläuft oder widerrufen wird.
- Ein Vorschlag aus OAuth gelingt nach Ablauf und Rotation des Zugriffstokens, solange die Familie gültig ist.
  Nach dem Widerruf der Familie scheitert er.
- Ein Vorschlag mit der Id eines fremden privaten Wertpapiers wird beim Anlegen abgelehnt, ohne dessen Metadaten
  zu zeigen.
- Wird der Heimatmandant zwischen Vorschlag und Freigabe schreibgeschützt, scheitert die Freigabe.
- Ein Vorschlag innerhalb von `closedUntil` scheitert mit derselben Meldung wie in der Oberfläche.
- `throwWhenTransactionLimitReached` greift wie im REST-Pfad, ein Transfer zählt zwei; mehr als 20 offene Vorschläge
  werden abgelehnt.
- Mit `gt.mcp.use = 0` antworten `/api/mcpproposal/**` mit 404.
- Die Positionen vor und nach einem freigegebenen Kauf unterscheiden sich genau um die Buchung.
  `scripts/check-hold-tables.mjs` meldet keine Abweichung.

## 6. Entscheidungen

| Entscheidung | Wahl | Grund |
|---|---|---|
| Bestätigung | Freigabe in der GT-Oberfläche, persistenter Vorschlag | nur so garantiert GT eine menschliche Zustimmung, unabhängig vom Client |
| Freigabe | eine Transaktion mit `SELECT … FOR UPDATE` über Id, Benutzer und Heimatmandant; kein Zwischenstatus; `FAILED` danach bedingt auf `PENDING` | weder Teil- noch Doppelbuchung, kein hängender Vorschlag, kein Zugriff auf fremde Vorschläge |
| Herkunft eines Vorschlags | persönliches Token muss noch gültig sein; bei OAuth die Familie, nicht das einzelne Zugriffstoken | ein Zugriffstoken lebt eine Stunde, ein Vorschlag 24 Stunden; ein Widerruf stoppt die Freigabe in beiden Fällen |
| Lebensdauer eines `WRITE`-Tokens | höchstens 90 Tage | gleich lang wie eine OAuth-Familie; ein Schreibrecht soll nicht ein Jahr unbemerkt bestehen |
| Scope-Prüfung | Filter in der MCP-Kette vor dem Transport | nur dort ist eine HTTP-Antwort `insufficient_scope` möglich |
| Consent bei `mcp.write` | Benutzer kann auf Lesen herabsetzen | Clients fordern alle angekündigten Scopes an |
