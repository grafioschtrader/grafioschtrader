package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;

/**
 * Finds listings at Boursorama without a key, through the search of its site ({@code recherche/ajax?query={ISIN}}),
 * which answers with HTML. Every hit is a link {@code …/cours/{symbol}/} followed by name, type ({@code Action},
 * {@code ETF}, {@code BND}), ticker, trading venue and last price with its currency.
 *
 * <p>
 * The Boursorama symbol is the URL extension of the Boursorama connector and starts with a market prefix: {@code 1rP}
 * Euronext Paris, {@code 1rA} Amsterdam, {@code 1rL} Lisbon, {@code 1z} Xetra, {@code 1u} London (with suffix
 * {@code .L}), {@code 2a} SIX, {@code 1g} Milan, {@code 3t} Toronto; a US listing has none. The {@code boursorama} rows
 * of {@code mic_provider_map} hold these prefixes as codes - {@code US} for the unprefixed symbols, whose exchange the
 * venue text names (NASDAQ, NYSE). A hit on any other market or a derivative ({@code Produit de bourse}) is skipped.
 * </p>
 *
 * <p>
 * The search is a full-text search: a hit repeats neither the ISIN nor its exchange code, so the ISIN stays unknown,
 * while the MIC counts as delivered through the prefix. The currency is the one of the last price ({@code €},
 * {@code $}, {@code CHF}, {@code GBX} …); a bond quoted in percent has none. Master data is the hit itself.
 * </p>
 */
@Component
public class BoursoramaSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "boursorama";
  /** Code of the unprefixed US symbols in {@code mic_provider_map}. */
  public static final String CODE_US = "US";

  static final String TYPE_SHARE = "Action";
  static final String TYPE_ETF = "ETF";
  static final String TYPE_BOND = "BND";

  private static final String DEFAULT_BASE_URL = "https://www.boursorama.com/";
  private static final Pattern HIT = Pattern
      .compile("<a class=\"search__list-link[^\"]*\" href=\"([^\"]*/cours/([^/\"]+)/?)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
  private static final Pattern SPAN = Pattern.compile("<span class=\"([^\"]*)\"[^>]*>(.*?)</span>", Pattern.DOTALL);
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  private static final Map<String, String> US_MIC_BY_VENUE = Map.of("NASDAQ", "XNAS", "NYSE", "XNYS");
  private static final Map<String, String> CURRENCY_BY_SIGN = Map.of("€", "EUR", "$", "USD", "£", "GBP");

  private final MicProviderMapRepository micProviderMapRepository;
  private final String baseUrl;
  private volatile List<MicProviderMap> rows;

  @Autowired
  public BoursoramaSecuritySearch(MicProviderMapRepository micProviderMapRepository) {
    this(micProviderMapRepository, DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param micProviderMapRepository source of the Boursorama market prefixes; read once
   * @param baseUrl                  base URL of Boursorama, ending with a slash; tests point it to a local server
   * @param requestTimeout           timeout of a single request
   */
  BoursoramaSecuritySearch(MicProviderMapRepository micProviderMapRepository, String baseUrl,
      Duration requestTimeout) {
    super(Bandwidth.builder().capacity(4).refillGreedy(4, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.micProviderMapRepository = micProviderMapRepository;
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** Exchanges and instrument types are those of {@code BoursoramaFeedConnector}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(PROVIDER_ID);
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin();
  }

  /** The search answers only the requests of its own page script, which sends {@code X-Requested-With}. */
  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    String body = sendExpectOk(HttpRequest
        .newBuilder(URI.create(
            baseUrl + "recherche/ajax?query=" + URLEncoder.encode(query.isin(), StandardCharsets.UTF_8)))
        .header("X-Requested-With", "XMLHttpRequest"));
    return parse(body, getRows());
  }

  /**
   * Converts the search answer into one candidate per hit on a GT stock exchange.
   *
   * @param html the search answer
   * @param rows the {@code boursorama} rows of {@code mic_provider_map}
   * @return the candidates, each symbol once
   */
  static List<SecuritySearchCandidateDto> parse(String html, List<MicProviderMap> rows) {
    List<MicProviderMap> byPrefixLength = rows.stream()
        .sorted(Comparator.comparingInt((MicProviderMap r) -> r.getCodeProvider().length()).reversed()).toList();
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Matcher hit = HIT.matcher(html);
    while (hit.find()) {
      String symbol = HtmlUtils.htmlUnescape(hit.group(2));
      if (!seen.add(symbol)) {
        continue;
      }
      List<String> texts = texts(hit.group(3));
      // name, type, ticker, venue, price; a derivative has a type of its own and is skipped
      if (texts.size() < 4 || !isListedType(texts.get(1))) {
        continue;
      }
      String mic = mic(symbol, texts.get(3), byPrefixLength);
      if (mic == null) {
        continue;
      }
      String currency = texts.size() > 4 ? currency(texts.get(4)) : null;
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, texts.get(2), symbol, texts.get(3), null,
          IdentityEvidence.UNKNOWN, currency, currency == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED,
          mic, IdentityEvidence.RETURNED, texts.get(0), texts.get(1)));
    }
    return candidates;
  }

  /** The texts of the spans of a hit, without the bullets between them. */
  private static List<String> texts(String body) {
    List<String> texts = new ArrayList<>();
    Matcher span = SPAN.matcher(body);
    while (span.find()) {
      if (!span.group(1).contains("bullet")) {
        texts.add(HtmlUtils.htmlUnescape(HTML_TAG.matcher(span.group(2)).replaceAll(" ")).replaceAll("\\s+", " ")
            .strip());
      }
    }
    return texts;
  }

  private static boolean isListedType(String type) {
    return TYPE_SHARE.equals(type) || TYPE_ETF.equals(type) || TYPE_BOND.equals(type);
  }

  /**
   * Places a symbol on a GT stock exchange by its market prefix; an unprefixed US symbol by the venue text.
   *
   * @param symbol          the Boursorama symbol, e.g. {@code 1zSAP} or {@code AAPL}
   * @param venue           the trading venue of the hit, e.g. {@code XETRA} or {@code NASDAQ}
   * @param byPrefixLength  the market rows, longest prefix first
   * @return the MIC, or null when the market has no GT stock exchange
   */
  static String mic(String symbol, String venue, List<MicProviderMap> byPrefixLength) {
    if (!symbol.isEmpty() && Character.isUpperCase(symbol.charAt(0)) && symbol.indexOf('-') < 0) {
      String usMic = US_MIC_BY_VENUE.get(venue);
      return usMic != null && byPrefixLength.stream().anyMatch(
          r -> CODE_US.equals(r.getCodeProvider()) && usMic.equals(r.getIdProviderMic().getMic())) ? usMic : null;
    }
    return byPrefixLength.stream().filter(r -> !CODE_US.equals(r.getCodeProvider()))
        .filter(r -> symbol.startsWith(r.getCodeProvider())).map(r -> r.getIdProviderMic().getMic()).findFirst()
        .orElse(null);
  }

  /**
   * The currency of a last price such as {@code 78,530 €}, {@code 336,64 $} or {@code 11 141,00 GBX}.
   *
   * @param price the price text
   * @return the ISO code, or null for a price in percent or without currency
   */
  static String currency(String price) {
    if (price == null || price.isBlank()) {
      return null;
    }
    String[] parts = price.strip().split("\\s+");
    String unit = parts[parts.length - 1];
    String bySign = CURRENCY_BY_SIGN.get(unit);
    if (bySign != null) {
      return bySign;
    }
    return unit.matches("[A-Z]{3}") ? unit : null;
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerInstrumentId() != null;
  }

  /** Builds the master data from the search hit; no request is sent. */
  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("type", candidate.providerInstrumentType());
    attributes.put("venue", candidate.providerExchangeCode());
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(),
        candidate.currency(), candidate.mic(), candidate.name(), candidate.providerSymbol(), null, null, null, null,
        null, null, null, null, null, attributes);
  }

  private List<MicProviderMap> getRows() {
    List<MicProviderMap> mapping = rows;
    if (mapping == null) {
      mapping = List.copyOf(micProviderMapRepository.findByIdProviderMic_IdProvider(PROVIDER_ID));
      rows = mapping;
    }
    return mapping;
  }
}
