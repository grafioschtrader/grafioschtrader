package grafioschtrader.udffill.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * Transport of {@link UDFFillHttp} on Apache HttpClient 5. Each request gets its own client whose DNS resolver answers
 * only the host of the request, with the addresses {@link UDFFillHttp} checked before; any other name fails. Redirects,
 * cookies and authentication are disabled, and no system proxy is used. The body is decompressed by the client.
 */
public class ApacheHttpTransport implements HttpTransport {

  @Override
  public <T> T get(URI uri, List<InetAddress> addresses, Duration timeout, String userAgent, ResponseHandler<T> handler)
      throws IOException {
    Timeout t = Timeout.of(timeout);
    PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
        .setDnsResolver(new CheckedAddressesResolver(hostOf(uri), addresses))
        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(t).setSocketTimeout(t).build()).build();
    RequestConfig requestConfig = RequestConfig.custom().setRedirectsEnabled(false).setResponseTimeout(t)
        .setConnectionRequestTimeout(t).build();
    try (CloseableHttpClient client = HttpClients.custom().setConnectionManager(connectionManager)
        .setDefaultRequestConfig(requestConfig).disableRedirectHandling().disableCookieManagement().disableAuthCaching()
        .setUserAgent(userAgent).build()) {
      HttpGet get = new HttpGet(uri);
      get.setHeader("Accept", "text/html, text/plain, application/json");
      return client.execute(get, (ClassicHttpResponse response) -> {
        HttpEntity entity = response.getEntity();
        InputStream body = entity == null ? InputStream.nullInputStream() : entity.getContent();
        String contentType = entity != null && entity.getContentType() != null ? entity.getContentType()
            : headerValue(response, "Content-Type");
        return handler.handle(new TransportResponse(response.getCode(), contentType, headerValue(response, "Location"),
            headerValue(response, "Retry-After"), body));
      });
    }
  }

  private static String headerValue(ClassicHttpResponse response, String name) {
    Header header = response.getFirstHeader(name);
    return header == null ? null : header.getValue();
  }

  private static String hostOf(URI uri) {
    String host = uri.getHost();
    return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
  }

  /**
   * Answers only the host of the request, with the addresses that were checked before.
   */
  private record CheckedAddressesResolver(String host, List<InetAddress> addresses) implements DnsResolver {

    @Override
    public InetAddress[] resolve(String name) throws UnknownHostException {
      if (host.equalsIgnoreCase(name)) {
        return addresses.toArray(new InetAddress[0]);
      }
      throw new UnknownHostException(name + " was not checked");
    }

    @Override
    public String resolveCanonicalHostname(String name) throws UnknownHostException {
      resolve(name);
      return name;
    }
  }
}
