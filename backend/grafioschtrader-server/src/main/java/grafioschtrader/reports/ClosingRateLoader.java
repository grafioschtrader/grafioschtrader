package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;

/**
 * Finds the currency pair that converts one currency into another and reads its closing rates. A pair is stored in one
 * direction only, so both the direct and the inverse pair are considered. There is no triangulation over a third
 * currency.
 */
@Component
public class ClosingRateLoader {

  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;

  public ClosingRateLoader(HistoryquoteJpaRepository historyquoteJpaRepository,
      CurrencypairJpaRepository currencypairJpaRepository) {
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
  }

  /**
   * A stored currency pair together with its direction relative to the needed conversion.
   *
   * @param pair    the stored pair
   * @param inverse true when the pair converts the target currency into the source currency, so its closes must be
   *                inverted
   */
  public record ConversionPair(Currencypair pair, boolean inverse) {

    /** Readable name of the stored pair, for example "USD/CHF". */
    public String name() {
      return pair.getFromCurrency() + "/" + pair.getToCurrency();
    }
  }

  /**
   * The stored pairs that can convert {@code fromCurrency} into {@code toCurrency}, the direct one first.
   *
   * @param fromCurrency currency of the amount to convert
   * @param toCurrency   currency to convert into
   * @return zero, one or two candidates
   */
  public List<ConversionPair> conversionPairs(String fromCurrency, String toCurrency) {
    Currencypair direct = currencypairJpaRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
    Currencypair inverse = currencypairJpaRepository.findByFromCurrencyAndToCurrency(toCurrency, fromCurrency);
    return java.util.stream.Stream.of(Optional.ofNullable(direct).map(p -> new ConversionPair(p, false)),
        Optional.ofNullable(inverse).map(p -> new ConversionPair(p, true))).flatMap(Optional::stream).toList();
  }

  /**
   * Closing rates from a currency into the main currency, from a pair of either direction. The rates of the direct
   * pair are used when it has any in the period, otherwise the inverted closes of the inverse pair.
   *
   * @param currency     currency to convert from
   * @param mainCurrency currency to convert into
   * @param from         first day, inclusive
   * @param to           last day, inclusive
   * @return rate by date, empty when the currencies are equal or no rate exists
   */
  public Map<LocalDate, Double> closingRates(String currency, String mainCurrency, LocalDate from, LocalDate to) {
    Map<LocalDate, Double> rates = new HashMap<>();
    if (currency.equals(mainCurrency)) {
      return rates;
    }
    for (Object[] r : historyquoteJpaRepository.getClosesOfCurrencyPairsInPeriod(List.of(currency), mainCurrency, from,
        to)) {
      rates.put(toLocalDate(r[1]), number(r[2]));
    }
    if (rates.isEmpty()) {
      for (Object[] r : historyquoteJpaRepository.getClosesOfCurrencyPairsInPeriod(List.of(mainCurrency), currency,
          from, to)) {
        double close = number(r[2]);
        if (close != 0) {
          rates.put(toLocalDate(r[1]), 1 / close);
        }
      }
    }
    return rates;
  }

  private static LocalDate toLocalDate(Object value) {
    return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
  }

  private static double number(Object value) {
    return value == null ? 0 : ((Number) value).doubleValue();
  }
}
