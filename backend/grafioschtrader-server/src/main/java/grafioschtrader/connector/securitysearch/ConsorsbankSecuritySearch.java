package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Finds the listings of an ISIN at Consorsbank without a key. Like onvista, Consorsbank is no exchange but quotes an
 * instrument on many venues, the German regional exchanges included, with a daily history back to April 1999.
 *
 * <p>
 * One request {@code stocks?id={ISIN}&field=BasicV1&field=ExchangesV2} answers the search: {@code BasicV1} names the
 * instrument with its type ({@code TOOL_SECURITY_TYPE}: {@code STO}, {@code BON}, {@code ETF}, {@code FUN}), WKN and
 * German exchange symbol, {@code ExchangesV2} lists every notation with its id ({@code CONSORS_ID}), exchange code
 * ({@code EXCHANGE_CODE}, e.g. {@code GER}, {@code SWX}, {@code NSB}) and currency. The {@code consorsbank} rows of
 * {@code mic_provider_map} place an exchange code on a GT stock exchange; venues without a row, such as Tradegate,
 * gettex or the off-exchange quotes, are skipped. US shares are quoted only as Nasdaq Basic ({@code NSB}), a code on
 * both US exchanges, so it is placed on the queried one only. An exchange may carry several notations in different
 * currencies, e.g. Apple on SIX in USD and CHF; the lookup keeps the queried currency. ISIN, currency and MIC count as
 * delivered.
 * </p>
 *
 * <p>
 * The provider instrument id {@code {CONSORS_ID},{EXCHANGE_CODE}}, e.g. {@code _39471840,GER}, is the URL extension of
 * the Consorsbank connector. The German exchange symbol is the ticker on a German venue only (Apple: APC), the WKN
 * replaces it for a bond there; elsewhere the listing has no ticker and merges with the listing of the same ISIN another
 * provider found. Master data is a second {@code BasicV1} from the endpoint of the type: {@code bonds} adds coupon,
 * coupon period, issue, maturity and nominal value, {@code etfs} and {@code funds} the domicile, which {@code stocks}
 * leaves empty for a fund.
 * </p>
 */
@Component
public class ConsorsbankSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "consorsbank";

  static final String TYPE_BOND = "BON";
  /** Consorsbank codes of the German venues, where the German exchange symbol is the ticker. */
  private static final Set<String> GERMAN_VENUES = Set.of("GER", "FRA", "STU", "MUN", "HAM", "BER", "DUS", "HAN");

  private static final String DEFAULT_BASE_URL = "https://www.consorsbank.de/web-financialinfo-service/api/marketdata/";

  private final MicProviderMapRepository micProviderMapRepository;
  private final String baseUrl;
  private volatile Map<String, List<String>> micsByCode;

  @Autowired
  public ConsorsbankSecuritySearch(MicProviderMapRepository micProviderMapRepository) {
    this(micProviderMapRepository, DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param micProviderMapRepository source of the Consorsbank exchange codes; read once
   * @param baseUrl                  base URL of the market data API, ending with a slash; tests point it to a local
   *                                 server
   * @param requestTimeout           timeout of a single request
   */
  ConsorsbankSecuritySearch(MicProviderMapRepository micProviderMapRepository, String baseUrl,
      Duration requestTimeout) {
    super(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.micProviderMapRepository = micProviderMapRepository;
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** Exchanges and instrument types are those of {@code ConsorsbankFeedConnector}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(PROVIDER_ID);
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin();
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    return parseSearch(sendExpectOk(HttpRequest.newBuilder(URI.create(baseUrl + "stocks?id=" + encode(query.isin())
        + "&field=BasicV1&field=ExchangesV2"))), query.isin(), query.mic(), getMicsByCode());
  }

  /**
   * Converts the search answer into one candidate per notation on a GT stock exchange.
   *
   * @param body       the answer with {@code BasicV1} and {@code ExchangesV2}
   * @param isin       the queried ISIN
   * @param queriedMic the queried MIC, which decides a code shared by several exchanges; may be null
   * @param micsByCode GT MICs per Consorsbank exchange code
   * @return the candidates
   */
  static List<SecuritySearchCandidateDto> parseSearch(String body, String isin, String queriedMic,
      Map<String, List<String>> micsByCode) {
    JsonNode answer = OBJECT_MAPPER.readTree(body).path(0);
    JsonNode basic = answer.path("BasicV1");
    String type = text(basic, "TOOL_SECURITY_TYPE");
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    if (!isin.equals(text(basic.path("ID"), "ISIN"))) {
      return candidates;
    }
    for (JsonNode notation : answer.path("ExchangesV2")) {
      String code = text(notation, "EXCHANGE_CODE");
      String mic = mic(micsByCode.get(code), queriedMic);
      String id = text(notation, "CONSORS_ID");
      if (mic == null || id == null) {
        continue;
      }
      String currency = text(notation, "ISO_CURRENCY");
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, ticker(basic, type, code), id + "," + code, code,
          isin, IdentityEvidence.RETURNED, currency,
          currency == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, mic, IdentityEvidence.RETURNED,
          text(basic, "NAME_SECURITY"), type));
    }
    return candidates;
  }

  /** The one MIC of a code, or the queried MIC when the code stands for several exchanges, like NSB. */
  private static String mic(List<String> mics, String queriedMic) {
    if (mics == null || mics.isEmpty()) {
      return null;
    }
    if (mics.size() == 1) {
      return mics.getFirst();
    }
    return mics.contains(queriedMic) ? queriedMic : null;
  }

  /**
   * The ticker of a notation: on a German venue the WKN for a bond and the German exchange symbol otherwise; no ticker
   * elsewhere, where the German symbol (Nestlé: NESR) is no exchange ticker.
   */
  private static String ticker(JsonNode basic, String type, String code) {
    if (!GERMAN_VENUES.contains(code)) {
      return null;
    }
    return TYPE_BOND.equals(type) ? text(basic.path("ID"), "WKN") : text(basic.path("ID"), "SYMBOL");
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerInstrumentId() != null;
  }

  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    String endpoint = masterDataEndpoint(candidate.providerInstrumentType());
    return toBaseData(candidate, OBJECT_MAPPER.readTree(sendExpectOk(HttpRequest.newBuilder(
        URI.create(baseUrl + endpoint + "?id=" + encode(candidate.isin()) + "&field=BasicV1")))).path(0)
        .path("BasicV1"));
  }

  /** The endpoint whose {@code BasicV1} carries the master data of the type; shares and unknown types use stocks. */
  static String masterDataEndpoint(String toolSecurityType) {
    return switch (toolSecurityType == null ? "" : toolSecurityType) {
    case TYPE_BOND -> "bonds";
    case "ETF" -> "etfs";
    case "FUN" -> "funds";
    default -> "stocks";
    };
  }

  /**
   * Builds the master data of a notation. A fund's country is its domicile, as SIX reports it.
   *
   * @param candidate the notation
   * @param basic     {@code BasicV1} of the instrument; for a bond the one of the {@code bonds} endpoint
   * @return the master data
   */
  static SecurityBaseDataDto toBaseData(SecuritySearchCandidateDto candidate, JsonNode basic) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("type", candidate.providerInstrumentType());
    attributes.put("exchange", candidate.providerExchangeCode());
    putIfPresent(attributes, "wkn", text(basic.path("ID"), "WKN"));
    putIfPresent(attributes, "subType", text(basic, "NAME_SUB_SECURITY_TYPE"));
    LocalDate activeFrom = null;
    LocalDate activeTo = null;
    Integer denomination = null;
    DistributionFrequency frequency = null;
    if (TYPE_BOND.equals(candidate.providerInstrumentType())) {
      activeTo = date(text(basic, "DATETIME_MATURITY"));
      activeFrom = date(text(basic, "DATETIME_EMISSION"));
      LocalDate oldest = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
      if (activeFrom != null && activeFrom.isBefore(oldest)) {
        activeFrom = oldest;
      }
      denomination = wholeAmount(basic.path("NOMINAL_VALUE"));
      frequency = OnvistaSecuritySearch.couponFrequency(text(basic, "COUPON_PERIOD"));
      putIfPresent(attributes, "coupon", text(basic, "COUPON"));
      putIfPresent(attributes, "couponType", text(basic, "COUPON_TYPE"));
      putIfPresent(attributes, "couponPeriod", text(basic, "COUPON_PERIOD"));
      putIfPresent(attributes, "issuer", text(basic, "NAME_ISSUER"));
    }
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(),
        candidate.currency(), candidate.mic(), candidate.name(), candidate.providerSymbol(), text(basic, "ISO_COUNTRY"),
        activeFrom, activeTo, frequency, denomination, null, null, null, null, attributes);
  }

  private Map<String, List<String>> getMicsByCode() {
    Map<String, List<String>> mapping = micsByCode;
    if (mapping == null) {
      mapping = new HashMap<>();
      for (MicProviderMap row : micProviderMapRepository.findByIdProviderMic_IdProvider(PROVIDER_ID)) {
        mapping.computeIfAbsent(row.getCodeProvider(), _ -> new ArrayList<>()).add(row.getIdProviderMic().getMic());
      }
      micsByCode = mapping;
    }
    return mapping;
  }

  /** The date part of a Consorsbank time stamp such as {@code 2028-02-15T12:00:00+0000}. */
  private static LocalDate date(String value) {
    try {
      return value == null || value.length() < 10 ? null : LocalDate.parse(value.substring(0, 10));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** A nominal value of at least one whole unit; the 0.01 of a German federal bond is no denomination. */
  private static Integer wholeAmount(JsonNode value) {
    if (!value.isNumber()) {
      return null;
    }
    double amount = value.asDouble();
    return amount >= 1 && amount == Math.rint(amount) && amount <= Integer.MAX_VALUE ? (int) amount : null;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static void putIfPresent(Map<String, String> attributes, String key, String value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString().trim();
  }
}
