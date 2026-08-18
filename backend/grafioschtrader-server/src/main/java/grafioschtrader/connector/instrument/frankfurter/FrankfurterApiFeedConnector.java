package grafioschtrader.connector.instrument.frankfurter;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import tools.jackson.databind.ObjectMapper;

/**
 * Supplies historical currency rates from a Frankfurter instance (<a href="https://frankfurter.dev">frankfurter.dev</a>),
 * a free and key-less exchange rate API whose daily reference rates are blended from a large number of central banks.
 *
 * <h3>Why this class is abstract</h3>
 * <p>
 * Frankfurter is open source and can be self-hosted, so the very same API is reachable under different host names. This
 * class therefore implements the complete protocol while the concrete subclass contributes nothing but its identity and
 * the root URL of one particular instance. Adding a further instance is a subclass with a constructor, see
 * {@link FrankfurterFeedConnector} and {@link GtFrankfurterFeedConnector}.
 * </p>
 *
 * <h3>What the API delivers</h3>
 * <p>
 * A time series is answered in a single request as a flat JSON array, one element per day:
 * {@code [{"date":"2026-08-01","base":"USD","quote":"CHF","rate":0.80904}, ...]}. Base and quote currency are free
 * parameters, so both directions of a currency pair are served. There is no range limit that would force chunked
 * requests, and no intraday endpoint - only one daily reference rate - which is why this connector registers for
 * alone.
 * </p>
 * <h3>Why Saturdays and Sundays are discarded</h3>
 * <p>
 * The delivered series covers every calendar day, unlike the working-day-only series of the ECB or Yahoo connectors,
 * but the weekend rows are not observations: foreign exchange markets are closed, and the values arise from the
 * carry-forward and decay-weighted blending the provider uses to bridge sources that publish irregularly (see
 * lineofflight/frankfurter issues 570 and 573). USD/KRW simply repeats the Friday rate on Saturday, EUR/CHF drifts by
 * a few ten-thousandths per weekend day. Storing that as a quote would put smoothed values next to real ones and would
 * make this connector's history incompatible with every other currency connector, so those rows are dropped.
 * </p>
 * <p>
 * Note that the blended default is an average across providers that fix at different times of day. It therefore
 * deviates from the reference rate of any single institution, measurably by one to three tenths of a percent against
 * the ECB. Scoping a request to one provider would restore the reference character but would also give up most of the
 * currency coverage that makes this connector worthwhile.
 * </p>
 *
 * <h3>Why small rates are requested the other way round</h3>
 * <p>
 * The provider rounds every value to a decimal count chosen by its magnitude, following the usual foreign exchange
 * convention: five decimal places below 1, four from 1 to 20, three from 20 to 80, two above that, and none for very
 * large rates. The stated purpose is to hide the false precision that averaging across providers invents (see
 * lineofflight/frankfurter issues 533 and 534). The bands keep roughly five significant digits, which is ample - but
 * only down to a rate of about 0.1. Below that the fixed five decimal places decay quickly: JPY/GBP is answered as
 * {@code 0.005} where the true rate is {@code 0.0050012}, and KRW/USD as {@code 0.00072} against {@code 0.00072472},
 * an error of 0.7 percent.
 * </p>
 * <p>
 * There is no query parameter to opt out of the rounding, and scoping the request to a single provider does not help
 * either, because a derived cross rate is rounded regardless. The connector therefore inspects the delivered series
 * and, when its smallest rate falls below {@link #MIN_WELL_CONDITIONED_RATE}, requests the opposite direction as well
 * and uses its reciprocal - JPY/GBP is derived from GBP/JPY {@code 199.95}. That recovers precision which genuinely
 * exists in the data rather than inventing any, and the second request happens only for the few pairs that need it.
 * </p>
 */
public abstract class FrankfurterApiFeedConnector extends BaseFeedConnector {

  private static final Map<FeedSupport, FeedIdentifier[]> SUPPORTED_FEED = new HashMap<>();

  static {
    SUPPORTED_FEED.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  /** Path of the time series endpoint, appended to the configured instance root. */
  private static final String RATES_PATH = "/v2/rates";
  /** Path of the currency catalogue, used to reject a pair this instance cannot answer. */
  private static final String CURRENCIES_PATH = "/v2/currencies";
  private static final int REQUEST_TIMEOUT_SECONDS = 30;
  /**
   * Length of the range used for the download link. The link is not only shown to the user, it is also fetched by
   * {@link BaseFeedConnector#checkUrl} on every save because of {@link UrlCheck#HISTORY}, so it must stay cheap.
   */
  private static final int DOWNLOAD_LINK_DAYS = 7;
  /**
   * Lower end of the range in which the rounding of the provider is harmless. Rates below 1 are rounded to five
   * decimal places, so the relative error stays at or below about 0.005 percent down to 0.1 and then grows in inverse
   * proportion to the rate - 0.006 percent at 0.08, 0.1 percent at 0.005, 0.7 percent at 0.0007. Below this bound the
   * opposite direction of the pair is worth a second request.
   */
  private static final double MIN_WELL_CONDITIONED_RATE = 0.1;
  /** The currency catalogue changes at most once a day, so it is worth caching per instance. */
  private static final Duration SUPPORTED_CURRENCIES_TTL = Duration.ofHours(24);
  private static final Map<String, String> JSON_HEADERS = Map.of("Accept", "application/json");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Root URL of this Frankfurter instance without a version path, for example {@code https://api.frankfurter.dev}. */
  protected final String apiRootUrl;

  private volatile Set<String> supportedCurrencies;
  private volatile Instant supportedCurrenciesLoadedAt;

  /**
   * Creates a connector for one Frankfurter instance.
   *
   * @param shortId      connector short id, becomes {@code gt.datafeed.<shortId>} and is persisted in
   *                     {@code securitycurrency.id_connector_history}, so it must never change afterwards
   * @param readableName name shown in the connector selection of the user interface
   * @param apiRootUrl   root URL of the instance <b>without</b> a version path; a blank value leaves the connector
   *                     deactivated and therefore invisible in the user interface
   */
  protected FrankfurterApiFeedConnector(String shortId, String readableName, String apiRootUrl) {
    super(SUPPORTED_FEED, shortId, readableName, null, EnumSet.of(UrlCheck.HISTORY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CURRENCY_PAIR);
    this.apiRootUrl = normalizeRootUrl(apiRootUrl);
  }

  /**
   * A Frankfurter instance without a configured URL cannot answer anything, so the connector reports itself as
   * deactivated and is filtered out of the connector lists offered to the user.
   */
  @Override
  public boolean isActivated() {
    return apiRootUrl != null && !apiRootUrl.isBlank();
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    LocalDate to = LocalDate.now();
    return createRatesUrl(currencypair, to.minusDays(DOWNLOAD_LINK_DAYS), to);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final LocalDate from,
      final LocalDate to) throws IOException, InterruptedException {
    List<FrankfurterRate> rates = readRates(
        createRatesUrl(currencyPair.getFromCurrency(), currencyPair.getToCurrency(), from, to));
    boolean inverted = false;

    if (isPoorlyConditioned(rates)) {
      List<FrankfurterRate> inverseRates = readRates(
          createRatesUrl(currencyPair.getToCurrency(), currencyPair.getFromCurrency(), from, to));
      if (smallestRate(inverseRates) > smallestRate(rates)) {
        rates = inverseRates;
        inverted = true;
      }
    }

    Map<LocalDate, Historyquote> quotesByDate = new TreeMap<>();
    for (FrankfurterRate rate : rates) {
      if (rate.date == null || rate.rate == null || rate.rate.signum() <= 0) {
        continue;
      }
      LocalDate quoteDate = LocalDate.parse(rate.date);
      if (!quoteDate.isBefore(from) && !quoteDate.isAfter(to) && !isWeekend(quoteDate)) {
        Historyquote historyquote = new Historyquote();
        historyquote.setDate(quoteDate);
        // Deliberately unrounded: an exchange rate such as USD/CHF 0.81136 would lose almost all of its significant
        // digits when rounded to the quote currency precision, which is meant for prices rather than for rates.
        double price = rate.rate.doubleValue();
        historyquote.setClose(inverted ? 1.0 / price : price);
        quotesByDate.put(quoteDate, historyquote);
      }
    }
    return new ArrayList<>(quotesByDate.values());
  }

  /**
   * Reports whether a date falls on a weekend, when foreign exchange markets are closed and the provider only carries
   * the previous rate forward.
   *
   * @param date the date of a delivered rate
   * @return true for Saturday and Sunday
   */
  private static boolean isWeekend(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
  }

  /**
   * Reports whether the series contains a rate so small that the five decimal places of the endpoint no longer carry
   * enough significant digits, which makes the opposite direction of the pair the better source.
   *
   * @param rates the delivered series, may be empty
   * @return true when at least one usable rate is below {@link #MIN_WELL_CONDITIONED_RATE}
   */
  private boolean isPoorlyConditioned(List<FrankfurterRate> rates) {
    double smallest = smallestRate(rates);
    return smallest > 0 && smallest < MIN_WELL_CONDITIONED_RATE;
  }

  /**
   * Returns the smallest usable rate of a series, which is the one that limits the precision of the whole series.
   *
   * @param rates the delivered series, may be empty
   * @return the smallest rate greater than zero, or 0 when the series holds none
   */
  private double smallestRate(List<FrankfurterRate> rates) {
    return rates.stream().filter(rate -> rate.rate != null && rate.rate.signum() > 0)
        .mapToDouble(rate -> rate.rate.doubleValue()).min().orElse(0);
  }

  /**
   * Rejects a currency pair whose currencies this instance does not offer, before the inherited connectivity check
   * turns the same situation into a far less specific "cannot connect" message.
   * <p>
   * The check has to sit here rather than in {@code extendedCheck}: this connector declares
   * {@link FeedIdentifier#CURRENCY}, so the base class always clears the URL extension and never reaches its
   * {@code extendedCheck} hook. {@code EcbCrossRateConnector} solves the same problem the same way.
   * </p>
   */
  @Override
  protected <S extends Securitycurrency<S>> boolean clearAndCheckUrlPatternSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend,
        errorMsgKey, feedIdentifier, specialInvestmentInstruments, assetclassType);
    checkForSupportedCurrencies((Currencypair) securitycurrency);
    return clear;
  }

  private void checkForSupportedCurrencies(Currencypair currencypair) {
    Set<String> currencies = getSupportedCurrencies();
    for (String currency : List.of(currencypair.getFromCurrency(), currencypair.getToCurrency())) {
      if (!currencies.contains(currency.toUpperCase(Locale.ROOT))) {
        throw new GeneralNotTranslatedWithArgumentsException("gt.connector.frankfurter.not.supported.currency",
            new Object[] { currency, apiRootUrl });
      }
    }
  }

  /**
   * Returns the ISO codes this instance offers, reloading them at most once per {@link #SUPPORTED_CURRENCIES_TTL}.
   *
   * @return the supported ISO currency codes in upper case
   * @throws GeneralNotTranslatedWithArgumentsException if the instance is unreachable or not configured at all
   */
  private Set<String> getSupportedCurrencies() {
    Set<String> cached = supportedCurrencies;
    Instant loadedAt = supportedCurrenciesLoadedAt;
    if (cached != null && loadedAt != null && Duration.between(loadedAt, Instant.now()).compareTo(
        SUPPORTED_CURRENCIES_TTL) < 0) {
      return cached;
    }
    try {
      HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(apiRootUrl + CURRENCIES_PATH,
          REQUEST_TIMEOUT_SECONDS, JSON_HEADERS);
      if (response.statusCode() != 200) {
        throw new IOException("Frankfurter currency request failed with HTTP status " + response.statusCode());
      }
      FrankfurterCurrency[] frankfurterCurrencies = OBJECT_MAPPER.readValue(response.body(),
          FrankfurterCurrency[].class);
      Set<String> currencies = Arrays.stream(frankfurterCurrencies).map(c -> c.iso_code).filter(c -> c != null)
          .map(c -> c.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
      supportedCurrencies = currencies;
      supportedCurrenciesLoadedAt = Instant.now();
      return currencies;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.frankfurter.not.reachable",
          new Object[] { apiRootUrl });
    } catch (Exception _) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.frankfurter.not.reachable",
          new Object[] { apiRootUrl });
    }
  }

  private List<FrankfurterRate> readRates(String url) throws IOException, InterruptedException {
    HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(url, REQUEST_TIMEOUT_SECONDS, JSON_HEADERS);
    if (response.statusCode() != 200) {
      throw new IOException("Frankfurter request failed with HTTP status " + response.statusCode());
    }
    return List.of(OBJECT_MAPPER.readValue(response.body(), FrankfurterRate[].class));
  }

  /**
   * Builds the time series URL for a currency pair in its natural direction.
   *
   * @param currencypair the currency pair whose rates are requested
   * @param from         first day of the requested range, inclusive
   * @param to           last day of the requested range, inclusive
   * @return the fully qualified request URL
   */
  private String createRatesUrl(Currencypair currencypair, LocalDate from, LocalDate to) {
    return createRatesUrl(currencypair.getFromCurrency(), currencypair.getToCurrency(), from, to);
  }

  /**
   * Builds the time series URL for one direction. Base and quote are free parameters of the endpoint, so the opposite
   * direction of a pair is requested by simply swapping them.
   *
   * @param baseCurrency  ISO code of the base currency
   * @param quoteCurrency ISO code of the quote currency
   * @param from          first day of the requested range, inclusive
   * @param to            last day of the requested range, inclusive
   * @return the fully qualified request URL
   */
  private String createRatesUrl(String baseCurrency, String quoteCurrency, LocalDate from, LocalDate to) {
    return apiRootUrl + RATES_PATH + "?base=" + baseCurrency + "&quotes=" + quoteCurrency + "&from=" + from + "&to="
        + to;
  }

  /**
   * Removes trailing slashes so that the configured root and the appended path never produce a double slash. A blank
   * or missing value is passed through unchanged and keeps the connector deactivated.
   *
   * @param apiRootUrl the configured root URL, may be null or blank
   * @return the root URL without a trailing slash
   */
  private static String normalizeRootUrl(String apiRootUrl) {
    if (apiRootUrl == null || apiRootUrl.isBlank()) {
      return apiRootUrl;
    }
    String trimmed = apiRootUrl.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class FrankfurterRate {
    public String date;
    public BigDecimal rate;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class FrankfurterCurrency {
    public String iso_code;
  }
}
