package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Searches listings by ISIN with the search API of EODHD ({@code api/search/{ISIN}}), for a user who holds an EODHD
 * subscription. The search is part of every EODHD plan and costs one API call; it is asked only when the EODHD
 * connector has an API key, which is its only activation.
 *
 * <p>
 * The answer holds one entry per listing with ticker ({@code Code}), exchange code ({@code Exchange}, e.g.
 * {@code XETRA}, {@code F}, {@code US}), name, type, currency and ISIN, so ISIN and currency count as delivered. The
 * {@code eodhd} rows of {@code mic_provider_map} place the exchange code on a GT stock exchange: the queried MIC when its
 * row carries the code, otherwise the one MIC of the code. The collective code {@code US} covers every US exchange and
 * is placed only on a queried US MIC. Master data is the search hit itself; the EODHD symbol {@code ticker.code} is the
 * URL extension of the EODHD connector.
 * </p>
 */
@Component
public class EodhdSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "eodhd";
  /** Short id of the EODHD price connector, whose API key the search uses. */
  public static final String CONNECTOR_SHORT_ID = "eodhistoricaldata";

  private static final String DEFAULT_BASE_URL = "https://eodhd.com/api/";
  private static final int MAX_RESULTS = 50;
  /** Instrument type given to a hit of the index list. */
  static final String TYPE_INDEX = "INDEX";

  private final MicProviderMapRepository micProviderMapRepository;
  private final Supplier<String> apiKey;
  private final String baseUrl;
  private volatile ExchangeMapping exchangeMapping;
  private volatile IndexList indexList;

  @Autowired
  public EodhdSecuritySearch(MicProviderMapRepository micProviderMapRepository,
      ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository) {
    this(micProviderMapRepository, () -> connectorApiKeyJpaRepository.findById(CONNECTOR_SHORT_ID)
        .map(ConnectorApiKey::getApiKey).orElse(null), DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param micProviderMapRepository source of the EODHD exchange codes; read once
   * @param apiKey                   supplies the API key of the EODHD connector, or null when there is none
   * @param baseUrl                  base URL of the EODHD API, ending with a slash; tests point it to a local server
   * @param requestTimeout           timeout of a single request
   */
  EodhdSecuritySearch(MicProviderMapRepository micProviderMapRepository, Supplier<String> apiKey, String baseUrl,
      Duration requestTimeout) {
    super(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofSeconds(1)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.micProviderMapRepository = micProviderMapRepository;
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** Exchanges and instrument types are those of {@code EodHistoricalDataConnector}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(CONNECTOR_SHORT_ID);
  }

  /** Without an EODHD API key the search is not asked at all, so that no user without a subscription sees it fail. */
  @Override
  public boolean supports(SecuritySearchQuery query) {
    String key = apiKey.get();
    return query.hasValidIsin() && key != null && !key.isBlank();
  }

  /**
   * Asks the search API; when it knows nothing, the list of the EODHD indices, which the search API does not cover:
   * OMX Helsinki 25 is neither found by its ISIN nor by its code, but {@code OMXH25.INDX} delivers prices.
   */
  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    String url = baseUrl + "search/" + URLEncoder.encode(query.isin(), StandardCharsets.UTF_8) + "?limit="
        + MAX_RESULTS + "&fmt=json&api_token=" + URLEncoder.encode(apiKey.get(), StandardCharsets.UTF_8);
    List<SecuritySearchCandidateDto> candidates = parse(sendExpectOk(HttpRequest.newBuilder(URI.create(url)).GET()),
        query.mic(), getExchangeMapping());
    return candidates.isEmpty() ? findIndex(getIndexList(), query, getExchangeMapping()) : candidates;
  }

  /**
   * Finds an index in the EODHD index list: by ISIN, which only some entries carry (DAX, S&P 500, SMI), otherwise by
   * the code in the query's ticker, e.g. {@code OMXH25} found by Nasdaq Nordic. The index lands on the queried MIC.
   *
   * @param indexList  the answer of {@code exchange-symbol-list/INDX}
   * @param query      the query; its ISIN and ticker are compared
   * @param mapping    the EODHD exchange codes
   * @return the matching index, or nothing
   */
  static List<SecuritySearchCandidateDto> findIndex(String indexList, SecuritySearchQuery query,
      ExchangeMapping mapping) {
    if (indexList == null) {
      return List.of();
    }
    JsonNode byTicker = null;
    for (JsonNode entry : OBJECT_MAPPER.readTree(indexList)) {
      String code = text(entry, "Code");
      if (code == null) {
        continue;
      }
      if (query.isin() != null && query.isin().equals(text(entry, "Isin"))) {
        return List.of(toIndexCandidate(entry, code, query.mic(), mapping));
      }
      if (byTicker == null && query.ticker() != null && code.equalsIgnoreCase(query.ticker())) {
        byTicker = entry;
      }
    }
    return byTicker == null ? List.of()
        : List.of(toIndexCandidate(byTicker, text(byTicker, "Code"), query.mic(), mapping));
  }

  private static SecuritySearchCandidateDto toIndexCandidate(JsonNode entry, String code, String queriedMic,
      ExchangeMapping mapping) {
    String isin = text(entry, "Isin");
    String currency = text(entry, "Currency");
    String mic = mapping.deriveMic(queriedMic, ExchangeMapping.CODE_INDEX);
    String symbol = code + "." + ExchangeMapping.CODE_INDEX;
    return new SecuritySearchCandidateDto(PROVIDER_ID, symbol, symbol, ExchangeMapping.CODE_INDEX, isin,
        isin == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, currency,
        currency == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, mic,
        mic == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, text(entry, "Name"), TYPE_INDEX);
  }

  /** The EODHD index list, loaded at most once a day: it holds some 1700 entries and costs one API call. */
  private String getIndexList() throws Exception {
    IndexList cached = indexList;
    if (cached == null || !cached.day().equals(LocalDate.now())) {
      String url = baseUrl + "exchange-symbol-list/" + ExchangeMapping.CODE_INDEX + "?fmt=json&api_token="
          + URLEncoder.encode(apiKey.get(), StandardCharsets.UTF_8);
      cached = new IndexList(LocalDate.now(), sendExpectOk(HttpRequest.newBuilder(URI.create(url)).GET()));
      indexList = cached;
    }
    return cached.body();
  }

  private record IndexList(LocalDate day, String body) {
  }

  /**
   * Converts the search answer.
   *
   * @param body       the JSON array of the search API
   * @param queriedMic MIC of the query, may be null
   * @param mapping    the EODHD exchange codes
   * @return one candidate per entry with ticker and exchange code
   */
  static List<SecuritySearchCandidateDto> parse(String body, String queriedMic, ExchangeMapping mapping) {
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (JsonNode entry : OBJECT_MAPPER.readTree(body)) {
      String code = text(entry, "Code");
      String exchange = text(entry, "Exchange");
      if (code == null || exchange == null) {
        continue;
      }
      String isin = text(entry, "ISIN");
      String currency = text(entry, "Currency");
      String mic = mapping.deriveMic(queriedMic, exchange);
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, code + "." + exchange, code + "." + exchange,
          exchange, isin, isin == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, currency,
          currency == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, mic,
          mic == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, text(entry, "Name"),
          text(entry, "Type")));
    }
    return candidates;
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerSymbol() != null;
  }

  /** Builds the master data from the search hit; no request is sent. The ticker is the symbol without the code. */
  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
    Map<String, String> attributes = new LinkedHashMap<>();
    if (candidate.providerInstrumentType() != null) {
      attributes.put("type", candidate.providerInstrumentType());
    }
    if (candidate.providerExchangeCode() != null) {
      attributes.put("exchange", candidate.providerExchangeCode());
    }
    String symbol = candidate.providerSymbol();
    String ticker = symbol.substring(0, symbol.lastIndexOf('.'));
    return new SecurityBaseDataDto(PROVIDER_ID, symbol, candidate.isin(), candidate.currency(), candidate.mic(),
        candidate.name(), ticker, null, null, null, null, null, null, null, null, null, attributes);
  }

  private ExchangeMapping getExchangeMapping() {
    ExchangeMapping mapping = exchangeMapping;
    if (mapping == null) {
      mapping = new ExchangeMapping(micProviderMapRepository.findByIdProviderMic_IdProvider(PROVIDER_ID));
      exchangeMapping = mapping;
    }
    return mapping;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString().trim();
  }

  /** The {@code eodhd} rows of {@code mic_provider_map}: exchange code by MIC and MICs by exchange code. */
  static class ExchangeMapping {
    /** EODHD keeps every index under this collective code, e.g. {@code OMXH25.INDX}; an index is bound to no exchange. */
    static final String CODE_INDEX = "INDX";
    private final Map<String, String> codeByMic = new HashMap<>();
    private final Map<String, Set<String>> micsByCode = new HashMap<>();

    ExchangeMapping(List<MicProviderMap> rows) {
      for (MicProviderMap row : rows) {
        String mic = row.getIdProviderMic().getMic();
        codeByMic.put(mic, row.getCodeProvider());
        micsByCode.computeIfAbsent(row.getCodeProvider(), _ -> new HashSet<>()).add(mic);
      }
    }

    /**
     * Places an exchange code on a MIC.
     *
     * @param queriedMic   MIC of the query, may be null
     * @param exchangeCode EODHD exchange code of the entry
     * @return the MIC, the queried one for an index, or null when the code is unknown or names several exchanges none
     *         of which was queried
     */
    String deriveMic(String queriedMic, String exchangeCode) {
      if (queriedMic != null && (exchangeCode.equals(codeByMic.get(queriedMic)) || CODE_INDEX.equals(exchangeCode))) {
        return queriedMic;
      }
      Set<String> mics = micsByCode.get(exchangeCode);
      return mics != null && mics.size() == 1 ? mics.iterator().next() : null;
    }
  }
}
