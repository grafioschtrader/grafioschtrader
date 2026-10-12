package grafioschtrader.connector.instrument.consorsbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

import grafioschtrader.connector.instrument.IFeedConnector.FeedIdentifier;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/** Runs real connector HTTP requests against controlled pages, independently of provider changes and database state. */
class ConsorsbankFeedConnectorHttpTest {
  private HttpServer server;
  private ConsorsbankFeedConnector connector;
  private String endpoint;
  private final Map<Integer, String> pages = new ConcurrentHashMap<>();
  private final List<String> requests = new CopyOnWriteArrayList<>();
  private int status = 200;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/quotes", exchange -> {
      String query = exchange.getRequestURI().getRawQuery();
      requests.add(query);
      int page = 0;
      for (String part : query.split("&")) {
        if (part.startsWith("page=")) {
          page = Integer.parseInt(part.substring(5));
        }
      }
      byte[] body = pages.getOrDefault(page, "[]").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, body.length);
      try (var output = exchange.getResponseBody()) {
        output.write(body);
      }
    });
    server.start();
    endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/quotes";
    connector = new ConsorsbankFeedConnector(endpoint);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  @DisplayName("Read all pages, filter inclusive bounds and retain weekend dates in chronological order")
  void paginationAndBoundaries() throws Exception {
    pages.put(0, page(0, 2, 6, quote("2025-01-06", 106), quote("2025-01-05", 105)));
    pages.put(1, page(1, 2, 6, quote("2025-01-04", 104), quote("2025-01-03", 103)));
    pages.put(2, page(2, 2, 6, quote("2025-01-02", 102), quote("2025-01-01", 101)));
    List<Historyquote> result = history("2025-01-02", "2025-01-05");
    assertThat(result).extracting(Historyquote::getDate).containsExactly(LocalDate.parse("2025-01-02"),
        LocalDate.parse("2025-01-03"), LocalDate.parse("2025-01-04"), LocalDate.parse("2025-01-05"));
    assertThat(result).extracting(Historyquote::getClose).containsExactly(102.0, 103.0, 104.0, 105.0);
    assertThat(requests).hasSize(3);
    assertThat(requests.getLast()).contains("page=2", "resolution=1D", "pagesize=1000");
  }

  @Test
  @DisplayName("Request decades rather than silently limiting the lookback to twelve months")
  void longLookbackAndEarlyStop() throws Exception {
    pages.put(0, page(0, 2, 4, quote("2000-01-04", 2), quote("1999-12-31", 1)));
    assertThat(history("2000-01-03", "2000-01-04")).hasSize(1);
    String range = List.of(requests.getFirst().split("&")).stream().filter(value -> value.startsWith("range="))
        .findFirst().orElseThrow();
    assertThat(Long.parseLong(range.substring(7))).isGreaterThan(300);
    assertThat(requests).hasSize(1);
  }

  @Test
  @DisplayName("Map OHLCV in the supplied currency unit without shifting the historical calendar date")
  void mapsPricesAndPreservesOptionalNulls() throws Exception {
    String row = """
        {"DATETIME_LAST":"2025-01-03T00:30:00+1400","FIRST":1200,"HIGH":1300,"LOW":1100,
         "LAST":1250,"TOTAL_VOLUME":42,"ISO_CURRENCY":"GBp"}
        """;
    pages.put(0, page(0, 2, 2, row, quote("2025-01-02", 7)));
    var result = connector.getEodSecurityHistory(security(), LocalDate.parse("2025-01-02"),
        LocalDate.parse("2025-01-03"));
    Historyquote q = result.getLast();
    assertThat(q.getDate()).isEqualTo(LocalDate.parse("2025-01-03"));
    assertThat(q.getOpen()).isEqualTo(12);
    assertThat(q.getHigh()).isEqualTo(13);
    assertThat(q.getLow()).isEqualTo(11);
    assertThat(q.getClose()).isEqualTo(12.5);
    assertThat(q.getVolume()).isEqualTo(42L);
    assertThat(result.getFirst().getOpen()).isNull();
    assertThat(result.getFirst().getVolume()).isNull();
  }

  @Test
  @DisplayName("Collapse identical duplicate dates and reject conflicting values")
  void duplicates() throws Exception {
    String row = quote("2025-01-03", 7);
    pages.put(0, page(0, 2, 2, row, row));
    assertThat(history("2025-01-03", "2025-01-03")).hasSize(1);
    pages.put(0, page(0, 2, 2, row, quote("2025-01-03", 8)));
    assertThatThrownBy(() -> history("2025-01-03", "2025-01-03")).isInstanceOf(IOException.class)
        .hasMessageContaining("Conflicting");
  }

  @Test
  @DisplayName("Return an empty history only for a valid empty provider response")
  void emptyHistory() throws Exception {
    pages.put(0, page(0, 1000, 0));
    assertThat(history("2025-01-01", "2025-01-31")).isEmpty();
    pages.put(0, page(0, 1000, 0).replace("\"ITEMS\":[]", "\"ITEMS\":null"));
    assertThat(history("2025-01-01", "2025-01-31")).isEmpty();
    assertThat(connectionOk()).isTrue();
  }

  @Test
  @DisplayName("Do not return earlier pages after a later API failure")
  void laterPageFailure() {
    pages.put(0, page(0, 1, 2, quote("2025-01-03", 3)));
    pages.put(1, """
        [{"Info":{"Errors":[{"ERROR_CODE":"IDMS","ERROR_MESSAGE":"access denied"}]}}]
        """);
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class)
        .hasMessageContaining("access denied");
  }

  @Test
  @DisplayName("Reject truncated pages, repeated page indexes and changing totals")
  void invalidPagination() {
    pages.put(0, page(0, 2, 3, quote("2025-01-03", 3)));
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class)
        .hasMessageContaining("Incomplete");
    pages.put(0, page(0, 1, 2, quote("2025-01-03", 3)));
    pages.put(1, page(0, 1, 2, quote("2025-01-02", 2)));
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class)
        .hasMessageContaining("pagination");
    pages.put(1, page(1, 1, 3, quote("2025-01-02", 2)));
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class)
        .hasMessageContaining("pagination");
  }

  @Test
  @DisplayName("Reject changing page sizes that would skip observations between otherwise valid pages")
  void changedPageSize() {
    pages.put(0, page(0, 1, 4, quote("2025-01-04", 4)));
    pages.put(1, page(1, 2, 4, quote("2025-01-02", 2), quote("2025-01-01", 1)));
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class)
        .hasMessageContaining("page size");
  }

  @ParameterizedTest
  @ValueSource(strings = { "[]", "[{}]", "[{\"HistoryV1\":{}}]",
      "[{\"Info\":{\"Errors\":[{\"ERROR_CODE\":\"IDMS\",\"ERROR_MESSAGE\":\"access denied\"}]}}]" })
  @DisplayName("Reject missing payloads and HTTP-200 provider errors, including during URL validation")
  void missingPayloadOrApiError(String response) throws Exception {
    pages.put(0, response);
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class);
    assertThat(connectionOk()).isFalse();
  }

  @Test
  @DisplayName("Reject HTTP failures and malformed JSON")
  void transportErrors() {
    pages.put(0, "not JSON");
    status = 429;
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class)
        .hasMessageContaining("429");
    status = 200;
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(Exception.class);
  }

  @ParameterizedTest
  @ValueSource(strings = { "{\"DATETIME_LAST\":\"2025-01-03T12:00:00Z\"}", "{\"DATETIME_LAST\":\"invalid\",\"LAST\":7}",
      "{\"LAST\":7}", "{\"DATETIME_LAST\":\"2025-01-03T12:00:00Z\",\"LAST\":0}" })
  @DisplayName("Reject missing dates and missing or nonpositive closing prices")
  void invalidQuote(String row) {
    pages.put(0, page(0, 1, 1, row));
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("Reject unordered pages and reversed requested date intervals")
  void invalidOrder() {
    pages.put(0, page(0, 2, 2, quote("2025-01-02", 2), quote("2025-01-03", 3)));
    assertThatThrownBy(() -> history("2025-01-01", "2025-01-31")).isInstanceOf(IOException.class)
        .hasMessageContaining("descending");
    assertThatThrownBy(() -> history("2025-02-01", "2025-01-31")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Use the actual UTC quote time and correctly encode the supplied exchange code")
  void intradayCurrencyAndSecurity() throws Exception {
    pages.put(0, """
        [{"PriceV2":{"PRICE":1250,"FIRST":1200,"HIGH":1300,"LOW":1100,"PREVIOUS_LAST":1000,
        "PERFORMANCE_PCT":25,"TOTAL_VOLUME":42,"ISO_CURRENCY":"GBp","DATETIME_PRICE":"2025-07-04T17:30:00+0200"}}]
        """);
    Security security = security();
    security.setUrlIntraExtend("_123,GER");
    connector.updateSecurityLastPrice(security);
    assertThat(security.getSLast()).isEqualTo(12.5);
    assertThat(security.getSOpen()).isEqualTo(12);
    assertThat(security.getSHigh()).isEqualTo(13);
    assertThat(security.getSLow()).isEqualTo(11);
    assertThat(security.getSPrevClose()).isEqualTo(10);
    assertThat(security.getSChangePercentage()).isEqualTo(25);
    assertThat(security.getSVolume()).isEqualTo(42L);
    assertThat(security.getSTimestamp()).isEqualTo(LocalDateTime.parse("2025-07-04T15:30:00"));
    assertThat(requests.getFirst()).contains("id=_123", "rtExchangeCode=GER");
    pages.put(0, """
        [{"PriceV2":{"PRICE":1.12,"ISO_CURRENCY":"USD","DATETIME_PRICE":"2025-07-04T15:30:00Z"}}]
        """);
    Currencypair pair = pair();
    pair.setUrlIntraExtend("_123,@W$");
    connector.updateCurrencyPairLastPrice(pair);
    assertThat(pair.getSLast()).isEqualTo(1.12);
    assertThat(pair.getSChangePercentage()).isNull();
    assertThat(pair.getSTimestamp()).isEqualTo(security.getSTimestamp());
    assertThat(requests.getLast()).contains("rtExchangeCode=%40W%24");
    assertThat(connectionOk()).isTrue();
  }

  @Test
  @DisplayName("Advertise both history and intraday for URL-identified securities and currency pairs")
  void capabilitiesAndExtensionValidation() {
    for (FeedSupport feed : List.of(FeedSupport.FS_HISTORY, FeedSupport.FS_INTRA)) {
      assertThat(connector.getSecuritycurrencyFeedSupport(feed)).containsExactly(FeedIdentifier.SECURITY_URL,
          FeedIdentifier.CURRENCY_URL);
    }
    Security security = security();
    security.setUrlHistoryExtend("_123,GER&injected=true");
    assertThatThrownBy(() -> connector.getSecurityHistoricalDownloadLink(security))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private boolean connectionOk() throws Exception {
    HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint + "?id=_123").toURL().openConnection();
    try {
      return connector.isConnectionOk(connection);
    } finally {
      connection.disconnect();
    }
  }

  private List<Historyquote> history(String from, String to) throws Exception {
    return connector.getEodCurrencyHistory(pair(), LocalDate.parse(from), LocalDate.parse(to));
  }

  private static Currencypair pair() {
    Currencypair pair = new Currencypair();
    pair.setFromCurrency("EUR");
    pair.setToCurrency("USD");
    pair.setUrlHistoryExtend("_123,@W$");
    return pair;
  }

  private static Security security() {
    Security security = new Security();
    security.setUrlHistoryExtend("_123,GER");
    return security;
  }

  private static String quote(String date, double close) {
    return "{\"DATETIME_LAST\":\"" + date + "T12:00:00+0000\",\"LAST\":" + close + "}";
  }

  private static String page(int index, int size, int total, String... rows) {
    return "[{\"HistoryV1\":{\"PAGE_INDEX\":" + index + ",\"PAGE_SIZE\":" + size + ",\"TOTAL_AMOUNT\":" + total
        + ",\"TOTAL_PAGES\":" + Math.max(1, (total + size - 1) / size) + ",\"AMOUNT\":" + rows.length + ",\"ITEMS\":["
        + String.join(",", rows) + "]}}]";
  }
}
