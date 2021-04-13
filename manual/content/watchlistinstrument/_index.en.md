---
title: "Watchlist and instruments"
date: 2021-03-13T22:54:47+01:00
draft: false
weight : 15
chapter: true
---
## Watchlist and instruments
A watchlist is a personal compilation of instruments with price development and further details. In GT the watchlist is a central element. The following functions are accessible via the watchlist:
+ **Create and delete instruments**: Instruments can only be created via a watchlist.
+  **Adding existing instruments**: Existing instruments can be added to the watchlist. There is a limit to the number of instruments in a watchlist.
+ **Transaction on security**: The recording of the first **purchase transaction** of a specific instrument must necessarily be done via a watchlist. Subsequent transactions such as a new **purchase**, **dividend** or **sale** can also be made via the **security account**.
+ **Intraday quotes update of the instruments**: Only through the watchlist can the user actively cause an update of the **Intraday quotes**. There is a system setting for the time interval before a new update of the rates takes place. The update is done automatically with the selection of the corresponding **watchlist** in the navigation area. The watchlist itself is **private data**, but its content is based on **shared data**.
+ **Monitoring external data sources**: Monitoring of historical and intraday price data and the resulting necessary actions.

The relationships are shown in the following simplified class diagram:
{{< mermaid >}}
classDiagram
     Watchlist "*" o-- "*" Instrument
    Instrument <|-- Currency_par
    Instrument <|-- Security
    Security "1" ..> "*" Historical_price_data : has
    Security "*" ..> "1" Stock_exchange : has
    Security "*" ..> "1" Asset_class : has
    Security "1" *-- "*" Splits
    Security "1" *-- "*" Dividend
    Historical_price_data <|-- Calculated
    Historical_price_data <|-- Period_historical_price
    Historical_price_data <|-- EOD_Price
    Currency_par "1" ..> "*" EOD_Price : has
    Calculated o-- EOD_Price
    class Instrument{
        timestamp: time
        high: double
        low: double
        last: double
    }
{{< /mermaid >}}
Video Watchlist Part 1