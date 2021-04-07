---
title: "Cliend and Portfolios"
date: 2018-01-13T22:54:47+01:00
draft: false
weight : 10
chapter: true
---
## Client and Portfolios
GT defines a **Client** from the aggregation of all **portfolios** and **watchlists**. Additionally it contains the information regarding the evaluation over all portfolios. A client can have one or more portfolios, the number of possible portfolios is limited. The following diagram shows the relationships of these **private data**. For example we can see that an account is assigned to a portfolio.

{{< mermaid >}}
erDiagram
    Client ||--|{ Portfolio : has
    Portfolio ||--|{ Account : has
    Portfolio ||--|{ Security-Account : has
    Client ||--|{ Watchlist : has
    Watchlist ||--|{ Instruments : has
    Account ||--|{ Transaktion : has
{{< /mermaid >}}