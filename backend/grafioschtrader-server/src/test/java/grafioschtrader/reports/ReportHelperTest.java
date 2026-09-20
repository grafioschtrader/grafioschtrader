package grafioschtrader.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;

/**
 * Pure unit tests for {@link ReportHelper#getReportExchangeRate}. A missing currency pair used to be a null pointer
 * when the transaction dialog asked for open positions; it must be a validation error, and the main currency must stay
 * a rate of 1.
 */
class ReportHelperTest {

  @Test
  @DisplayName("Main currency converts at 1 even when no pair is loaded")
  void mainCurrencyIsOne() {
    DateTransactionCurrencypairMap map = map("EUR", LocalDate.now(), List.of());
    assertThat(ReportHelper.getReportExchangeRate("EUR", map, null)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Today uses the last price of the loaded pair")
  void usesLastPriceWhenPairExists() {
    Currencypair pair = new Currencypair("USD", "EUR");
    pair.setSLast(0.92);
    DateTransactionCurrencypairMap map = map("EUR", LocalDate.now(), List.of(pair));
    assertThat(ReportHelper.getReportExchangeRate("USD", map, null)).isEqualTo(0.92);
  }

  @Test
  @DisplayName("A missing pair on today is a validation error, not a null pointer")
  void missingCurrencypairOnTodayThrowsDataViolation() {
    DateTransactionCurrencypairMap map = map("EUR", LocalDate.now(), List.of());
    assertThatThrownBy(() -> ReportHelper.getReportExchangeRate("USD", map, null))
        .isInstanceOf(DataViolationException.class).satisfies(thrown -> {
          DataViolationException dve = (DataViolationException) thrown;
          assertThat(dve.getDataViolation()).hasSize(1);
          assertThat(dve.getDataViolation().getFirst().getMessageKey()).isEqualTo("gt.missing.currencypair");
          assertThat(dve.getDataViolation().getFirst().getField()).isEqualTo("currencypair");
        });
  }

  @Test
  @DisplayName("A pair without a last price is the same validation error")
  void pairWithoutLastPriceThrowsDataViolation() {
    DateTransactionCurrencypairMap map = map("EUR", LocalDate.now(), List.of(new Currencypair("USD", "EUR")));
    assertThatThrownBy(() -> ReportHelper.getReportExchangeRate("USD", map, null))
        .isInstanceOf(DataViolationException.class);
  }

  private static DateTransactionCurrencypairMap map(String mainCurrency, LocalDate untilDate,
      List<Currencypair> currencypairs) {
    return new DateTransactionCurrencypairMap(mainCurrency, untilDate, List.of(), currencypairs, false);
  }
}
