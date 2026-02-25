Es gibt einen Post-Request https://lseg-widgets.financial.com/auth/api/v1/tokens. Ist es dieser der danach weiter verwendet wird für zusätzlich Anfragen.


Ja, genau – der POST-Request an /auth/api/v1/tokens ist der zentrale Authentifizierungs-Einstiegspunkt, dessen Rückgabe für alle weiteren Anfragen genutzt wird. Hier ist der vollständige Flow, den ich aus dem SDK (sdk.v6.4.0.js) rekonstruiert habe: 1. Token-Request (POST /auth/api/v1/tokens) Der Request wird mit einem Authorization: Basic …-Header abgeschickt (Base64-kodierte Credentials). Der SDK macht das über method: "post". 2. Token-Speicherung Die Response wird in setToken(n.data) verarbeitet und unter dem Schlüssel "token" im internen Storage abgelegt:

setToken(e) { return this.storage.set("token", e) }
3. Verwendung bei Folge-Requests Bevor jede weitere API-Anfrage abgesendet wird (z.B. an /rest/api/quote/info), wird über getTokenString() der gespeicherte Token abgerufen und als jwt-Header an den Request angehängt:

getTokenString().then(r => (e.headers.jwt = r, e))
4. Token-Refresh Bei einem 401-Fehler oder kurz vor Ablauf der Session wird refreshToken() automatisch aufgerufen, der erneut einen POST an /auth/api/v1/tokens schickt. Zusammenfassung des Ablaufs:

POST /auth/api/v1/tokens   →  Token wird gespeichert
  ↓
GET /rest/api/quote/info   →  Header: jwt: <token>
GET /rest/api/...          →  Header: jwt: <token>
GET /streaming/streams     →  Header: jwt: <token>
  ↓
(bei 401 oder Ablauf)
POST /auth/api/v1/tokens   →  neuer Token
Der Token wird also nicht als Bearer-Token im Authorization-Header verwendet, sondern als custom jwt-Header in den nachfolgenden REST-API-Requests. Das ist das Token-Muster der LSEG (Refinitiv) Widgets API.

Ich habe in Bruno eine Request ausgeführt. Damit dieser funktioniert musste ich sid un user-agent im Header gesetzt haben. Wo kommt die sid her.
28 Schritte

Die sid kommt aus der Response des POST-Requests an /auth/api/v1/sessions. Das ist der eigentliche Login-Endpoint, der vor dem /tokens-Request aufgerufen wird. Hier der vollständige 2-stufige Ablauf:
Schritt 1 – Login → Session (inkl. sid)

POST /auth/api/v1/sessions
Body: { user, password, product, fetchToken: true }
Die Response enthält das Session-Objekt mit u.a.:

json
{
  "sid": "...",       ← die Session-ID
  "token": "...",     ← das JWT
  "expiresAt": ...
}
```
Das SDK speichert daraus `sid` im Session-Objekt und `token` im Token-Storage.
---
**Schritt 2 – Token-Refresh (verwendet die `sid`)**
```
POST /auth/api/v1/tokens
Headers: { sid: <sid aus Schritt 1> }
Im Code steht das wörtlich so:

js
requestToken(e) {
  const t = { sid: e.sid };  // sid als Header
  this.auth.request({ url: urlToken, method: "post", headers: t })
}
Fazit: Die sid ist eine Session-Kennung, die du beim initialen Login-Call (/auth/api/v1/sessions) mit Username + Password erhältst. Sie wird dann bei jedem folgenden /tokens-Request im Header mitgeschickt, um einen neuen JWT-Token zu beziehen. Für deinen Bruno-Request musst du also zuerst einen POST gegen /auth/api/v1/sessions mit den Login-Credentials machen und die sid aus der Response verwenden.

Ich bin nicht angemeldet an Gettext. Kann es nicht sein, dass die sid einfach mit dem Aufruf von https://www.gettex.de/ in das Cookie gesetzt wird.




46 Schritte
46 Schritte

Ja, du hast recht – die sid kommt ohne manuellen Login, direkt beim Aufruf von gettex.de. Hier ist was tatsächlich passiert: Der echte Ablauf: gettex.de hat einen serverseitig generierten SAML-Assertion direkt im HTML der Seite eingebettet. Beim Laden der Seite sendet das SDK diese automatisch an /auth/api/v1/sessions (SSO-Login via SAML) – ohne dass du etwas eingibst. Der Server von LSEG antwortet dann mit Session-Daten, die im Browser in IndexedDB (localforage) gespeichert werden:

json
// localforage → keyvaluepairs → "auth.session"
{
  "isSSO": true,
  "sid": "46da1586-2e34-4a72-9228-aa3c0fb6845d",
  "exp": 1771862369,
  ...
}
// "auth.token"
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
Die sid kommt also aus der SAML-SSO-Response – gettex.de ist als vertrauenswürdiger Service-Provider bei LSEG registriert und bekommt automatisch eine Session für jeden Besucher der Seite. Für deinen Bruno-Request bedeutet das: Du kannst die aktuelle sid direkt aus dem Browser auslesen – sie liegt in der IndexedDB unter localforage → keyvaluepairs → auth.session. Einfach die Seite aufrufen, dann über die Browser-DevTools (Application → IndexedDB → localforage) die sid kopieren und in Bruno einsetzen.

a, es ist möglich! Hier sind die vollständigen API-Details:
Die Seite nutzt die LSEG / financial.com Widget-API (lseg-widgets.financial.com). Es gibt zwei relevante Endpunkte, die per XHR aufgerufen werden.
Schritt 1: ISIN → RIC auflösen
Zuerst muss die ISIN in einen RIC (Reuters Instrument Code) umgewandelt werden – dieser wird für die Kursdaten-Abfragen benötigt:
GET https://lseg-widgets.financial.com/rest/api/find/securities
  ?fids=x._DSPLY_NAME,x._ISIN,x._TYPE,x._LOCAL_ID,q.RIC
  &search={ISIN}
  &searchFor=ISIN
  &exchanges=GTX
  &pageSize=2
  &pageNo=0
Beispiel-Response (für DE0006305006):
json{
  "data": [{
    "q.RIC": "DEZG.GTX",
    "x._LOCAL_ID": "630500",
    "x._DSPLY_NAME": "DEUTZ",
    "x._ISIN": "DE0006305006",
    "x._TYPE": "STO"
  }]
}
```
→ Das RIC-Suffix für gettex ist immer `.GTX`
---
### Schritt 2a: Historische Tagesdaten
```
GET https://lseg-widgets.financial.com/rest/api/timeseries/historical
  ?ric={RIC}
  &fids=_DATE_END,CLOSE_PRC,ACVOL_1
  &samples={INTERVAL}
  &appendRecentData=all
  &toDate={YYYY-MM-DDT23:59:59}
  &fromDate={YYYY-MM-DDT00:00:00}
Parameter samples (Intervall):
WertBedeutungDtäglichWwöchentlichMmonatlichBeispiel-Response:
json{
  "status": "OK",
  "data": [
    { "_DATE_END": "2025-02-24", "CLOSE_PRC": "5.17", "ACVOL_1": "36909" },
    { "_DATE_END": "2025-02-25", "CLOSE_PRC": "5.22", "ACVOL_1": "46040" }
  ]
}
```
---
### Schritt 2b: Intraday-Daten
```
GET https://lseg-widgets.financial.com/rest/api/timeseries/intraday
  ?ric={RIC}
  &fids=TIME,CLOSE,VOLUME
  &interval={MINUTEN}
  &toDate={YYYY-MM-DDT23:59:59}
  &fromDate={YYYY-MM-DDT00:00:00}
Parameter interval (in Minuten):
WertChart-Period auf gettex11 Tag (T2T)51 Woche152 Wochen601 MonatBeispiel-Response:
json{
  "status": "OK",
  "data": [
    { "TIME": "1771828207", "CLOSE": "11.67", "VOLUME": "2082" },
    { "TIME": "1771828311", "CLOSE": "11.68", "VOLUME": "200"  }
  ]
}

TIME ist ein Unix-Timestamp (Sekunden)


Wichtiger Hinweis zur Authentifizierung
Die API erfordert ein JWT-Bearer-Token, das via SAML-Login gegenüber lseg-widgets.financial.com ausgestellt wird. Gettex.de führt diesen SAML-Flow beim Seitenaufruf automatisch durch. Für direkte XHR-Calls von außerhalb der Seite müsste man entweder:

Die Session-Cookies aus dem Browser mitschicken (funktioniert solange die Session gültig ist), oder
Den SAML-Handshake nachbauen (nicht ohne Weiteres möglich, da der Private Key auf dem gettex-Server liegt)
Am einfachsten: Die dory.Rest.get()-Funktion direkt im Browser-Kontext der Seite nutzen – die kümmert sich automatisch um Auth-Token. Das geht z. B. per Bookmarklet oder Browser-Extension:
Ja! Der erweiterte Chart liefert vollständige OHLC-Daten.
Sowohl für historische als auch für Intraday-Daten werden Open, High, Low, Close und Volume zurückgegeben – man muss lediglich die entsprechenden fids mitgeben.
Historische Tagesdaten mit OHLC
GET https://lseg-widgets.financial.com/rest/api/timeseries/historical
  ?ric={RIC}
  &fids=_DATE_END,OPEN_PRC,HIGH_1,LOW_1,CLOSE_PRC,ACVOL_1
  &samples={D|W|M}
  &appendRecentData=all
  &toDate=2026-02-23T23:59:59
  &fromDate=2025-02-23T00:00:00
Beispiel-Response:
json{
  "_DATE_END": "2025-02-24",
  "OPEN_PRC":  "5.27",
  "HIGH_1":    "5.31",
  "LOW_1":     "5.16",
  "CLOSE_PRC": "5.17",
  "ACVOL_1":   "36909"
}
```
---
### Intraday-Daten mit OHLC
```
GET https://lseg-widgets.financial.com/rest/api/timeseries/intraday
  ?ric={RIC}
  &fids=TIME,OPEN,HIGH,LOW,CLOSE,VOLUME
  &interval={1|5|15|60}
  &toDate=2026-02-23T23:59:59
  &fromDate=2026-02-23T00:00:00
Beispiel-Response:
json{
  "TIME":   "1771828835",
  "OPEN":   "11.71",
  "HIGH":   "11.71",
  "LOW":    "11.60",
  "CLOSE":  "11.65",
  "VOLUME": "4574"
}

TIME ist ein Unix-Timestamp in Sekunden


Verfügbare Intervalle im Überblick
Endpunktinterval / samplesBeschreibungintraday11 Minuteintraday55 Minutenintraday1515 Minutenintraday6060 MinutenhistoricalDtäglichhistoricalWwöchentlichhistoricalMmonatlichDer Unterschied zum einfachen Chart (KURSDATEN-Tab) ist also lediglich, dass dieser nur CLOSE + VOLUME abfragt, während der erweiterte Chart zusätzlich OPEN, HIGH und LOW in denselben Endpunkten anfordert.