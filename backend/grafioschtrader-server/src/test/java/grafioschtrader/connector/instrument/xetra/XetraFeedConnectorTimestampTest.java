package grafioschtrader.connector.instrument.xetra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the time of the last price against the format of the recorded answers of the Deutsche Boerse price endpoint.
 * It needs no network.
 */
class XetraFeedConnectorTimestampTest {

  @Test
  @DisplayName("The time of the last price is converted to UTC in summer and in winter")
  void timestampIsUtc() {
    assertThat(XetraFeedConnector.parseIso8601Timestamp("2026-10-09T17:02:59+02:00"))
        .isEqualTo(LocalDateTime.of(2026, 10, 9, 15, 2, 59));
    assertThat(XetraFeedConnector.parseIso8601Timestamp("2026-11-09T17:35:46+01:00"))
        .isEqualTo(LocalDateTime.of(2026, 11, 9, 16, 35, 46));
  }
}
