package grafioschtrader.connector.instrument.stockanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.MicProviderMapRepository;

/**
 * Path derivation and answer parsing of the Stock Analysis connector against answers recorded on 2026-10-11: the
 * chart of Nestlé on SIX and the quotes of Nestlé and of Vodafone, whose London price is in pence.
 */
class StockAnalysisFeedConnectorFixtureTest {

  private static SecurityBaseDataDto baseData(String ticker, String mic) {
    return new SecurityBaseDataDto("six", null, "CH0038863350", "CHF", mic, null, ticker, null, null, null, null, null,
        null, null, null, null, null);
  }

  private static String fixture(String name) throws IOException {
    try (InputStream in = StockAnalysisFeedConnectorFixtureTest.class
        .getResourceAsStream("/connector/stockanalysis/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("US exchanges give s/{ticker}, others a/{CODE}-{TICKER}; a share class dash becomes a dot")
  void derive() {
    MicProviderMapRepository repository = mock(MicProviderMapRepository.class);
    when(repository.findByIdProviderMic_IdProvider(StockAnalysisFeedConnector.STOCK_ANALYSIS_ID)).thenReturn(List.of(
        new MicProviderMap(StockAnalysisFeedConnector.STOCK_ANALYSIS_ID, "XSWX", "SWX", null),
        new MicProviderMap(StockAnalysisFeedConnector.STOCK_ANALYSIS_ID, "XNYS", StockAnalysisFeedConnector.CODE_US,
            null),
        new MicProviderMap(StockAnalysisFeedConnector.STOCK_ANALYSIS_ID, "XCSE", "CPH", null)));
    StockAnalysisFeedConnector connector = new StockAnalysisFeedConnector();
    ReflectionTestUtils.setField(connector, "micProviderMapRepository", repository);

    assertEquals(Optional.of("a/SWX-NESN"), connector.deriveUrlExtension(baseData("NESN", "XSWX"), FeedSupport.FS_HISTORY));
    assertEquals(Optional.of("a/SWX-NESN"), connector.deriveUrlExtension(baseData("NESN", "XSWX"), FeedSupport.FS_INTRA));
    assertEquals(Optional.of("s/BRK.B"), connector.deriveUrlExtension(baseData("BRK-B", "XNYS"), FeedSupport.FS_HISTORY));
    assertEquals(Optional.of("a/CPH-NOVO.B"),
        connector.deriveUrlExtension(baseData("NOVO B", "XCSE"), FeedSupport.FS_HISTORY));
    assertTrue(connector.deriveUrlExtension(baseData("SAP", "XMUN"), FeedSupport.FS_HISTORY).isEmpty(),
        "a German regional exchange has no Stock Analysis code");
    assertTrue(connector.deriveUrlExtension(baseData(" ", "XSWX"), FeedSupport.FS_HISTORY).isEmpty());
  }

  @Test
  @DisplayName("Without the repository, e.g. outside Spring, no path is derived")
  void withoutRepository() {
    assertTrue(new StockAnalysisFeedConnector().deriveUrlExtension(baseData("NESN", "XSWX"), FeedSupport.FS_HISTORY)
        .isEmpty());
  }

  @Test
  @DisplayName("The chart answer is cut to the requested days")
  void history() throws IOException {
    List<Historyquote> quotes = StockAnalysisFeedConnector.parseHistory(fixture("chart-nesn.json"),
        LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), 1.0);
    assertEquals(4, quotes.size());
    Historyquote first = quotes.getFirst();
    assertEquals(LocalDate.of(2026, 10, 5), first.getDate());
    assertEquals(75.01, first.getOpen());
    assertEquals(75.33, first.getHigh());
    assertEquals(74.46, first.getLow());
    assertEquals(74.48, first.getClose());
    assertEquals(2379778L, first.getVolume());
    assertEquals(LocalDate.of(2026, 10, 8), quotes.getLast().getDate());
  }

  @Test
  @DisplayName("The quote answer sets last price, day range, previous close, change and time")
  void lastPrice() throws IOException {
    Security security = new Security();
    StockAnalysisFeedConnector.setLastPrice(security, fixture("quote-nesn.json"), 1.0);
    assertEquals(76.04, security.getSLast());
    assertEquals(76.5, security.getSOpen());
    assertEquals(76.86, security.getSHigh());
    assertEquals(75.6, security.getSLow());
    assertEquals(76.0, security.getSPrevClose());
    assertEquals(0.05, security.getSChangePercentage());
    assertEquals(2539878L, security.getSVolume());
    assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(1791559813000L), ZoneId.systemDefault()),
        security.getSTimestamp());
  }

  @Test
  @DisplayName("A price in pence is divided into pounds; the percentage change is not")
  void lastPriceMinorUnit() throws IOException {
    Security security = new Security();
    StockAnalysisFeedConnector.setLastPrice(security, fixture("quote-vod.json"), 100.0);
    assertEquals(1.1835, security.getSLast(), 1e-9);
    assertEquals(1.244, security.getSPrevClose(), 1e-9);
    assertEquals(-4.86, security.getSChangePercentage());
  }

  @Test
  @DisplayName("An answer without a price leaves the security untouched")
  void unknownSymbol() {
    Security security = new Security();
    StockAnalysisFeedConnector.setLastPrice(security, "{\"status\":404}", 1.0);
    assertNull(security.getSLast());
    assertNull(security.getSTimestamp());
  }
}
