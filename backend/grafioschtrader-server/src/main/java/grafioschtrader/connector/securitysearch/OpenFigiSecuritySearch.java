package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.IFeedConnector.AssetclassCategory;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Maps an ISIN to listings through the OpenFIGI mapping API, used without a key (25 requests per minute, up to ten jobs
 * per request). The response repeats neither ISIN, MIC nor currency; they count as {@link IdentityEvidence#FILTERED}
 * because they are sent as exact filters of the mapping job. The FIGI is reported as provider instrument id, the
 * returned ticker as provider symbol - neither is the symbol of another provider nor a price-feed URL extension.
 *
 * <p>
 * OpenFIGI knows the NASDAQ listings by their segment MICs (XNGS, XNMS, XNCM), not by the operating MIC XNAS that GT
 * uses (see {@link MarketSegmentMics}). A query for an operating MIC therefore sends one job per segment MIC in the
 * same request. A further job without MIC and currency filter tells "ISIN unknown to OpenFIGI" apart from "no listing
 * on this exchange", which is reported in the detail of a {@link SecuritySearchStatus#NO_MATCH}.
 * </p>
 *
 * <p>
 * Master data needs no further request: it is the search hit itself - the Bloomberg ticker, which at most European
 * exchanges equals the exchange ticker, the name, the FIGI and {@code securityType2}. OpenFIGI is an identification
 * service: it delivers no prices, dates or links.
 * </p>
 */
@Component
public class OpenFigiSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "openfigi";

  private static final String DEFAULT_BASE_URL = "https://api.openfigi.com/";
  private static final String ID_TYPE_ISIN = "ID_ISIN";
  private static final Set<AssetclassCategory> COVERED_CATEGORIES = EnumSet.of(AssetclassCategory.EQUITIES,
      AssetclassCategory.FIXED_INCOME, AssetclassCategory.ETF, AssetclassCategory.MUTUAL_FUND);

  private final String baseUrl;

  @Autowired
  public OpenFigiSecuritySearch() {
    this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param baseUrl        base URL of the OpenFIGI API, ending with a slash
   * @param requestTimeout timeout of a single request
   */
  OpenFigiSecuritySearch(String baseUrl, Duration requestTimeout) {
    super(Bandwidth.builder().capacity(25).refillGreedy(25, Duration.ofMinutes(1)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin();
  }

  /**
   * OpenFIGI has no price connector. It identifies shares, bonds and funds; an index, a currency pair or a CFD is not
   * worth a request from its small budget.
   */
  @Override
  public boolean covers(AssetclassType categoryType, SpecialInvestmentInstruments sii) {
    return COVERED_CATEGORIES.stream().anyMatch(c -> c.matches(categoryType, sii));
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId());
  }

  /** Builds the master data from the search hit; no request is sent. */
  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
    Map<String, String> attributes = new LinkedHashMap<>();
    if (candidate.providerInstrumentType() != null) {
      attributes.put("securityType2", candidate.providerInstrumentType());
    }
    if (candidate.providerExchangeCode() != null) {
      attributes.put("exchCode", candidate.providerExchangeCode());
    }
    if (candidate.providerInstrumentId() != null) {
      attributes.put("figi", candidate.providerInstrumentId());
    }
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(),
        candidate.currency(), candidate.mic(), candidate.name(), candidate.providerSymbol(), null, null, null, null,
        null, null, null, null, null, attributes);
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    List<Map<String, String>> jobs = createJobs(query);
    String body = sendExpectOk(
        HttpRequest.newBuilder(URI.create(baseUrl + "v3/mapping")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(jobs))));
    return parse(query, body);
  }

  /**
   * Creates the mapping jobs: with a MIC one job per MIC variant followed by an unfiltered diagnosis job, without a MIC
   * a single job. The currency filter is added to the filtered jobs when the query has a currency.
   *
   * @param query the search query
   * @return the jobs in the order their answers are expected
   */
  static List<Map<String, String>> createJobs(SecuritySearchQuery query) {
    List<Map<String, String>> jobs = new ArrayList<>();
    if (query.mic() == null) {
      jobs.add(job(query.isin(), null, query.currency()));
    } else {
      micVariants(query.mic()).forEach(mic -> jobs.add(job(query.isin(), mic, query.currency())));
      jobs.add(job(query.isin(), null, null));
    }
    return jobs;
  }

  /**
   * Converts the answers of the jobs created by {@link #createJobs(SecuritySearchQuery)}.
   *
   * @param query the search query
   * @param body  the JSON array with one answer per job
   * @return the candidates of the filtered jobs, or of the single job when the query has no MIC
   * @throws SearchFailureException with {@code NO_MATCH} when only the diagnosis job found the ISIN, or with
   *                                {@code PROVIDER_ERROR} when a job reports an error other than an invalid ISIN
   */
  static List<SecuritySearchCandidateDto> parse(SecuritySearchQuery query, String body) {
    List<JsonNode> answers = new ArrayList<>();
    OBJECT_MAPPER.readTree(body).forEach(answers::add);
    checkErrors(answers);
    if (query.mic() == null) {
      return answers.isEmpty() ? List.of() : toCandidates(query, answers.getFirst(), false);
    }
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    answers.stream().limit(answers.size() - 1L).forEach(a -> candidates.addAll(toCandidates(query, a, true)));
    if (candidates.isEmpty() && !answers.isEmpty() && answers.getLast().path("data").size() > 0) {
      throw new SearchFailureException(SecuritySearchStatus.NO_MATCH, "ISIN known to OpenFIGI, but no listing on "
          + query.mic() + (query.currency() == null ? "" : " in " + query.currency()));
    }
    return candidates;
  }

  private static void checkErrors(List<JsonNode> answers) {
    for (JsonNode answer : answers) {
      String error = answer.path("error").asString("");
      if (!error.isEmpty() && !error.startsWith("Invalid idValue")) {
        throw new SearchFailureException(SecuritySearchStatus.PROVIDER_ERROR, error);
      }
    }
  }

  private static List<SecuritySearchCandidateDto> toCandidates(SecuritySearchQuery query, JsonNode answer,
      boolean micFiltered) {
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (JsonNode data : answer.path("data")) {
      boolean currencyFiltered = query.currency() != null;
      String type = data.path("securityType2").asString("");
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, data.path("ticker").asString(null),
          data.path("figi").asString(null), data.path("exchCode").asString(null), query.isin(),
          IdentityEvidence.FILTERED, currencyFiltered ? query.currency() : null,
          currencyFiltered ? IdentityEvidence.FILTERED : IdentityEvidence.UNKNOWN, micFiltered ? query.mic() : null,
          micFiltered ? IdentityEvidence.FILTERED : IdentityEvidence.UNKNOWN, data.path("name").asString(null),
          type.isEmpty() ? data.path("securityType").asString(null) : type));
    }
    return candidates;
  }

  private static List<String> micVariants(String mic) {
    List<String> variants = new ArrayList<>();
    variants.add(mic);
    variants.addAll(MarketSegmentMics.segmentsOf(mic));
    return variants;
  }

  private static Map<String, String> job(String isin, String mic, String currency) {
    Map<String, String> job = new LinkedHashMap<>();
    job.put("idType", ID_TYPE_ISIN);
    job.put("idValue", isin);
    if (mic != null) {
      job.put("micCode", mic);
    }
    if (currency != null) {
      job.put("currency", currency);
    }
    return job;
  }
}
