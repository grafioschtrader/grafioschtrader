package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Supplies the stock exchange hyperlink of a listing on the Bolsa Institucional de Valores (BIVA). The page of an
 * issuer at BIVA is keyed by an internal id that neither ticker nor ISIN reveal; the BIVA web site resolves it itself
 * with {@code emisoras/empresas/isin/{ISIN}}, which answers without a key with id, issuer code ({@code clave}), legal
 * name, listing date and sector, and with HTTP 404 for an unknown ISIN. The answer names the issuer, not a series, and
 * carries no currency.
 *
 * <p>
 * The listing has no ticker: the issuer code ({@code GMEXICO}) lacks the series that the data providers append
 * ({@code GMEXICOB}), so the listing joins the row of the same exchange that Yahoo or EODHD found and only fills what
 * that row leaves open, in particular the hyperlink. BIVA is asked for a BIVA query only.
 * </p>
 */
@Component
public class BivaSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "biva";
  public static final String MIC_BIVA = "BIVA";

  private static final String DEFAULT_BASE_URL = "https://www.biva.mx/";
  private static final ZoneId MEXICO_CITY = ZoneId.of("America/Mexico_City");

  private final String baseUrl;

  public BivaSecuritySearch() {
    this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param baseUrl        base URL of the BIVA web site, ending with a slash; tests point it to a local server
   * @param requestTimeout timeout of a single request
   */
  BivaSecuritySearch(String baseUrl, Duration requestTimeout) {
    super(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && MIC_BIVA.equals(query.mic());
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(
        baseUrl + "emisoras/empresas/isin/" + URLEncoder.encode(query.isin(), StandardCharsets.UTF_8))));
    return response.statusCode() == 200 ? parse(response.body(), query.isin()) : List.of();
  }

  /**
   * Converts the issuer answer into the one listing on BIVA. The ISIN counts as filtered, since the endpoint is keyed
   * by it; the issuer id travels as provider instrument id. The listing carries no symbol, because the lookup would
   * take it as ticker when the master data request fails.
   *
   * @param body the answer of {@code emisoras/empresas/isin/{ISIN}}
   * @param isin the queried ISIN
   * @return the listing, or nothing when the answer names no issuer
   */
  static List<SecuritySearchCandidateDto> parse(String body, String isin) {
    JsonNode issuer = OBJECT_MAPPER.readTree(body);
    String id = text(issuer, "id");
    String clave = text(issuer, "clave");
    if (id == null || clave == null) {
      return List.of();
    }
    return List.of(new SecuritySearchCandidateDto(PROVIDER_ID, null, id, text(issuer, "bolsa"), isin,
        IdentityEvidence.FILTERED, null, IdentityEvidence.UNKNOWN, MIC_BIVA, IdentityEvidence.FILTERED,
        text(issuer, "razonSocial"), null));
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerInstrumentId() != null;
  }

  /** The master data is the search answer, requested once more so that the listing date and sector are at hand. */
  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    return toBaseData(candidate, OBJECT_MAPPER.readTree(sendExpectOk(HttpRequest.newBuilder(URI.create(
        baseUrl + "emisoras/empresas/isin/" + URLEncoder.encode(candidate.isin(), StandardCharsets.UTF_8))))));
  }

  /**
   * Builds the master data: legal name, BIVA listing date as {@code listedSince} and the issuer page as stock exchange
   * hyperlink, with the query parameters the BIVA site itself uses. The issuer code stays a raw attribute, not the
   * ticker.
   *
   * @param candidate the listing
   * @param issuer    the issuer answer
   * @return the master data
   */
  static SecurityBaseDataDto toBaseData(SecuritySearchCandidateDto candidate, JsonNode issuer) {
    Map<String, String> attributes = new LinkedHashMap<>();
    String clave = text(issuer, "clave");
    putIfPresent(attributes, "clave", clave);
    putIfPresent(attributes, "bolsa", candidate.providerExchangeCode());
    putIfPresent(attributes, "sector", text(issuer.path("sector"), "nombre"));
    putIfPresent(attributes, "ramo", text(issuer.path("ramo"), "nombre"));
    putIfPresent(attributes, "estatus", text(issuer, "estatus"));
    JsonNode listed = issuer.path("fechaListado");
    LocalDate listedSince = listed.isNumber()
        ? Instant.ofEpochMilli(listed.asLong()).atZone(MEXICO_CITY).toLocalDate()
        : null;
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(), null, MIC_BIVA,
        candidate.name(), null, null, null, null, null, null, null,
        clave == null ? null : issuerPage(candidate.providerInstrumentId(), clave), listedSince, null, attributes);
  }

  /** The issuer page of the BIVA site, e.g. for Grupo México id 1693 and issuer code GMEXICO. */
  static String issuerPage(String id, String clave) {
    return "https://www.biva.mx/empresas/emisoras_inscritas/emisoras_inscritas?instrumento=null&tipo_valor=null"
        + "&sector=null&sub_sector=null&ramo=null&sub_ramo=null&emisora_id=" + id + "&clave="
        + URLEncoder.encode(clave, StandardCharsets.UTF_8) + "&canceladas=null&biva=null&page=1";
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
