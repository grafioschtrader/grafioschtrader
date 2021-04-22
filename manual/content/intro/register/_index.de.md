---
title: "Registrieren"
date: 2021-03-23T11:00:00+01:00
draft: false
weight : 30
chapter: true
---
## Registrieren
Für die Registrierung muss der Benutzer zwei Formulare ausfüllen. Das erste für seine Daten und das zweite für die Daten des Klienten. Dazwischen muss er eine E-Mail bestätigen und sich zum ersten Mal bei GT anmelden. Der Registrierungsprozess endet mit dem Anmelden-Formular.

{{< mermaid >}}
sequenceDiagram
    autonumber
    participant Benutzer
    participant A as Anmelden-Form
    participant R as Registrien-Form
    participant K as Klient-Form
    participant Backend
    Benutzer->>A: Wählt Registrieren
    A-->>R: Weiterleitung
    Benutzer->>R: Benutzer registrieren
    R-->>Backend: Eingabe prüfen
    Backend-->>Benutzer: Email mit Bestätigungs URI  
    Benutzer->>Backend: Bestätigt URI
    Backend-->>Benutzer: Weiterleitung Anmelden-Form
    Benutzer->>A: Benutzer meldet sich an
    A-->Backend: Authentifizierung prüfen
    Backend-->>Benutzer: Weiterleitung Klient-Form
    Benutzer->>K: Klient eintragen
    K-->>Backend: Eingabe prüfen
    Backend-->>A: Weiterleitung Anmelden-Form
{{< /mermaid >}}
Im Video ist es vielleicht einfacher...
{{< youtube C9MKBfUXLPA >}}

### Eigenschaften
- **Spitzname**: Dieser Spitzname ist pro GT-Instanz einzigartig, andernfalls könnten Sie nicht von den anderen Benutzer unterschieden werden. Zurzeit wird diese zwischen den Benutzer noch nicht genutzt.
- **E-Mail**: Muss eine für die GT-Instanz einzigartig sein, sonst könnte Sie GT nicht von anderen Benutzer unterscheiden.