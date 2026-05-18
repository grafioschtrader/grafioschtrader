package grafioschtrader.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.RiskFreeRateMapping;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.RiskFreeRateMappingJpaRepository;

/**
 * Resolves the risk-free interest rate for a given ISO currency on a given date.
 *
 * <p>
 * The rate-of-record for each currency is stored as historical quotes on a synthetic Security (asset-class category
 * {@code NON_INVESTABLE_INDICES}) whose id is registered in {@link RiskFreeRateMapping}. Quotes are populated by
 * {@code FredFeedConnector} (or any other connector wired up to that security) and stored as decimal fractions, e.g.
 * 4.32% → 0.0432.
 *
 * <p>
 * Lookups carry forward the last known value when an exact date is missing — this matters because FRED provides
 * monthly-only series for many non-US currencies, and consumers (e.g. a Sharpe ratio calculation) need a defined rate
 * on every trading day.
 */
@Service
public class RiskFreeRateService {

  @Autowired
  private RiskFreeRateMappingJpaRepository riskFreeRateMappingJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  /**
   * Returns the risk-free rate (annualized, as a decimal fraction) for the given currency on or before the given date.
   * If the date itself has no quote (e.g. monthly-only series, weekends, holidays), returns the most recent earlier
   * quote.
   *
   * @param currencyIso ISO 4217 currency code (3 letters, uppercase).
   * @param date        the target date.
   * @return the rate as a decimal (0.0432 = 4.32%), or {@code null} if the currency is unmapped or has no quote
   *         on/before the date.
   */
  public Double getRateOnDate(String currencyIso, LocalDate date) {
    Optional<RiskFreeRateMapping> mapping = riskFreeRateMappingJpaRepository.findByCurrency(currencyIso);
    if (mapping.isEmpty()) {
      return null;
    }
    return historyquoteJpaRepository
        .findFirstByIdSecuritycurrencyAndDateLessThanEqualOrderByDateDesc(mapping.get().getIdSecuritycurrency(), date)
        .map(Historyquote::getClose).orElse(null);
  }

  /**
   * Returns a date-keyed map of risk-free rates for the given currency over an inclusive date range. Only dates with
   * stored quotes are present in the map — callers that need a value on every date should fall back to
   * {@link #getRateOnDate(String, LocalDate)} for carry-forward semantics, or apply forward-fill themselves.
   *
   * @param currencyIso ISO 4217 currency code.
   * @param from        start of range (inclusive).
   * @param to          end of range (inclusive).
   * @return ordered map (date → rate). Empty if the currency is unmapped or no quotes exist in the range.
   */
  public TreeMap<LocalDate, Double> getRateSeries(String currencyIso, LocalDate from, LocalDate to) {
    TreeMap<LocalDate, Double> series = new TreeMap<>();
    Optional<RiskFreeRateMapping> mapping = riskFreeRateMappingJpaRepository.findByCurrency(currencyIso);
    if (mapping.isEmpty()) {
      return series;
    }
    List<Historyquote> quotes = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
        mapping.get().getIdSecuritycurrency(), from, to);
    for (Historyquote h : quotes) {
      series.put(h.getDate(), h.getClose());
    }
    return series;
  }
}
