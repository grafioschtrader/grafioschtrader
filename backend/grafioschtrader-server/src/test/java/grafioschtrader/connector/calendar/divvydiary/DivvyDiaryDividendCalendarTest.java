package grafioschtrader.connector.calendar.divvydiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import grafioschtrader.GlobalConstants;

/** Exercises calendar HTTP requests and parsing without provider access or a Spring/database context. */
class DivvyDiaryDividendCalendarTest {

  private HttpServer server;
  private DivvyDiaryDividendCalendar connector;
  private volatile int status = 200;
  private volatile String query;
  private volatile String apiKey;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/dividends/calendar", exchange -> {
      query = exchange.getRequestURI().getRawQuery();
      apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
      boolean acceptedAgent = GlobalConstants.USER_AGENT_HTTPCLIENT
          .equals(exchange.getRequestHeaders().getFirst("User-Agent"));
      int responseStatus = acceptedAgent ? status : 403;
      String body = responseStatus == 200 ? """
          {"dividends":[{"name":"Apple Inc.","isin":"US0378331005","exDate":"2026-10-30",
            "payDate":"2026-11-12","amount":0.26,"currency":"USD","extraField":true}]}
          """ : "Forbidden";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(responseStatus, bytes.length);
      try (var output = exchange.getResponseBody()) {
        output.write(bytes);
      }
    });
    server.start();
    connector = new DivvyDiaryDividendCalendar(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/dividends/calendar");
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void loadsCalendarWithApplicationUserAgentWithoutApiKey() throws Exception {
    var dividends = connector.getExDateDividend(LocalDate.of(2026, 10, 30));

    assertThat(query).isEqualTo("prefDate=exDate&month=10&year=2026&day=30");
    assertThat(apiKey).isNull();
    assertThat(dividends).singleElement().satisfies(dividend -> {
      assertThat(dividend.isin).isEqualTo("US0378331005");
      assertThat(dividend.currency).isEqualTo("USD");
      var entity = dividend.getDivident(123);
      assertThat(entity.getExDate()).isEqualTo(LocalDate.of(2026, 10, 30));
      assertThat(entity.getPayDate()).isEqualTo(LocalDate.of(2026, 11, 12));
      assertThat(entity.getAmount()).isEqualTo(0.26);
    });
  }

  @Test
  void reportsHttpFailureInsteadOfParsingErrorPage() {
    status = 403;

    assertThatThrownBy(() -> connector.getExDateDividend(LocalDate.of(2026, 10, 30))).isInstanceOf(IOException.class)
        .hasMessageContaining("HTTP 403").hasMessageContaining("/dividends/calendar?");
  }
}
