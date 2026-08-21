import {AppHelper, Comparison} from '../../lib/helper/app.helper';
import {TwoKeyMap} from '../../lib/helper/two.key.map';
import {CrossRateResponse, CurrenciesAndClosePrice} from '../../securitycurrency/service/currencypair.service';
import {HistoryquoteDateClose} from '../../entities/projection/historyquote.date.close';

/**
 * The part of a loaded chart series this normalizer reads and writes. The loaded data structure of the
 * time series chart satisfies this shape structurally, so the normalizer needs no import of the component.
 */
export interface NormalizableQuoteSeries {
  /** Currency the raw quotes of this series are denominated in, null for a currency pair. */
  currencySecurity: string;
  /** Quotes as delivered by the backend, never modified by the normalizer. */
  historyquotes: HistoryquoteDateClose[];
  /** Quotes converted into the requested currency, the same array as historyquotes when no conversion applies. */
  historyquotesNorm: HistoryquoteDateClose[];
}

/**
 * Converts the quotes of one or more chart series into a currency the user picked, so that instruments
 * denominated in different currencies can be compared on a single chart.
 *
 * The exchange rates arrive from the backend as a set of currency pairs relative to the main currency of
 * the tenant. Getting from the currency of a security to the requested currency therefore takes one of
 * three routes, and every pair may be delivered in either direction:
 *
 * <ol>
 * <li>The requested currency is the main currency, so the main/security pair converts directly.</li>
 * <li>A pair between the requested currency and the security currency exists and involves the main
 *     currency, so that single pair converts directly.</li>
 * <li>Otherwise the main currency sits in the middle (requested EUR, main CHF, security USD) and the
 *     quote is multiplied through both pairs in turn.</li>
 * </ol>
 *
 * This class is deliberately free of Angular and Plotly, so the conversion arithmetic can be unit tested.
 */
export class ChartCurrencyNormalizer {

  private readonly compareHistoricalFN = (h: HistoryquoteDateClose, o: string) =>
    h.date === o ? Comparison.EQ : h.date > o ? Comparison.GT : Comparison.LT;

  /** Exchange rate histories keyed by from-currency and to-currency. */
  private crossRateMap = new TwoKeyMap<CurrenciesAndClosePrice>();
  private mainCurrency: string;

  /**
   * Returns the currency pairs already held, each encoded as fromCurrency, a vertical bar and toCurrency.
   * The chart sends these along with the next cross rate request so the backend only delivers pairs that
   * are still missing.
   *
   * @returns array of pair keys, empty when no rates have been taken over yet
   */
  public getExistingCurrencypairKeys(): string[] {
    return this.crossRateMap.getValues().map(crossRate =>
      crossRate.currencypair.fromCurrency + '|' + crossRate.currencypair.toCurrency);
  }

  /**
   * Takes over the main currency and the exchange rate histories of a cross rate response. A rate history
   * already held for the same pair is replaced.
   *
   * @param crossRateResponse response of the cross rate endpoint
   */
  public setCrossRates(crossRateResponse: CrossRateResponse): void {
    this.mainCurrency = crossRateResponse.mainCurrency;
    crossRateResponse.currenciesAndClosePrice.forEach(crr =>
      this.crossRateMap.set(crr.currencypair.fromCurrency, crr.currencypair.toCurrency, crr));
  }

  /**
   * @returns the main currency of the tenant, undefined before the first cross rate response
   */
  public getMainCurrency(): string {
    return this.mainCurrency;
  }

  /**
   * Normalizes every series that has a currency of its own. Currency pairs are skipped, their quotes are
   * already an exchange rate and must not be converted.
   *
   * @param seriesList all series shown on the chart
   * @param requestedCurrency currency chosen by the user, empty string when no conversion is wanted
   */
  public normalizeAll(seriesList: NormalizableQuoteSeries[], requestedCurrency: string): void {
    seriesList.filter(series => series.currencySecurity != null)
      .forEach(series => this.normalize(series, requestedCurrency));
  }

  /**
   * Writes the converted quotes of a single series into its historyquotesNorm. When no conversion is
   * required, historyquotesNorm becomes the untouched historyquotes array rather than a copy.
   *
   * @param series the series to convert
   * @param requestedCurrency currency chosen by the user, empty string when no conversion is wanted
   */
  public normalize(series: NormalizableQuoteSeries, requestedCurrency: string): void {
    if (this.isNormalizeNotNeeded(series, requestedCurrency)) {
      series.historyquotesNorm = series.historyquotes;
    } else {
      const mainToSecurityCurrency = this.getCurrencypairOrReverse(this.mainCurrency, series.currencySecurity);
      const requestToSecurityCurrency = this.getCurrencypairOrReverse(requestedCurrency, series.currencySecurity);
      if (requestedCurrency === this.mainCurrency) {
        this.calculateHistoryquotes(series, [mainToSecurityCurrency],
          [mainToSecurityCurrency.currencypair.fromCurrency !== this.mainCurrency]);
      } else if (requestToSecurityCurrency != null && (requestToSecurityCurrency.currencypair.fromCurrency === this.mainCurrency
        || requestToSecurityCurrency.currencypair.toCurrency === this.mainCurrency)) {
        this.calculateHistoryquotes(series, [requestToSecurityCurrency],
          [requestToSecurityCurrency.currencypair.fromCurrency === this.mainCurrency]);
      } else {
        // Main currency is in the middle like EUR -> CHF -> USD (requested currency: EUR, main currency: CHF,
        // security currency: USD
        const mainToRequestCurrency = this.getCurrencypairOrReverse(this.mainCurrency, requestedCurrency);
        this.calculateHistoryquotes(series, [mainToSecurityCurrency, mainToRequestCurrency],
          [mainToSecurityCurrency.currencypair.fromCurrency !== this.mainCurrency,
            mainToRequestCurrency.currencypair.fromCurrency === this.mainCurrency]);
      }
    }
  }

  /**
   * True when the quotes of a series can be charted exactly as delivered, because the user asked for no
   * particular currency, the series is a currency pair, or it already is denominated in the requested
   * currency.
   *
   * @param series the series in question
   * @param requestedCurrency currency chosen by the user, empty string when no conversion is wanted
   * @returns true when no conversion has to be applied
   */
  public isNormalizeNotNeeded(series: NormalizableQuoteSeries, requestedCurrency: string): boolean {
    return requestedCurrency.length === 0 || series.currencySecurity == null
      || series.currencySecurity === requestedCurrency;
  }

  /**
   * Looks up the rate history between two currencies, accepting the pair in either direction. The caller
   * decides from the fromCurrency of the returned pair whether the rate has to be multiplied or divided.
   *
   * @param reqesteOrMainCurrency the requested or the main currency
   * @param currencySecurity currency of the series
   * @returns the matching rate history, or null when neither direction is held
   */
  private getCurrencypairOrReverse(reqesteOrMainCurrency: string, currencySecurity: string): CurrenciesAndClosePrice {
    let mainToSecurityCurrency: CurrenciesAndClosePrice = this.crossRateMap.get(reqesteOrMainCurrency, currencySecurity);
    if (mainToSecurityCurrency == null) {
      mainToSecurityCurrency = this.crossRateMap.get(currencySecurity, reqesteOrMainCurrency);
    }
    return mainToSecurityCurrency;
  }

  /**
   * Multiplies every quote by the exchange rate of the same date and collects the results in
   * historyquotesNorm. Quote dates and rate dates are walked with two cursors that only ever advance,
   * because both lists are sorted ascending but need not cover the same days, so a date missing on either
   * side is skipped. With two rate histories the second pass multiplies into the entries the first pass
   * produced, which is how a conversion through the main currency is applied.
   *
   * @param series the series to convert
   * @param currenciesAndClosePrice one rate history for a direct conversion, two for one through the main currency
   * @param multiple per rate history, true multiplies by the rate and false divides by it
   */
  private calculateHistoryquotes(series: NormalizableQuoteSeries, currenciesAndClosePrice: CurrenciesAndClosePrice[],
    multiple: boolean[]): void {
    const map2Loop: { [date: string]: HistoryquoteDateClose } = {};
    series.historyquotesNorm = [];

    for (let i = 0; i < currenciesAndClosePrice.length; i++) {
      let cIndex = Math.abs(AppHelper.binarySearch(currenciesAndClosePrice[i].closeAndDateList,
        series.historyquotes[0].date, this.compareHistoricalFN));
      for (let hIndex = 0; hIndex < series.historyquotes.length; hIndex++) {
        do {
          if (series.historyquotes[hIndex].date > currenciesAndClosePrice[i].closeAndDateList[cIndex].date) {
            cIndex++;
          } else if (series.historyquotes[hIndex].date < currenciesAndClosePrice[i].closeAndDateList[cIndex].date) {
            hIndex++;
          }
        } while (hIndex < series.historyquotes.length && cIndex < currenciesAndClosePrice[i].closeAndDateList.length
        && series.historyquotes[hIndex].date !== currenciesAndClosePrice[i].closeAndDateList[cIndex].date);
        if (hIndex < series.historyquotes.length && cIndex < currenciesAndClosePrice[i].closeAndDateList.length) {
          if (series.historyquotes[hIndex].close != null) {
            if (i === 0) {
              const historyquoteDateClose = {
                date: series.historyquotes[hIndex].date,
                close: series.historyquotes[hIndex].close * (multiple[i] ? currenciesAndClosePrice[i].closeAndDateList[cIndex].close :
                  1 / currenciesAndClosePrice[i].closeAndDateList[cIndex].close)
              };
              series.historyquotesNorm.push(historyquoteDateClose);
              currenciesAndClosePrice.length > 1 && (map2Loop[historyquoteDateClose.date] = historyquoteDateClose);
            } else {
              const historyquoteDateClose = map2Loop[series.historyquotes[hIndex].date];
              if (historyquoteDateClose) {
                historyquoteDateClose.close *= (multiple[i] ? currenciesAndClosePrice[i].closeAndDateList[cIndex].close :
                  1 / currenciesAndClosePrice[i].closeAndDateList[cIndex].close);
              }
            }
          }
        }
      }
    }
  }
}
