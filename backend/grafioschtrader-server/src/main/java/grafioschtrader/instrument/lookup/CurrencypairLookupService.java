package grafioschtrader.instrument.lookup;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedIdentifier;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.securitysearch.CurrencypairSearchResult;
import grafioschtrader.connector.securitysearch.ICurrencypairSearchProvider;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.dto.CurrencypairLookupData;
import grafioschtrader.dto.CurrencypairLookupResponse;
import grafioschtrader.dto.CurrencypairSearchHit;
import grafioschtrader.dto.SecurityLookupResponse.ProviderStatus;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Proposes the connectors of a currency pair that is being created. For the history and the intraday feed it tries
 * every activated connector that serves currency pairs of this kind - fiat or cryptocurrency - and reports which of
 * them deliver data, with the URL extension each one needs and, for the history, the oldest closing price.
 *
 * <p>
 * Most connectors build their request from the two currencies alone ({@link FeedIdentifier#CURRENCY}) and are probed
 * without an extension. A connector that needs one ({@link FeedIdentifier#CURRENCY_URL}) derives it through
 * {@link IFeedConnector#deriveCurrencyUrlExtension}: CoinMarketCap from fixed ids, onvista and Consorsbank from the
 * FactSet notation the onvista search delivers, Boursorama from the symbol its search delivers. A connector that
 * derives nothing - finanzen.ch, finanzen.net and comdirect need a page name that no search delivers - is not offered.
 * </p>
 *
 * <p>
 * Nothing is written: the probes run on a transient currency pair, and the user copies the chosen connectors into the
 * form, which is saved through the normal dialog and its validation. Every search and every probe runs under a
 * deadline; a failure becomes a status and never discards the results of the others.
 * </p>
 */
@Service
public class CurrencypairLookupService {

  private static final Logger log = LoggerFactory.getLogger(CurrencypairLookupService.class);

  private static final Duration SEARCH_DEADLINE = Duration.ofSeconds(20);
  /** The history probe loads the whole history since {@link GlobalConstants#OLDEST_TRADING_DAY}, in chunks for some. */
  private static final Duration PROBE_DEADLINE = Duration.ofSeconds(45);
  /**
   * Two prices of the same pair are on the same scale while one is less than this factor of the other. A unit error is
   * a power of ten, while the probes of a pair are at most a few days apart.
   */
  static final double SCALE_MISMATCH_RATIO = 5;
  /** The fewest connectors that must agree on a price before another one is marked as on a different scale. */
  static final int SCALE_MIN_AGREEING = 2;

  private final List<ICurrencypairSearchProvider> searchProviders;
  private final List<IFeedConnector> feedConnectors;
  private final ConnectorProbe connectorProbe;

  public CurrencypairLookupService(List<ICurrencypairSearchProvider> searchProviders,
      List<IFeedConnector> feedConnectors, ConnectorProbe connectorProbe) {
    this.searchProviders = List.copyOf(searchProviders);
    this.feedConnectors = List.copyOf(feedConnectors);
    this.connectorProbe = connectorProbe;
  }

  /**
   * Looks up the connectors of a currency pair.
   *
   * @param fromCurrency base currency, an ISO 4217 code or a cryptocurrency GT supports
   * @param toCurrency   quote currency, likewise
   * @return the probed connectors of both feeds and the status of every search provider that was asked
   * @throws IllegalArgumentException when a currency is unknown or both are the same
   */
  public CurrencypairLookupResponse lookup(String fromCurrency, String toCurrency) {
    String from = normalize(fromCurrency);
    String to = normalize(toCurrency);
    if (from.equals(to)) {
      throw new IllegalArgumentException("The two currencies of a pair must differ");
    }
    boolean cryptocurrency = isCryptocurrency(from) || isCryptocurrency(to);
    List<CurrencypairSearchResult> results = search(from, to, cryptocurrency);
    List<CurrencypairSearchHit> hits = results.stream().flatMap(r -> r.hits().stream()).toList();
    Map<FeedSupport, List<ConnectorAlternativeDto>> alternatives = probeConnectors(
        new CurrencypairLookupData(from, to, hits), cryptocurrency);
    List<ProviderStatus> providerStatus = results.stream()
        .map(r -> new ProviderStatus(r.providerId(), r.status(), r.detail())).toList();
    return new CurrencypairLookupResponse(alternatives.get(FeedSupport.FS_HISTORY),
        alternatives.get(FeedSupport.FS_INTRA), providerStatus);
  }

  private static String normalize(String currency) {
    String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    if (!isCryptocurrency(code)
        && Currency.getAvailableCurrencies().stream().noneMatch(c -> c.getCurrencyCode().equals(code))) {
      throw new IllegalArgumentException("Unknown currency " + currency);
    }
    return code;
  }

  private static boolean isCryptocurrency(String code) {
    return GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(code);
  }

  /** Asks the search providers that quote pairs of this kind, in parallel and under a common deadline. */
  private List<CurrencypairSearchResult> search(String from, String to, boolean cryptocurrency) {
    Map<ICurrencypairSearchProvider, Future<CurrencypairSearchResult>> futures = new LinkedHashMap<>();
    long deadlineNanos = System.nanoTime() + SEARCH_DEADLINE.toNanos();
    // Not closed by try-with-resources: close() would wait for a provider that ignores the interrupt.
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      searchProviders.stream().filter(p -> p.coversCurrencypair(cryptocurrency))
          .forEach(p -> futures.put(p, executor.submit(() -> p.searchCurrencypair(from, to))));
      List<CurrencypairSearchResult> results = new ArrayList<>();
      futures.forEach((provider, future) -> results.add(awaitSearch(provider, future, deadlineNanos)));
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private static CurrencypairSearchResult awaitSearch(ICurrencypairSearchProvider provider,
      Future<CurrencypairSearchResult> future, long deadlineNanos) {
    try {
      return future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      return CurrencypairSearchResult.failure(provider.getProviderId(), SecuritySearchStatus.TIMEOUT,
          "Deadline of the lookup exceeded");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      return CurrencypairSearchResult.failure(provider.getProviderId(), SecuritySearchStatus.PROVIDER_ERROR,
          "Interrupted");
    } catch (ExecutionException e) {
      return CurrencypairSearchResult.failure(provider.getProviderId(), SecuritySearchStatus.PROVIDER_ERROR,
          String.valueOf(e.getCause()));
    }
  }

  /**
   * Probes the qualifying connectors of both feeds in parallel, each on its own transient currency pair.
   *
   * @param data           currencies and search hits for deriving URL extensions
   * @param cryptocurrency true when one of the currencies is a cryptocurrency
   * @return the sorted alternatives per feed
   */
  private Map<FeedSupport, List<ConnectorAlternativeDto>> probeConnectors(CurrencypairLookupData data,
      boolean cryptocurrency) {
    Map<FeedSupport, List<PendingProbe>> pending = new EnumMap<>(FeedSupport.class);
    long deadlineNanos = System.nanoTime() + PROBE_DEADLINE.toNanos();
    LocalDate today = LocalDate.now();
    // Not closed by try-with-resources: close() would wait for a connector that ignores the interrupt.
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      for (FeedSupport feedSupport : List.of(FeedSupport.FS_HISTORY, FeedSupport.FS_INTRA)) {
        List<PendingProbe> feedProbes = new ArrayList<>();
        for (IFeedConnector connector : qualifyingConnectors(feedSupport, cryptocurrency)) {
          Optional<String> urlExtension = needsUrlExtension(connector, feedSupport)
              ? deriveUrlExtension(connector, data, feedSupport)
              : Optional.of("");
          urlExtension.ifPresent(
              e -> feedProbes.add(startProbe(executor, connector, feedSupport, data, e.isEmpty() ? null : e, today)));
        }
        pending.put(feedSupport, feedProbes);
      }
      Map<FeedSupport, List<ConnectorAlternativeDto>> alternatives = new EnumMap<>(FeedSupport.class);
      pending.forEach((feedSupport, feedProbes) -> alternatives.put(feedSupport,
          feedProbes.stream().map(p -> p.await(deadlineNanos)).toList()));
      Map<FeedSupport, List<ConnectorAlternativeDto>> checked = flagScaleMismatches(alternatives);
      checked.replaceAll((_, feedAlternatives) -> sort(feedAlternatives));
      return checked;
    } finally {
      executor.shutdownNow();
    }
  }

  private PendingProbe startProbe(ExecutorService executor, IFeedConnector connector, FeedSupport feedSupport,
      CurrencypairLookupData data, String urlExtension, LocalDate today) {
    return new PendingProbe(connector, urlExtension, executor.submit(() -> connectorProbe.probeCurrencypair(connector,
        feedSupport, ConnectorProbe.draftCurrencypair(data.fromCurrency(), data.toCurrency()), urlExtension, today)));
  }

  /** A derivation that fails is logged; the connector is then not offered. */
  private static Optional<String> deriveUrlExtension(IFeedConnector connector, CurrencypairLookupData data,
      FeedSupport feedSupport) {
    try {
      return connector.deriveCurrencyUrlExtension(data, feedSupport);
    } catch (RuntimeException e) {
      log.warn("URL extension of {} for {} of {}/{} could not be derived: {}", connector.getID(), feedSupport,
          data.fromCurrency(), data.toCurrency(), e.toString());
      return Optional.empty();
    }
  }

  /**
   * A connector that offers the feed for currency pairs, is activated and supports pairs of this kind - fiat pairs or
   * cryptocurrency pairs - by the same rule as the connector selection of the currency pair form.
   */
  private List<IFeedConnector> qualifyingConnectors(FeedSupport feedSupport, boolean cryptocurrency) {
    SpecialInvestmentInstruments sii = cryptocurrency ? SpecialInvestmentInstruments.CFD
        : SpecialInvestmentInstruments.FOREX;
    return feedConnectors.stream()
        .filter(fc -> fc.isActivated() && servesCurrencypairs(fc, feedSupport)
            && fc.supports(null, null, AssetclassType.CURRENCY_PAIR, sii))
        .sorted(Comparator.comparing(IFeedConnector::getReadableName, String::compareToIgnoreCase)).toList();
  }

  private static boolean servesCurrencypairs(IFeedConnector connector, FeedSupport feedSupport) {
    FeedIdentifier[] identifiers = connector.getSecuritycurrencyFeedSupport().get(feedSupport);
    return identifiers != null && Arrays.stream(identifiers)
        .anyMatch(fi -> fi == FeedIdentifier.CURRENCY || fi == FeedIdentifier.CURRENCY_URL);
  }

  /**
   * Tells whether the connector needs a URL extension for the feed. As when saving, a connector registered with both
   * identifiers builds its request from the currencies.
   */
  private static boolean needsUrlExtension(IFeedConnector connector, FeedSupport feedSupport) {
    List<FeedIdentifier> identifiers = Arrays.asList(connector.getSecuritycurrencyFeedSupport().get(feedSupport));
    return identifiers.contains(FeedIdentifier.CURRENCY_URL) && !identifiers.contains(FeedIdentifier.CURRENCY);
  }

  /**
   * Marks a connector whose price lies on another scale than that of the other connectors. Some providers quote a pair
   * for a multiple of the base currency - Boursorama quotes JPY/CAD for 100 yen (0.88 instead of 0.0088) - and nothing
   * in their answer says so. GT stores a currency pair rate without a factor, so such a connector would corrupt every
   * conversion; it is therefore no longer offered as usable.
   *
   * <p>
   * The sample prices of all successful probes of both feeds are compared. The reference is the price that most others
   * agree with, i.e. lie within {@link #SCALE_MISMATCH_RATIO} of it; only when that group is a strict majority of at
   * least {@link #SCALE_MIN_AGREEING} connectors is a price outside it marked. With fewer connectors, or two groups of
   * equal size, it cannot be told which scale is right, and nothing is marked.
   * </p>
   *
   * @param alternatives the probed connectors per feed
   * @return the connectors per feed, those on another scale with status {@link ConnectorProbeStatus#SCALE_MISMATCH}
   */
  static Map<FeedSupport, List<ConnectorAlternativeDto>> flagScaleMismatches(
      Map<FeedSupport, List<ConnectorAlternativeDto>> alternatives) {
    double[] prices = alternatives.values().stream().flatMap(List::stream)
        .filter(CurrencypairLookupService::hasComparablePrice).mapToDouble(ConnectorAlternativeDto::sampleValue)
        .toArray();
    double reference = 0;
    int agreeing = 0;
    for (double price : prices) {
      int count = (int) Arrays.stream(prices).filter(other -> sameScale(other, price)).count();
      if (count > agreeing) {
        agreeing = count;
        reference = price;
      }
    }
    Map<FeedSupport, List<ConnectorAlternativeDto>> result = new EnumMap<>(FeedSupport.class);
    if (agreeing < SCALE_MIN_AGREEING || agreeing * 2 <= prices.length) {
      result.putAll(alternatives);
      return result;
    }
    double scaleReference = reference;
    alternatives.forEach((feedSupport, feedAlternatives) -> result.put(feedSupport,
        feedAlternatives.stream()
            .map(a -> hasComparablePrice(a) && !sameScale(a.sampleValue(), scaleReference)
                ? asScaleMismatch(a, scaleReference)
                : a)
            .toList()));
    return result;
  }

  private static boolean hasComparablePrice(ConnectorAlternativeDto alternative) {
    return alternative.probeStatus() == ConnectorProbeStatus.OK && alternative.sampleValue() != null
        && alternative.sampleValue() > 0;
  }

  private static boolean sameScale(double price, double reference) {
    double ratio = price / reference;
    return ratio < SCALE_MISMATCH_RATIO && ratio > 1 / SCALE_MISMATCH_RATIO;
  }

  private static ConnectorAlternativeDto asScaleMismatch(ConnectorAlternativeDto a, double reference) {
    String detail = String.format(Locale.ROOT, "Price %s is %.4g times the price %s of the other connectors",
        a.sampleValue(), a.sampleValue() / reference, reference);
    return new ConnectorAlternativeDto(a.idConnector(), a.readableName(), a.urlExtension(),
        ConnectorProbeStatus.SCALE_MISMATCH, a.sampleDate(), a.sampleValue(), a.sampleCount(), a.oldestDate(),
        a.distributionFrequency(), detail);
  }

  /**
   * Puts the connectors that delivered data first and among them the one reaching furthest back; a connector without
   * oldest date, such as every intraday probe, keeps the alphabetical order.
   *
   * @param alternatives the probed connectors in alphabetical order
   * @return the sorted connectors
   */
  static List<ConnectorAlternativeDto> sort(List<ConnectorAlternativeDto> alternatives) {
    return alternatives.stream()
        .sorted(Comparator.comparing((ConnectorAlternativeDto a) -> a.probeStatus() != ConnectorProbeStatus.OK)
            .thenComparing(ConnectorAlternativeDto::oldestDate, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }
}
