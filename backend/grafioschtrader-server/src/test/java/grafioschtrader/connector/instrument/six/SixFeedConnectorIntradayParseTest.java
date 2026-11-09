package grafioschtrader.connector.instrument.six;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Security;

/**
 * Checks the intraday answer of SIX against the timestamps of an answer received on 2026-11-09. It needs no network.
 */
class SixFeedConnectorIntradayParseTest {

  private static final String ANSWER = """
      {"delayMinutes":15,"delayedMillis":1794254836408,"delayedDateTime":"20261109T21:07:16.408",
      "rowData":[["NESN",20261109,"CHF",86.13,0.56,1.35,2.01,13.86,12.13,7300,36145,85.65,86.0,86.2,15000,15000,85.5,
      92935.84,86.5,150552.39,76.9,20261104,93.85,20260626]]}""";

  @Test
  @DisplayName("sTimestamp is the UTC instant of the answer, not the wall-clock time in Zurich")
  void timestampIsUtc() throws Exception {
    Security security = new Security();

    new SixFeedConnector().parseJsonData(security, ANSWER);

    assertThat(security.getSTimestamp()).isEqualTo(LocalDateTime.of(2026, 11, 9, 20, 7, 16, 408_000_000));
  }
}
