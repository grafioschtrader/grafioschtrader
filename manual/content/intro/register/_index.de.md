---
title: "Registrieren"
date: 2021-03-14T22:54:47+01:00
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
