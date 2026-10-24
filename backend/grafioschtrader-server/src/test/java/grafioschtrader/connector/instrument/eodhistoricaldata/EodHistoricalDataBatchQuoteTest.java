package grafioschtrader.connector.instrument.eodhistoricaldata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Security;

/**
 * Mapping of a batched EODHD real-time answer onto the requested securities. The recorded answer was fetched on
 * 2026-10-23 with the public demo key from {@code real-time/AAPL.US?s=MSFT.US,TSLA.US}.
 */
class EodHistoricalDataBatchQuoteTest {

  private static final int DELAY_SECONDS = 900;

  @Test
  @DisplayName("Recorded answer of three tickers: each quote reaches the security of its code, not of its position")
  void recordedAnswerIsMatchedByCode() throws IOException {
    Security tsla = new Security();
    Security aapl = new Security();
    Security msft = new Security();
    Map<String, List<Security>> bySymbol = new LinkedHashMap<>();
    bySymbol.put("TSLA.US", List.of(tsla));
    bySymbol.put("AAPL.US", List.of(aapl));
    bySymbol.put("MSFT.US", List.of(msft));

    List<Security> missing = EodHistoricalDataConnector.applyQuotes(read("real-time-batch-2026-10-23.json"), bySymbol,
        _ -> 1.0, DELAY_SECONDS);

    assertTrue(missing.isEmpty());
    assertEquals(348.22, aapl.getSLast());
    assertEquals(344.94, aapl.getSOpen());
    assertEquals(348.295, aapl.getSHigh());
    assertEquals(342.28, aapl.getSLow());
    assertEquals(343.99, aapl.getSPrevClose());
    assertEquals(1.2297, aapl.getSChangePercentage());
    assertEquals(521.08, msft.getSLast());
    assertEquals(532.95, msft.getSPrevClose());
    assertEquals(374.58, tsla.getSLast());
    assertEquals(-3.0766, tsla.getSChangePercentage());
    assertTrue(Duration.between(aapl.getSTimestamp(), LocalDateTime.now()).getSeconds() >= DELAY_SECONDS);
  }

  @Test
  @DisplayName("A ticker absent from the answer and an element with NA values fail alone, the rest is applied")
  void absentAndNaTickersFailAlone() {
    String body = """
        [{"code":"AAPL.US","open":344.94,"high":348.295,"low":342.28,"close":348.22,"previousClose":343.99,
          "change_p":1.2297},
         {"code":"XYZ.US","timestamp":"NA","gmtoffset":0,"open":"NA","high":"NA","low":"NA","close":"NA",
          "volume":"NA","previousClose":"NA","change":"NA","change_p":"NA"}]""";
    Security aapl = new Security();
    Security xyz = new Security();
    Security msft = new Security();
    Map<String, List<Security>> bySymbol = new LinkedHashMap<>();
    bySymbol.put("AAPL.US", List.of(aapl));
    bySymbol.put("XYZ.US", List.of(xyz));
    bySymbol.put("MSFT.US", List.of(msft));

    List<Security> missing = EodHistoricalDataConnector.applyQuotes(body, bySymbol, _ -> 1.0, DELAY_SECONDS);

    assertEquals(2, missing.size());
    assertTrue(missing.get(0) == xyz && missing.get(1) == msft);
    assertEquals(348.22, aapl.getSLast());
    assertNull(xyz.getSLast());
    assertNull(msft.getSLast());
  }

  @Test
  @DisplayName("A one-ticker answer is a single object; a pence price is divided for every security of the symbol")
  void singleObjectAnswerWithMinorUnitDivider() {
    String body = """
        {"code":"VOD.LSE","open":7140,"high":7250,"low":7080,"close":7200,"previousClose":7100,"change_p":1.4085}""";
    Security first = new Security();
    Security second = new Security();
    Map<String, List<Security>> bySymbol = Map.of("VOD.LSE", List.of(first, second));

    List<Security> missing = EodHistoricalDataConnector.applyQuotes(body, bySymbol, _ -> 100.0, DELAY_SECONDS);

    assertTrue(missing.isEmpty());
    for (Security security : List.of(first, second)) {
      assertEquals(72.0, security.getSLast());
      assertEquals(71.4, security.getSOpen());
      assertEquals(71.0, security.getSPrevClose());
      assertEquals(1.4085, security.getSChangePercentage());
    }
  }

  private String read(String fileName) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/connector/eodhd/" + fileName)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
