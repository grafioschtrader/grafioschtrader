from pathlib import Path
import re

manual = Path(r'C:/SoftwareProjekte/Hugos/gt-user-manual/content/watchlistinstrument/externaldata')
out = Path(r'C:/SoftwareProjekte/Hugos/grafioschtrader/tmp/eulerpool-manual')
out.mkdir(exist_ok=True)

notes = {
    'en': '''- **Eulerpool**

  Eulerpool is a programmed connector for shares. It supplies split-adjusted historical prices, intraday prices, dividends adjusted for splits and split history. The GT connector does not support ETFs, funds, indices or currency pairs, even if Eulerpool offers separate services for them.

  An administrator must store a personal Eulerpool API key as described under [Connection API key](../../admindata/connectorapikey/). Select Eulerpool as the data source and enter the share's ISIN as the **URL extension**, for example `US0378331005` for Apple. Ticker symbols, WKN, CUSIP and SEDOL identifiers are also accepted, but an ISIN and a ticker for the same company can return different price series. Prefer the ISIN and use the same identifier for historical prices, intraday prices, dividends and splits.

  The free plan provides intraday prices with a delay of about 15 minutes. GT prefers a listing in the instrument's currency and, where available, the provider's primary listing in that currency. If no currency matches, it uses the primary listing or the first available listing. This selection does not convert currencies or guarantee the instrument's configured trading venue, so check the price and currency before relying on them. Historical prices for a share on its home market normally use that market's currency. Observed London listings could not be interpreted reliably as either pence or pounds; compare them with another source before use.

  Check the imported history for data errors. The live tests found Saturday quotes for Cisco on 27 February 2016 and Coca-Cola on 14 November 2020, and zero closing prices for Nestlé and Geberit on 4 and 12 January 2021. The connector currently preserves these values. Verify and correct affected daily prices against another source. Some dividend amounts, particularly in older Swiss series, also need checking.

  At the time of writing, the [free plan](https://eulerpool.com/financial-data-api/pricing) allows 100,000 requests per month for non-commercial use. Eulerpool requires a visible [Data by Eulerpool](https://eulerpool.com) attribution wherever its data or charts are displayed publicly. Its [licensing terms](https://eulerpool.com/financial-data-api/licensing) distinguish displaying data from redistributing raw data. Do not enable sharing of Eulerpool data through GTNet unless your licence permits that redistribution; a personal API key alone does not grant those rights.
''',
    'de': '''- **Eulerpool**

  Eulerpool ist ein programmierter Konnektor für Aktien. Er liefert splitbereinigte historische Kurse, Innertag-Kurse, splitbereinigte Dividenden und die Split-Historie. Der GT-Konnektor unterstützt keine ETFs, Fonds, Indizes oder Währungspaare, auch wenn Eulerpool dafür separate Dienste anbietet.

  Ein Administrator muss einen persönlichen Eulerpool-API-Key hinterlegen, wie unter [Verbindungs-API-Key](../../admindata/connectorapikey/) beschrieben. Wählen Sie Eulerpool als Datenquelle und tragen Sie die ISIN der Aktie als **URL-Erweiterung** ein, beispielsweise `US0378331005` für Apple. Auch Tickersymbole, WKN, CUSIP und SEDOL werden akzeptiert. ISIN und Ticker derselben Gesellschaft können jedoch unterschiedliche Kursreihen liefern. Bevorzugen Sie die ISIN und verwenden Sie denselben Identifikator für historische Kurse, Innertag-Kurse, Dividenden und Splits.

  Im kostenlosen Plan sind Innertag-Kurse um etwa 15 Minuten verzögert. GT bevorzugt eine Notierung in der Währung des Instruments und, falls vorhanden, die vom Anbieter als primär bezeichnete Notierung in dieser Währung. Gibt es keine passende Währung, verwendet GT die primäre oder die erste verfügbare Notierung. Dabei werden keine Währungen umgerechnet, und der beim Instrument hinterlegte Handelsplatz ist nicht garantiert. Prüfen Sie deshalb Kurs und Währung vor der Verwendung. Historische Kurse einer Aktie an ihrem Heimmarkt werden normalerweise in dessen Währung geliefert. Beobachtete Londoner Notierungen liessen sich weder als Pence noch als Pfund zuverlässig interpretieren; vergleichen Sie diese vor der Verwendung mit einer anderen Quelle.

  Prüfen Sie die importierte Historie auf Datenfehler. Die Live-Tests fanden Samstagskurse bei Cisco am 27. Februar 2016 und Coca-Cola am 14. November 2020 sowie Schlusskurse von null bei Nestlé und Geberit am 4. und 12. Januar 2021. Der Konnektor übernimmt diese Werte derzeit unverändert. Kontrollieren und korrigieren Sie betroffene Tageskurse anhand einer anderen Quelle. Auch einzelne Dividendenbeträge, besonders in älteren Schweizer Kursreihen, sollten geprüft werden.

  Zum Zeitpunkt der Dokumentation erlaubt der [kostenlose Plan](https://eulerpool.com/financial-data-api/pricing) 100'000 Abfragen pro Monat für die nicht kommerzielle Nutzung. Wo Daten oder Diagramme öffentlich angezeigt werden, verlangt Eulerpool einen sichtbaren Hinweis [Data by Eulerpool](https://eulerpool.com). Die [Lizenzbedingungen](https://eulerpool.com/financial-data-api/licensing) unterscheiden zwischen der Anzeige und der Weitergabe von Rohdaten. Aktivieren Sie die Weitergabe von Eulerpool-Daten über GTNet nur, wenn Ihre Lizenz diese Weitergabe erlaubt; ein persönlicher API-Key allein gewährt dieses Recht nicht.
'''
}

for lang in ('de', 'en'):
    source = manual / f'_index.{lang}.md'
    text = source.read_text(encoding='utf-8')
    assert '**Eulerpool**' not in text
    text = re.sub(r'^date: .*$', 'date: 2026-09-19T15:00:00+02:00', text, count=1, flags=re.M)
    row = ('| [Eulerpool](https://eulerpool.com/financial-data-api) | {{< svg "lock.svg" svg-icon-size >}} | ISIN, Ticker, WKN, CUSIP, SEDOL | Einfach | {{< svg "eq.svg" svg-icon-size >}} | {{< svg "eq.svg" svg-icon-size >}} | International | Siehe Hinweise |\n'
           if lang == 'de' else
           '| [Eulerpool](https://eulerpool.com/financial-data-api) | {{< svg "lock.svg" svg-icon-size >}} | ISIN, ticker, WKN, CUSIP, SEDOL | Simple | {{< svg "eq.svg" svg-icon-size >}} | {{< svg "eq.svg" svg-icon-size >}} | International | See notes |\n')
    assert text.count('| [Euronext]') == 1
    text = text.replace('| [Euronext]', row + '| [Euronext]', 1)
    assert text.count('- **Euronext**') == 1
    text = text.replace('- **Euronext**', notes[lang] + '\n- **Euronext**', 1)
    with (out / source.name).open('w', encoding='utf-8', newline='\n') as file:
        file.write(text)
    print(f'Prepared {source.name}')
