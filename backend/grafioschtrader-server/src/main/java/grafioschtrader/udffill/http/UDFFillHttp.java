package grafioschtrader.udffill.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * The one way the automatic filling of user-defined fields reads a public source. Every request is guarded:
 * <ul>
 * <li>https only, port 443 only, no user info in the URL;</li>
 * <li>the host is resolved once and every address is checked; the URL is refused when any address is loopback,
 * link-local, site-local, unique-local, multicast, unspecified, or an IPv4-mapped or IPv4-compatible IPv6 form of
 * these;</li>
 * <li>the connection is made to the checked addresses only, so lookup and connect cannot diverge;</li>
 * <li>at most 3 redirects, each target through the same checks; no credentials or cookies;</li>
 * <li>10 s connect and read timeout per request, 20 s per fetch including redirects; the body is capped at 1 MB after
 * decompression; accepted content types are text/html, text/plain and application/json.</li>
 * </ul>
 * The DNS lookup and the transport are injected, so tests run without network.
 */
@Component
public class UDFFillHttp {

  public static final int MAX_REDIRECTS = 3;
  public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);
  public static final int MAX_BODY_BYTES = 1024 * 1024;
  private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of("text/html", "text/plain", "application/json");
  private static final Set<Integer> REDIRECT_STATUS = Set.of(301, 302, 303, 307, 308);

  /**
   * The answer of a source.
   *
   * @param status      the HTTP status of the last response
   * @param finalUrl    the URL after redirects
   * @param contentType the content type of a 2xx response, null otherwise
   * @param retryAfter  the Retry-After header, or null
   * @param body        the text of a 2xx response, null for every other status
   */
  public record FetchResult(int status, String finalUrl, String contentType, String retryAfter, String body) {

    public boolean isSuccess() {
      return status >= 200 && status < 300;
    }
  }

  private record Hop(int status, String contentType, String location, String retryAfter, String body) {
  }

  /** Carries a refusal raised while the response is read out of the transport. */
  private static class RefusedIOException extends IOException {
    private static final long serialVersionUID = 1L;

    RefusedIOException(String message) {
      super(message);
    }
  }

  private final DnsLookup dnsLookup;
  private final HttpTransport transport;
  private final String userAgent;

  @Autowired
  public UDFFillHttp(Optional<BuildProperties> buildProperties) {
    this(InetAddress::getAllByName, new ApacheHttpTransport(),
        userAgent(buildProperties.map(BuildProperties::getVersion).orElse("dev")));
  }

  public UDFFillHttp(DnsLookup dnsLookup, HttpTransport transport, String userAgent) {
    this.dnsLookup = dnsLookup;
    this.transport = transport;
    this.userAgent = userAgent;
  }

  /**
   * Builds the User-Agent of every request to a public source.
   */
  public static String userAgent(String version) {
    return "Grafioschtrader/" + version + " (+https://github.com/grafioschtrader/grafioschtrader)";
  }

  /**
   * Fetches a URL, following at most 3 redirects.
   *
   * @param url the URL
   * @return the last response; the body is set for a 2xx response only
   * @throws UDFFillHttpException when a check refuses the URL, a redirect target or the response, or on a transport
   *                              failure
   */
  public FetchResult fetch(String url) throws UDFFillHttpException {
    long deadline = System.nanoTime() + FETCH_TIMEOUT.toNanos();
    URI uri = parse(url);
    for (int redirects = 0;; redirects++) {
      checkUrl(uri);
      List<InetAddress> addresses = resolveChecked(hostOf(uri));
      Hop hop = execute(uri, addresses, deadline);
      if (REDIRECT_STATUS.contains(hop.status()) && hop.location() != null) {
        if (redirects >= MAX_REDIRECTS) {
          throw new UDFFillHttpException("More than " + MAX_REDIRECTS + " redirects: " + url, true);
        }
        uri = resolveLocation(uri, hop.location());
        continue;
      }
      return new FetchResult(hop.status(), uri.toString(), hop.contentType(), hop.retryAfter(), hop.body());
    }
  }

  private Hop execute(URI uri, List<InetAddress> addresses, long deadline) throws UDFFillHttpException {
    Duration remaining = Duration.ofNanos(deadline - System.nanoTime());
    if (remaining.isNegative() || remaining.isZero()) {
      throw new UDFFillHttpException("Fetch exceeded " + FETCH_TIMEOUT.toSeconds() + " s: " + uri, false);
    }
    Duration timeout = remaining.compareTo(REQUEST_TIMEOUT) < 0 ? remaining : REQUEST_TIMEOUT;
    try {
      return transport.get(uri, addresses, timeout, userAgent, response -> readResponse(response, deadline));
    } catch (RefusedIOException e) {
      throw new UDFFillHttpException(e.getMessage(), true);
    } catch (IOException e) {
      throw new UDFFillHttpException("Request to " + uri + " failed: " + e.getMessage(), e);
    }
  }

  private static Hop readResponse(HttpTransport.TransportResponse response, long deadline) throws IOException {
    int status = response.status();
    if (status < 200 || status >= 300) {
      return new Hop(status, null, response.location(), response.retryAfter(), null);
    }
    String mediaType = mediaType(response.contentType());
    if (mediaType == null || !ACCEPTED_CONTENT_TYPES.contains(mediaType)) {
      throw new RefusedIOException("Content type not accepted: " + response.contentType());
    }
    byte[] body = readCapped(response.body(), deadline);
    return new Hop(status, mediaType, null, response.retryAfter(), new String(body, charsetOf(response.contentType())));
  }

  private static byte[] readCapped(InputStream in, long deadline) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
      if (out.size() > MAX_BODY_BYTES) {
        throw new RefusedIOException("Body larger than " + MAX_BODY_BYTES + " bytes");
      }
      if (System.nanoTime() > deadline) {
        throw new IOException("Fetch exceeded " + FETCH_TIMEOUT.toSeconds() + " s while reading the body");
      }
    }
    return out.toByteArray();
  }

  private static String mediaType(String contentType) {
    if (contentType == null) {
      return null;
    }
    int semicolon = contentType.indexOf(';');
    return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).strip().toLowerCase(Locale.ROOT);
  }

  private static Charset charsetOf(String contentType) {
    for (String part : contentType.split(";")) {
      String p = part.strip();
      if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
        try {
          return Charset.forName(p.substring(8).replace("\"", "").strip());
        } catch (IllegalArgumentException e) {
          return StandardCharsets.UTF_8;
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  private static URI parse(String url) throws UDFFillHttpException {
    try {
      return new URI(url);
    } catch (URISyntaxException | NullPointerException e) {
      throw new UDFFillHttpException("Not a valid URL: " + url, true);
    }
  }

  private static URI resolveLocation(URI base, String location) throws UDFFillHttpException {
    try {
      return base.resolve(new URI(location));
    } catch (URISyntaxException | IllegalArgumentException e) {
      throw new UDFFillHttpException("Redirect to an invalid location: " + location, true);
    }
  }

  /**
   * Refuses every URL that is not https on port 443 with a host and without user info.
   */
  static void checkUrl(URI uri) throws UDFFillHttpException {
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new UDFFillHttpException("Only https is allowed: " + uri, true);
    }
    if (uri.getRawUserInfo() != null) {
      throw new UDFFillHttpException("User info is not allowed in the URL", true);
    }
    if (uri.getPort() != -1 && uri.getPort() != 443) {
      throw new UDFFillHttpException("Only port 443 is allowed: " + uri, true);
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new UDFFillHttpException("The URL has no host: " + uri, true);
    }
  }

  private static String hostOf(URI uri) {
    String host = uri.getHost();
    return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
  }

  /**
   * Resolves the host once and refuses it when any of its addresses is not public. An IP literal is taken as it is.
   */
  private List<InetAddress> resolveChecked(String host) throws UDFFillHttpException {
    InetAddress[] addresses;
    try {
      addresses = new InetAddress[] { InetAddress.ofLiteral(host) };
    } catch (IllegalArgumentException notALiteral) {
      try {
        addresses = dnsLookup.lookup(host);
      } catch (UnknownHostException e) {
        throw new UDFFillHttpException("Host does not resolve: " + host, e);
      }
    }
    if (addresses == null || addresses.length == 0) {
      throw new UDFFillHttpException("Host does not resolve: " + host, false);
    }
    for (InetAddress address : addresses) {
      if (!isPublic(address)) {
        throw new UDFFillHttpException("Host " + host + " resolves to a non-public address " + address, true);
      }
    }
    return Arrays.asList(addresses);
  }

  /**
   * Tells whether an address may be connected to: neither loopback, link-local, site-local, unique-local, multicast nor
   * unspecified, also not in the IPv4-mapped or IPv4-compatible IPv6 form of such an address.
   *
   * @param address the address
   * @return true for a public address
   */
  static boolean isPublic(InetAddress address) {
    byte[] b = address.getAddress();
    if (address instanceof Inet6Address) {
      if (isEmbeddedIpv4(b)) {
        try {
          return isPublic(Inet4Address.getByAddress(Arrays.copyOfRange(b, 12, 16)));
        } catch (UnknownHostException e) {
          return false;
        }
      }
      if ((b[0] & 0xfe) == 0xfc) {
        return false;
      }
    } else if (b[0] == 0) {
      return false;
    }
    return !(address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isMulticastAddress() || address.isAnyLocalAddress());
  }

  /** IPv4-compatible {@code ::a.b.c.d} or IPv4-mapped {@code ::ffff:a.b.c.d}. */
  private static boolean isEmbeddedIpv4(byte[] b) {
    for (int i = 0; i < 10; i++) {
      if (b[i] != 0) {
        return false;
      }
    }
    return (b[10] == 0 && b[11] == 0) || (b[10] == (byte) 0xff && b[11] == (byte) 0xff);
  }
}
