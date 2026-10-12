package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Searches the listings of the SIX Swiss Exchange through its public delayed-data query service (FQS), which needs no
 * key. A query by ISIN returns one row per trading currency, for example Apple in CHF and USD. Since the data comes
 * from the exchange itself, ISIN, currency and MIC count as delivered.
 *
 * <p>
 * The provider symbol is the {@code ValorSymbol} (e.g. {@code NOVN}); the {@code ValorId} (ISIN + currency + market
 * segment digit, e.g. {@code CH0012005267CHF4}) is reported as the provider instrument id. It happens to be the URL
 * extension of the SIX price connector, but deriving that extension is left to the connector.
 * </p>
 *
 * <p>
 * Master data comes from the detail endpoint that matches the product line of the candidate: bonds of domestic
 * ({@code DB}) and foreign ({@code FB}) issuers from
 * {@code bond_details}, exchange traded products ({@code ET}) from {@code fund_details}, everything else from
 * {@code share_details}. Only values with exactly the meaning of a GT field are mapped; in particular the
 * {@code firstTradingDate} of a share or fund denotes its current SIX listing, so it becomes {@code listedSince} and
 * never the first active day, and the {@code countryCode} of a share is not the country of the issuer (Novartis:
 * 2020-12-07 and US), so it stays a raw attribute. For a bond the first trading day is the issue and is taken as the
 * first active day as well.
 * </p>
 */
@Component
public class SixSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "six";

  private static final String DEFAULT_BASE_URL = "https://www.six-group.com/";
  private static final String COLUMNS = "ISIN,ValorId,ValorSymbol,ShortName,IssuerNameFull,TradingBaseCurrency,"
      + "ProductLine,SecTypeCode";
  /** Product line of a bond of a domestic issuer. */
  private static final String PRODUCT_LINE_BOND = "DB";
  /** Product line of a bond of a foreign issuer, e.g. a CHF bond of a French issuer; the same detail endpoint. */
  private static final String PRODUCT_LINE_FOREIGN_BOND = "FB";
  private static final String PRODUCT_LINE_EXCHANGE_TRADED = "ET";
  private static final String EXCHANGE_PAGE_BASE = "https://www.six-group.com/";
  private static final Pattern VALOR_ID_PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9][A-Za-z]{3}\\d$");
  /** SIX writes this date for an open end, e.g. as last trading day of a share. */
  private static final String SIX_OPEN_END = "99991231";

  private final String baseUrl;

  @Autowired
  public SixSecuritySearch() {
    this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param baseUrl        base URL of the SIX web site, ending with a slash; tests point it to a local server
   * @param requestTimeout timeout of a single request
   */
  SixSecuritySearch(String baseUrl, Duration requestTimeout) {
    super(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** SIX answers only for its own exchange and only by ISIN, which is placed into the filter expression. */
  /** Exchanges and instrument types are those of {@code SixFeedConnector}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(PROVIDER_ID);
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && (query.mic() == null || GlobalConstants.STOCK_EX_MIC_SIX.equals(query.mic()));
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    String url = baseUrl + "itf/fqs/delayed/movie.json?select=" + COLUMNS + "&where=ISIN=" + query.isin();
    return parse(sendExpectOk(HttpRequest.newBuilder(URI.create(url)).GET()));
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerInstrumentId() != null
        && VALOR_ID_PATTERN.matcher(candidate.providerInstrumentId()).matches();
  }

  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    String language = pageLanguage(locale);
    String valorId = candidate.providerInstrumentId();
    String productLine = candidate.providerInstrumentType() == null ? "" : candidate.providerInstrumentType();
    String path = switch (productLine) {
    case PRODUCT_LINE_BOND, PRODUCT_LINE_FOREIGN_BOND -> "sheldon/bond_details/v3/" + valorId + "/details/info.json";
    case PRODUCT_LINE_EXCHANGE_TRADED -> "sheldon/fund_details/v2/" + valorId + "/fund/info.json";
    default -> "sheldon/share_details/v3/" + valorId + "/share/info.json";
    };
    String body = sendExpectOk(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    return switch (productLine) {
    case PRODUCT_LINE_BOND, PRODUCT_LINE_FOREIGN_BOND -> parseBond(body, candidate, language);
    case PRODUCT_LINE_EXCHANGE_TRADED -> parseFund(body, candidate, language);
    default -> parseShare(body, candidate, language);
    };
  }

  /**
   * Converts the answer of {@code bond_details/v3/{valorId}/details/info.json}.
   *
   * @param body      the JSON response
   * @param candidate the candidate the request was made for
   * @param language  language of the SIX page, de or en
   * @return the master data, or null when the response holds no item
   */
  static SecurityBaseDataDto parseBond(String body, SecuritySearchCandidateDto candidate, String language) {
    JsonNode item = firstItem(body);
    if (item == null) {
      return null;
    }
    JsonNode info = item.path("info");
    checkIdentity(candidate, text(info, "isin"), text(info, "tradingCurrency"));
    Map<String, String> attributes = rawAttributes(item);
    String ticker = attributes.remove("info.valorSymbol");
    LocalDate activeFrom = sixDate(attributes.remove("info.firstTradingDate"));
    LocalDate listedSince = activeFrom;
    LocalDate activeTo = sixDate(attributes.remove("maturity"));
    Integer denomination = toInteger(attributes.remove("smallestDenomination"));
    DistributionFrequency frequency = couponFrequency(attributes.remove("couponInfo.interestFrequency"));
    String issuerCountry = attributes.remove("issuerCountryCode");
    attributes.remove("info.isin");
    return baseData(candidate, ticker, issuerCountry, activeFrom, activeTo, frequency, denomination, listedSince, null,
        language, "bonds/bond-explorer/bond-details.", attributes);
  }

  /**
   * Converts the answer of {@code fund_details/v2/{valorId}/fund/info.json}. The domicile of the fund counts as the
   * issuer country; an accumulating fund gets no distribution as frequency; asset class, region and replication remain
   * raw attributes.
   *
   * @param body      the JSON response
   * @param candidate the candidate the request was made for
   * @param language  language of the SIX page, de or en
   * @return the master data, or null when the response holds no item
   */
  static SecurityBaseDataDto parseFund(String body, SecuritySearchCandidateDto candidate, String language) {
    JsonNode item = firstItem(body);
    if (item == null) {
      return null;
    }
    JsonNode general = item.path("general");
    checkIdentity(candidate, text(general, "isin"), text(general, "tradingCurrency"));
    Map<String, String> attributes = rawAttributes(item);
    String ticker = attributes.remove("general.valorSymbol");
    String issuerCountry = attributes.remove("general.domicileOfFund");
    LocalDate listedSince = sixDate(attributes.remove("general.firstTradingDate"));
    Boolean distributing = fundDistributing(attributes.remove("general.dividendEntitlement"));
    attributes.remove("general.isin");
    return baseData(candidate, ticker, issuerCountry, null, null,
        Boolean.FALSE.equals(distributing) ? DistributionFrequency.DF_NONE : null, null, listedSince, distributing,
        language, "etf/etf-explorer/etf-detail.", attributes);
  }

  /**
   * Converts the answer of {@code share_details/v3/{valorId}/share/info.json}. Neither {@code firstTradingDate} nor
   * {@code countryCode} is mapped, see the class documentation.
   *
   * @param body      the JSON response
   * @param candidate the candidate the request was made for
   * @param language  language of the SIX page, de or en
   * @return the master data, or null when the response holds no item
   */
  static SecurityBaseDataDto parseShare(String body, SecuritySearchCandidateDto candidate, String language) {
    JsonNode item = firstItem(body);
    if (item == null) {
      return null;
    }
    checkIdentity(candidate, text(item, "isin"), text(item, "tradingCurrency"));
    Map<String, String> attributes = rawAttributes(item);
    String ticker = attributes.remove("valorSymbol");
    LocalDate listedSince = sixDate(attributes.remove("firstTradingDate"));
    attributes.remove("isin");
    return baseData(candidate, ticker, null, null, null, null, null, listedSince, null, language,
        "shares/share-explorer/share-details.", attributes);
  }

  /**
   * The {@code dividendEntitlement} of a SIX fund tells whether it distributes ({@code 1}) or accumulates ({@code 0}),
   * e.g. 1 for Vanguard FTSE All-World Dist and iShares SMI, 0 for iShares MSCI China Tech Acc. The share endpoint has
   * a field of the same name, a mere entitlement flag that says nothing about actual payments, so it is not used.
   *
   * @param value the raw value
   * @return true for distributing, false for accumulating, null for any other value
   */
  static Boolean fundDistributing(String value) {
    return "1".equals(value) ? Boolean.TRUE : "0".equals(value) ? Boolean.FALSE : null;
  }

  /**
   * The coupon frequency of SIX is the number of payments per year; any other value leaves the field open.
   *
   * @param value the raw {@code interestFrequency}
   * @return the frequency, or null when it has no GT counterpart
   */
  static DistributionFrequency couponFrequency(String value) {
    Integer perYear = toInteger(value);
    if (perYear == null) {
      return null;
    }
    return switch (perYear) {
    case 1 -> DistributionFrequency.DF_YEAR;
    case 2 -> DistributionFrequency.DF_SEMI_ANNUAL;
    case 4 -> DistributionFrequency.DF_QUARTERLY;
    case 12 -> DistributionFrequency.DF_MONTHLY;
    default -> null;
    };
  }

  private static SecurityBaseDataDto baseData(SecuritySearchCandidateDto candidate, String ticker, String issuerCountry,
      LocalDate activeFrom, LocalDate activeTo, DistributionFrequency frequency, Integer denomination,
      LocalDate listedSince, Boolean distributing, String language, String exchangePage,
      Map<String, String> attributes) {
    if (candidate.providerInstrumentType() != null) {
      attributes.put("productLine", candidate.providerInstrumentType());
    }
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(),
        candidate.currency(), GlobalConstants.STOCK_EX_MIC_SIX, candidate.name(),
        ticker == null ? candidate.providerSymbol() : ticker, issuerCountry, activeFrom, activeTo, frequency,
        denomination, null,
        EXCHANGE_PAGE_BASE + language + "/market-data/" + exchangePage + candidate.providerInstrumentId() + ".html#/",
        listedSince, distributing, attributes);
  }

  /**
   * SIX offers its pages in German and English; any other language gets the English page.
   *
   * @param locale language of the user, may be null
   * @return {@code de} or {@code en}
   */
  static String pageLanguage(Locale locale) {
    return locale != null && "de".equals(locale.getLanguage()) ? "de" : "en";
  }

  private static JsonNode firstItem(String body) {
    JsonNode items = OBJECT_MAPPER.readTree(body).path("itemList");
    return items.isArray() && !items.isEmpty() ? items.get(0) : null;
  }

  /**
   * Rejects a detail answer whose ISIN or trading currency contradicts the candidate. The exchange is not compared,
   * because SIX writes it inconsistently ({@code XSWX} for bonds and funds, {@code SWX} for shares).
   */
  private static void checkIdentity(SecuritySearchCandidateDto candidate, String isin, String currency) {
    if (isin != null && !isin.equals(candidate.isin())) {
      throw new SearchFailureException(SecuritySearchStatus.NO_MATCH,
          "Detail ISIN " + isin + " contradicts " + candidate.isin());
    }
    if (currency != null && candidate.currency() != null && !currency.equals(candidate.currency())) {
      throw new SearchFailureException(SecuritySearchStatus.NO_MATCH,
          "Detail currency " + currency + " contradicts " + candidate.currency());
    }
  }

  /** Flattens all scalar values of the item; nested objects get a dotted key, arrays a comma-separated value. */
  private static Map<String, String> rawAttributes(JsonNode item) {
    Map<String, String> attributes = new LinkedHashMap<>();
    flatten("", item, attributes);
    return attributes;
  }

  private static void flatten(String prefix, JsonNode node, Map<String, String> attributes) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue(), attributes);
      }
    } else if (node.isArray()) {
      List<String> values = new ArrayList<>();
      node.forEach(n -> {
        if (n.isValueNode() && !n.isNull()) {
          values.add(n.asString());
        }
      });
      putIfPresent(attributes, prefix, String.join(",", values));
    } else if (!node.isNull() && !node.isMissingNode()) {
      putIfPresent(attributes, prefix, node.asString());
    }
  }

  private static void putIfPresent(Map<String, String> attributes, String key, String value) {
    if (value != null && !value.isBlank()) {
      attributes.put(key, value);
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : blankToNull(value.asString());
  }

  private static LocalDate sixDate(String value) {
    if (value == null || SIX_OPEN_END.equals(value)) {
      return null;
    }
    try {
      return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Integer toInteger(String value) {
    if (value == null) {
      return null;
    }
    try {
      return (int) Math.round(Double.parseDouble(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Converts the FQS response, whose rows are arrays ordered as its {@code colNames}.
   *
   * @param body the JSON response
   * @return one candidate per row
   */
  static List<SecuritySearchCandidateDto> parse(String body) {
    JsonNode root = OBJECT_MAPPER.readTree(body);
    List<String> columns = new ArrayList<>();
    root.path("colNames").forEach(n -> columns.add(n.asString()));
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (JsonNode row : root.path("rowData")) {
      String currency = blankToNull(value(row, columns, "TradingBaseCurrency"));
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, value(row, columns, "ValorSymbol"),
          value(row, columns, "ValorId"), null, value(row, columns, "ISIN"), IdentityEvidence.RETURNED, currency,
          currency == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, GlobalConstants.STOCK_EX_MIC_SIX,
          IdentityEvidence.RETURNED, value(row, columns, "ShortName"),
          blankToNull(value(row, columns, "ProductLine"))));
    }
    return candidates;
  }

  private static String value(JsonNode row, List<String> columns, String column) {
    int index = columns.indexOf(column);
    JsonNode node = index < 0 ? null : row.path(index);
    return node == null || node.isMissingNode() || node.isNull() ? null : node.asString();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
