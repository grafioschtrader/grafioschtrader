package grafioschtrader.tax.swiss.ictax;

import java.util.List;

import grafioschtrader.entities.IctaxSecurityTaxData;

/**
 * Everything one pass over a Kursliste XML file yields. The securities and the exchange rates sit in unrelated parts of
 * the document, so they are collected together rather than by streaming the file twice — the full Kursliste is several
 * hundred megabytes.
 *
 * @param securities    per-security tax data, already filtered when a selective import was requested
 * @param exchangeRates the {@code exchangeRateYearEnd} rows, always complete regardless of the selection
 */
public record KurslisteParseResult(List<IctaxSecurityTaxData> securities, List<ParsedExchangeRate> exchangeRates) {
}
