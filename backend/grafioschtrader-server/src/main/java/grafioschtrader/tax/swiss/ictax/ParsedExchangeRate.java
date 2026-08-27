package grafioschtrader.tax.swiss.ictax;

/**
 * One {@code <exchangeRateYearEnd>} element of the ICTax Kursliste, as it stands in the XML. It is not an entity yet
 * because the parser does not know which {@code tax_year} row the upload belongs to; the import service resolves that
 * and rejects a rate whose {@link #year()} does not match.
 *
 * @param currency       ISO code of the foreign currency, quoted against CHF
 * @param year           the year the rate belongs to, from the XML rather than from the upload
 * @param denomination   number of currency units both rates refer to, 1 unless the Kursliste quotes per 100
 * @param yearEndRate    XML attribute {@code value} — the tax value as of 31 December
 * @param annualMeanRate XML attribute {@code valueMiddle} — the annual mean rate, may be null
 */
public record ParsedExchangeRate(String currency, Short year, Integer denomination, Double yearEndRate,
    Double annualMeanRate) {
}
