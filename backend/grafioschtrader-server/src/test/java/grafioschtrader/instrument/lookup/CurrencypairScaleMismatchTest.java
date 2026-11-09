package grafioschtrader.instrument.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.types.ConnectorProbeStatus;

/**
 * The currency pair lookup must not offer a connector that quotes the pair for a multiple of the base currency. Prices
 * are those measured for JPY/CAD on 2026-11-04: Boursorama quotes 100 yen, every other provider one yen.
 */
class CurrencypairScaleMismatchTest {

  @Test
  @DisplayName("Boursorama JPY/CAD for 100 yen is marked in both feeds, the others stay usable")
  void boursoramaHundredYenIsMarked() {
    Map<FeedSupport, List<ConnectorAlternativeDto>> checked = CurrencypairLookupService
        .flagScaleMismatches(feeds(List.of(ok("onvista", 0.008833), ok("yahoo", 0.00887), ok("boursorama", 0.8807)),
            List.of(ok("yahoo", 0.00884), ok("boursorama", 0.88055))));

    assertThat(checked.get(FeedSupport.FS_HISTORY)).extracting(ConnectorAlternativeDto::probeStatus)
        .containsExactly(ConnectorProbeStatus.OK, ConnectorProbeStatus.OK, ConnectorProbeStatus.SCALE_MISMATCH);
    assertThat(checked.get(FeedSupport.FS_INTRA)).extracting(ConnectorAlternativeDto::probeStatus)
        .containsExactly(ConnectorProbeStatus.OK, ConnectorProbeStatus.SCALE_MISMATCH);
  }

  @Test
  @DisplayName("Two connectors on different scales cannot be told apart, so neither is marked")
  void tieMarksNothing() {
    Map<FeedSupport, List<ConnectorAlternativeDto>> checked = CurrencypairLookupService
        .flagScaleMismatches(feeds(List.of(ok("yahoo", 0.00887), ok("boursorama", 0.8807)), List.of()));

    assertThat(checked.get(FeedSupport.FS_HISTORY)).extracting(ConnectorAlternativeDto::probeStatus)
        .containsOnly(ConnectorProbeStatus.OK);
  }

  private static Map<FeedSupport, List<ConnectorAlternativeDto>> feeds(List<ConnectorAlternativeDto> history,
      List<ConnectorAlternativeDto> intra) {
    Map<FeedSupport, List<ConnectorAlternativeDto>> feeds = new EnumMap<>(FeedSupport.class);
    feeds.put(FeedSupport.FS_HISTORY, history);
    feeds.put(FeedSupport.FS_INTRA, intra);
    return feeds;
  }

  private static ConnectorAlternativeDto ok(String connector, double price) {
    return new ConnectorAlternativeDto("gt.datafeed." + connector, connector, null, ConnectorProbeStatus.OK, null,
        price, 1, null, null, null);
  }
}
