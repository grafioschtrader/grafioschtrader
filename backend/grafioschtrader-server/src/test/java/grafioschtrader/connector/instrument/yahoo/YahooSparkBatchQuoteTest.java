package grafioschtrader.connector.instrument.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

/**
 * Mapping of a batched Yahoo spark answer onto the requested instruments. The recorded answer was fetched on 2026-10-29
 * from {@code v7/finance/spark?symbols=AAPL,VOD.L,EURCHF%3DX,XXXXINVALID&range=1d&interval=1d}; Yahoo returned the
 * three known symbols in an order of its own and left out the unknown one.
 */
class YahooSparkBatchQuoteTest {

  private static final int DELAY_SECONDS = 900;

  @Test
  @DisplayName("Recorded answer: each price reaches the security of its symbol, a pence price is divided")
  void recordedAnswerIsMatchedBySymbol() throws IOException {
    Security aapl = new Security();
    Security vod = new Security();
    Security unknown = new Security();
    Map<String, List<Security>> bySymbol = new LinkedHashMap<>();
    bySymbol.put("AAPL", List.of(aapl));
    bySymbol.put("VOD.L", List.of(vod));
    bySymbol.put("XXXXINVALID", List.of(unknown));
    Map<Security, Double> divider = new IdentityHashMap<>(Map.of(aapl, 1.0, vod, 100.0, unknown, 1.0));

    List<Security> missing = YahooFeedConnectorCOM.applySpark(read("spark-batch-2026-10-29.json"), bySymbol,
        divider::get, DELAY_SECONDS);

    assertEquals(1, missing.size());
    assertSame(unknown, missing.getFirst());
    assertNull(unknown.getSLast());
    assertEquals(343.83, aapl.getSLast());
    assertEquals(347.18, aapl.getSHigh());
    assertEquals(343.53, aapl.getSLow());
    assertEquals(34405006L, aapl.getSVolume());
    // The getter rounds to two decimals: (343.83 - 347.1) / 347.1 * 100 = -0.942
    assertEquals(-0.94, aapl.getSChangePercentage());
    assertEquals(1.1795, vod.getSLast(), 1e-9);
    assertEquals(1.18655, vod.getSHigh(), 1e-9);
    assertEquals(1.175, vod.getSLow(), 1e-9);
    assertEquals(0.43, vod.getSChangePercentage());
    assertTrue(Duration.between(aapl.getSTimestamp(), LocalDateTime.now()).getSeconds() >= DELAY_SECONDS);
  }

  @Test
  @DisplayName("A currency pair gets its rate, and two securities sharing one symbol both get its price")
  void currencyPairAndSharedSymbol() throws IOException {
    Currencypair eurChf = new Currencypair();
    Map<String, List<Currencypair>> pairs = Map.of("EURCHF=X", List.of(eurChf));
    Security first = new Security();
    Security second = new Security();
    Map<String, List<Security>> securities = Map.of("AAPL", List.of(first, second));
    String body = read("spark-batch-2026-10-29.json");

    List<Currencypair> missingPairs = YahooFeedConnectorCOM.applySpark(body, pairs, _ -> 1.0, DELAY_SECONDS);
    List<Security> missingSecurities = YahooFeedConnectorCOM.applySpark(body, securities, _ -> 1.0, DELAY_SECONDS);

    assertTrue(missingPairs.isEmpty());
    assertEquals(0.9221, eurChf.getSLast());
    assertEquals(0.9239, eurChf.getSHigh());
    assertEquals(0.9214, eurChf.getSLow());
    assertTrue(missingSecurities.isEmpty());
    assertEquals(343.83, first.getSLast());
    assertEquals(343.83, second.getSLast());
  }

  private String read(String fileName) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/connector/yahoo/" + fileName)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
