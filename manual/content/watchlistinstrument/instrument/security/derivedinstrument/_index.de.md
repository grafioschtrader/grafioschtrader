---
title: "Abgeleitetes Instrument"
date: 2018-01-13T22:54:47+01:00
draft: false
weight : 8
chapter: true
---
## Abgeleitetes Instrument
Durch ein abgeleitetes Instrument wird ermöglicht, die Kursdaten von einem oder mehreren Wertpapieren durch eine Formel von minimal einem anderen Instrument berechnen zu lassen. Somit hat ein abgeleitetes Instrument immer eine Abhängigkeit zu mindesten einem anderen Instrument.

Ein abgeleitetes Instrument kann einer Börse und Anlageklasse zugeordnet werden und ist somit in GT handelbar.

### Formel
Eine Formel besteht aus Zahlen, Variablen, mathematischen und boolesche Operatoren und möglichen Funktionen. GT verwendet das Framework [EvalEx - Java Expression Evaluator](//github.com/uklimaschewski/EvalEx), daher siehe die Webseite für die möglichen Funktionen.

#### Variable die Zuordnung zum einem Instrument 
Maximal können **5** Variablen mit den Buchstaben **o, p, q, r, s** in einer Formel benutzt werden. Die Variablen müssen einem Wertpapier zugeordnet werden.