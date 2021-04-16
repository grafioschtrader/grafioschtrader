---
title: "Introduction"
date: 2021-03-29T15:00:00+01:00
draft: false
weight : 1
chapter: true
---
## Introduction
A VIDEO will follow ...

### Why exists Grafioschtrader (GT)
For several years I used a portfolio management application that met my initial needs. Over time, I realized that transaction and custody costs have a significant impact on the overall portfolio return. So I searched different online stock brokers that offered the most favorable trading conditions for my investment. So far, only a combination of several trading platforms satisfies my needs. As a Swiss investor, sooner or later investments are made with foreign currencies.

Over time, you lose track of where how much is invested in what. In addition, there is no overview of the overall performance of all portfolios. I thought this does not have to be and as a software developer I plunged into the adventure Grafioschtrader.

### What can GT
+ **The software is free and open source**: GT is released as open source and can be found on [GitHub](//github.com/hugograf/grafioschtrader).
+ **Multi-tenancy**: GT can be run for a group of investors or in single mode.
+ **Web application**: GT is a web application and provides the clearest results using a desktop web browser.
+ **Multiple portfolios with currency accounts**: Replicates multiple portfolios with one or more securities accounts and one or more bank cash accounts.
+ **Multiple currencies**: Trading securities in different currencies
+ **Trading from the turn of the millennium**: Basic support for historical price data from the year 2000 onwards, noting that obtaining price data from non-traded securities may be a problem.
+ **Different financial instruments**: Stocks, Bonds, ETF, securities without price data, short ETF, CFD, Forex.
+ **Import of transactions**: An import of single or multiple PDFs with securities transactions Via CSV file, account transactions can also be loaded.
+ **Evaluations by asset classes**: Evaluations by common asset classes such as stocks, bonds, real estate, commodities, etc.

### For whom is suitable GT
GT is the attempt to replicate the portfolios according to the reality, therefore there are accounts as in your online stock broker. Each booking is linked to a cash account, so the bookings are replicated according to your trading platform.
+ The investor who does not want to entrust his financial data to a non-transparent platform. You can host the GT yourself, all you need is a [Raspberry Pi 4](//www.raspberrypi.org/products/raspberry-pi-4-model-b/) with 4GB memory.
+ A shareholder who wants to have a history of all his trades. You can view all closed positions at any time.
+ Private investor who wants to know how his currency gains are developing. The currency profit on the foreign currency accounts is shown continuously.
+ Investors club that wants to host their own GT instance and a member is happy to take care of technical aspects.
+ Investors with multiple portfolios on different trading platforms. GT provides information per portfolio and can display it in aggregated form.

### For whom GT is not made
+ The day trader lacks real-time quotes.
+ Investors who want to manage their trading detached from cash accounts.
+ Investor who has only one portfolio with an online broker and the evaluations there are sufficient.

### For whom GT does not work
+ For **billionaires**, in whatever currency, here the number format of GT is unfortunately not sufficient.
+ For investors who want to manage transaction with GT before 2000. GT consistently implements double-digit years.
+ For investors who want to assign an instrument to a **sector/topic** and **countries/regions** at the same time.
+ For investors from Switzerland, Germany or USA who need a **Valor-No.**, **Securities Identification Number (WKN)** or **CUSIP-Number**. GT uses the International Securities Identification Number ISIN and the not always unique ticker/symbols.

{{% notice info %}}
The information in this help refers to **GT** version **0.12.0**.
{{% /notice %}}