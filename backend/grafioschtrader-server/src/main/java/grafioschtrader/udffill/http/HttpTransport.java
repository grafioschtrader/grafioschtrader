package grafioschtrader.udffill.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Executes one GET request of {@link UDFFillHttp} without following redirects. The connection must be made to the
 * given, already checked addresses only, so that the lookup and the connect cannot diverge.
 */
public interface HttpTransport {

  /**
   * Executes the request and hands the response to the consumer while the connection is open.
   *
   * @param uri       the URL, scheme https
   * @param addresses the checked addresses of its host; the transport must not resolve the host itself
   * @param timeout   connect and read timeout
   * @param userAgent the User-Agent header
   * @param handler   reads the response
   * @return what the handler returned
   * @throws IOException on a transport failure
   */
  <T> T get(URI uri, List<InetAddress> addresses, Duration timeout, String userAgent, ResponseHandler<T> handler)
      throws IOException;

  /**
   * Reads one response.
   */
  @FunctionalInterface
  interface ResponseHandler<T> {
    T handle(TransportResponse response) throws IOException;
  }

  /**
   * One response as the transport received it; the body is already decompressed.
   *
   * @param status      the HTTP status
   * @param contentType the Content-Type header, or null
   * @param location    the Location header, or null
   * @param retryAfter  the Retry-After header, or null
   * @param body        the body stream, never null
   */
  record TransportResponse(int status, String contentType, String location, String retryAfter, InputStream body) {
  }
}
