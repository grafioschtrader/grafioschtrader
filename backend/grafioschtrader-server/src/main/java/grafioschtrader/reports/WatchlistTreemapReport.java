package grafioschtrader.reports;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.types.Language;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.reports.WatchlistTreemapBuilder.Amount;
import grafioschtrader.reportviews.securitycurrency.WatchlistTreemap;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityFundamentalJpaRepository;
import grafioschtrader.service.PriceFreshnessService;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Loads what the market map of a watchlist is calculated from and hands it to {@link WatchlistTreemapBuilder}: share
 * counts, market capitalisations, exchange rates into the main currency of the tenant and the closing times of the
 * exchanges.
 */
@Service
public class WatchlistTreemapReport {

  @Autowired
  private SecurityFundamentalJpaRepository securityFundamentalJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private PriceFreshnessService priceFreshnessService;

  /**
   * Calculates the market map.
   *
   * @param securities        the securities of the watchlist with current prices
   * @param currencypairCount the number of currency pairs of the watchlist, which never get a tile
   * @param mainCurrency      the main currency of the tenant
   * @param language          the language of the user
   * @param today             the calendar day of the client
   * @return the market map, never null
   */
  public WatchlistTreemap calculate(List<Security> securities, int currencypairCount, String mainCurrency,
      Language language, LocalDate today) {
    List<Security> stocks = securities.stream().filter(security -> isActiveDirectStock(security, today)).toList();
    int notApplicableCount = securities.size() - stocks.size() + currencypairCount;
    if (stocks.isEmpty()) {
      return new WatchlistTreemap(mainCurrency, notApplicableCount, List.of(), List.of());
    }
    List<Integer> ids = stocks.stream().map(Security::getIdSecuritycurrency).toList();
    Map<Integer, Double> shares = loadShares(ids, today);
    Map<Integer, Amount> marketCaps = loadMarketCaps(ids, today);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    return WatchlistTreemapBuilder.build(new WatchlistTreemapBuilder.Input(stocks, shares, marketCaps,
        loadRates(stocks, marketCaps, mainCurrency, today), loadCloses(stocks, today, now), mainCurrency, language,
        notApplicableCount, now));
  }

  private static boolean isActiveDirectStock(Security security, LocalDate today) {
    return security.getAssetClass().getCategoryType() == AssetclassType.EQUITIES
        && security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
        && !security.getActiveToDate().isBefore(today);
  }

  private Map<Integer, Double> loadShares(List<Integer> ids, LocalDate today) {
    Map<Integer, Double> shares = new HashMap<>();
    securityFundamentalJpaRepository.findSelectedByIds(ids, today).stream()
        .filter(row -> row.getMetric() == FundamentalMetric.SHARES.getValue() && row.getValue() != null)
        .forEach(row -> shares.put(row.getIdSecuritycurrency(), row.getValue()));
    return shares;
  }

  private Map<Integer, Amount> loadMarketCaps(List<Integer> ids, LocalDate today) {
    Map<Integer, Amount> marketCaps = new HashMap<>();
    securityFundamentalJpaRepository.findMarketCapChecks(ids, today)
        .forEach(row -> marketCaps.put(row.getIdSecuritycurrency(), new Amount(row.getValue(), row.getCurrency())));
    return marketCaps;
  }

  private Map<String, Double> loadRates(List<Security> stocks, Map<Integer, Amount> marketCaps, String mainCurrency,
      LocalDate today) {
    Set<String> currencies = Stream
        .concat(stocks.stream().map(Security::getCurrency), marketCaps.values().stream().map(Amount::currency))
        .filter(currency -> !currency.equals(mainCurrency)).collect(Collectors.toSet());
    Map<String, Double> rates = new HashMap<>();
    if (!currencies.isEmpty()) {
      currencypairJpaRepository.findNewestRatesToCurrency(currencies, mainCurrency, today).stream()
          .filter(row -> row.getRate() != null).forEach(row -> rates.put(row.getCurrency(), row.getRate()));
    }
    return rates;
  }

  /**
   * Determines the close of the newest opened session per security. The sessions reach one day beyond the day of the
   * client, because the local date of an exchange can be ahead of it.
   */
  private Map<Integer, LocalDateTime> loadCloses(List<Security> stocks, LocalDate today, LocalDateTime now) {
    Map<Integer, Stockexchange> exchanges = stocks.stream().map(Security::getStockexchange)
        .collect(Collectors.toMap(Stockexchange::getIdStockexchange, Function.identity(), (first, _) -> first));
    Map<Integer, List<LocalDate>> sessions = priceFreshnessService.loadSessions(exchanges.keySet(), today.plusDays(1));
    Map<Integer, LocalDateTime> closeByExchange = new HashMap<>();
    exchanges.forEach((id, exchange) -> {
      LocalDateTime close = priceFreshnessService.closeOfNewestOpenedSession(sessions.get(id), exchange, now);
      if (close != null) {
        closeByExchange.put(id, close);
      }
    });
    Map<Integer, LocalDateTime> closes = new HashMap<>();
    stocks.forEach(security -> {
      LocalDateTime close = closeByExchange.get(security.getStockexchange().getIdStockexchange());
      if (close != null) {
        closes.put(security.getIdSecuritycurrency(), close);
      }
    });
    return closes;
  }
}
