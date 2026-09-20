package grafioschtrader.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CouponDayCountTest {

  @ParameterizedTest
  @CsvSource({ "CHF, THIRTY_E_360", "chf, THIRTY_E_360", "' CHF ', THIRTY_E_360", "EUR, ACT_ACT_ICMA",
      "USD, ACT_ACT_ICMA", "GBP, ACT_ACT_ICMA" })
  void proposesTheUsualConventionOfTheBondMarket(String currency, CouponDayCount expected) {
    assertThat(CouponDayCount.defaultForCurrency(currency)).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = { "  " })
  void proposesNothingWithoutACurrency(String currency) {
    assertThat(CouponDayCount.defaultForCurrency(currency)).isNull();
  }

  @Test
  void everyDeviatingCurrencyDiffersFromTheFallback() {
    assertThat(CouponDayCount.CURRENCY_DEFAULTS.values()).doesNotContain(CouponDayCount.FALLBACK);
  }
}
