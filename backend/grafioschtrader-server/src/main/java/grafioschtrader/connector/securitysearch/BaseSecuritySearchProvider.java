package grafioschtrader.connector.securitysearch;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.SecuritySearchStatus;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Common frame of all provider adapters. It gives every search the same bounds and the same failure reporting, so an
 * adapter only fetches and converts listings:
 * <ul>
 * <li><b>Request budget:</b> each provider owns one token bucket. A request waits at most {@code maxTokenWait} for a
 * token and otherwise ends as {@link SecuritySearchStatus#RATE_LIMITED} instead of blocking without bound.</li>
 * <li><b>Timeouts:</b> one shared HTTP client with a connect timeout, and a request timeout on every request.</li>
 * <li><b>Status mapping:</b> HTTP 429 becomes {@code RATE_LIMITED}, 401/403 {@code NOT_ENTITLED}, 5xx
 * {@code PROVIDER_ERROR}; a timeout {@code TIMEOUT}; any other exception, including a response that cannot be parsed,
 * {@code PROVIDER_ERROR}.</li>
 * <li><b>Identity rules:</b> the fetched listings pass through {@link SearchCandidateMatcher}.</li>
 * </ul>
 */
public abstract class BaseSecuritySearchProvider implements ISecuritySearchProvider {

  /** Request timeout used by the adapters unless a test sets a shorter one. */
  protected static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

  /** Longest wait for a token of the request budget before the search reports {@code RATE_LIMITED}. */
  protected static final Duration DEFAULT_MAX_TOKEN_WAIT = Duration.ofSeconds(10);

  protected static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
      .followRedirects(Redirect.NORMAL).build();

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final Bucket bucket;
  private final Duration requestTimeout;
  private final Duration maxTokenWait;

  /**
   * @param bandwidth      request budget of the provider
   * @param requestTimeout timeout of a single HTTP request
   * @param maxTokenWait   longest wait for a token of the request budget
   */
  protected BaseSecuritySearchProvider(Bandwidth bandwidth, Duration requestTimeout, Duration maxTokenWait) {
    this.bucket = Bucket.builder().addLimit(bandwidth).build();
    this.requestTimeout = requestTimeout;
    this.maxTokenWait = maxTokenWait;
  }

  @Override
  public final SecuritySearchResult search(SecuritySearchQuery query) {
    if (!supports(query)) {
      return SecuritySearchResult.failure(getProviderId(), SecuritySearchStatus.UNSUPPORTED_QUERY,
          "Provider cannot answer this query");
    }
    try {
      return SearchCandidateMatcher.match(getProviderId(), query, fetchCandidates(query));
    } catch (SearchFailureException e) {
      return SecuritySearchResult.failure(getProviderId(), e.getStatus(), e.getMessage());
    } catch (HttpTimeoutException e) {
      return SecuritySearchResult.failure(getProviderId(), SecuritySearchStatus.TIMEOUT, e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return SecuritySearchResult.failure(getProviderId(), SecuritySearchStatus.PROVIDER_ERROR, "Interrupted");
    } catch (Exception e) {
      log.warn("Security search of provider {} failed for ISIN {}: {}", getProviderId(), query.isin(), e.toString());
      return SecuritySearchResult.failure(getProviderId(), SecuritySearchStatus.PROVIDER_ERROR, e.toString());
    }
  }

  /**
   * Asks the provider and converts its answer into candidates. Identity evidence must be set exactly as the provider
   * established it; the conflict check and ordering follow in {@link SearchCandidateMatcher}.
   *
   * @param query the query, already accepted by {@link #supports(SecuritySearchQuery)}
   * @return the listings delivered by the provider, possibly empty
   * @throws Exception any failure; it is converted into a status by {@link #search(SecuritySearchQuery)}
   */
  protected abstract List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception;

  /**
   * Sends a request within the request budget and the request timeout. Status codes that say nothing about the
   * instrument are turned into a {@link SearchFailureException}; every other response, including 4xx codes a provider
   * uses for "not found", is returned to the adapter.
   *
   * @param requestBuilder the request without timeout and user agent
   * @return the response with a status code below 500 other than 401, 403 and 429
   * @throws IOException          on a transport failure, including {@link HttpTimeoutException}
   * @throws InterruptedException when the thread is interrupted while waiting for a token or the response
   */
  protected HttpResponse<String> send(HttpRequest.Builder requestBuilder) throws IOException, InterruptedException {
    if (!bucket.asBlocking().tryConsume(1, maxTokenWait)) {
      throw new SearchFailureException(SecuritySearchStatus.RATE_LIMITED, "Local request budget exhausted");
    }
    HttpRequest request = requestBuilder.timeout(requestTimeout)
        .header("User-Agent", FeedConnectorHelper.getHttpAgentAsString(true)).build();
    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    int statusCode = response.statusCode();
    if (statusCode == 429) {
      throw new SearchFailureException(SecuritySearchStatus.RATE_LIMITED, "HTTP 429");
    } else if (statusCode == 401 || statusCode == 403) {
      throw new SearchFailureException(SecuritySearchStatus.NOT_ENTITLED, "HTTP " + statusCode);
    } else if (statusCode >= 500) {
      throw new SearchFailureException(SecuritySearchStatus.PROVIDER_ERROR, "HTTP " + statusCode);
    }
    return response;
  }

  /**
   * Like {@link #send(HttpRequest.Builder)}, but accepts only HTTP 200.
   *
   * @param requestBuilder the request without timeout and user agent
   * @return the response body
   * @throws IOException          on a transport failure
   * @throws InterruptedException when interrupted
   */
  protected String sendExpectOk(HttpRequest.Builder requestBuilder) throws IOException, InterruptedException {
    HttpResponse<String> response = send(requestBuilder);
    if (response.statusCode() != 200) {
      throw new SearchFailureException(SecuritySearchStatus.PROVIDER_ERROR, "HTTP " + response.statusCode());
    }
    return response.body();
  }
}
