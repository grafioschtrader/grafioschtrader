package grafioschtrader.udffill.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Transport of {@link UDFFillHttp} for tests: answers from a function instead of a socket and records every request
 * with the addresses it was allowed to connect to.
 */
public class FakeHttpTransport implements HttpTransport {

  /**
   * One prepared response.
   */
  public record Answer(int status, String contentType, String location, String retryAfter, byte[] body) {

    public static Answer json(String body) {
      return new Answer(200, "application/json; charset=utf-8", null, null, body.getBytes(StandardCharsets.UTF_8));
    }

    public static Answer status(int status) {
      return new Answer(status, "text/html", null, null, new byte[0]);
    }

    public static Answer redirect(String location) {
      return new Answer(302, null, location, null, new byte[0]);
    }
  }

  /** A request the transport received. */
  public record Request(URI uri, List<InetAddress> addresses) {
  }

  private final Function<URI, Answer> answers;
  private final List<Request> requests = new ArrayList<>();

  public FakeHttpTransport(Function<URI, Answer> answers) {
    this.answers = answers;
  }

  @Override
  public <T> T get(URI uri, List<InetAddress> addresses, Duration timeout, String userAgent, ResponseHandler<T> handler)
      throws IOException {
    requests.add(new Request(uri, List.copyOf(addresses)));
    Answer answer = answers.apply(uri);
    if (answer == null) {
      throw new IOException("Connection refused by the fake transport: " + uri);
    }
    return handler.handle(new TransportResponse(answer.status(), answer.contentType(), answer.location(),
        answer.retryAfter(), new ByteArrayInputStream(answer.body())));
  }

  public List<Request> getRequests() {
    return requests;
  }
}
