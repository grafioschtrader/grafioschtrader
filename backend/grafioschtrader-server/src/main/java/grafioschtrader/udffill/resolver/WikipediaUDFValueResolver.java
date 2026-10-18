package grafioschtrader.udffill.resolver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Security;
import grafioschtrader.udffill.http.UDFFillHttp;
import grafioschtrader.udffill.http.UDFFillHttp.FetchResult;
import grafioschtrader.udffill.http.UDFFillHttpException;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFoundReason;
import grafioschtrader.udffill.resolver.ResolveOutcome.Retryable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves the Wikipedia article of the Wikidata item that carries the ISIN of the security as statement P946 — for a
 * share usually the article of the issuer, for a fund the fund or its family when such an item exists. The argument is
 * a list of Wikipedia language codes in order of preference; the first language with an article wins.
 *
 * Two requests per security go out to the Action API of Wikidata: a search for the statement and the entities found.
 * The search index is eventually consistent and can return near matches, so only entities whose P946 claims contain the
 * ISIN exactly are kept. Only the ISIN leaves the instance. Requests are separated by at least 250 ms; since the
 * background worker runs one task at a time, they are serial for the whole instance.
 */
@Component
public class WikipediaUDFValueResolver implements IUDFValueResolver {

  public static final String RESOLVER_ID = "WIKIPEDIA";
  public static final String DEFAULT_ARGUMENT = "de,en";
  /** NLS key of the message for an invalid language list. */
  public static final String INVALID_ARGUMENT_KEY = "gt.udf.fill.resolver.wikipedia.languages";

  static final String API = "https://www.wikidata.org/w/api.php";
  private static final int MAX_LANGUAGES = 5;
  private static final int MAX_SEARCH_HITS = 10;
  private static final long MIN_REQUEST_DISTANCE_MILLIS = 250;
  private static final long MAX_RETRY_AFTER_SECONDS = 60;
  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[a-z]+)?");
  private static final String ISIN_PROPERTY = "P946";

  /** Waits between requests; replaced in tests. */
  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  private final UDFFillHttp udfFillHttp;
  private final Sleeper sleeper;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private long lastRequestNanos;

  @Autowired
  public WikipediaUDFValueResolver(UDFFillHttp udfFillHttp) {
    this(udfFillHttp, Thread::sleep);
  }

  WikipediaUDFValueResolver(UDFFillHttp udfFillHttp, Sleeper sleeper) {
    this.udfFillHttp = udfFillHttp;
    this.sleeper = sleeper;
  }

  @Override
  public String getResolverId() {
    return RESOLVER_ID;
  }

  @Override
  public Set<UDFDataType> getSupportedDataTypes() {
    return Set.of(UDFDataType.UDF_URLString);
  }

  @Override
  public String getDefaultArgument() {
    return DEFAULT_ARGUMENT;
  }

  /**
   * Normalises a comma-separated list of Wikipedia language codes ({@code [a-z]{2,3}}, optionally followed by {@code -}
   * and letters) to lower case without duplicates. At most 5 codes; blank means the default.
   */
  @Override
  public String normalizeArgument(String argument) {
    if (argument == null || argument.isBlank()) {
      return DEFAULT_ARGUMENT;
    }
    Set<String> languages = new LinkedHashSet<>();
    for (String part : argument.split(",", -1)) {
      String language = part.strip().toLowerCase(Locale.ROOT);
      if (!LANGUAGE.matcher(language).matches()) {
        throw new IllegalArgumentException(INVALID_ARGUMENT_KEY);
      }
      languages.add(language);
    }
    if (languages.size() > MAX_LANGUAGES) {
      throw new IllegalArgumentException(INVALID_ARGUMENT_KEY);
    }
    return String.join(",", languages);
  }

  @Override
  public ResolveOutcome resolve(Security security, String argument) {
    String isin = security.getIsin();
    if (isin == null || isin.isBlank()) {
      return new NotFound(NotFoundReason.NO_ISIN);
    }
    try {
      JsonNode search = request(API + "?action=query&list=search&srsearch="
          + URLEncoder.encode("haswbstatement:" + ISIN_PROPERTY + "=" + isin, StandardCharsets.UTF_8)
          + "&srnamespace=0&srlimit=" + MAX_SEARCH_HITS + "&format=json&maxlag=5");
      List<String> ids = searchHitIds(search);
      if (ids.isEmpty()) {
        return new NotFound(NotFoundReason.ZERO_HITS);
      }
      JsonNode entities = request(API + "?action=wbgetentities&ids=" + String.join("%7C", ids)
          + "&props=claims%7Csitelinks%2Furls&format=json&maxlag=5").path("entities");
      if (!entities.isObject()) {
        return new Retryable("Wikidata entities answer without entities");
      }
      return pickArticle(entities, ids, isin, argument);
    } catch (WikidataRetryException e) {
      return new Retryable(e.getMessage());
    }
  }

  private static List<String> searchHitIds(JsonNode search) throws WikidataRetryException {
    JsonNode hits = search.path("query").path("search");
    if (!hits.isArray()) {
      throw new WikidataRetryException("Wikidata search answer without search result");
    }
    List<String> ids = new ArrayList<>();
    for (JsonNode hit : hits) {
      if (hit.path("title").isString()) {
        ids.add(hit.path("title").asString());
      }
    }
    return ids;
  }

  private static ResolveOutcome pickArticle(JsonNode entities, List<String> ids, String isin, String argument) {
    List<JsonNode> carrying = new ArrayList<>();
    for (String id : ids) {
      JsonNode entity = entities.path(id);
      if (carriesIsin(entity, isin)) {
        carrying.add(entity);
      }
    }
    if (carrying.isEmpty()) {
      return new NotFound(NotFoundReason.ZERO_HITS);
    }
    if (carrying.size() > 1) {
      return new NotFound(NotFoundReason.AMBIGUOUS);
    }
    JsonNode entity = carrying.getFirst();
    for (String language : argument.split(",")) {
      JsonNode url = entity.path("sitelinks").path(language.replace('-', '_') + "wiki").path("url");
      if (url.isString() && !url.asString().isBlank()) {
        return new Found(url.asString(), "https://www.wikidata.org/wiki/" + entity.path("id").asString());
      }
    }
    return new NotFound(NotFoundReason.NO_SITELINK);
  }

  private static boolean carriesIsin(JsonNode entity, String isin) {
    for (JsonNode claim : entity.path("claims").path(ISIN_PROPERTY)) {
      JsonNode value = claim.path("mainsnak").path("datavalue").path("value");
      if (value.isString() && value.asString().equals(isin)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Sends one request to the Action API. Wikibase reports {@code maxlag} and other errors with status 200 and an
   * {@code error} object; those, a status outside 2xx, a body that is not JSON and every transport failure are
   * retryable. On 429 the resolver waits for Retry-After, at most 60 s, and then gives up for this run.
   */
  private synchronized JsonNode request(String url) throws WikidataRetryException {
    keepDistance();
    FetchResult result;
    try {
      result = udfFillHttp.fetch(url);
    } catch (UDFFillHttpException e) {
      throw new WikidataRetryException(e.getMessage());
    } finally {
      lastRequestNanos = System.nanoTime();
    }
    if (result.status() == 429) {
      waitRetryAfter(result.retryAfter());
      throw new WikidataRetryException("Wikidata answered 429 Too Many Requests");
    }
    if (!result.isSuccess()) {
      throw new WikidataRetryException("Wikidata answered status " + result.status());
    }
    JsonNode json;
    try {
      json = objectMapper.readTree(result.body());
    } catch (JacksonException e) {
      throw new WikidataRetryException("Wikidata answer is not JSON");
    }
    if (json.has("error")) {
      throw new WikidataRetryException("Wikidata error " + json.path("error").path("code").asString(""));
    }
    return json;
  }

  private void keepDistance() throws WikidataRetryException {
    long waitMillis = MIN_REQUEST_DISTANCE_MILLIS - (System.nanoTime() - lastRequestNanos) / 1_000_000;
    if (lastRequestNanos != 0 && waitMillis > 0) {
      sleepInterruptibly(waitMillis);
    }
  }

  private void waitRetryAfter(String retryAfter) throws WikidataRetryException {
    long seconds = MAX_RETRY_AFTER_SECONDS;
    if (retryAfter != null) {
      try {
        seconds = Math.min(Math.max(Long.parseLong(retryAfter.strip()), 0), MAX_RETRY_AFTER_SECONDS);
      } catch (NumberFormatException e) {
        // An HTTP date is not evaluated; the longest wait applies
      }
    }
    sleepInterruptibly(seconds * 1000);
  }

  /**
   * Sleeps; an interruption is kept on the thread, so that the fill run stops before the next security.
   */
  private void sleepInterruptibly(long millis) throws WikidataRetryException {
    try {
      sleeper.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WikidataRetryException("Interrupted while waiting for Wikidata");
    }
  }

  /** A retryable failure of one request. */
  private static class WikidataRetryException extends Exception {
    private static final long serialVersionUID = 1L;

    WikidataRetryException(String message) {
      super(message);
    }
  }
}
