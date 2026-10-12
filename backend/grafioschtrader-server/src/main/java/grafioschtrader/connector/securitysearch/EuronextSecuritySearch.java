package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.euronext.EuronextFeedConnector;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Finds a listing on a Euronext exchange (Paris, Amsterdam, Brussels, Lisbon, Dublin, Oslo) without a key. Euronext
 * keeps the master data of a listing in factsheet blocks that its product page loads separately,
 * {@code ajax/getFactsheetInfoBlock/{TYPE}/{ISIN}-{MIC}/{block}}; the type segment is ignored by the server, and an
 * ISIN not listed on the exchange gives an empty block.
 *
 * <p>
 * The search therefore asks the general block {@code fs_info_block} of the queried listing (type, e.g. {@code Bonds},
 * {@code Stock} or {@code ETP}/{@code Etf}, and the ISIN) and takes ticker and name from the quick search
 * ({@code instrumentSearch/searchJSON}), which lists shares and ETFs but no bonds. ISIN and MIC count as delivered;
 * Euronext names no trading currency in either, so the currency stays unknown.
 * </p>
 *
 * <p>
 * Master data: the first listing of {@code fs_tradingcharacteristics_block}. A bond adds the repayment date as maturity,
 * the first listing as first active day, the denomination, the interest frequency of {@code fs_couponinfo_block} and the
 * issuer country of {@code fs_issuerinfo_block}; its name is built from issuer, interest rate and term, since Euronext
 * offers only an abbreviation such as {@code OAT3%25MAY33}. The stock exchange hyperlink is the product page.
 * </p>
 */
@Component
public class EuronextSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "euronext";

  static final String TYPE_BOND = "Bonds";
  static final String TYPE_STOCK = "Stock";
  static final String TYPE_ETP = "ETP";

  private static final String DEFAULT_BASE_URL = "https://live.euronext.com/";
  private static final Pattern TABLE_ROW = Pattern
      .compile("<tr[^>]*>\\s*<t[dh][^>]*>(.*?)</t[dh]>\\s*<td[^>]*>(.*?)</td>", Pattern.DOTALL);
  private static final Pattern PARAGRAPH_ROW = Pattern.compile("<p[^>]*>(.*?)<strong>(.*?)</strong>",
      Pattern.DOTALL);
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  private static final Pattern SYMBOL = Pattern.compile("class='symbol'>([^<]*)<");
  private static final DateTimeFormatter BLOCK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  /** Longest name a security may have ({@code Security.name}). */
  private static final int MAX_NAME_LENGTH = 80;
  private static final Map<String, String> ISO2_BY_ISO3 = new HashMap<>();

  static {
    for (String iso2 : Locale.getISOCountries()) {
      try {
        ISO2_BY_ISO3.put(Locale.of("", iso2).getISO3Country(), iso2);
      } catch (MissingResourceException e) {
        // a country without three-letter code is simply not mapped
      }
    }
  }

  private final String baseUrl;

  @Autowired
  public EuronextSecuritySearch() {
    this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param baseUrl        base URL of Euronext live, ending with a slash; tests point it to a local server
   * @param requestTimeout timeout of a single request
   */
  EuronextSecuritySearch(String baseUrl, Duration requestTimeout) {
    // A bond needs the info block, the quick search and four master data blocks
    super(Bandwidth.builder().capacity(8).refillGreedy(8, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** Exchanges and instrument types are those of {@code EuronextFeedConnector}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(PROVIDER_ID);
  }

  /** The factsheet blocks are keyed by ISIN and exchange, so the query needs both. */
  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && query.mic() != null;
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    Map<String, String> info = parseBlock(fetchBlock(query.isin(), query.mic(), "fs_info_block"));
    if (!query.isin().equals(info.get("ISIN Code"))) {
      return List.of();
    }
    String search = sendExpectOk(HttpRequest.newBuilder(URI.create(
        baseUrl + "en/instrumentSearch/searchJSON?q=" + URLEncoder.encode(query.isin(), StandardCharsets.UTF_8))));
    return List.of(toCandidate(query.isin(), query.mic(), info, search));
  }

  /**
   * Builds the candidate of the queried listing from its general block and the quick search.
   *
   * @param isin   the queried ISIN, confirmed by the general block
   * @param mic    the queried MIC
   * @param info   rows of {@code fs_info_block}
   * @param search answer of the quick search; a bond is not in it
   * @return the candidate
   */
  static SecuritySearchCandidateDto toCandidate(String isin, String mic, Map<String, String> info, String search) {
    String ticker = null;
    String name = null;
    String operatingMic = operatingMic(mic);
    for (JsonNode entry : OBJECT_MAPPER.readTree(search)) {
      if (isin.equals(entry.path("isin").asString(null)) && operatingMic.equals(entry.path("mic").asString(null))) {
        Matcher symbol = SYMBOL.matcher(entry.path("label").asString(""));
        ticker = symbol.find() && !symbol.group(1).isBlank() ? symbol.group(1).trim() : null;
        name = entry.path("name").asString(null);
        break;
      }
    }
    String type = info.get("Type");
    String subType = info.get("Sub type");
    String instrumentType = type == null ? null : subType == null ? type : type + "/" + subType;
    return new SecuritySearchCandidateDto(PROVIDER_ID, ticker, isin + "-" + operatingMic, mic, isin,
        IdentityEvidence.RETURNED, null, IdentityEvidence.UNKNOWN, mic, IdentityEvidence.RETURNED, name,
        instrumentType);
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.isin() != null && candidate.mic() != null;
  }

  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    Map<String, String> blocks = new LinkedHashMap<>(
        parseBlock(fetchBlock(candidate.isin(), candidate.mic(), "fs_tradingcharacteristics_block")));
    if (isBond(candidate.providerInstrumentType())) {
      for (String block : List.of("fs_instrumentinfo_block", "fs_couponinfo_block", "fs_issuerinfo_block")) {
        blocks.putAll(parseBlock(fetchBlock(candidate.isin(), candidate.mic(), block)));
      }
    }
    return toBaseData(candidate, blocks);
  }

  /**
   * Converts the rows of the master data blocks.
   *
   * @param candidate the listing
   * @param rows      label and value of every row of the fetched blocks
   * @return the master data
   */
  static SecurityBaseDataDto toBaseData(SecuritySearchCandidateDto candidate, Map<String, String> rows) {
    Map<String, String> remaining = new LinkedHashMap<>(rows);
    LocalDate listedSince = date(remaining.remove("First listing"));
    boolean bond = isBond(candidate.providerInstrumentType());
    LocalDate activeFrom = null;
    LocalDate activeTo = null;
    Integer denomination = null;
    DistributionFrequency frequency = null;
    String issuerCountry = null;
    String name = candidate.name();
    if (bond) {
      activeFrom = listedSince != null ? listedSince : date(remaining.get("Issue Date"));
      LocalDate oldest = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
      if (activeFrom != null && activeFrom.isBefore(oldest)) {
        activeFrom = oldest;
      }
      activeTo = date(remaining.remove("Repayment date"));
      denomination = wholeAmount(remaining.remove("Denomination"));
      frequency = couponFrequency(remaining.remove("Interest rate frequency"));
      issuerCountry = ISO2_BY_ISO3.get(remaining.remove("Issuer country"));
      if (name == null) {
        name = bondName(remaining.get("Issuer name"), remaining.get("Interest Rate"), date(remaining.get("Issue Date")),
            activeTo);
      }
    }
    Map<String, String> attributes = new LinkedHashMap<>();
    if (candidate.providerInstrumentType() != null) {
      attributes.put("type", candidate.providerInstrumentType());
    }
    attributes.putAll(remaining);
    String link = DEFAULT_BASE_URL + "en/product/" + productPath(candidate.providerInstrumentType()) + "/"
        + candidate.providerInstrumentId();
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(), null, candidate.mic(),
        name, candidate.providerSymbol(), issuerCountry, activeFrom, activeTo, frequency, denomination, null, link,
        listedSince, null, attributes);
  }

  /**
   * Builds the name of a bond, e.g. {@code REPUBLIC OF FRANCE 3.0% 2023-2033}, shortened to the issuer and rate when it
   * would exceed a security name.
   */
  static String bondName(String issuer, String rate, LocalDate issueDate, LocalDate maturity) {
    if (issuer == null) {
      return null;
    }
    StringBuilder name = new StringBuilder(issuer);
    if (rate != null) {
      name.append(' ').append(rate);
    }
    String withTerm = issueDate != null && maturity != null
        ? name + " " + issueDate.getYear() + "-" + maturity.getYear()
        : name.toString();
    String result = withTerm.length() <= MAX_NAME_LENGTH ? withTerm : name.toString();
    return result.length() <= MAX_NAME_LENGTH ? result : result.substring(0, MAX_NAME_LENGTH);
  }

  /**
   * Maps the interest rate frequency of a bond.
   *
   * @param value e.g. {@code Annual}, {@code Semi-annual}, {@code Quarterly}, {@code Monthly}; may be null
   * @return the frequency, or null for a value without counterpart
   */
  static DistributionFrequency couponFrequency(String value) {
    if (value == null) {
      return null;
    }
    return switch (value.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "")) {
    case "annual", "yearly" -> DistributionFrequency.DF_YEAR;
    case "semiannual", "biannual" -> DistributionFrequency.DF_SEMI_ANNUAL;
    case "quarterly" -> DistributionFrequency.DF_QUARTERLY;
    case "monthly" -> DistributionFrequency.DF_MONTHLY;
    default -> null;
    };
  }

  /**
   * Reads label and value of every row of a factsheet block: table rows, and the paragraphs of the issuer block
   * ({@code Issuer name : <strong>…</strong>}). A trailing colon of the label is removed.
   *
   * @param html the block
   * @return label and value in block order; empty for an empty block
   */
  static Map<String, String> parseBlock(String html) {
    Map<String, String> rows = new LinkedHashMap<>();
    for (Pattern pattern : List.of(TABLE_ROW, PARAGRAPH_ROW)) {
      Matcher row = pattern.matcher(html);
      while (row.find()) {
        String label = cellText(row.group(1)).replaceFirst("\\s*:$", "");
        String value = cellText(row.group(2));
        if (!label.isEmpty() && !value.isEmpty()) {
          rows.putIfAbsent(label, value);
        }
      }
    }
    return rows;
  }

  static boolean isBond(String instrumentType) {
    return instrumentType != null && instrumentType.startsWith(TYPE_BOND);
  }

  private String fetchBlock(String isin, String mic, String block) throws Exception {
    return sendExpectOk(HttpRequest.newBuilder(URI.create(baseUrl + "en/ajax/getFactsheetInfoBlock/BONDS/"
        + URLEncoder.encode(isin, StandardCharsets.UTF_8) + "-" + operatingMic(mic) + "/" + block)));
  }

  /** Euronext Dublin is addressed by its operating MIC XDUB, the other exchanges by their GT MIC. */
  private static String operatingMic(String mic) {
    return EuronextFeedConnector.mappingMicOperation.getOrDefault(mic, mic);
  }

  private static String productPath(String instrumentType) {
    if (instrumentType == null) {
      return "equities";
    }
    if (isBond(instrumentType)) {
      return "bonds";
    }
    return instrumentType.startsWith(TYPE_ETP) ? "etfs" : "equities";
  }

  private static String cellText(String html) {
    return HtmlUtils.htmlUnescape(HTML_TAG.matcher(html).replaceAll(" ")).replaceAll("\\s+", " ").strip();
  }

  private static LocalDate date(String value) {
    try {
      return value == null ? null : LocalDate.parse(value, BLOCK_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** A whole amount such as {@code 1} or {@code 1,000}; null when absent, fractional or below one. */
  private static Integer wholeAmount(String value) {
    if (value == null) {
      return null;
    }
    try {
      double amount = Double.parseDouble(value.replace(",", ""));
      return amount >= 1 && amount == Math.rint(amount) && amount <= Integer.MAX_VALUE ? (int) amount : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
