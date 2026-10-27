# GNet / GTNet: instanzübergreifende E2E-Tests ab 600

**Code baseline:** backend 0.39.1, highest Flyway script
`V0_39_2__typed_null_globalparameters.sql`.

## 1. Ziel

Neue Playwright-Tests ab Nummer `600` sollen Benutzeraktionen auf zwei voneinander unabhängigen Instanzen
ausführen und die fachlichen Auswirkungen auf beiden Seiten prüfen. Ein Szenario kann beispielsweise auf A eine
Anfrage senden, auf B eine Freigabe erteilen und auf A den tatsächlich übernommenen Datenbestand kontrollieren.

GNet bezeichnet hier die wiederverwendbaren Netzwerkfunktionen der `grafiosch-*`-Bibliotheken. GTNet bezeichnet
deren Verwendung in Grafioschtrader einschließlich Wertpapierstammdaten und Kursdaten. Die beiden vorhandenen
Testumgebungen bleiben getrennt. Bibliothekstests erhalten keine Abhängigkeit auf Grafioschtrader.

Die Erweiterung soll einzeln gegen laufende Instanzen wiederholbar sein. Die Anmeldung, Vorbereitung und
Fehlerbereinigung eines Tests dürfen keinen vollständigen E2E-Durchlauf voraussetzen.

## 2. Ansatzpunkte im Quellcode

Folgende vorhandenen Bausteine werden für die Erweiterung verwendet:

| Baustein | Ansatzpunkt |
| --- | --- |
| Zwei Instanzen starten, Datenbanken initialisieren, Prozesse beenden | `scripts/e2e-test.mjs`, `runPeerTopology`, `runGTNetSuite`, `runGTNetAppSuite` |
| GNet-Browsertests | `frontend/e2e/gtnet/`, `frontend/playwright.gtnet.config.ts` |
| GTNet-Browsertests | `frontend/e2e/gtnet-app/`, `frontend/playwright.gtnet-app.config.ts` |
| Getrennte Anmeldung und Benutzerregistrierung | `global.setup.ts` und `peer.helpers.ts` in den beiden Testverzeichnissen |
| Erreichbare eigene Instanzadresse | `scripts/gtnet-peer-address.mjs`, `GTNetInstanceIdentity`, `GTNetJpaRepositoryImpl.saveOnlyAttributes` |
| Protokolltests ohne weiteren Spring-Kontext | `GTNetLibraryPeerTestSuite`, `GTNetApplicationPeerTestSuite` |
| Aufgaben gezielt ausführen | `GTNetTestTaskResource`, `GTNetPeerTestTaskResource` |

Die GNet-Browsertests `075` bis `099` prüfen bereits unter anderem Handshake, manuelle Datenfreigabe,
Konfigurationsdialoge, Nachrichten und den Hintergrundarbeiter. Die GTNet-Browsertests `070` und `075` stellen
eine Verbindung her und prüfen eine automatische Freigabe samt Synchronisationsauftrag. Diese Tests bleiben
erhalten; ihre Nummern ändern sich nicht.

Die Anwendungssuite enthält außerdem HTTP-Protokolltests, beispielsweise `GTNetAppLastpriceExchangeTest` und
`GTNetAppHistoryquoteExchangeTest`. Diese senden über `SyntheticPeer` Anfragen an einen laufenden Server.
Die neuen Browsertests ergänzen den Nachweis, dass eine Benutzeraktion auf A über die echte A-B-Verbindung
die erwarteten Daten auf der anderen Instanz erzeugt. Ein erfolgreiches Antwortpaket allein genügt dafür nicht.

## 3. Entscheidungen

| Frage | Festlegung | Begründung |
| --- | --- | --- |
| Zwei Datenbanken? | Ja, jeweils `*_t` und `*_t1`. | Jede Instanz braucht einen eigenen Datenbestand und eine eigene GTNet-Identität. |
| Vorher die gesamte Datenbank kopieren? | Im regulären Testlauf beide Datenbanken unabhängig initialisieren. | Benutzer und Referenzdaten können gezielt angelegt werden; Netzwerkzustände werden nicht übernommen. |
| Gleiche Benutzer auf beiden Instanzen? | Gleiche bekannte Testzugangsdaten sind möglich; Benutzer und Mandanten werden je Instanz angelegt. | Ein Login benötigt keine identischen Datenbank-IDs. |
| Zwischen Instanzen wechseln? | Zwei gleichzeitig geöffnete Browser-Kontexte, je einer für A und B. | Beide Sitzungen und ihre HTTP-Ziele bleiben eindeutig. |
| Eine Testdatei je beteiligter Instanz? | Eine Datei je fachlichem Szenario, mit expliziten Schritten für A und B. | Ein einzelner Wiederholungslauf enthält Anfrage, Gegenaktion und Ergebnisprüfung. |
| Neue Nummerierung? | Neue Szenarien ab `600`, im vorhandenen Dateinamensschema `600-*.spec.ts`. | Der gewünschte Nummernbereich passt zur bestehenden Konvention mit Bindestrich. |
| Teil des normalen Eininstanzlaufs? | Weiterhin eigene GTNet-Auswahl im Runner. | Die zusätzlichen Dienste und Profile müssen gezielt bereitgestellt werden. |
| Protokoll-Sonderfälle? | Weiterhin in den vorhandenen HTTP-Tests prüfen. | Größenlimits, ungültige Pakete und Wiederholungskennungen benötigen keinen zusätzlichen Browserablauf. |

## 4. Testtopologie

| Umgebung | Instanz | Datenbank | Backend | Frontend | Spring-Profile |
| --- | --- | --- | --- | --- | --- |
| GNet | A | `grafiosch_t` | 8081 | 4201 | `e2e` |
| GNet | B | `grafiosch_t1` | 8082 | 4202 | `e2e,e2e-peer` |
| GTNet | A | `grafioschtrader_t` | 8080 | 4200 | `e2e,e2e-gtnet` |
| GTNet | B | `grafioschtrader_t1` | 8082 | 4202 | `e2e,e2e-gtnet,e2e-peer` |

Die bestehenden Profile und Frontend-Proxys werden weiterverwendet. Die beiden Umgebungen laufen nacheinander,
da B jeweils die Ports 8082 und 4202 belegt. `--gtnet-lib`, `--gtnet-app` und `--gtnet` behalten ihre Bedeutung;
der normale Lauf und `--all` erhalten keine zusätzlichen Instanzen.

Pro Datenbank darf genau ein Anwendungskontext laufen. Die GTNet-Protokolltests bleiben reine HTTP-Clients.
Die nummerierten `ResourceTestSuite_*` werden während dieser Testphasen nicht zusätzlich gestartet.

Vor der ersten Anmeldung und vor jeder Vorbereitung mit Schreibzugriff wird das Ziel geprüft:

- GNet: `/api/integration-info`, exakter Datenbankname und `activeProfiles` als Array.
- GTNet: `/api/gtinfo`, exakter Datenbankname und `activeProfile` als kommaseparierte Liste.
- Beide Instanzen müssen `e2e` verwenden; GTNet zusätzlich `e2e-gtnet`, B zusätzlich `e2e-peer`.
- Die Frontend-Proxys müssen denselben Datenbanknamen melden wie das jeweilige direkte Backend.

Die eigene Netzwerkadresse stammt aus `GTNET_PEER_A_OWN_URL` beziehungsweise `GTNET_PEER_B_OWN_URL`.
Sie muss von beiden Servern und von der jeweiligen Instanz selbst erreichbar sein. Der bestehende Resolver
liefert eine dafür geeignete IPv4-Adresse. Die eigene Adresse von A und B muss verschieden sein.
Das `g.gnet.my.entry.id` jeder Datenbank muss auf deren eigene Adresse zeigen. Gleiche numerische IDs in zwei
Datenbanken sind zulässig; sie sind kein Beleg für eine gemeinsame Identität.

## 5. Datenbankaufbau und Bewertung einer Kopie

### 5.1 Regulärer Aufbau

`runPeerTopology` initialisiert bereits heute beide Datenbanken separat. Dieser Aufbau wird beibehalten:

1. Ports und Zielnamen prüfen; einen laufenden fremden Dienst weder beenden noch dessen Datenbank zurücksetzen.
2. Im ausdrücklich gestarteten vollständigen GTNet-Lauf die beiden zugehörigen Testdatenbanken neu erstellen.
3. Beide Backends mit ihren Testprofilen starten; Flyway initialisiert jeden Datenbestand separat.
4. Beide Frontends starten und direkte sowie weitergeleitete Info-Endpunkte prüfen.
5. Die benötigten Benutzer auf beiden Instanzen registrieren, verifizieren und deren Mandanten anlegen.
6. Eigene und gegenseitige Einträge herstellen; nur die für das Szenario erforderlichen Freigaben vorbereiten.

Die vorhandenen `global.setup.ts` registrieren Benutzer bei jedem Bootstrap neu. Für die Erweiterung wird die
Benutzervorbereitung wiederholbar: vorhandene Benutzer anmelden und ihre Voraussetzungen prüfen, fehlende Benutzer
gezielt registrieren. Ein Loginfehler darf nicht pauschal als fehlender Benutzer behandelt werden; unerwartete
HTTP- oder Anmeldefehler brechen mit Instanz und Ursache ab.

`GTNET_SKIP_BOOTSTRAP=true` darf weiterhin die Registrierung überspringen. Zielprüfung und Prüfung der benötigten
Benutzer, Rollen und Mandanten bleiben dabei aktiv. Jeder neue Test stellt seine fachlichen Voraussetzungen selbst
her; er verlangt nicht, dass zuvor die gesamte nummerierte Testsuite durchgelaufen ist.

### 5.2 Warum ein vollständiger Klon zusätzliche Arbeit erzeugt

Eine Kopie übernimmt mehr als Benutzer und Referenzdaten: insbesondere den eigenen Eintrag samt
`g.gnet.my.entry.id`, Remote-Einträge, Handshake-Tokens, Freigaben, Nachrichten, Austauschdaten und
`task_data_change`. B würde damit zunächst die gespeicherte Identität von A verwenden.

`GTNetInstanceIdentity` erzeugt seinen Challenge-Schlüssel bei jedem Prozessstart neu. Dieser Laufzeitschlüssel
wird nicht aus der Datenbank kopiert. Das beseitigt den übernommenen Datenbankzeiger jedoch nicht:
`saveOnlyAttributes` beansprucht eine eigene Identität nur, wenn noch kein gültiger eigener Eintrag existiert.
Ein unveränderter Klon darf deshalb nicht als fertiger zweiter Peer gestartet werden.

Für Kurs- und Importtests braucht es ohnehin absichtlich verschiedene Zustände: Ein Instrument existiert nur auf B,
oder A besitzt eine Kurslücke, die B füllen kann. Zwei identische Datenbestände würden solche Tests abschwächen.

### 5.3 Option für einen späteren gemeinsamen Datenstand

Ein Snapshot ist erst sinnvoll, wenn ein konkreter Test umfangreiche, zuvor erzeugte Daten benötigt, deren gezielter
Aufbau wesentlich aufwendiger ist. Er wird als eigener Vorbereitungsmodus behandelt, nicht vor jeder Testdatei.
Für einen solchen Modus gelten folgende Anforderungen:

1. Quelle und Ziel ausschließlich als fest zugeordnete Paare erlauben:
   `grafiosch_t` nach `grafiosch_t1` oder `grafioschtrader_t` nach `grafioschtrader_t1`.
2. Alle Anwendungskontexte beider Datenbanken vor dem Snapshot beenden und Ports sowie Verbindungen kontrollieren.
3. Konsistenten Snapshot einschließlich passender Flyway-Historie übernehmen. Benutzerrechte und
   Datenbankzugangsdaten des Zielservers bleiben dessen eigene Konfiguration.
4. Vor B's erstem Start Netzwerkidentität und Netzwerkbeziehungen im Ziel vollständig zurücksetzen.
   Die Tabellenmengen aus `GTNetJpaRepositoryImpl.GTNET_BASE_TABLES_DELETE_ORDER` beziehungsweise
   `GTNetExportTablesGT` dienen als Ausgangspunkt; zusätzlich sind `g.gnet.my.entry.id`, abhängige Tabellen,
   Netzwerkzähler und übernommene Aufträge zu berücksichtigen. Die Bereinigung braucht einen eigenen,
   gegen das konkrete Schema geprüften Ablauf in Fremdschlüsselreihenfolge.
5. In B wartende und laufende Aufträge entfernen oder gezielt neu vorbereiten. Keine Hintergrundausführung vor
   Abschluss der Bereinigung zulassen. Browser-Sitzungen verwerfen und auf beiden Instanzen neu anmelden.
6. B mit eigener Adresse starten; eigene Identität, gegenseitigen Handshake und Freigaben über die normalen Wege
   herstellen. Asymmetrische Testdaten danach gezielt erzeugen.

Dieser Snapshot-Modus gehört nicht zum ersten Implementierungsumfang.

## 6. Anmeldung und Teststeuerung

In beiden Testverzeichnissen wird eine instanzbewusste Playwright-Fixture ergänzt. Die Bezeichner `peerA` und
`peerB` stehen jeweils für einen Browser-Kontext, eine Seite, deren Frontend-/Backendadresse und die angemeldete
Rolle. Ein Rollenwechsel erhält einen eigenen Kontext.

Jeder Kontext wird mit seiner eigenen `baseURL` erzeugt. Nur `page.goto` auf eine andere absolute Adresse
umzustellen reicht für Hilfsfunktionen nicht: relative `page.request`-Aufrufe müssen ebenfalls das richtige Ziel
verwenden. JWT-Header werden ausschließlich aus der Sitzung des adressierten Peers gelesen.

Die Anwendung speichert JWT und weitere Logininformationen in `sessionStorage`
(`frontend/src/app/lib/login/service/log-in.service.ts`). Die erste Umsetzung meldet deshalb pro Test und Peer
über die vorhandene Login-Oberfläche an. Ein gemeinsamer gespeicherter Anmeldestatus wird nicht vorausgesetzt.
Eine spätere Optimierung muss den vollständigen benötigten Sitzungszustand je Ursprung und Rolle wiederherstellen.

Gleiche E-Mail und gleiches Testpasswort auf A und B sind zulässig. Der Test ermittelt IDs pro Instanz und verwendet
keine Benutzer-, Mandanten-, Peer- oder Instrument-ID der anderen Datenbank. Die Fixture verhindert das Vertauschen
von Sitzungen; eine serverseitige Ablehnung fremder JWTs wäre eine zusätzliche Anforderung an getrennte
Signaturschlüssel und wird aus zwei getrennten Datenbanken allein nicht abgeleitet.

Die Reihenfolge der Benutzeraktionen steht innerhalb des Tests, beispielsweise:

```text
Vorbereitung: auf A und B anmelden; beide Peer-Ziele prüfen; eigenen Szenariozustand bereinigen
A: Datenanfrage für eine ausdrücklich benannte Austauschartenmenge senden
B: eingehende Anfrage im Nachrichtenbaum prüfen und über die Oberfläche beantworten
A: Antwort und genau die vereinbarte Austauschkonfiguration prüfen
A: zugehörigen fachlichen Vorgang auslösen
B und A: Übertragung sowie tatsächliche Zielwerte kontrollieren
```

Die Schritte erhalten `test.step`-Titel mit `A:` beziehungsweise `B:`. Ein Wechsel zwischen Testdateien wird nicht
als Synchronisationsmechanismus verwendet. `workers: 1` bleibt für den gemeinsamen Peer-Zustand erhalten.

## 7. Neue Szenarien und Nummernbereiche

Neue Szenarien verwenden dreistellige Präfixe ab `600` mit Lücken von fünf. Die Ziffer bestimmt die Reihenfolge
innerhalb ihrer Suite; die Reihenfolge verschiedener Runner-Phasen wird ausdrücklich im Runner festgelegt.
Die vorhandenen Tests behalten ihre Namen. Die normalen Aufräumtests `844` und `888` bleiben in ihrer Suite.

### 7.1 GNet: `frontend/e2e/gtnet/`

| Vorgesehene Datei | Benutzerablauf und messbares Ergebnis |
| --- | --- |
| `600-peer-sessions.spec.ts` | Auf A und B anmelden. Info-Endpunkte über beide Frontends prüfen, eigene Adressen kontrollieren und eine eigene lokale Testeinstellung nur auf A ändern. B behält seinen Ausgangswert; ein Logout auf A beendet B's Sitzung nicht. |
| `605-peer-settings-broadcast.spec.ts` | A ändert eine übertragbare eigene Einstellung. Den dadurch erzeugten `GTNET_SETTINGS_BROADCAST` gezielt ausführen. B zeigt den Wert am Remote-Eintrag von A; B's eigene Einstellung bleibt unverändert. |
| `610-peer-handshake-recovery.spec.ts` | Eine Verbindung herstellen, auf beiden Seiten über den vorhandenen Handshake-Reset zurücksetzen und erneut verbinden. Zwischenzustand, unautorisierte Verbindung und erneute beidseitige Autorisierung prüfen; eigene Einträge bleiben bestehen. |
| `615-peer-role-boundaries.spec.ts` | Als nicht administrativer Benutzer auf B die explizit administrativen Status-/Resetaktionen prüfen. Direkter HTTP-Aufruf wird verweigert, Zustand bleibt gleich; der anschließende zulässige Admin-Vorgang funktioniert. |

Die lokale Einstellung für `600` darf kein GTNet-Broadcast sein, etwa eine ausschließlich lokale
Benutzerpräferenz. Dadurch ist die fehlende Änderung auf B ein belastbarer Nachweis der Sitzungszuordnung.
Die vorhandene manuelle Freigabe in `086-data-request-approval.spec.ts` wird für einen Einzeltestlauf mit derselben
Voraussetzungs-Fixture ausführbar gemacht; ein zweiter identischer Freigabetest wird nicht angelegt.

### 7.2 GTNet: `frontend/e2e/gtnet-app/`

| Vorgesehene Datei | Benutzerablauf und messbares Ergebnis |
| --- | --- |
| `650-peer-sessions.spec.ts` | Instanzzuordnung und getrennte Sitzungen des vollständigen Clients prüfen; auch bei identischen Zugangsdaten bleiben die Ziele und lokalen IDs korrekt. |
| `655-peer-manual-approval.spec.ts` | A beantragt eine benannte Austauschart ohne automatische Antwortregel. B prüft und akzeptiert manuell. Auf beiden Seiten exakt die neue Freigabe prüfen; nach Wiederherstellung des Ausgangszustands auch den Ablehnungsfall prüfen. |
| `660-peer-exchange-sync.spec.ts` | B aktiviert das Senden eines Testinstruments, A dessen Empfang. Den neu ausgelösten `GTNET_EXCHANGE_SYNC` ausführen. Die Lieferantenzuordnung auf A muss das Instrument und B enthalten. Nach Abschalten und erneutem Sync muss die Zuordnung entfallen. |
| `665-peer-security-import.spec.ts` | Ein nur auf B vorhandenes Wertpapier über die Oberfläche von A suchen beziehungsweise in einem eigenen Importset anfordern. `SECURITY_METADATA` und dessen erforderliche Freigabe vorbereiten. Importauftrag ausführen; A enthält genau ein Wertpapier mit passendem ISIN-/Währungsschlüssel und den erwarteten Stammdaten. |
| `670-peer-historyquotes.spec.ts` | A besitzt eine definierte Kurslücke, B die erwarteten Kurswerte. Empfang/Senden und Lieferant vorbereiten, den normalen Ladevorgang auf A auslösen. Die Lücke wird mit den konkreten Werten und Daten von B geschlossen. Ein zweiter Lauf erzeugt keine doppelten Kurse. |
| `675-peer-lastprice.spec.ts` | B besitzt einen festgelegten frischen Testkurs, A einen älteren. Den normalen Aktualisierungsweg von A auslösen. Nachweisen, dass A den erwarteten Kurs samt Zeitbezug von B übernimmt; anschließend die Gegenrichtung mit einem getrennten Testinstrument prüfen. |

Die Szenarien `665` bis `675` prüfen tatsächliche Instrument-/Kursdaten zusätzlich zu Nachrichten, Auftragsstatus
und Austauschprotokoll. Sie verwenden keine abgefangenen oder nachgebauten GTNet-Antworten.
Der Nachweis der Herkunft enthält die Gegenstelle und den Szenariobezug im Austauschprotokoll oder in den
Lieferantendetails. Positive Fälle müssen vor dem Auslösen einen abweichenden Zielbestand nachweisen.

Für `670` muss die Vorbereitung den tatsächlichen Auswahlregeln von `HistoryquoteThruGTNet` entsprechen:
ein geeignetes Instrument mit bestehender Teilhistorie oder ein gezielt vorbereiteter Connector-Retry-Zustand.
Ein komplett leeres Instrument kann zunächst den Connectorweg auslösen. Das Ergebnis darf nicht von einer
erreichbaren Börsenkursquelle abhängen. Für `675` werden Zeitstempel relativ zu einem einmal pro Szenario
ermittelten UTC-Zeitpunkt erzeugt, damit der Testkurs innerhalb des gültigen Frischefensters liegt.

Ein fachlicher Negativfall je Datenweg schaltet eine erforderliche Voraussetzung ab, etwa das Sendeflag oder die
Freigabe. Die definierte Ziel-Lücke beziehungsweise der alte Zielwert bleibt dann erhalten. Fremde Kursanbieter
werden für diese Testinstrumente durch geeignete Konfiguration ausgeschlossen; Netzwerkantworten zwischen A
und B bleiben echt. Ein grüner Test muss somit die Übertragung über GTNet belegen.

Spätere Erweiterungen können Nichterreichbarkeit, Wartungsfenster und einen dritten Lieferanten abdecken.
Sie erhalten eigene Voraussetzungen und freie Nummern unterhalb `844`. Ein dritter Server gehört nicht zur
ersten Ausbaustufe.

## 8. Testdaten, Aufgaben und Wiederholbarkeit

Die vorhandene Fixture
`backend/grafioschtrader-server/src/test/resources/testdata/gtnet_peer_instruments.json` benennt Referenzinstrumente
auf beiden Seiten. Für die neuen Szenarien werden daneben eigene, kleine JSON-Fixtures unter `testdata/` ergänzt.
Sie beschreiben peerweise Ausgangszustände und konkrete erwartete Werte. Sie liegen außerhalb von `generated/`.
Natürliche Schlüssel sind ISIN plus Währung, Währungspaar, Benutzername oder ein eindeutiger Szenarioname;
der bestehende `e2e`-Routingtag steht je Datensatz zuletzt.

Referenzinstrumente der vorhandenen Protokolltests werden nicht gelöscht oder dauerhaft verändert. Ein
Importszenario erhält ein eigenes Instrument, das ausschließlich auf B existiert. Kursszenarien verwenden eigene
Instrumente beziehungsweise klar abgegrenzte Zeitbereiche. Externe Connectoren, automatische Aktualisierungen
und konkurrierende Lieferanten dürfen für diese Daten keine Ergebnisse liefern.

Die Hintergrundarbeiter und Cronjobs bleiben während der deterministischen Szenarien deaktiviert. Die bestehende
GNet-Phase `099-worker-pickup` mit aktivem Arbeiter läuft erst nach den neuen deterministischen Tests und deren
Protokolltests. Das globale numerische Sortieren aller Dateien ersetzt diese Phasenfolge nicht.

Ein Test ermittelt den von seiner Aktion erzeugten Auftrag anhand von Typ, Entity und Ausgangsbestand. Er startet
genau diesen Auftrag über den vorhandenen Test-Endpunkt:

- GNet: `/api/integration-gtnet-test/tasks/{idTaskDataChange}/run`.
- GTNet: `/api/gtnet-peer-test/tasks/{idTaskDataChange}/run`.

Der jeweils neueste beliebige Auftrag einer Tabelle ist kein zulässiges Auswahlkriterium. Für Typen ohne Entity
muss der Test durch serialisierte Ausführung und die Differenz der Auftrags-IDs eine eindeutige Zuordnung herstellen.
Mehrdeutige Zuordnung führt zum Abbruch mit Diagnose.

Auf Zustandsübergänge wird mit begrenztem Polling gewartet. Lange feste Wartezeiten und das Abwarten eines
Cronlaufs werden vermieden. Ein erfolgreicher Auftrag muss zusätzlich das erwartete fachliche Ergebnis liefern.

Jeder neue Test bereinigt seine eigenen Daten zu Beginn auf beiden Instanzen. Bei Verbindungs- oder
Freigabetests wird der erforderliche Ausgangszustand beider Seiten ausdrücklich hergestellt. Offene
Request-/Response-Nachrichten werden beantwortet oder über die vorgesehenen Wege bereinigt. Ein kontrollierter
Handshake-Reset kann zur Wiederherstellung eines testverwalteten Remote-Eintrags erforderlich sein:
`deleteGTNet` schützt offene Nachrichten einer noch autorisierten Beziehung. Eigene Einträge bleiben erhalten.

Bereinigungsfehler werden geprüft und mit Instanz, Objekt und Antwort gemeldet. Ein ignorierter Fehler mit
anschließendem Anlegen weiterer Anfragen genügt nicht. Eine Bereinigung am Testende kann hinzukommen, ersetzt
aber die Wiederherstellung am Anfang nicht. Gemeinsame Vorbereitungsdaten werden durch szenariobezogene
Bereinigung nicht entfernt; ein vollständiger Reset gehört ausschließlich zum ausdrücklich gestarteten Runner.

## 9. Runner, Auswahl und Diagnose

`scripts/e2e-test.mjs` erhält explizite Phasen für die neuen Nummernbereiche. Die bestehenden Bootstrap- und
Protokollphasen werden wiederverwendet. Zwischen Phasen werden fachliche Voraussetzungen erneut hergestellt,
soweit vorherige Tests sie verändert haben. `global.setup.ts` übernimmt die Instanz-/Benutzerprüfung;
die Szenario-Fixture stellt Beziehungen, Freigaben und Testdaten bereit.

Die normale `frontend/playwright.config.ts` verwendet `testDir: './e2e'` und schließt bisher nur `lib/` aus.
Beide Peer-Verzeichnisse werden dort für das Setup- und das Hauptprojekt ausdrücklich ausgeschlossen.
So sammelt ein direkter normaler Playwright-Aufruf keine Tests ein, die eine zweite Instanz verlangen.
Die gezielte Dateiauswahl des Haupt-Runners bleibt davon unabhängig bestehen.

Die beiden Peer-Konfigurationen erhalten getrennte Artefaktverzeichnisse. Traces und Fehlerscreenshots müssen
A und B zugeordnet werden können. Für B's zusätzlich erzeugten Kontext werden die notwendigen Artefakte
ausdrücklich über die Fixture aufgenommen und angehängt. Jede Phase behält ihre Ergebnisse; ein nachfolgender
Playwright-Prozess darf die Artefakte einer vorigen Phase nicht entfernen. Tokens und Passwörter gehören nicht
in zusätzliche Diagnoseausgaben.

Gezielte Wiederholung verwendet die bestehende Peer-Konfiguration und bereits laufende Dienste. Beispielsweise
nach Umsetzung, aus `frontend/`:

```powershell
$env:GTNET_SKIP_BOOTSTRAP = 'true'
npx playwright test e2e/gtnet/610-peer-handshake-recovery.spec.ts --config=playwright.gtnet.config.ts
npx playwright test e2e/gtnet-app/670-peer-historyquotes.spec.ts --config=playwright.gtnet-app.config.ts
```

Diese Beispiele gelten für die jeweils passende Topologie und die zuvor gesetzten `GTNET_PEER_*`-Adressen.
Ein Einzeltest startet keine vollständige Suite und setzt keine Datenbank zurück. Vom Runner gestartete Dienste
werden auch im Fehlerfall beendet; vorbestehende Dienste bleiben unberührt.

Ein späterer Einbau als Phase des normalen Anwendungslaufs würde eine Aufteilung von `050–888` verlangen:
zunächst bis `599`, anschließend die explizite Peer-Vorbereitung und der GTNet-Bereich, schließlich verbleibende
Eininstanztests samt `844`/`888`. Dabei müsste A vor dem Profilwechsel auf `e2e-gtnet` kontrolliert neu gestartet
werden. Diese Integration und der Snapshot-Modus sind eigenständige spätere Entscheidungen.

## 10. Umsetzung in Etappen und Abnahme

### Etappe 1: Wiederholbare Infrastruktur und GNet

Zielprüfung über Backend und Frontend, wiederholbare Benutzeranlage, getrennte Peer-Fixtures, gezielte
Voraussetzungs-/Fehlerbereinigung, Auswahl im Runner und Artefakte umsetzen. Danach die GNet-Szenarien ab `600`
ergänzen und die bestehende manuelle Freigabe einzeln ausführbar machen.

Abnahme:

- Der normale Playwright-Testbestand enthält keine Peer-Specs.
- Beide Peer-Konfigurationen listen jeweils nur die für sie vorgesehenen Szenarien.
- Ein falscher Datenbankname oder ein vertauschter Frontend-Proxy wird vor einem Schreibzugriff erkannt.
- Die neuen GNet-Szenarien laufen einzeln zweimal gegen denselben Datenbestand erfolgreich.
- Ein gezielt nach dem Versand einer Anfrage abgebrochener Lauf wird beim erneuten Einzeltest bereinigt.
- Logout und lokale Änderung auf A beeinflussen B's Sitzung und lokalen Ausgangswert nicht.
- Die deterministischen Phasen laufen bei deaktivierten Hintergrundarbeitern; der bestehende Arbeitertest bleibt
  als eigene spätere Phase ausführbar.

### Etappe 2: GTNet-Datenwege zwischen zwei echten Instanzen

Die GTNet-Szenarien ab `650`, ihre asymmetrischen Fixtures und die konkreten Ergebnisprüfungen ergänzen.
Für normale Ladewege bestehende UI-/REST-Auslöser und die produktive Aufgabenverarbeitung verwenden.
Ein zusätzlich benötigter Testauslöser muss den echten fachlichen Einstieg aufrufen und auf das jeweilige
Testprofil sowie Administratoren begrenzt bleiben; er darf keine GTNet-Antwort oder Zielwerte nachbauen.

Abnahme:

- Jede neue Datei läuft allein ohne vorherige nummerierte Backend- oder Playwright-Suite.
- Ein Wertpapier wird von B nach A übernommen; Wiederholung erzeugt kein Duplikat.
- Historische Kurse und aktueller Kurs entsprechen konkreten, zuvor nur auf der Lieferantenseite vorhandenen
  Werten. Datenbank-IDs dürfen zwischen A und B verschieden sein.
- Fehlende Sendeberechtigung beziehungsweise Freigabe verhindert den geprüften Transfer.
- Synchronisation, Herkunft und fachliches Ergebnis sind durch beide Instanzen nachvollziehbar.
- Wiederholung nach einem Teilfehler erfordert weder Datenbankkopie noch vollständigen E2E-Neustart.
- Die bestehenden HTTP-Protokolltests und Browserabläufe bleiben im vollständigen, ausdrücklich angeforderten
  GTNet-Lauf ausführbar.

Testbedienung und Wiederherstellung werden bei Umsetzung in `frontend/e2e/README.md` und im `e2e-test`-Skill
dokumentiert. Nach vollständiger Umsetzung wird diese Spezifikation gemäß Repository-Verfahren entfernt;
gegebenenfalls neue benutzersichtbare Regeln werden zuvor in das Benutzerhandbuch übernommen.
