---
title: "Klient und Portfolio Reports"
date: 2018-01-13T22:54:47+01:00
draft: true
weight : 12
chapter: true
---
## Klient und Portfolio Reports
Auswertungen ist das, was der Benutzer von GT letztendlich interessiert. Die Auswertungen haben sehr unterschiedliche Schwerpunkte, daher gibt es unterschiedliche Reports.

### Performanceberechnung
...

### Warum keine Prozentangabe Vermögensänderung
In GT gibt es keine Angabe einer prozentualen Vermögensänderung. Auf diese wurde verzichtet, da die Aussagekräftig bei einem nicht andauernden 100 prozentiges Investment gering ist. 
{{% notice info %}}
Möglicherweise wird es in zukünftigen GT-Versionen eine Möglichkeit eines Benchmarking geben. Eine Idee einer Simulation: Wie wäre die Performance wenn die selben Beträge in den Benchmark investiert würden im Vergleich zu den Investitionen die real getätigt wurden.
{{% /notice %}}

### Abweichung zur Realität
Für die Berechnung der Performance werden hypothetische Transaktionen durchgeführt. Beispielsweise müssen Wertpapiere verkauft und die Fremdwährungen gegen die Hauptwährung gekauft werden. Dazu werden hypothetische Verkäufe und Währungstransaktionen durchgeführt. Bei den Fremdwährungskursen wird der 

### Problematik Fremdwährung
Sobald eine Applikation Konten und Handel mit Fremdwährungen unterstützt, wird es Diskussionen über unterschiedliche Ansätze der Performance Berechnung geben. GT selbst is wissentlich nicht durchgehend konsistent was diese Berechnung betrifft. Beispielsweise werden im Report der **Portfolios** die Erträge und Aufwände für **Kontozins** bzw. **Konto- und Depotkosten** anders berechnet als im Report des Periodenertrags. Im ersteren werden die Erträge zum Transaktionsdatum in die Hauptwährung umgerechnet, beim Periodenertrag wird das Datum auf welche sich die Berechnung bezieht genommen.  
