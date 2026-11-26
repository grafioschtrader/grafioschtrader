package grafioschtrader.connector.instrument.onvista;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The connector sets {@code startDate} and {@code range} itself. A stored URL extension carrying one of them made
 * onvista answer with a single month per request, because it honours the first occurrence of a repeated parameter; the
 * full load of Mercedes-Benz then ran into HTTP 429 and was discarded.
 */
class OnvistaUrlExtensionTest {

  @Test
  @DisplayName("A range stored in the URL extension is removed, the notation is kept")
  void removesStoredRange() {
    assertThat(OnvistaFeedConnector.withoutPeriodParameters("STOCK/82840/eod_history?idNotation=161766&range=M1"))
        .isEqualTo("STOCK/82840/eod_history?idNotation=161766");
  }

  @Test
  @DisplayName("startDate and range are removed in any case and position")
  void removesStartDateAndRangeAnywhere() {
    assertThat(OnvistaFeedConnector
        .withoutPeriodParameters("STOCK/1/eod_history?Range=Y1&idNotation=2&startDate=2020-01-01&x=3"))
        .isEqualTo("STOCK/1/eod_history?idNotation=2&x=3");
  }

  @Test
  @DisplayName("An extension without period parameters stays unchanged")
  void keepsCleanExtension() {
    assertThat(OnvistaFeedConnector.withoutPeriodParameters("CURRENCY/GBPUSD/eod_history?idNotation=1305587"))
        .isEqualTo("CURRENCY/GBPUSD/eod_history?idNotation=1305587");
  }
}
