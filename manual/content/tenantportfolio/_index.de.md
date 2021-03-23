---
title: "Klient und Portfolios"
date: 2021-03-14T22:54:47+01:00
draft: false
weight : 10
chapter: true
---
## Klient und Portfolios
GT definiert einen **Klient** aus dem Zusammenzug aller **Portfolios** und **Watchlists**. Zusätzlich enthält er die Informationen bezüglich der Auswertung über alle Portfolios. Ein Klient kann ein oder mehrere Portfolios enthalten, die Anzahl möglicher Portfolios ist beschränkt. Im folgenden Diagramm ist die Beziehungen dargestellt. Daraus entnehmen wir, d.h beispielweise eine Konto einem Portfolio zugeordnet ist.


{{< mermaid >}}
erDiagram
    Klient ||--|{ Portfolio : hat
    Portfolio ||--|{ Konto : hat
    Portfolio ||--|{ Depot : hat
    Klient ||--|{ Watchlist : hat
    Watchlist ||--|{ Instruments : hat
    Konto ||--|{ Transaktion : hat
{{< /mermaid >}}
