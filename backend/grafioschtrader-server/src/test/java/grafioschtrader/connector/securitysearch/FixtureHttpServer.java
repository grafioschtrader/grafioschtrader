package grafioschtrader.connector.securitysearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/**
 * Local HTTP server for the provider adapters: it answers each path with a recorded fixture, a status code or a delayed
 * response, so that the adapters run their real HTTP code without reaching the provider. Requests are recorded for
 * assertions on what an adapter sent.
 */
public class FixtureHttpServer implements AutoCloseable {

  private final HttpServer server;
  private final Map<String, Answer> answers = new ConcurrentHashMap<>();
  private final List<String> requests = new CopyOnWriteArrayList<>();

  private record Answer(int status, String body, long delayMillis) {
  }

  public FixtureHttpServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      String query = exchange.getRequestURI().getRawQuery();
      String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      requests.add(path + (query == null ? "" : "?" + query) + (requestBody.isEmpty() ? "" : " " + requestBody));
      Answer answer = answers.get(path + "?q=" + queryParameter(query, "q"));
      if (answer == null) {
        answer = answers.getOrDefault(path, new Answer(404, "", 0));
      }
      if (answer.delayMillis() > 0) {
        try {
          Thread.sleep(answer.delayMillis());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(answer.status(), bytes.length == 0 ? -1 : bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    server.start();
  }

  private static String queryParameter(String rawQuery, String name) {
    if (rawQuery != null) {
      for (String pair : rawQuery.split("&")) {
        if (pair.startsWith(name + "=")) {
          return URLDecoder.decode(pair.substring(name.length() + 1), StandardCharsets.UTF_8);
        }
      }
    }
    return null;
  }

  /** Base URL of the server, ending with a slash. */
  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }

  /**
   * Answers the path with HTTP 200 and the content of a fixture below {@code securitysearch/}. A path of the form
   * {@code /path?q=value} answers only requests whose {@code q} parameter has that value.
   */
  public FixtureHttpServer fixture(String path, String fixture) {
    answers.put(path, new Answer(200, readFixture(fixture), 0));
    return this;
  }

  /** Answers the path with the given status and body. */
  public FixtureHttpServer status(String path, int status, String body) {
    answers.put(path, new Answer(status, body, 0));
    return this;
  }

  /** Answers the path with HTTP 200 and an empty JSON array after the given delay. */
  public FixtureHttpServer delayed(String path, long delayMillis) {
    answers.put(path, new Answer(200, "[]", delayMillis));
    return this;
  }

  /** Path, query and body of every request received so far. */
  public List<String> requests() {
    return requests;
  }

  public static String readFixture(String fixture) {
    try (InputStream in = FixtureHttpServer.class.getResourceAsStream("/securitysearch/" + fixture)) {
      if (in == null) {
        throw new IllegalArgumentException("Missing fixture " + fixture);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
