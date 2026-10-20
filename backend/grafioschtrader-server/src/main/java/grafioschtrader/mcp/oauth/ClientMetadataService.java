package grafioschtrader.mcp.oauth;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Identifies OAuth clients by their Client ID Metadata Document: the {@code client_id} is an HTTPS URL, and the JSON
 * document behind it names the client and its redirect URIs. GT offers no dynamic client registration, so this is the
 * only way a client becomes known.
 *
 * <p>
 * A document is accepted when its {@code client_id} equals the URL it was loaded from, the client can work without a
 * client secret and every redirect URI is HTTPS or HTTP to a loopback address. A client that lists several methods in
 * {@code token_endpoint_auth_methods_supported} qualifies when {@code none} is among them, whatever its preferred
 * method in {@code token_endpoint_auth_method}; ChatGPT, for example, prefers {@code private_key_jwt} and also offers
 * {@code none}, which GT's metadata announce as the only method. Without that list the single method must be
 * {@code none} or absent. The documents are fetched through {@link ClientMetadataFetcher}, whose default refuses
 * private addresses.
 * </p>
 *
 * <p>
 * The load on foreign servers is bounded for the whole instance, not only per fetch: at most 200 results are cached for
 * an hour, failures included, and at most four fetches run at the same time.
 * </p>
 */
@Service
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class ClientMetadataService {

  public static final int MAX_CACHED_CLIENTS = 200;
  public static final Duration CACHE_TIME = Duration.ofHours(1);
  public static final int MAX_PARALLEL_FETCHES = 4;
  public static final int MAX_DOCUMENT_LENGTH = 64 * 1024;
  public static final int MAX_CLIENT_NAME_LENGTH = 100;

  private static final Logger log = LoggerFactory.getLogger(ClientMetadataService.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** A known client. */
  public record ClientMetadata(String clientId, String clientName, List<String> redirectUris) {
    /** The host of the client_id, shown on the consent page next to the self-declared name. */
    public String clientHost() {
      return URI.create(clientId).getHost();
    }
  }

  /** The cached outcome of one lookup; {@code client} is null when the document was refused or not reachable. */
  private record CachedLookup(ClientMetadata client, long loadedAtNanos) {
  }

  private final ClientMetadataFetcher fetcher;
  private final Semaphore fetches = new Semaphore(MAX_PARALLEL_FETCHES);
  private final Map<String, CachedLookup> cache = new LinkedHashMap<>(16, 0.75f, true) {
    private static final long serialVersionUID = 1L;

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, CachedLookup> eldest) {
      return size() > MAX_CACHED_CLIENTS;
    }
  };

  public ClientMetadataService(ClientMetadataFetcher fetcher) {
    this.fetcher = fetcher;
  }

  /**
   * Returns the client behind a {@code client_id}.
   *
   * @param clientId the client_id of an authorization request
   * @return the client, or null when the URL or its document is not acceptable, cannot be loaded at the moment, or the
   *         instance is already fetching four documents
   */
  public ClientMetadata findClient(String clientId) {
    if (!isAcceptableClientIdUrl(clientId)) {
      return null;
    }
    synchronized (cache) {
      CachedLookup cached = cache.get(clientId);
      if (cached != null && System.nanoTime() - cached.loadedAtNanos() < CACHE_TIME.toNanos()) {
        return cached.client();
      }
    }
    try {
      if (!fetches.tryAcquire(5, TimeUnit.SECONDS)) {
        return null;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    ClientMetadata client;
    try {
      client = parse(clientId, fetcher.fetch(clientId));
    } catch (Exception e) {
      log.info("Client metadata document {} refused: {}", clientId, e.getMessage());
      client = null;
    } finally {
      fetches.release();
    }
    synchronized (cache) {
      cache.put(clientId, new CachedLookup(client, System.nanoTime()));
    }
    return client;
  }

  /** CIMD requires an HTTPS URL with a path; fragments and user info are refused. */
  static boolean isAcceptableClientIdUrl(String clientId) {
    if (clientId == null || clientId.length() > 255) {
      return false;
    }
    try {
      URI uri = URI.create(clientId);
      return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null
          && uri.getFragment() == null && uri.getRawPath() != null && uri.getRawPath().length() > 1;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static ClientMetadata parse(String clientId, String document) {
    if (document == null || document.length() > MAX_DOCUMENT_LENGTH) {
      throw new IllegalArgumentException("missing or too large document");
    }
    JsonNode metadata = JSON.readTree(document);
    if (!clientId.equals(text(metadata, "client_id"))) {
      throw new IllegalArgumentException("client_id differs from the document URL");
    }
    if (!canAuthenticateWithoutSecret(metadata)) {
      throw new IllegalArgumentException("only public clients without a secret are supported");
    }
    List<String> redirectUris = new ArrayList<>();
    JsonNode uris = metadata.get("redirect_uris");
    if (uris == null || !uris.isArray() || uris.isEmpty()) {
      throw new IllegalArgumentException("no redirect_uris");
    }
    for (JsonNode uri : uris) {
      String value = uri.isString() ? uri.asString() : null;
      if (!isAcceptableRedirectUri(value)) {
        throw new IllegalArgumentException("redirect URI must be https or loopback");
      }
      redirectUris.add(value);
    }
    String name = text(metadata, "client_name");
    if (name == null || name.isBlank()) {
      name = URI.create(clientId).getHost();
    }
    name = name.strip();
    return new ClientMetadata(clientId,
        name.length() > MAX_CLIENT_NAME_LENGTH ? name.substring(0, MAX_CLIENT_NAME_LENGTH) : name,
        List.copyOf(redirectUris));
  }

  private static boolean canAuthenticateWithoutSecret(JsonNode metadata) {
    JsonNode supported = metadata.get("token_endpoint_auth_methods_supported");
    if (supported != null && supported.isArray()) {
      for (JsonNode method : supported) {
        if (method.isString() && "none".equals(method.asString())) {
          return true;
        }
      }
      return false;
    }
    String authMethod = text(metadata, "token_endpoint_auth_method");
    return authMethod == null || "none".equals(authMethod);
  }

  static boolean isAcceptableRedirectUri(String value) {
    if (value == null) {
      return false;
    }
    try {
      URI uri = URI.create(value);
      if (uri.getHost() == null || uri.getFragment() != null) {
        return false;
      }
      if ("https".equals(uri.getScheme())) {
        return true;
      }
      return "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost())
          || InetAddress.ofLiteral(stripBrackets(uri.getHost())).isLoopbackAddress());
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static String stripBrackets(String host) {
    return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }
}
