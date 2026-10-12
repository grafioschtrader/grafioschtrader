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
 * Finds the listings of an ISIN at onvista without a key. onvista is no exchange but quotes an instrument on many
 * trading venues - in particular the German regional exchanges and Milan, which no other provider of the lookup covers.
 *
 * <p>
 * The search takes two requests: {@code instruments/query} names type and onvista id of the instrument, and its snapshot
 * lists every venue with notation ({@code market.idNotation}), exchange code ({@code market.codeExchange}, e.g.
 * {@code GER}, {@code STU}, {@code NYS}) and currency. The {@code onvista} rows of {@code mic_provider_map} place an
 * exchange code on a GT stock exchange; venues without row, such as Tradegate or gettex, are skipped. ISIN, currency
 * and MIC count as delivered. A bond's snapshot is requested from {@code bonds/ISIN:…/snapshot}, which adds its terms.
 * </p>
 *
 * <p>
 * The provider instrument id {@code {TYPE}/{id}/eod_history?idNotation={notation}} is the URL extension of the onvista
 * connector. The ticker is the home symbol on an exchange of the instrument's home country and the onvista symbol
 * elsewhere (Apple: AAPL on Nasdaq, APC in Frankfurt); a bond on a German venue has the WKN as ticker.
 * </p>
 */
@Component
public class OnvistaSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "onvista";

  static final String TYPE_BOND = "BOND";
  private static final String GERMANY = "DE";

  private static final String DEFAULT_BASE_URL = "https://api.onvista.de/api/v1/";

  private final MicProviderMapRepository micProviderMapRepository;
  private final String baseUrl;
  private volatile Map<String, String> micByCode;

  @Autowired
  public OnvistaSecuritySearch(MicProviderMapRepository micProviderMapRepository) {
    this(micProviderMapRepository, DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param micProviderMapRepository source of the onvista exchange codes; read once
   * @param baseUrl                  base URL of the onvista API, ending with a slash; tests point it to a local server
   * @param requestTimeout           timeout of a single request
   */
  OnvistaSecuritySearch(MicProviderMapRepository micProviderMapRepository, String baseUrl, Duration requestTimeout) {
    // onvista answers bursts with HTTP 429, see OnvistaFeedConnector
    super(Bandwidth.builder().capacity(4).refillGreedy(4, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.micProviderMapRepository = micProviderMapRepository;
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** Exchanges and instrument types are those of {@code OnvistaFeedConnector}. */
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
    JsonNode instrument = findInstrument(sendExpectOk(HttpRequest.newBuilder(URI.create(baseUrl
        + "instruments/query?searchValue=" + URLEncoder.encode(query.isin(), StandardCharsets.UTF_8)))), query.isin());
    if (instrument == null) {
      return List.of();
    }
    return parseSnapshot(sendExpectOk(HttpRequest.newBuilder(URI.create(snapshotUrl(instrument, query.isin())))),
        query.isin(), getMicByCode());
  }

  /**
   * Picks the entry of the query answer that names the ISIN; a search for an ISIN may also return derivatives on it.
   *
   * @param body the answer of {@code instruments/query}
   * @param isin the queried ISIN
   * @return the entry, or null when none names the ISIN
   */
  static JsonNode findInstrument(String body, String isin) {
    for (JsonNode entry : OBJECT_MAPPER.readTree(body).path("list")) {
      if (isin.equals(text(entry, "isin")) && text(entry, "entityType") != null
          && text(entry, "entityValue") != null) {
        return entry;
      }
    }
    return null;
  }

  private String snapshotUrl(JsonNode instrument, String isin) {
    String type = text(instrument, "entityType");
    return TYPE_BOND.equals(type) ? baseUrl + "bonds/ISIN:" + URLEncoder.encode(isin, StandardCharsets.UTF_8) + "/snapshot"
        : baseUrl + "instruments/" + type + "/" + text(instrument, "entityValue") + "/snapshot";
  }

  /**
   * Converts the snapshot into one candidate per venue on a GT stock exchange.
   *
   * @param body      the snapshot
   * @param isin      the queried ISIN
   * @param micByCode GT MIC per onvista exchange code
   * @return the candidates
   */
  static List<SecuritySearchCandidateDto> parseSnapshot(String body, String isin, Map<String, String> micByCode) {
    JsonNode snapshot = OBJECT_MAPPER.readTree(body);
    JsonNode instrument = snapshot.path("instrument");
    String type = text(instrument, "entityType");
    String id = text(instrument, "entityValue");
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    if (type == null || id == null || !isin.equals(text(instrument, "isin"))) {
      return candidates;
    }
    String homeCountry = isin.substring(0, 2);
    for (JsonNode quote : snapshot.path("quoteList").path("list")) {
      JsonNode market = quote.path("market");
      String mic = micByCode.get(text(market, "codeExchange"));
      String notation = text(market, "idNotation");
      if (mic == null || notation == null) {
        continue;
      }
      String currency = text(quote, "isoCurrency");
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, ticker(instrument, type, text(market, "isoCountry"),
          homeCountry), type + "/" + id + "/eod_history?idNotation=" + notation, text(market, "codeExchange"), isin,
          IdentityEvidence.RETURNED, currency, currency == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED,
          mic, IdentityEvidence.RETURNED, text(instrument, "name"), type));
    }
    return candidates;
  }

  /**
   * The ticker of a venue: for a bond on a German venue the WKN, the German identifier Deutsche Börse also shows;
   * otherwise the home symbol in the home country of the ISIN and the onvista symbol elsewhere. A bond on a foreign
   * venue usually has neither, e.g. on SIX, whose own ticker (FON25) the SIX row supplies.
   */
  private static String ticker(JsonNode instrument, String type, String marketCountry, String homeCountry) {
    if (TYPE_BOND.equals(type) && GERMANY.equals(marketCountry)) {
      return text(instrument, "wkn");
    }
    String home = text(instrument, "homeSymbol");
    return home != null && homeCountry.equals(marketCountry) ? home : text(instrument, "symbol");
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerInstrumentId() != null;
  }

  /** A bond's terms come from its snapshot; everything else is the search hit itself. */
  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    JsonNode bond = TYPE_BOND.equals(candidate.providerInstrumentType()) ? OBJECT_MAPPER.readTree(sendExpectOk(
        HttpRequest.newBuilder(URI.create(baseUrl + "bonds/ISIN:" + candidate.isin() + "/snapshot")))) : null;
    return toBaseData(candidate, bond);
  }

  /**
   * Builds the master data of a listing.
   *
   * @param candidate the listing
   * @param bond      the bond snapshot, or null for any other instrument
   * @return the master data
   */
  static SecurityBaseDataDto toBaseData(SecuritySearchCandidateDto candidate, JsonNode bond) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("type", candidate.providerInstrumentType());
    attributes.put("exchange", candidate.providerExchangeCode());
    LocalDate activeFrom = null;
    LocalDate activeTo = null;
    Integer denomination = null;
    DistributionFrequency frequency = null;
    String issuerCountry = null;
    if (bond != null) {
      JsonNode details = bond.path("bondsDetails");
      JsonNode base = bond.path("bondsBaseData");
      activeTo = date(text(base, "datetimeMaturity"));
      activeFrom = date(text(base, "datetimeEmission"));
      LocalDate oldest = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
      if (activeFrom != null && activeFrom.isBefore(oldest)) {
        activeFrom = oldest;
      }
      denomination = wholeAmount(details.path("nominal"));
      frequency = couponFrequency(text(base, "nameCouponPeriod"));
      issuerCountry = text(bond.path("bondsIssuer"), "isoCountry");
      putIfPresent(attributes, "coupon", text(details, "coupon"));
      putIfPresent(attributes, "typeCoupon", text(details, "nameTypeCoupon"));
      putIfPresent(attributes, "issuer", text(bond.path("bondsIssuer"), "name"));
      putIfPresent(attributes, "couponPeriod", text(base, "nameCouponPeriod"));
    }
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(),
        candidate.currency(), candidate.mic(), candidate.name(), candidate.providerSymbol(), issuerCountry, activeFrom,
        activeTo, frequency, denomination, null, null, null, null, attributes);
  }

  /**
   * Maps the coupon period of a bond as onvista names it in German.
   *
   * @param name e.g. {@code Jahr}, {@code 6 Monate}, {@code 3 Monate}, {@code Monat}; may be null
   * @return the frequency, or null for a period without counterpart
   */
  static DistributionFrequency couponFrequency(String name) {
    if (name == null) {
      return null;
    }
    return switch (name.strip().toLowerCase(Locale.ROOT)) {
    case "jahr", "12 monate" -> DistributionFrequency.DF_YEAR;
    case "6 monate", "halbjahr" -> DistributionFrequency.DF_SEMI_ANNUAL;
    case "3 monate", "quartal" -> DistributionFrequency.DF_QUARTERLY;
    case "monat", "1 monat" -> DistributionFrequency.DF_MONTHLY;
    default -> null;
    };
  }

  private Map<String, String> getMicByCode() {
    Map<String, String> mapping = micByCode;
    if (mapping == null) {
      mapping = new HashMap<>();
      for (MicProviderMap row : micProviderMapRepository.findByIdProviderMic_IdProvider(PROVIDER_ID)) {
        mapping.put(row.getCodeProvider(), row.getIdProviderMic().getMic());
      }
      micByCode = mapping;
    }
    return mapping;
  }

  /** The date part of an onvista time stamp such as {@code 2034-02-15T12:00:00.000+00:00}. */
  private static LocalDate date(String value) {
    try {
      return value == null || value.length() < 10 ? null : LocalDate.parse(value.substring(0, 10));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static Integer wholeAmount(JsonNode value) {
    if (!value.isNumber()) {
      return null;
    }
    double amount = value.asDouble();
    return amount >= 1 && amount == Math.rint(amount) && amount <= Integer.MAX_VALUE ? (int) amount : null;
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
